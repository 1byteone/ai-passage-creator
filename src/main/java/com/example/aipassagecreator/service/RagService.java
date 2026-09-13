package com.example.aipassagecreator.service;

import com.example.aipassagecreator.mapper.ArticleMapper;
import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.model.po.SkillExecutionPo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RAG 向量检索服务
 *
 * <p>职责：文章/Skill 产出 → 分块 → DashScope embedding → 入向量库；
 * 语义检索（按 userId + type 隔离）。</p>
 */
@Slf4j
@Service
public class RagService {

    /** 分块参数：800 tokens/块（TokenTextSplitter 无重叠概念，第 2 参 200 为最小块字符数），少于 300 字符不嵌入，最多 50 块 */
    private static final int CHUNK_SIZE = 800;
    private static final int MIN_CHUNK_SIZE_CHARS = 200;
    private static final int MIN_CHUNK_TO_EMBED = 300;

    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;
    private final TokenTextSplitter splitter;
    private final ArticleMapper articleMapper;
    /** 向量库是否返回余弦距离（越小越相关）：pgvector 是，SimpleVectorStore 返回相似度 */
    private final boolean distanceScore;
    /** 默认相似度阈值（0~1，越大越相关），可经 rag.search.threshold 配置 */
    private final double defaultThreshold;
    /**
     * 按 taskId 的条纹锁：让同一文章的「索引」与「删除」互斥，防异步竞态。
     * 固定 64 条纹（内存有界）；同 taskId 必映射同一条纹，不同 taskId 可能共享条纹
     * 仅造成轻微伪并发，无正确性问题。
     */
    private static final int LOCK_STRIPES = 64;
    private final Object[] taskLocks = new Object[LOCK_STRIPES];

    /** 检索失败熔断：连续失败阈值 + 短路时长，防对已挂向量库的雪崩重试 */
    private static final int SEARCH_FAILURE_THRESHOLD = 5;
    private static final long SEARCH_CIRCUIT_OPEN_MS = 30_000;
    private final AtomicInteger searchFailures = new AtomicInteger();
    private final AtomicLong circuitOpenUntil = new AtomicLong(0);

    public RagService(VectorStore vectorStore, ObjectMapper objectMapper, ArticleMapper articleMapper,
                      @Value("${rag.search.threshold:0.25}") double defaultThreshold) {
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
        this.articleMapper = articleMapper;
        this.splitter = new TokenTextSplitter(CHUNK_SIZE, MIN_CHUNK_SIZE_CHARS, MIN_CHUNK_TO_EMBED, 50, true);
        this.distanceScore = vectorStore instanceof PgVectorStore;
        this.defaultThreshold = defaultThreshold;
        Arrays.setAll(taskLocks, i -> new Object());
    }

    private Object taskLock(String taskId) {
        return taskLocks[(taskId.hashCode() & 0x7fffffff) % LOCK_STRIPES];
    }

    /** 检索命中项（供前端相关文章/历史参考/文档问答使用）— content 截断 300 字符 */
    public record RagHit(String refId, String title, String content, double score, String type) {
    }

    /** 检索命中项（供生成增强链路使用）— content 为完整 chunk 文本 */
    public record RagChunk(String refId, String title, String content, double score, String type) {
    }

    /** 文章完成 → 分块嵌入入向量库（metadata: taskId/userId/type=article/title） */
    public void indexArticle(Article article) {
        if (article == null || article.getTaskId() == null || article.getUserId() == null) {
            return;
        }
        // 逻辑删除的文章不入向量：防「删除后异步索引仍执行」导致僵尸向量复活
        if (Integer.valueOf(1).equals(article.getIsDelete())) {
            log.info("RAG 跳过已删除文章索引: taskId={}", article.getTaskId());
            return;
        }
        // 与 deleteByTaskId 按 taskId 互斥，防止并发下删除/索引交错产生竞态
        synchronized (taskLock(article.getTaskId())) {
            // 先清除旧向量，使重复索引幂等
            deleteByTaskId(article.getTaskId());
            String text = buildArticleText(article);
            if (text.isBlank()) {
                return;
            }
            // 截断长文本，避免 oversized tail chunk 超 embedding 上限
            if (text.length() > 30000) {
                text = truncate(text, 30000);
            }
            List<Document> docs = new ArrayList<>();
            for (Document chunkDoc : splitter.split(new Document(text))) {
                String chunk = chunkDoc.getText();
                Map<String, Object> metadata = Map.of(
                        "type", "article",
                        "taskId", article.getTaskId(),
                        "userId", article.getUserId(),       // Long，与检索时类型一致
                        "title", article.getMainTitle() != null ? article.getMainTitle() : article.getTopic() == null ? "" : article.getTopic());
                docs.add(new Document(chunk, metadata));
            }
            if (!docs.isEmpty()) {
                vectorStore.add(docs);
                log.info("RAG 已索引文章: taskId={}, chunks={}", article.getTaskId(), docs.size());
            }
        }
    }

    /** Skill 完成 → 输出 JSON 分块嵌入（metadata: executionId/userId/skillName/type=skill） */
    public void indexSkill(SkillExecutionPo po, Map<String, Object> output) {
        if (po == null || po.getSkillExecutionId() == null || po.getUserId() == null || output == null || output.isEmpty()) {
            return;
        }
        String text;
        try {
            text = objectMapper.writeValueAsString(output);
        } catch (JsonProcessingException e) {
            log.warn("Skill 输出序列化失败，跳过索引: executionId={}", po.getSkillExecutionId());
            return;
        }
        if (text.length() > 30000) {
            text = truncate(text, 30000);
        }
        text = sanitizeForIndex(text);
        List<Document> docs = new ArrayList<>();
        for (Document chunkDoc : splitter.split(new Document(text))) {
            String chunk = chunkDoc.getText();
            Map<String, Object> metadata = Map.of(
                    "type", "skill",
                    "executionId", po.getSkillExecutionId(),
                    "userId", po.getUserId(),           // Long
                    "skillName", po.getSkillName() == null ? "" : po.getSkillName());
            docs.add(new Document(chunk, metadata));
        }
        if (!docs.isEmpty()) {
            vectorStore.add(docs);
            log.info("RAG 已索引 Skill: executionId={}, chunks={}", po.getSkillExecutionId(), docs.size());
        }
    }

    /** 语义检索，按 type + userId 隔离，使用默认相似度阈值 */
    public List<RagHit> search(String query, String type, Long userId, int topK) {
        return search(query, type, userId, topK, defaultThreshold);
    }

    /**
     * 语义检索，按 type + userId 隔离，带相似度阈值。
     * <p>返回的 {@code RagHit.score} 恒为相似度（越大越相关，0~1），与向量库实现无关：
     * pgvector 返回余弦距离（越小越相关），此处归一化为 {@code 1 - distance}，
     * 保证前端按 score 降序排序、去重取最高分始终正确。</p>
     */
    public List<RagHit> search(String query, String type, Long userId, int topK, double similarityThreshold) {
        return doSearch(query, type, userId, topK, similarityThreshold).stream()
                .map(c -> new RagHit(c.refId(), c.title(), truncate(c.content(), 300), c.score(), c.type()))
                .toList();
    }

    /**
     * 生成增强检索：返回完整 chunk 文本（供重排/注入 prompt 使用）。
     * 语义与 {@link #search} 一致（租户隔离 / 阈值 / 熔断），但不截断 content。
     */
    public List<RagChunk> searchChunks(String query, String type, Long userId, int topK, double similarityThreshold) {
        return doSearch(query, type, userId, topK, similarityThreshold);
    }

    /** 检索核心：相似度查询 + 归一化分数，返回全文 chunk（截断由上层 search 决定） */
    private List<RagChunk> doSearch(String query, String type, Long userId, int topK, double similarityThreshold) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        // 熔断期内直接返回空：向量库故障时降级为"无参考"，不阻塞生成/展示
        if (isSearchCircuitOpen()) {
            log.warn("RAG 检索熔断中，直接返回空");
            return List.of();
        }
        int k = Math.max(1, Math.min(topK <= 0 ? 5 : topK, 20));

        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(k)
                .similarityThreshold(similarityThreshold);
        Filter.Expression filter = buildFilter(type, userId);
        if (filter != null) {
            builder.filterExpression(filter);
        }

        try {
            List<RagChunk> hits = vectorStore.similaritySearch(builder.build()).stream()
                    .map(doc -> new RagChunk(
                            refIdOf(doc),
                            titleOf(doc),
                            doc.getText(),
                            normalizedScore(doc.getScore()),
                            (String) doc.getMetadata().getOrDefault("type", "")))
                    .toList();
            searchFailures.set(0);
            return hits;
        } catch (Exception e) {
            recordSearchFailure();
            log.warn("RAG 检索失败，返回空列表（err={}）", e.getMessage());
            return List.of();
        }
    }

    private boolean isSearchCircuitOpen() {
        return System.currentTimeMillis() < circuitOpenUntil.get();
    }

    private void recordSearchFailure() {
        int failures = searchFailures.incrementAndGet();
        if (failures >= SEARCH_FAILURE_THRESHOLD) {
            circuitOpenUntil.set(System.currentTimeMillis() + SEARCH_CIRCUIT_OPEN_MS);
            searchFailures.set(0);
            log.warn("RAG 检索连续失败 {} 次，熔断 {}ms", failures, SEARCH_CIRCUIT_OPEN_MS);
        }
    }

    /**
     * 外部文档 → 分块嵌入入向量库（metadata: type=document/title/source）。
     * <p>全站共享知识库：不存 userId，所有登录用户可检索。按 source 幂等
     * （重复上传同 source 先清旧向量再插入，避免累积）。</p>
     */
    public void indexDocument(String title, String source, String text) {
        indexDocument(title, source, text, Map.of(
                "sourceType", "MANUAL",
                "domain", "business",
                "documentKind", "reference",
                "status", "ACTIVE"));
    }

    /** 研发知识文档索引：保留来源、领域、版本和章节信息，供只读知识库检索使用。 */
    public void indexDocument(String title, String source, String text, Map<String, Object> knowledgeMetadata) {
        if (text == null || text.isBlank()) {
            return;
        }
        // 共享文档对全站登录用户开放，入库前必须清洗（防注入/防 XSS）
        text = sanitizeForIndex(text);
        // 幂等：按 source 清旧向量（失败仅告警，不阻断插入）
        if (source != null && !source.isBlank()) {
            deleteBySource(source);
        }
        if (text.length() > 30000) {
            text = truncate(text, 30000);
        }
        List<Document> docs = new ArrayList<>();
        for (Document chunkDoc : splitter.split(new Document(text))) {
            String chunk = chunkDoc.getText();
            java.util.Map<String, Object> metadata = new java.util.HashMap<>(Map.of(
                    "type", "document",
                    "title", title == null ? "" : title,
                    "source", source == null ? "" : source));
            if (knowledgeMetadata != null) {
                metadata.putAll(knowledgeMetadata);
            }
            docs.add(new Document(chunk, metadata));
        }
        if (!docs.isEmpty()) {
            vectorStore.add(docs);
            log.info("RAG 已索引文档: title={}, chunks={}", title, docs.size());
        }
    }

    /** 供 RagDocumentStore 入库前清洗（P2 知识库正文同策略） */
    public String sanitizeForIndexPublic(String text) {
        return sanitizeForIndex(text);
    }

    /** 按 source 删除文档向量（幂等清理，供文档覆盖/删除用） */
    public void deleteBySource(String source) {
        if (source == null || source.isBlank()) {
            return;
        }
        try {
            Filter.Expression filter = new FilterExpressionBuilder().eq("source", source).build();
            vectorStore.delete(filter);
            log.info("RAG 已删除文档向量: source={}", source);
        } catch (Exception e) {
            log.warn("RAG 删除文档向量失败: source={}, err={}", source, e.getMessage());
        }
    }

    /** 异步索引入口（供文章/Skill 完成点调用，失败静默不影响主流程） */
    @Async("ragExecutor")
    public void indexArticleAsync(Article article) {
        try {
            if (article == null || article.getTaskId() == null) {
                return;
            }
            // 异步执行时以 DB 为准重新校验：防止「删除先于索引执行」时用过期对象插入僵尸向量
            Article fresh = articleMapper.selectOneByQuery(
                    QueryWrapper.create().eq(Article::getTaskId, article.getTaskId()));
            if (fresh == null || Integer.valueOf(1).equals(fresh.getIsDelete())) {
                log.info("RAG 索引跳过：文章已删除或不存在, taskId={}", article.getTaskId());
                return;
            }
            indexArticle(fresh);
        } catch (Exception e) {
            log.warn("RAG 文章索引失败（不影响业务）: taskId={}, err={}", article == null ? null : article.getTaskId(), e.getMessage());
        }
    }

    @Async("ragExecutor")
    public void indexSkillAsync(SkillExecutionPo po, Map<String, Object> output) {
        try {
            indexSkill(po, output);
        } catch (Exception e) {
            log.warn("RAG Skill 索引失败（不影响业务）: executionId={}, err={}", po == null ? null : po.getSkillExecutionId(), e.getMessage());
        }
    }

    /** 删除某文章的全部向量（文章被删时同步清理，防孤儿数据；与索引按 taskId 互斥） */
    public void deleteByTaskId(String taskId) {
        if (taskId == null || taskId.isBlank()) return;
        synchronized (taskLock(taskId)) {
            try {
                Filter.Expression filter = new FilterExpressionBuilder().eq("taskId", taskId).build();
                vectorStore.delete(filter);
                log.info("RAG 已删除文章向量: taskId={}", taskId);
            } catch (Exception e) {
                log.warn("RAG 删除文章向量失败（不影响业务）: taskId={}, err={}", taskId, e.getMessage());
            }
        }
    }

    @Async("ragExecutor")
    public void deleteByTaskIdAsync(String taskId) {
        deleteByTaskId(taskId);
    }

    // ─── helpers ──────────────────────────────

    private String buildArticleText(Article a) {
        StringBuilder sb = new StringBuilder();
        if (a.getMainTitle() != null) sb.append("标题：").append(a.getMainTitle()).append('\n');
        if (a.getTopic() != null) sb.append("选题：").append(a.getTopic()).append('\n');
        if (a.getOutline() != null) {
            try {
                sb.append("大纲：").append(objectMapper.writeValueAsString(a.getOutline())).append('\n');
            } catch (JsonProcessingException ignored) {
                // outline JSON 解析失败则跳过
            }
        }
        if (a.getFullContent() != null) sb.append(a.getFullContent());
        else if (a.getContent() != null) sb.append(a.getContent());
        return sanitizeForIndex(sb.toString());
    }

    /**
     * 入库前清洗（纵深防御，防投毒内容进入生成 prompt / 渲染引用）。
     * <ul>
     *   <li>剥离 HTML 标签：防 `<img onerror=…>` 等经参考块渲染成 XSS</li>
     *   <li>丢弃强指令注入行：忽略/ignore previous/system prompt 等，防文章正文或共享文档
     *       携带的"忽略系统提示"类指令在检索命中拼进 prompt 时劫持模型</li>
     * </ul>
     * 保守起见只过滤最强签名，避免误伤正常内容。
     */
    private String sanitizeForIndex(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String noHtml = text.replaceAll("<[^>]+>", " ");
        StringBuilder sb = new StringBuilder();
        for (String line : noHtml.split("\n")) {
            String lower = line.trim().toLowerCase();
            boolean injection = lower.startsWith("忽略") || lower.startsWith("ignore previous")
                    || lower.startsWith("ignore all") || lower.contains("system prompt")
                    || lower.startsWith("从现在开始") || lower.startsWith("新的指令是");
            if (!injection) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * 构建过滤表达式（编程式 FilterExpressionBuilder，避免字符串拼接注入风险）。
     * <p>隔离语义（后端为唯一权威，前端不应再二次过滤）：
     * <ul>
     *   <li>admin（userId=null）：type 指定则只查该类型，否则全站（含 document）</li>
     *   <li>普通用户 + 未指定 type：个人内容 OR 全站共享文档</li>
     *   <li>普通用户 + type=document：只看共享文档</li>
     *   <li>普通用户 + 指定 type（article/skill）：只查个人该类型内容，不含共享文档</li>
     * </ul></p>
     */
    private Filter.Expression buildFilter(String type, Long userId) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        if (userId == null) {
            // admin：type 指定则过滤 type，否则全站
            return type != null && !type.isBlank() ? b.eq("type", type).build() : null;
        }
        // 普通用户
        if (type == null || type.isBlank()) {
            // 个人内容 OR 全站共享文档
            return b.or(b.eq("userId", userId), b.eq("type", "document")).build();
        }
        if ("document".equals(type)) {
            // 只看共享文档
            return b.eq("type", "document").build();
        }
        // 指定类型：只查个人该类型内容
        return b.and(b.eq("userId", userId), b.eq("type", type)).build();
    }

    /** 归一化分数：距离型向量库（pgvector）转相似度（1-distance），相似度型原样保留 */
    private double normalizedScore(Double score) {
        if (score == null) {
            return 0.0;
        }
        return distanceScore ? 1.0 - score : score;
    }

    private String refIdOf(Document doc) {
        Object id = doc.getMetadata().getOrDefault("taskId", doc.getMetadata().get("executionId"));
        // document 类型无 taskId/executionId，用 source 兜底（前端跳转不显示空串）
        if (id == null) {
            id = doc.getMetadata().get("source");
        }
        return id == null ? "" : String.valueOf(id);
    }

    private String titleOf(Document doc) {
        Object t = doc.getMetadata().getOrDefault("title", doc.getMetadata().get("skillName"));
        return t == null ? "" : String.valueOf(t);
    }

    /** 截断文本（按 code point 边界，避免截断 surrogate pair 导致 Jackson 序列化失败） */
    private String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        int cut = max;
        while (cut > 0 && Character.isLowSurrogate(s.charAt(cut))) cut--;
        return s.substring(0, cut) + "…";
    }
}
