package com.example.aipassagecreator.service;

import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.model.po.SkillExecutionPo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG 向量检索服务
 *
 * <p>职责：文章/Skill 产出 → 分块 → DashScope embedding → 入向量库；
 * 语义检索（按 userId + type 隔离）。</p>
 */
@Slf4j
@Service
public class RagService {

    /** 分块参数：800 tokens 一块，重叠 200，少于 300 字符不嵌入 */
    private static final int CHUNK_SIZE = 800;
    private static final int CHUNK_OVERLAP = 200;
    private static final int MIN_CHUNK_TO_EMBED = 300;

    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;
    private final TokenTextSplitter splitter;

    public RagService(VectorStore vectorStore, ObjectMapper objectMapper) {
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
        this.splitter = new TokenTextSplitter(CHUNK_SIZE, 200, MIN_CHUNK_TO_EMBED, 50, true);
    }

    /** 检索命中项（供前端相关文章/历史参考/文档问答使用） */
    public record RagHit(String refId, String title, String content, double score, String type) {
    }

    /** 文章完成 → 分块嵌入入向量库（metadata: taskId/userId/type=article/title） */
    public void indexArticle(Article article) {
        if (article == null || article.getTaskId() == null || article.getUserId() == null) {
            return;
        }
        // 先清除旧向量，使重复索引幂等
        deleteByTaskId(article.getTaskId());
        String text = buildArticleText(article);
        if (text.isBlank()) {
            return;
        }
        // 截断长文本，避免 oversized tail chunk 超 embedding 上限
        if (text.length() > 30000) {
            text = text.substring(0, 30000);
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
            text = text.substring(0, 30000);
        }
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

    /** 语义检索，按 type + userId 隔离（不传 type/userId 则查全部） */
    public List<RagHit> search(String query, String type, Long userId, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int k = Math.max(1, Math.min(topK <= 0 ? 5 : topK, 20));

        SearchRequest.Builder builder = SearchRequest.builder().query(query).topK(k);
        Filter.Expression filter = buildFilter(type, userId);
        if (filter != null) {
            builder.filterExpression(filter);
        }

        return vectorStore.similaritySearch(builder.build()).stream()
                .map(doc -> new RagHit(
                        refIdOf(doc),
                        titleOf(doc),
                        truncate(doc.getText(), 300),
                        doc.getScore() == null ? 0.0 : doc.getScore(),
                        (String) doc.getMetadata().getOrDefault("type", "")))
                .toList();
    }

    /** 异步索引入口（供文章/Skill 完成点调用，失败静默不影响主流程） */
    @Async("ragExecutor")
    public void indexArticleAsync(Article article) {
        try {
            indexArticle(article);
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

    /** 删除某文章的全部向量（文章被删时同步清理，防孤儿数据） */
    public void deleteByTaskId(String taskId) {
        if (taskId == null || taskId.isBlank()) return;
        try {
            Filter.Expression filter = new FilterExpressionBuilder().eq("taskId", taskId).build();
            vectorStore.delete(filter);
            log.info("RAG 已删除文章向量: taskId={}", taskId);
        } catch (Exception e) {
            log.warn("RAG 删除文章向量失败（不影响业务）: taskId={}, err={}", taskId, e.getMessage());
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
        return sb.toString();
    }

    /**
     * 构建过滤表达式（编程式 FilterExpressionBuilder，避免字符串拼接注入风险）
     * type 与 userId 均为值传递而非拼接，不受注入影响。
     */
    private Filter.Expression buildFilter(String type, Long userId) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op expr = null;
        if (type != null && !type.isBlank()) {
            expr = b.eq("type", type);
        }
        if (userId != null) {
            FilterExpressionBuilder.Op uidExpr = b.eq("userId", userId);
            expr = (expr == null) ? uidExpr : b.and(expr, uidExpr);
        }
        return expr == null ? null : expr.build();
    }

    private String refIdOf(Document doc) {
        Object id = doc.getMetadata().getOrDefault("taskId", doc.getMetadata().get("executionId"));
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
