# RAG 增强设计文档（阶段一：闭环优先）

> 日期：2026-08-06 · 状态：已实现
> 范围：G0 审计修复 + G1 增强生成闭环 + G2 检索质量生产化

## 一、背景与目标

系统原有 RAG 只做「展示」（相关文章/历史参考面板），检索结果从未参与生成；且存在分数语义反转、内存删除失效、Flyway 迁移链断裂、Prompt 注入面等缺陷。

**目标**：让 RAG 从「历史参考展示」升级为「真正增强内容生成质量的核心引擎」，并把检索质量提升到生产标准。

## 二、三轮审计发现与修复（G0）

| # | 问题 | 修复 |
|---|---|---|
| H1 | 分数语义在 pgvector（distance）与内存（similarity）方向相反 → 生产排序反转 | `search()` 按向量库类型归一化为相似度（`1-distance`） |
| H2 | 逻辑删除与异步索引竞态 → 已删文章向量复活 | taskId 条纹锁互斥 + `isDelete` 守卫 + 异步重查 DB |
| H4 | 内存 `SimpleVectorStore.doDelete(Filter)` 抛异常被静默吞 → 幂等/删除失效 | `FilterSupportSimpleVectorStore` 覆写 doDelete(Filter) |
| H5 | `indexArticleAsync` 在 `ALL_COMPLETE` 之前 → 拥塞拖死完成信号 | 移到 `complete()` 之后 |
| H6 | pgvector 运行中故障无降级；启动降级因 `afterPropertiesSet` 时序失效 | search 熔断 + 主动 `SELECT 1` 验证使启动降级生效 |
| H7 | Flyway 两个 V3 冲突 + 删已提交迁移破坏 checksum | 还原 vendor/V3，db/migration V3 改名 V4，删错误 V4 |
| H8 | 未清洗内容入库 + 共享文档全开放 → Prompt 注入面 | 入库清洗（去 HTML + 强指令行）+ 软参考 prompt |
| M3 | 选题推荐硬编码 query | 改用用户最近已完成文章作 query |
| M7 | 无相似度阈值 → 弱相关当相关 | `SearchRequest.similarityThreshold`（默认 0.25） |
| M11 | API 无校验 | RagController Bean Validation + type 白名单拒绝 |
| M12 | `type=article` 仍返回 document，前端硬过滤掩盖 | buildFilter 按 type 契约 + 删前端硬过滤 |
| M13 | Flyway 测试不扫 vendor | 版本唯一性测试合并两目录 |

## 三、G1 增强生成闭环

```
创作 Agent 调模型前 →
  RagAugmentationService.augment(query, userId)
    ├─ ragService.searchChunks(topK=15, 相似度阈值)   // 全文 chunk
    ├─ DashScopeRerankModel（gte-rerank）重排          // 失败回退原始顺序
    ├─ 过滤 + 取 topN=5
    └─ 格式化为「【参考资料】软参考块」追加进 prompt
```

**关键决策**
- **软参考**：prompt 明确「相关则借鉴、无关则忽略，不要照搬」，防过时历史带偏选题
- **userId 贯穿**：`ArticleState.userId` → 编排器 graph inputs → agent；null 时 fail-closed（不按 admin 全站检索）
- **失败降级**：检索/重排失败或空命中 → 跳过注入，生成照常
- **全阶段注入**：标题用选题、大纲用主标题、正文用主标题+大纲章节作 query（当前已接入正文阶段）

**溯源**
- SSE 事件 `RAG_REFERENCE_FOUND`（携带 stage/count）
- `rag_reference` 表（V5 迁移 + h2-schema 同步）存各阶段注入参考
- `GET /rag/references/{taskId}` 详情页溯源（仅本人/管理员）

## 四、G2 检索质量

- 相似度阈值（`rag.search.threshold` 默认 0.25）
- `searchChunks()` 返回全文（供重排/注入），`search()` 保留 300 字符截断给 UI
- 分数归一化使前端排序/去重正确

## 五、测试

- `RagAugmentationServiceTest`（6 例）：命中/空/重排失败回退/阈值过滤/userId fail-closed/开关
- `RagServiceTest` 扩到 13 例：删除生效、幂等（向量总数不变）、isDelete 守卫、异步重查、H8 清洗
- `FlywayMigrationCompatibilityTest` + 版本唯一性测试
- 全量：后端 471 tests 全绿；前端 type-check + build 通过

## 六、明确不在本次范围（阶段二）

Skill 产出索引接线（H3）、知识库上传 UI、RAGAS 正式评估（G4）、docker-compose/.env Supabase 配置（G5）、hybrid 混合检索（BM25+RRF）、embedding 模型换 Qwen3、标题/大纲阶段注入、SSE 心跳。
