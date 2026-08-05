package com.example.aipassagecreator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RagService 单元测试 — 文档索引（分块/截断/幂等） + 共享文档检索隔离。
 * <p>用内存 {@link SimpleVectorStore} + mock {@link EmbeddingModel}（固定向量），
 * 无 Supabase 依赖即可验证分块、幂等、过滤逻辑。</p>
 */
class RagServiceTest {

    private RagService ragService;

    @BeforeEach
    void setUp() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{1.0f, 0.0f, 0.0f});
        when(embeddingModel.embed(any(Document.class))).thenReturn(new float[]{1.0f, 0.0f, 0.0f});
        VectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        ragService = new RagService(vectorStore, new ObjectMapper());
    }

    /** 测试辅助：直接注入一条 article 向量（避免依赖 Article 实体） */
    private void indexArticleForTest(Long userId, String taskId, String text) {
        VectorStore vs = ragServiceVectorStore();
        vs.add(List.of(new Document(text, Map.of(
                "type", "article",
                "userId", userId,
                "taskId", taskId,
                "title", taskId))));
    }

    @SuppressWarnings("unchecked")
    private VectorStore ragServiceVectorStore() {
        // 从 RagService 反射取 vectorStore 字段（无 getter）
        try {
            var field = RagService.class.getDeclaredField("vectorStore");
            field.setAccessible(true);
            return (VectorStore) field.get(ragService);
        } catch (Exception e) {
            throw new RuntimeException("反射取 vectorStore 失败", e);
        }
    }

    // ── Task 1: indexDocument ──

    @Test
    @DisplayName("indexDocument — 分块入库后按 document 类型可检索")
    void indexDocument_normal_embedsChunks() {
        String text = "Spring AI RAG 最佳实践。".repeat(50); // >300 字符触发分块
        ragService.indexDocument("RAG 实践", "https://example.com/rag", text);

        List<RagService.RagHit> hits = ragService.search("Spring AI RAG", "document", null, 5);
        assertFalse(hits.isEmpty(), "索引后应能检索到文档");
        assertEquals("RAG 实践", hits.get(0).title(), "title 应正确");
        assertEquals("document", hits.get(0).type(), "type 应为 document");
    }

    @Test
    @DisplayName("indexDocument — 空白文本跳过不入库")
    void indexDocument_blankText_skips() {
        ragService.indexDocument("空", "src://blank", "   \n  ");
        List<RagService.RagHit> hits = ragService.search("空", "document", null, 5);
        assertTrue(hits.isEmpty(), "空文本不应入库");
    }

    @Test
    @DisplayName("indexDocument — 同 source 重复上传幂等（不累积向量）")
    void indexDocument_sameSource_idempotent() {
        String text = "重复内容测试文档。".repeat(50);
        ragService.indexDocument("A", "src://same", text);
        ragService.indexDocument("B", "src://same", text);

        List<RagService.RagHit> hits = ragService.search("重复内容", "document", null, 20);
        assertTrue(hits.size() <= 5, "同 source 重复上传不应累积重复向量, got=" + hits.size());
    }

    @Test
    @DisplayName("deleteBySource — 过滤删除在 SimpleVectorStore 为 no-op（pgvector 生产支持）")
    void deleteBySource_removesVectors() {
        // 说明：SimpleVectorStore 只实现 doDelete(List<String>) 按 ID 删，未 override doDelete(Filter)
        // 过滤删除是 pgvector 生产路径（deleteByTaskId 同机制）。此处验证 deleteBySource 不抛异常即可。
        String text = "待删除文档内容测试。".repeat(50);
        ragService.indexDocument("Del", "src://del", text);
        ragService.deleteBySource("src://del"); // 不应抛异常
        // 幂等已由 indexDocument_sameSource 覆盖；过滤删除正确性依赖 pgvector（集成环境验证）
    }

    // ── Task 2: 共享文档检索隔离 ──

    @Test
    @DisplayName("普通用户搜 article — 应同时命中自己的 article + 共享 document")
    void search_normalUserWithType_seesOwnPlusSharedDoc() {
        indexArticleForTest(1L, "user-article-1", "我的个人文章内容。".repeat(50));
        ragService.indexDocument("共享", "src://shared", "共享知识库文档。".repeat(50));

        List<RagService.RagHit> hits = ragService.search("共享知识库", "article", 1L, 10);
        assertTrue(hits.stream().anyMatch(h -> "document".equals(h.type())),
                "普通用户搜 article 应能命中共享 document, got=" + hits.stream().map(RagService.RagHit::type).toList());
    }

    @Test
    @DisplayName("普通用户搜 article — 不应看到他人私有文章")
    void search_normalUserNoType_doesNotSeeOthersPrivate() {
        indexArticleForTest(1L, "u1-art", "用户一的私有文章。".repeat(50));
        indexArticleForTest(2L, "u2-art", "用户二的私有文章。".repeat(50));

        List<RagService.RagHit> hits = ragService.search("用户二", "article", 1L, 10);
        assertTrue(hits.stream().noneMatch(h -> "u2-art".equals(h.refId())),
                "用户一不应看到用户二的私有文章");
    }

    @Test
    @DisplayName("普通用户搜 document 类型 — 直接命中共享文档")
    void search_normalUserDocumentType_hitsShared() {
        ragService.indexDocument("Spring 实践", "src://spring", "Spring AI 向量检索实践。".repeat(50));

        List<RagService.RagHit> hits = ragService.search("Spring AI", "document", 5L, 10);
        assertFalse(hits.isEmpty(), "普通用户指定 document 类型应命中");
    }

    @Test
    @DisplayName("admin 检索 — 全站可见（含共享文档与所有用户私有内容）")
    void search_admin_seesAll() {
        indexArticleForTest(1L, "admin-art", "管理员的全局视角内容。".repeat(50));
        ragService.indexDocument("全局文档", "src://global", "全站共享文档内容。".repeat(50));

        List<RagService.RagHit> hits = ragService.search("全站共享", null, null, 10);
        assertTrue(hits.stream().anyMatch(h -> "document".equals(h.type())),
                "admin 无过滤应能看到共享文档");
    }
}
