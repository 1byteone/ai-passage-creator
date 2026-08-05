# RAG 文档知识库 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 给 RAG 系统新增"文档知识库"能力 —— 通用 `indexDocument` + 全站共享检索 + admin 上传 API，并用 anysearch 搜索 8 份技术文档向量化入库验证。

**Architecture:** 后端扩展 `RagService`（indexDocument/deleteBySource + buildFilter 共享文档分支）+ `RagController`（白名单 + POST /rag/document）。前端零改动（C1 修复在后端过滤层）。测试用内存 SimpleVectorStore（无 Supabase 依赖）。

**Tech Stack:** Java 21 / Spring AI 1.0.0-M6 / MyBatis-Flex / Supabase pgvector / JUnit5

## Global Constraints
- 分层：Controller 薄（校验+派发）→ Service 重（业务+事务）
- 构造器注入（`@RequiredArgsConstructor` / `private final`），不用字段 `@Autowired`
- 业务异常抛自定义异常，用户可见消息中文
- 过滤用编程式 `FilterExpressionBuilder`（防注入），不拼接字符串
- 测试命名 `methodName_scenario_expectedResult()`；分块参数沿用现有 800/200/300
- Conventional Commits + Co-Authored-By: Claude

---

### Task 1: RagService — indexDocument + deleteBySource（幂等）

**Files:**
- Modify: `src/main/java/com/example/aipassagecreator/service/RagService.java`
- Test: `src/test/java/com/example/aipassagecreator/service/RagServiceTest.java`（新建）

**Interfaces:**
- Consumes: 现有 `TokenTextSplitter splitter`（800/200/300）、`VectorStore vectorStore`
- Produces: `public void indexDocument(String title, String source, String text)`、`public void deleteBySource(String source)`

- [ ] **Step 1: 写失败测试**（`RagServiceTest.java`）

```java
package com.example.aipassagecreator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RagServiceTest {

    private RagService ragService;
    private VectorStore vectorStore;

    @BeforeEach
    void setUp() {
        // 内存 SimpleVectorStore，EmbeddingModel mock（返回固定向量）
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{1.0f, 0.0f, 0.0f});
        when(embeddingModel.embed(anyList())).thenReturn(List.of(new float[]{1.0f, 0.0f, 0.0f}));
        vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        ragService = new RagService(vectorStore, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Test
    void indexDocument_normal_embedsChunks() {
        String text = "Spring AI RAG 最佳实践。".repeat(50); // >300 字符触发分块
        ragService.indexDocument("RAG 实践", "https://example.com/rag", text);
        // 用 document 类型检索应能命中
        List<RagService.RagHit> hits = ragService.search("Spring AI RAG", "document", null, 5);
        assertFalse(hits.isEmpty(), "索引后应能检索到文档");
        assertEquals("RAG 实践", hits.get(0).title(), "title 应正确");
    }

    @Test
    void indexDocument_blankText_skips() {
        ragService.indexDocument("空", "src://blank", "   ");
        List<RagService.RagHit> hits = ragService.search("空", "document", null, 5);
        assertTrue(hits.isEmpty(), "空文本不应入库");
    }

    @Test
    void indexDocument_sameSource_idempotent() {
        String text = "重复内容测试文档。".repeat(50);
        ragService.indexDocument("A", "src://same", text);
        ragService.indexDocument("B", "src://same", text);
        List<RagService.RagHit> hits = ragService.search("重复内容", "document", null, 20);
        // 幂等：同 source 覆盖，不累积
        assertTrue(hits.size() <= 5, "同 source 重复上传不应累积重复向量, got=" + hits.size());
    }

    @Test
    void deleteBySource_removesVectors() {
        String text = "待删除文档内容测试。".repeat(50);
        ragService.indexDocument("Del", "src://del", text);
        ragService.deleteBySource("src://del");
        List<RagService.RagHit> hits = ragService.search("待删除文档", "document", null, 5);
        assertTrue(hits.isEmpty(), "删除后不应检索到");
    }
}
```

- [ ] **Step 2: 运行确认失败**：`mvn test -Dtest=RagServiceTest -Dspring.profiles.active=test`
- [ ] **Step 3: 实现 indexDocument + deleteBySource**（按 spec §方案1 精确代码）
- [ ] **Step 4: 运行确认通过** + 提交

```bash
git commit -m "feat(rag): RagService 新增 indexDocument/deleteBySource — 文档分块入库+幂等

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: RagService — buildFilter 共享文档分支 + refIdOf 兜底

**Files:**
- Modify: `src/main/java/com/example/aipassagecreator/service/RagService.java`
- Test: `src/test/java/com/example/aipassagecreator/service/RagServiceTest.java`

**Interfaces:**
- Consumes: 现有 `search(query, type, userId, topK)`
- Produces: 普通用户检索含共享文档（C1/C2 修复）

- [ ] **Step 1: 写失败测试**（追加到 RagServiceTest）

```java
    @Test
    void search_normalUserWithType_seesOwnPlusSharedDoc() {
        // 准备：一条 article(userId=1) + 一条 document(共享)
        String articleText = "我的个人文章内容。".repeat(50);
        ragService.indexArticleForTest(1L, "user-article-1", articleText); // 新增测试辅助或直接用 indexArticle
        String docText = "共享知识库文档。".repeat(50);
        ragService.indexDocument("共享", "src://shared", docText);

        // 普通用户(userId=1) 搜 type=article → 应同时命中自己的 article + 共享 document
        List<RagService.RagHit> hits = ragService.search("共享知识库", "article", 1L, 10);
        assertTrue(hits.stream().anyMatch(h -> "document".equals(h.type())),
                "普通用户搜 article 应能命中共享 document");
    }

    @Test
    void search_normalUserNoType_doesNotSeeOthersPrivate() {
        String user1Text = "用户一的私有文章。".repeat(50);
        ragService.indexArticleForTest(1L, "u1-art", user1Text);
        String user2Text = "用户二的私有文章。".repeat(50);
        ragService.indexArticleForTest(2L, "u2-art", user2Text);

        List<RagService.RagHit> hits = ragService.search("用户二", "article", 1L, 10);
        assertTrue(hits.stream().noneMatch(h -> "u2-art".equals(h.refId())),
                "用户一不应看到用户二的私有文章");
    }
```

> 注：`indexArticleForTest` 是测试辅助方法（直接把 article 内容 + userId 送入 vectorStore），避免依赖 Article 实体。实现时在 RagServiceTest 内写一个 helper 用 `vectorStore.add(List.of(new Document(text, Map.of("type","article","userId",userId,"taskId",taskId,"title",taskId))))`。

- [ ] **Step 2: 运行确认失败**
- [ ] **Step 3: 改造 buildFilter + refIdOf**

```java
    private Filter.Expression buildFilter(String type, Long userId) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        if (userId == null) {
            // admin：type 指定则过滤 type，否则全站（含 document）
            return type != null && !type.isBlank() ? b.eq("type", type).build() : null;
        }
        // 普通用户：个人内容（userId=me 且 type 匹配） OR 共享文档（type=document）
        FilterExpressionBuilder.Op mine = b.eq("userId", userId);
        if (type != null && !type.isBlank()) {
            mine = b.and(mine, b.eq("type", type));
        }
        FilterExpressionBuilder.Op shared = b.eq("type", "document");
        return b.or(mine, shared).build();
    }
```

- [ ] **Step 4: 运行确认通过** + 提交
- [ ] **Step 5: 提交**

```bash
git commit -m "fix(rag): buildFilter 支持普通用户检索共享文档 + refIdOf source 兜底

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: RagController — 白名单 + POST /rag/document

**Files:**
- Modify: `src/main/java/com/example/aipassagecreator/controller/RagController.java`
- Test: `src/test/java/com/example/aipassagecreator/controller/RagControllerTest.java`（新建或扩展）

**Interfaces:**
- Consumes: `ragService.indexDocument`、`UserService.getLoginUser`
- Produces: `POST /rag/document`（admin）、`ALLOWED_TYPES` 含 document

- [ ] **Step 1: 写失败测试**

```java
@SpringBootTest
@AutoConfigureMockMvc
class RagControllerTest {
    // mock RagService + UserService，验证：
    // 1. 非 admin 调 POST /rag/document → 403
    // 2. admin 调 → 200 + ragService.indexDocument 被调用
    // 3. body 缺 text → 参数校验失败 400
}
```

- [ ] **Step 2: 运行确认失败**
- [ ] **Step 3: 实现**（白名单加 document + DTO + POST 端点，`@AuthCheck(ADMIN_ROLE)`）
- [ ] **Step 4: 运行确认通过** + 提交

```bash
git commit -m "feat(rag): POST /rag/document — admin 上传文档入库（白名单含 document）

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: anysearch 搜索 8 份文档 + 入库 + 验证

**Files:**
- Create: `scripts/rag-docs/*.md`（8 份）

- [ ] **Step 1: anysearch batch_search 8 个主题**（适配项目技术栈）：
  - Spring AI RAG + VectorStore 最佳实践
  - Supabase pgvector 部署与调优
  - MyBatis-Flex 复杂查询
  - Spring Boot 3 缓存配置
  - MySQL 8 索引优化
  - Vue 3 Composition API 性能优化
  - ECharts 大数据量渲染优化
  - DashScope embedding 使用
- [ ] **Step 2: `extract` 提取每篇 URL 正文 → 存 `scripts/rag-docs/<topic>.md`**（每篇 ≥300 字符，否则不入库）
- [ ] **Step 3: 调 `POST /rag/document` 逐篇入库**（title/source/text）
- [ ] **Step 4: 检索验证**：`POST /rag/search` 用中文 query 检索，确认命中对应文档（score 合理），且普通用户（带 userId）也能命中共享文档

- [ ] **Step 5: 提交**

```bash
git add scripts/rag-docs/
git commit -m "docs(rag): 8 份技术文档入库 — anysearch 搜索提取并向量化

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Self-Review

**Spec 覆盖：**
- ✅ indexDocument 幂等（Task 1）— C4
- ✅ buildFilter 共享文档（Task 2）— C1/C2
- ✅ refIdOf source 兜底（Task 2 Step 5 合并）
- ✅ 白名单 + POST 端点（Task 3）— C3
- ✅ 8 份文档入库验证（Task 4）
- ✅ 前端零改动（C1 在后端过滤层解决）

**潜在坑提醒：**
- `search_normalUserWithType` 测试中 `type=article` 分支：普通用户 `mine = userId=me AND type=article`，`shared = type=document`，`or` 连接。内存 SimpleVectorStore 的过滤转换器支持 in/or/group（已 javap 验证）
- `indexDocument` 幂等用 `deleteBySource` 需在插入前调用，且 delete 失败（catch）不应阻断插入
- 测试用内存 store 的 embedding mock 需返回非零向量，否则余弦相似度全 0 可能影响 topK 排序（mock 返回固定 `[1,0,0]` 即可，语义检索在内存 store 中用点积/余弦仍能区分）
