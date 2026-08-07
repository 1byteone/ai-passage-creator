# RAG 阶段二设计文档（P1+P2+P3 补全闭环）

> 日期：2026-08-07 · 状态：已批准
> 范围：P1 Skill 产出索引接线 + P2 知识库管理 UI + P3 标题/大纲阶段注入

## 一、背景

阶段一已打通「正文阶段检索→重排→软参考注入→溯源」闭环（G1/G2），但存在三个闭环缺口：

1. **P1**：`indexSkillAsync` 零调用方，技能产出（调研/改写/翻译等）从未入向量库，`type=skill` 检索恒空
2. **P2**：后端 `POST /rag/document` 存在但文档只存向量库、无法列表/删除，且无前端页面
3. **P3**：RAG 只注入正文阶段，标题/大纲阶段不消费参考，G1 的"全阶段注入"目标未完成

## 二、P1 — Skill 产出索引接线

```
技能执行到达 SUCCESS 终态
  → SkillExecution.finishSuccess 持久化 outputData
  → ragService.indexSkillAsync(po, resultData)   ← 新接线
      ├─ outputData JSON → sanitizeForIndex → 分块 → DashScope embedding
      └─ metadata: type=skill / executionId / userId / skillName
```

**改造点**
- `SkillExecution.finishSuccess`（`src/.../skill/SkillExecution.java:293`）注入 `RagService`，SUCCESS 后异步索引
- 与文章索引同享 `ragExecutor` + 失败静默（`indexSkillAsync` 已内置 try-catch）

**效果**：`type=skill` 检索有数据；技能历史产出可被后续参考检索命中

**测试**：Skill 完成 → mock 验证 `indexSkillAsync` 调用 + skill 类型可检索

## 三、P2 — 知识库管理

### 数据模型：新建 `rag_document` 表（V7 迁移 + h2-schema 同步）

```
rag_document(
  id bigint PK auto,
  title varchar(200),
  source varchar(512)  -- 幂等键（重复上传覆盖），unique
  text longtext,       -- 正文（索引前按 30000 截断 + sanitizeForIndex）
  user_id bigint,      -- 上传人
  create_time datetime,
  update_time datetime
)
```

### 后端 API（RagController，全部 `@AuthCheck(admin)`）

| API | 说明 |
|---|---|
| `POST /rag/document` | 已有，改为**同时 upsert rag_document 表** + `indexDocument` 向量索引 |
| `GET /rag/documents` | 列表（分页 + 关键词模糊 title/source） |
| `DELETE /rag/document/{id}` | 删表行 + `ragService.deleteBySource` 清向量 |

### 前端 `/admin/knowledge`（admin 路由，侧边栏入口）

- **上传区**：粘贴文本 或 上传 `.md`/`.txt` 文件（`FileReader` 读正文 → 现有 JSON 接口，无需 multipart）
- **列表区**：文档表格（标题/source/字数/上传时间/操作），支持删除（`a-popconfirm` 二次确认）
- **权限**：路由守卫 `/admin/*` 已校验 `userRole === 'admin'`

### 数据流

```
admin 上传 → POST /rag/document → upsert rag_document + indexDocument(向量)
admin 删除 → DELETE /rag/document/{id} → 删表 + deleteBySource(清向量)
列表        → GET /rag/documents → 分页查表
```

## 四、P3 — 标题/大纲阶段注入

| 阶段 | query 来源 | 注入点（编排器） | 注入点（legacy） |
|---|---|---|---|
| 标题 | 选题 topic | `TitleGeneratorAgent` | `ArticleAgentService.agent1GenerateTitle` |
| 大纲 | 主标题+副标题 | `OutlineGeneratorAgent` | `ArticleAgentService.agent2GenerateOutline` |
| 正文 | 主标题+大纲章节（已实现） | `ContentGeneratorAgent` | 已实现 |

**同机制复用**：`RagAugmentationService.augment(query, userId)`（软参考 + 阈值 + 重排 + topN=5），三阶段一致。

**参考回流**：
- 编排器：phase1/phase2 的 `finalState.value(KEY_RAG_REFERENCES)` → `state.setRagReferences`（跨 ClassLoader 拷贝，同 phase3）
- `ArticleAsyncService`：每阶段结束 `saveStage(taskId, "title"/"outline", refs)` + SSE `RAG_REFERENCE_FOUND`（携带 stage/count）
- legacy 路径：3 个 agent 方法注入后写 `state.setRagReferences`

**query 构造**（与正文区分，避免跨阶段语义漂移）：
- 标题：`topic`
- 大纲：`mainTitle + subTitle`
- 正文：已有

## 五、明确不做（YAGNI）

- RAGAS 正式评估（G4）、docker-compose/.env Supabase 配置（G5）、hybrid 混合检索、Qwen3 embedding → 阶段三
- 文档标签/分类/全文编辑 → 本次只做上传+列表+删除（编辑可后续）

## 六、测试策略（TDD）

- **P1**：`SkillExecution` SUCCESS → `indexSkillAsync` 被调用；`type=skill` 检索可命中
- **P2**：
  - `RagDocumentStore`：upsert 幂等（同 source 覆盖）、列表、删除
  - 删除 → 表行消失 + 向量清空
  - RagController 权限（非 admin 拒绝）
  - 迁移版本唯一性（FlywayMigrationCompatibilityTest 自动覆盖）
- **P3**：`TitleGeneratorAgent`/`OutlineGeneratorAgent` 有命中 → prompt 含参考块；`state.ragReferences` 回流；SSE 分阶段事件

## 七、质量闸门

- 后端 `mvn test` 全绿（当前 471 → 预计 +若干）
- 前端 `npm run type-check` + `lint:check` + `test:skill`（pre-push 闸门自动跑）
- CI 全绿（Backend + Frontend）
