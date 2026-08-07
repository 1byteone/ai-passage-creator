package com.example.aipassagecreator.service;

import com.alibaba.cloud.ai.document.DocumentWithScore;
import com.alibaba.cloud.ai.model.RerankModel;
import com.alibaba.cloud.ai.model.RerankRequest;
import com.alibaba.cloud.ai.model.RerankResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RagAugmentationService 单元测试 — 检索→重排→过滤→软参考块格式化，以及失败/空命中降级。
 */
class RagAugmentationServiceTest {

    private RagService ragService;
    private RerankModel rerankModel;
    private RagAugmentationService service;

    @BeforeEach
    void setUp() {
        ragService = mock(RagService.class);
        rerankModel = mock(RerankModel.class);
        service = new RagAugmentationService(ragService, rerankModel);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "retrievalTopK", 15);
        ReflectionTestUtils.setField(service, "searchThreshold", 0.25);
        ReflectionTestUtils.setField(service, "rerankThreshold", 0.0);
        ReflectionTestUtils.setField(service, "topN", 5);
    }

    private RagService.RagChunk chunk(String refId, String title, String text, double score, String type) {
        return new RagService.RagChunk(refId, title, text, score, type);
    }

    private void stubRerank(double... scores) {
        when(rerankModel.call(any(RerankRequest.class))).thenAnswer(inv -> {
            RerankRequest req = inv.getArgument(0);
            List<DocumentWithScore> results = new java.util.ArrayList<>();
            for (int i = 0; i < req.getInstructions().size(); i++) {
                Document doc = req.getInstructions().get(i);
                double score = i < scores.length ? scores[i] : 0.5;
                results.add(DocumentWithScore.builder().withScore(score).withDocument(doc).build());
            }
            return new RerankResponse(results);
        });
    }

    @Test
    @DisplayName("augment — 检索命中并重排后，生成含参考标题与【参考资料】分隔的 prompt 块")
    void augment_withCandidates_formatsReferenceBlock() {
        when(ragService.searchChunks(anyString(), any(), any(), anyInt(), anyDouble()))
                .thenReturn(List.of(
                        chunk("t1", "历史文章一", "历史文章一的正文片段内容。", 0.8, "article"),
                        chunk("t2", "共享文档", "共享文档的正文片段内容。", 0.7, "document")));
        stubRerank(0.92, 0.61);

        RagAugmentationService.AugmentedResult result = service.augment("如何写爆款标题", 1L);

        assertFalse(result.isEmpty(), "有命中不应为空");
        assertTrue(result.promptBlock().contains("【参考资料】"), "参考块应带分隔标签");
        assertTrue(result.promptBlock().contains("历史文章一"), "应含参考标题");
        assertTrue(result.references().get(0).score() > 0.9, "重排分应生效");
    }

    @Test
    @DisplayName("augment — 无检索命中返回 EMPTY，不阻断生成")
    void augment_noCandidates_empty() {
        when(ragService.searchChunks(anyString(), any(), any(), anyInt(), anyDouble()))
                .thenReturn(List.of());

        assertTrue(service.augment("冷门主题", 1L).isEmpty());
    }

    @Test
    @DisplayName("augment — userId 缺失 fail-closed（不按 admin 全站检索）")
    void augment_nullUserId_failClosed() {
        assertTrue(service.augment("主题", null).isEmpty());
    }

    @Test
    @DisplayName("augment — 重排失败回退原始相似度顺序，仍返回参考")
    void augment_rerankFailure_fallsBackToRawOrder() {
        when(ragService.searchChunks(anyString(), any(), any(), anyInt(), anyDouble()))
                .thenReturn(List.of(chunk("t1", "标题", "正文内容。", 0.75, "article")));
        when(rerankModel.call(any(RerankRequest.class))).thenThrow(new RuntimeException("rerank 500"));

        RagAugmentationService.AugmentedResult result = service.augment("主题", 1L);

        assertFalse(result.isEmpty(), "重排失败应回退原始顺序而非空");
        assertTrue(result.promptBlock().contains("标题"));
    }

    @Test
    @DisplayName("augment — 重排阈值过滤低分参考")
    void augment_rerankThreshold_filtersLow() {
        when(ragService.searchChunks(anyString(), any(), any(), anyInt(), anyDouble()))
                .thenReturn(List.of(
                        chunk("t1", "高分", "高分内容。", 0.8, "article"),
                        chunk("t2", "低分", "低分内容。", 0.6, "article")));
        stubRerank(0.9, 0.3);
        ReflectionTestUtils.setField(service, "rerankThreshold", 0.5);

        RagAugmentationService.AugmentedResult result = service.augment("主题", 1L);

        assertFalse(result.isEmpty());
        assertTrue(result.references().stream().noneMatch(r -> "低分".equals(r.title())),
                "重排分低于阈值的参考应被过滤");
    }

    @Test
    @DisplayName("augment — enabled=false 时不注入")
    void augment_disabled_empty() {
        ReflectionTestUtils.setField(service, "enabled", false);
        when(ragService.searchChunks(anyString(), any(), any(), anyInt(), anyDouble()))
                .thenReturn(List.of(chunk("t1", "标题", "内容。", 0.8, "article")));
        stubRerank(0.9);

        assertTrue(service.augment("主题", 1L).isEmpty());
    }
}
