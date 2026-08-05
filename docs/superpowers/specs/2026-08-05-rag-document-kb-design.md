# RAG 文档知识库 — 8 份技术文档向量化入库

日期: 2026-08-05
状态: 已批准（含三遍审计修订）

## Context

RAG 向量库（Supabase pgvector，已接入）目前只有 `article`/`skill` 两种索引入口，由业务完成时自动触发。用户希望用 anysearch 搜索 8 份适配项目的公开技术文档，上传入库做文本向量化，作为全站共享知识库。

**现状**：`RagController` 只有 `search`，无上传接口；`RagService` 的 `indexArticle`/`indexSkill` 是唯一索引入口；检索隔离按 `userId + type` 过滤（普通用户只搜自己的 article/skill）。

## 决策（已与用户对齐）

1. **文档来源**：anysearch 搜公开技术文档（Spring AI RAG 实践、Vue 3 优化、MyBatis-Flex、MySQL 索引等适配项目技术栈）
2. **上传通道**：`RagService` 新增通用 `indexDocument(title, source, text)` 方法
3. **隔离范围**：全站共享（文档不存 userId，所有登录用户可检索）
4. **执行方式**：可持续功能（加正式 API 端点 `POST /rag/document`）
5. **共享检索**：普通用户检索时能同时看到自己的内容 + 全站共享文档

## 三遍审计修订（5 项）

### 🔴 C1: 前端显式 `type=article` 会屏蔽共享文档
- 证据：`ArticleCreatePage.vue:1260`、`ArticleDetailPage.vue:345` 调 `searchRag(val, { type: 'article' })`
- 修复：`buildFilter` 对普通用户 + 指定 type 时改为
  `(type=<requested> AND userId=me) OR type=document`
  → 指定类型的个人内容 + 全站共享文档同时命中

### 🔴 C2: `userId` Long vs jsonb text 比较
- pgvector `metadata->>'userId'` 返回 text，Spring AI 比较时 Long 转字符串
- 现有 article/skill 已用 Long 且内部一致，不回归；**document 不存 userId** 规避此坑，用 `type=document` 独立分支过滤

### 🟠 C3: `ALLOWED_TYPES` 白名单未含 document
- `RagController.java:47` 白名单加 `document`，否则前端传 `type=document` 被视为 null

### 🟠 C4: 文档上传缺幂等
- `indexDocument` 先按 `source` 删除旧文档向量再插入，避免重复上传产生重复向量

### 🟠 C5: `refIdOf` 对 document 取不到值
- `refIdOf`（取 taskId/executionId）对 document 返回空串；改为 `source` 兜底
- `titleOf` 已兼容 title（document 存 title）✓

## 方案

### 1. RagService 新增 `indexDocument`（幂等）
```java
/** 外部文档 → 分块嵌入入向量库（metadata: type=document/title/source，全站共享无 userId） */
public void indexDocument(String title, String source, String text) {
    if (text == null || text.isBlank()) return;
    // 幂等：按 source 清旧向量
    if (source != null && !source.isBlank()) deleteBySource(source);
    if (text.length() > 30000) text = text.substring(0, 30000);
    List<Document> docs = new ArrayList<>();
    for (Document chunkDoc : splitter.split(new Document(text))) {
        docs.add(new Document(chunkDoc.getText(), Map.of(
            "type", "document",
            "title", title == null ? "" : title,
            "source", source == null ? "" : source)));
    }
    if (!docs.isEmpty()) { vectorStore.add(docs); log.info("RAG 已索引文档: title={}, chunks={}", title, docs.size()); }
}

/** 按 source 删除文档向量（幂等清理） */
public void deleteBySource(String source) {
    if (source == null || source.isBlank()) return;
    try {
        Filter.Expression f = new FilterExpressionBuilder().eq("source", source).build();
        vectorStore.delete(f);
        log.info("RAG 已删除文档向量: source={}", source);
    } catch (Exception e) {
        log.warn("RAG 删除文档向量失败: source={}, err={}", source, e.getMessage());
    }
}
```

### 2. 检索隔离改造（`buildFilter` 支持共享文档）

`RagService.search` 语义变更：
- **admin**（userId=null）：type 指定则 `type=X`，否则无过滤（含 document）
- **普通用户**（userId 非 null）：
  - type 指定（article/skill）：`(type=X AND userId=me) OR type=document`
  - type 为 null：`(type=article OR type=skill) AND userId=me` **OR** `type=document` → 等价 `(userId=me AND type IN [article,skill]) OR (type=document)`

用 `FilterExpressionBuilder` 的 `in`/`or`/`and`/`group` 编程式构造（javap 已验证 1.0.0-M6 API 支持）。

### 3. RagController 新端点 + 白名单
- `ALLOWED_TYPES` 加 `document`
- 新增 `POST /rag/document`：`@AuthCheck(ADMIN_ROLE)`，body `{ title, source, text }`，调 `ragService.indexDocument`
- 新增 DTO `RagDocumentRequest`（title/source/text，@NotBlank text + @Size 上限）

### 4. 入库流程
anysearch 搜 8 主题 → `extract` 提取正文 → 保存 Markdown 到 `scripts/rag-docs/` → 调 `POST /rag/document` 入库 → 验证检索命中

## 安全
- 文档上传仅 admin（`@AuthCheck(ADMIN_ROLE)`）
- text 长度上限校验（防超长文档撑爆 embedding）
- 过滤仍用编程式 FilterExpressionBuilder（防注入）
- 文档为公开技术资料，无敏感信息

## 测试
- `RagServiceTest`（内存 SimpleVectorStore，无 Supabase 也可跑）：
  - `indexDocument` 分块/截断/空文本跳过/幂等（同 source 重复 index 不重复）
  - `search` 普通用户检索 article 时能命中共享 document（C1 修复验证）
  - `buildFilter` 各分支：admin 全站 / 普通用户指定 type / 普通用户 null type
- `RagControllerTest`：document 上传鉴权（非 admin 403）、参数校验
- 真实验证：应用已连 Supabase，8 份文档入库后检索命中（score > 0.7 中文）

## 不做的事
- 不改前端（现有 searchRag 无需改动，C1 修复在后端过滤层）
- 不改现有 article/skill 的隔离语义（新增 OR document 分支，向后兼容）
- 不做文档删除 UI（仅 admin 通过 API 幂等覆盖）

## 文件清单
| 文件 | 操作 |
|------|------|
| `src/main/java/.../service/RagService.java` | 新增 indexDocument/deleteBySource + buildFilter 改造 + refIdOf 兜底 |
| `src/main/java/.../controller/RagController.java` | 白名单加 document + POST /rag/document |
| `src/main/java/.../controller/RagController.java` 内 DTO | RagDocumentRequest |
| `src/test/java/.../service/RagServiceTest.java` | 新增 |
| `src/test/java/.../controller/RagControllerTest.java` | 新增（或扩展现有） |
| `scripts/rag-docs/` | 8 份 Markdown 文档（anysearch 提取） |
