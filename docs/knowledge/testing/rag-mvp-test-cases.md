# RAG MVP 测试用例矩阵

## 测试信息

- 范围：当前项目 `dev_rag` 分支的研发知识库 MVP。
- 事实源：Git 根目录规范文档和 `docs/knowledge/**/*.md`。
- 测试原则：后端权限和状态以后端结果为准；检索结果必须可引用；没有证据不能判定完成。
- 执行状态：标记为“已执行”的用例已经由自动化测试覆盖；“待环境”需要真实数据库、向量服务或人工审核。当前 RAG 定向回归共 27 个测试，全部通过，其中包含 3 个真实 Spring AOP/Session 集成测试；检索排序另有回归用例锁定召回扩大、字段优先级和同源高分合并行为。

## 用例

| ID | 场景 | 预期结果 | 自动化/执行状态 |
|---|---|---|---|
| RAG-001 | Git 同步根目录规范文档 | 只读取固定文件 | 已执行 |
| RAG-002 | Git 同步 `docs/knowledge/**/*.md` | 递归读取 Markdown | 已执行 |
| RAG-003 | Git 同步 Markdown 章节 | 每章有稳定 `source` 和 `sectionPath` | 已执行 |
| RAG-004 | Git commit 获取失败 | 使用 `working-tree`，同步不阻断 | 待环境 |
| RAG-005 | 文档 UTF-8 读取失败 | 跳过坏文件并记录告警 | 已执行逻辑覆盖 |
| RAG-006 | 手工上传有效正文 | 登记为 `PENDING_REVIEW` | 已执行 |
| RAG-007 | 手工上传后搜索 | 审核前不可进入正式检索 | 已执行逻辑约束 |
| RAG-008 | 审核通过文档 | 状态变为 `ACTIVE` 并建立向量索引 | 已执行 |
| RAG-009 | 重复审核已通过文档 | 返回失败，不重复建立索引 | 已执行 |
| RAG-010 | Git 文档重复同步 | 同 source 幂等，不累积旧章节 | 已执行逻辑约束 |
| RAG-011 | 文档删除 | 登记记录和向量均删除 | 已有测试覆盖 |
| RAG-012 | 关键词命中标题 | 标题命中结果优先 | 已执行，含排序回归 |
| RAG-013 | 关键词命中类名/路径/错误码 | LIKE 检索可返回精确来源 | 待环境 |
| RAG-014 | 向量命中与关键词命中同源 | 按 source 去重并保留高分 | 已执行，含同源高分回归 |
| RAG-015 | topK 小于 1 | 接口参数校验失败 | 已执行集成 |
| RAG-016 | topK 大于 20 | 接口参数校验失败 | 已执行集成 |
| RAG-017 | 空 query | 接口参数校验失败 | 已执行集成 |
| RAG-018 | 向量库不可用 | 返回空结果，不阻断只读接口 | 已有熔断测试 |
| RAG-019 | 关键词数据库不可用 | 返回统一错误，不泄漏 SQL | 待环境 |
| RAG-020 | 普通用户搜索当前项目知识 | 只返回 `ACTIVE` 当前项目知识 | 已执行 Controller 契约 |
| RAG-021 | 未登录调用知识搜索 | 返回登录错误 | 已执行真实 Spring 集成 |
| RAG-022 | 普通用户调用 Git 同步 | 返回无权限 | 已执行真实 Spring 集成 |
| RAG-023 | 普通用户调用审核 | 返回无权限 | 已执行真实 Spring 集成 |
| RAG-024 | 检索结果引用来源 | 返回 path、section、commit、domain | 已执行 Controller 契约，向量命中补齐数据库引用 |
| RAG-025 | 文档中包含提示注入文本 | 不覆盖系统规则，不作为指令执行 | 已有清洗测试，待端到端 |
| RAG-026 | 文档包含 HTML 标签 | 入库前清洗，结果不产生 XSS | 已有清洗测试，待 UI |
| RAG-027 | 文档包含 Cookie/API Key | 不进入文档导出和日志 | 待安全测试 |
| RAG-028 | 索引模型/维度变更 | 不混用旧索引，需重建并回归 | 待运维测试 |
| RAG-029 | 40 条规范/业务/开发/测试问题集 | 人工确认 gold source 后执行 Recall@5/MRR@5 门禁 | 待真实 embedding 环境和人工审核 |
| RAG-030 | 无答案问题 | 明确返回 `UNCONFIRMED/知识库未确认`，不编造 | 已执行 |
| RAG-031 | 管理员查看知识库健康状态 | 返回 active 批次、索引状态分布和失败数量；不伪装成外部向量服务连通性 | 已执行 |
| RAG-032 | 干净 Git commit 重复同步 | 复用已成功批次，不重复创建任务；工作树有变化时允许新同步 | 已执行 |
| RAG-033 | Markdown 结构化章节解析 | 代码块内标题不切章节，嵌套标题返回稳定的父子 sectionPath | 已执行 |

## 执行命令

```bash
mvn -q '-Dspring.profiles.active=test' \
  '-Dtest=infra.FlywayMigrationCompatibilityTest,service.RagDocumentStoreKnowledgeTest,\
service.RagKnowledgeSyncServiceTest,service.RagKnowledgeBaseServiceTest,\
controller.RagKnowledgeControllerTest,service.RagServiceTest,service.RagAugmentationServiceTest' test

cd frontend
npm run type-check
npm run build-only
```

## 判定规则

- RAG-001 至 RAG-024 的当前自动化覆盖已通过，其中 RAG-021 至 RAG-023 已通过真实 Spring AOP/Session 集成测试。
- RAG-021 至 RAG-023 必须在带鉴权过滤器的 MockMvc 或真实测试环境执行，不能用 Controller 直接调用替代；当前已满足该条件。
- RAG-029 必须由人工审核 gold source 后执行，不能用模型自评替代；当前已建立 40 条问题及来源映射，但尚未宣称通过 Recall/MRR 发布门槛。
- RAG-030 已在检索层完成明确未确认状态；LLM 问答编排仍不属于本 MVP，不把检索 API 冒充问答功能。

## 当前检索排序基线

- 关键词召回池为最终 `topK` 的最多 3 倍，最终返回数量仍不超过 `topK`。
- 字段优先级为：标题精确命中、标题包含命中、来源路径/来源标识、章节路径、正文。
- 关键词与向量命中同一 `source` 时保留分数更高的结果，并保留唯一引用。
- 知识库引用响应携带 `documentId`、`projectKey`、`sourceType`、`branchName`、`sourcePath`、`sectionPath` 和 `commitSha`；向量兜底命中也明确标记项目与来源类型。
- 当前基线是确定性字段评分，不宣称已经达到 Recall@5 或 MRR 门槛；正式门禁仍需人工审核 40 条 gold set 并在真实 embedding 环境执行。
- Markdown 章节解析会忽略 fenced code block 内的伪标题，并以 `父章节 > 子章节` 形式返回层级路径。
