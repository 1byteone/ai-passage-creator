# Skill 前端落地实施计划

> 项目：AI Passage Creator（灵犀写作）
> 日期：2026-07-24
> 输入：`2026-07-24-skill-frontend-design.md`、`2026-07-24-skill-frontend-requirements-alignment.md`、当前代码
> 状态：需求已冻结，实施中
> 决策记录：2026-07-24，Q01-Q61 全部选择推荐项 A

## 1. 目标与完成定义

首期交付 Skill 中心、通用执行页、三类定制结果、文章流程内快捷入口，以及支撑真实执行、恢复和结果展示所需的最小后端契约。

完成必须同时满足：

1. `/skill` 可浏览首期公开 Skill，`/skill/:skillName` 可完成登录回跳、输入、执行、进度、恢复、失败重试和结果查看。
2. `proofreading`、`topic-gen`、`article-to-x` 分别使用适合其结果结构的视图，未知结果有安全回退。
3. CompletedState 与 ArticleDetailPage 能在不覆盖原文的前提下打开嵌入式执行入口。
4. 响应使用的 executionId、异步执行、数据库记录、SSE 和结果查询始终指向同一次执行。
5. 刷新或 SSE 断线后可通过结果接口恢复最终状态和结构化输出。
6. 通过后端测试、前端类型检查、构建，以及 1440/768/375 三档浏览器验收。

## 2. 当前契约缺口与修正方案

### 2.1 executionId 一致性

当前控制器创建一次 `SkillExecution` 生成响应 ID，异步服务又创建一次执行对象并生成新 ID。修正为控制器只创建一次执行对象，并把同一对象交给异步服务执行；初始数据库记录需在返回响应前可查询，避免进度订阅与落库竞争。

验收证据：执行响应、`skill_execution.skill_execution_id`、SSE 消息和结果查询四处 ID 相同的自动化测试。

### 2.2 SSE 生命周期与结构

当前 POST 创建的 emitter 与 GET progress 再次创建的 emitter 会互相覆盖。Skill 进度连接应复用同一 emitter 或提供有界事件缓冲，并统一输出 JSON：

- `skill.started`
- `skill.progress`
- `skill.phase_complete`
- `skill.complete`
- `skill.error`

每个事件至少携带 executionId；阶段事件携带 `phase`、`phaseIndex`、`totalPhases`，流式事件通过 `data` 传递片段。事件 JSON 必须通过序列化器构建，不能拼接未转义的模型内容或异常文本。

验收证据：包含引号、换行和中文片段的事件序列化测试；首次连接、断线重连与完成关闭测试。

### 2.3 可恢复结果

执行完成时按阶段 `outputKey` 汇总并持久化完整结果对象，而非只保存最后阶段值。结果接口返回解析后的 `outputData`、状态、阶段、耗时和安全错误摘要。proofreading 至少保留 `reviewResult`、`polishedContent`、`finalContent`。

验收证据：三阶段模拟状态写入后，结果接口能返回三个键；运行中、成功、失败和不存在均有稳定响应结构。

### 2.4 动态表单元数据

`VariableDef` 增加可选 `uiType`、`options`、`defaultValue`、`placeholder`、`maxLength`。三个首期 YAML 填写明确元数据；前端保留按变量名的通用 fallback，避免未知 Skill 空白。

验收证据：YAML 加载测试覆盖 textarea、select/radio、默认值和最大长度；未知字段渲染为普通输入框。

### 2.5 多轮确认

此项必须按 Q61 决策执行。推荐首期关闭三个公开 Skill 的确认标记，完整执行到结果；若选择真正多轮，则需另行设计 `WAITING_CONFIRMATION` 持久化状态、确认载荷、恢复点、超时和并发语义，不能沿用当前只写日志的接口。

## 3. 前端实现结构

### 3.1 数据与基础能力

- 新增 `frontend/src/api/skillController.ts`：列表、定义、执行、结果接口。
- 扩展 `frontend/src/api/typings.d.ts`：Skill 定义、UI 元数据、执行响应、结果和 SSE 事件。
- 扩展 `frontend/src/utils/sse.ts`：带一次受控重连的 Skill SSE；终态关闭连接；失败后回退结果查询。
- 新增 `frontend/src/config/skill.ts`：首期公开顺序、中文标签、动作文案、字段 fallback 和结果组件映射所需配置。

### 3.2 页面与组件

- `SkillCenterPage.vue`：紧凑标题、分类控制、稳定骨架、可操作空态和三项 Skill 列表。
- `SkillExecutePage.vue`：INPUT / EXECUTING / COMPLETED / FAILED 状态机，草稿恢复、字段校验、阶段进度和 executionId 恢复。
- `SkillInputForm.vue`：按后端元数据渲染，显式配置优先，未知字段 fallback。
- `SkillProgress.vue`：真实阶段步骤和流式预览，不生成虚假百分比。
- `SkillResultProofreading.vue`：问题摘要、前后对比、最终稿复制与 Markdown 导出。
- `SkillResultTopicGen.vue`：单列可比较选题、键盘选择与结构化 select 事件。
- `SkillResultArticleToX.vue`：原文摘要与社交文案响应式对照、字符数和复制。
- `SkillResultDefault.vue`：结构化 JSON、Markdown 或纯文本的安全回退。
- `SkillLauncher.vue`：桌面侧边 Drawer、移动端全屏；预填上下文且不自动覆盖文章。

### 3.3 接入点

- 路由新增 `/skill` 和 `/skill/:skillName`；执行页使用登录守卫并保留完整 redirect。
- GlobalHeader 增加“AI 工具”，窄屏将低频入口收纳到更多菜单。
- HomePage 在主创作入口之后加入紧凑工具带，不替换现有核心创作动作。
- CompletedState 传入文章正文启动 proofreading。
- ArticleDetailPage 传入文章正文启动 article-to-x。

## 4. 视觉与交互基线

采用 precision system：白色与浅灰画布、深色正文、绿色主动作、琥珀警示、细边框和稳定网格。Skill 页面是操作工作台，不使用营销型大 Hero、装饰性插画、渐变光球、毛玻璃堆叠或嵌套卡片。

控件覆盖 hover、focus-visible、pressed、disabled、loading、empty 和 error。动效只用于状态反馈、结果揭示和 Drawer 连续性，并在 `prefers-reduced-motion` 下移除非必要过渡。

## 5. 实施顺序

1. 用户确认 Q01-Q61，记录差异项并冻结需求文档。（已完成：全部选择 A）
2. 修复 executionId、SSE、完整结果和字段元数据契约，补后端测试。
3. 实现前端 API、类型、配置、状态机和 SSE 恢复。
4. 实现中心页、执行页和三类结果组件。
5. 接入导航、首页、CompletedState 和 ArticleDetailPage。
6. 运行后端测试、前端 type-check、build、lint，并处理回归。
7. 启动本地服务，在 1440、768、375 检查主流程、溢出、键盘焦点和 reduced-motion。
8. 完成需求逐项审计，提交本轮变更并推送至 GitHub 远程仓库。

## 6. 验收矩阵

| 范围 | 必须证明的行为 |
| --- | --- |
| 列表 | 加载、筛选、错误、空态、游客浏览、未知 Skill |
| 表单 | 必填、默认值、中文标签、长文本计数、草稿恢复、首错聚焦 |
| 执行 | 单次提交、防重复、阶段进度、流式片段、离页提示 |
| 恢复 | SSE 首连、一次重连、刷新、终态查询、过期 executionId |
| 结果 | 三阶段审校、选题选择、社交文案、未知结构 fallback |
| 嵌入 | 上下文预填、Drawer 响应式、关闭后任务继续、不覆盖原文 |
| 可访问性 | 标签、语义结构、键盘顺序、焦点可见、对比度、reduced-motion |
| 响应式 | 1440/768/375 无横向滚动、重叠、裁切或不可点击控件 |

## 7. 暂不纳入首期推荐范围

- research 专属结果与真正多轮确认。
- 文档文件上传与解析。
- 结果区内编辑、保存和版本管理。
- 自动添加 hashtags、emoji 或直接发布到外部平台。
- 执行历史中心与跨设备草稿同步。
