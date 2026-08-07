package com.example.aipassagecreator.service;

import com.alibaba.cloud.ai.model.RerankModel;
import com.alibaba.cloud.ai.model.RerankRequest;
import com.alibaba.cloud.ai.model.RerankResponse;
import com.alibaba.cloud.ai.document.DocumentWithScore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * RAG 检索增强服务 — 把向量检索结果转成可注入生成 prompt 的「软参考」上下文。
 *
 * <p>流程：粗召回（searchChunks, topK=15, 相似度阈值）→ DashScope 重排（gte-rerank）
 * → 过滤 + 取 topN → 格式化为参考块。任何环节失败或空命中都返回 {@link #EMPTY}，
 * 生成流程照常进行（软参考，不阻断）。</p>
 */
@Slf4j
@Service
public class RagAugmentationService {

    private final RagService ragService;
    private final RerankModel rerankModel;

    @Value("${rag.augment.enabled:true}")
    private boolean enabled;
    @Value("${rag.augment.retrieval-topk:15}")
    private int retrievalTopK;
    @Value("${rag.augment.search-threshold:0.25}")
    private double searchThreshold;
    @Value("${rag.augment.rerank-threshold:0.0}")
    private double rerankThreshold;
    @Value("${rag.augment.top-n:5}")
    private int topN;

    public RagAugmentationService(RagService ragService, RerankModel rerankModel) {
        this.ragService = ragService;
        this.rerankModel = rerankModel;
    }

    /** 单条参考（含重排后相关度分数） */
    public record Reference(String refId, String title, String content, double score, String type) {
    }

    /** 增强结果：引用列表 + 注入 prompt 的参考块；references 为空即 isEmpty */
    public record AugmentedResult(List<Reference> references, String promptBlock) {
        public static final AugmentedResult EMPTY = new AugmentedResult(List.of(), "");
        public boolean isEmpty() {
            return references.isEmpty();
        }
    }

    /**
     * 检索 → 重排 → 过滤 → 格式化参考块。
     *
     * @param query  当前阶段的检索主题（标题阶段用选题、大纲用主标题、正文用大纲要点）
     * @param userId 当前用户（租户隔离；null 时 fail-closed 跳过，避免按 admin 全站检索泄漏）
     */
    public AugmentedResult augment(String query, Long userId) {
        if (!enabled || query == null || query.isBlank()) {
            return AugmentedResult.EMPTY;
        }
        if (userId == null) {
            log.warn("RAG 增强跳过：userId 缺失（避免按 admin 全站检索泄漏跨租户内容）");
            return AugmentedResult.EMPTY;
        }
        try {
            List<RagService.RagChunk> candidates = ragService.searchChunks(
                    query, null, userId, retrievalTopK, searchThreshold);
            if (candidates.isEmpty()) {
                return AugmentedResult.EMPTY;
            }
            List<ScoredChunk> scored = rerank(query, candidates);
            List<Reference> top = scored.stream()
                    .filter(s -> s.score() >= rerankThreshold)
                    .limit(topN)
                    .map(s -> new Reference(
                            s.chunk().refId(), s.chunk().title(), s.chunk().content(), s.score(), s.chunk().type()))
                    .toList();
            if (top.isEmpty()) {
                return AugmentedResult.EMPTY;
            }
            log.info("RAG 增强命中 {} 条参考, query={}, userId={}", top.size(), shortQuery(query), userId);
            return new AugmentedResult(top, buildPromptBlock(top));
        } catch (Exception e) {
            log.warn("RAG 增强失败，跳过注入（不阻断生成）: {}", e.getMessage());
            return AugmentedResult.EMPTY;
        }
    }

    private record ScoredChunk(RagService.RagChunk chunk, double score) {
    }

    /** 重排（gte-rerank）；异常时回退为原始相似度顺序，保证增强链路不因重排失败中断 */
    private List<ScoredChunk> rerank(String query, List<RagService.RagChunk> candidates) {
        Map<String, RagService.RagChunk> byRefId = candidates.stream()
                .collect(java.util.stream.Collectors.toMap(RagService.RagChunk::refId, c -> c, (a, b) -> a));
        try {
            List<Document> docs = candidates.stream()
                    .map(c -> new Document(c.content(), Map.of(
                            "refId", c.refId(), "title", c.title(), "type", c.type())))
                    .toList();
            RerankResponse response = rerankModel.call(new RerankRequest(query, docs));
            return response.getResults().stream()
                    .map(dws -> mapScored(dws, byRefId))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.warn("RAG 重排失败，回退原始相似度顺序: {}", e.getMessage());
            return candidates.stream().map(c -> new ScoredChunk(c, c.score())).toList();
        }
    }

    /** 按重排响应的 refId 回查原始 chunk，用重排分数替代相似度分数 */
    private ScoredChunk mapScored(DocumentWithScore dws, Map<String, RagService.RagChunk> byRefId) {
        Document doc = dws.getOutput();
        if (doc == null || doc.getMetadata() == null) {
            return null;
        }
        String refId = (String) doc.getMetadata().get("refId");
        RagService.RagChunk chunk = byRefId.get(refId);
        if (chunk == null) {
            return null;
        }
        double score = dws.getScore() == null ? chunk.score() : dws.getScore();
        return new ScoredChunk(chunk, score);
    }

    /** 软参考块：明确告知模型「仅作参考，相关则借鉴、无关则忽略」，隔离在分隔标签内 */
    private String buildPromptBlock(List<Reference> refs) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n【参考资料】\n")
                .append("以下片段来自你的历史文章或平台共享文档，可能与当前主题相关也可能不相关。\n")
                .append("它们仅供你参考：若与当前主题相关，可借鉴其中的结构、论据或表达方式；\n")
                .append("若无关或相关性低，请直接忽略，不要照搬或抄袭其中的内容。\n");
        for (int i = 0; i < refs.size(); i++) {
            Reference r = refs.get(i);
            sb.append("<参考").append(i + 1).append("> 标题：《").append(r.title())
                    .append("》 相关度 ").append(String.format("%.2f", r.score())).append('\n')
                    .append(r.content()).append('\n');
        }
        sb.append("【参考资料结束】\n");
        return sb.toString();
    }

    private String shortQuery(String query) {
        return query.length() <= 30 ? query : query.substring(0, 30) + "…";
    }
}
