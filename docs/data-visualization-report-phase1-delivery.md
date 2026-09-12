# 数据可视化报告 Skill — Phase 1 交付记录

- 日期：2026-09-12
- 分支：`dev`　起始 HEAD：`bc54e91`
- 对应设计：`docs/data-visualization-report-design.md`
- 对应研究：`docs/data-visualization-report-phase0.md`
- 对应计划：`docs/superpowers/plans/2026-09-11-data-visualization-report-phase1.md`

---

## 1. 交付范围与目标

**目标**：让用户粘贴一份结构化数据（CSV / JSON 对象数组），由 AI 产出**受限**的图表规格（Chart Spec），由服务端把规格渲染成一张**自包含、可截图、可下载**的图表报告（HTML + PNG）。全链路后端闭环，无前端工作台。

**Phase 1 范围（已交付）**

| 能力 | 状态 |
|------|------|
| CSV / JSON 数据集解析 → `Dataset` | ✅ |
| 数据校验与字段画像（time / category / measure 语义推断，缺失/重复告警） | ✅ |
| Chart Spec 模型 + 白名单校验器 | ✅ |
| 统计分析器（趋势/排名由程序计算，AI 不复算） | ✅ |
| 服务端 SVG 渲染器（Spec → 内联 SVG HTML，无 JS 无 CDN） | ✅ |
| Skill 定义（2 阶段：画像 → 图表推荐，第二阶段 HITL 确认） | ✅ |
| Skill SUCCESS 后异步生成 HTML + PNG（静默降级） | ✅ |
| 报告产物查询 API（就绪状态 / HTML / PNG，含归属校验） | ✅ |

**明确不在 Phase 1**（见第 9 节已知限制）：前端工作台、PDF 导出、三套整页报告模板、RAG 索引、文章回流、pie/scatter/area 等其余图型。

> 其中 **pie/scatter/area 与三套报告风格**已由 Phase 2 补齐，另附结果渲染器打通展示链路，见 `data-visualization-report-phase2-delivery.md`。

---

## 2. 新增 / 修改文件清单（按层分组）

### 2.1 后端 — Skill 定义与资源（新增）

| 文件 | 说明 |
|------|------|
| `src/main/resources/skills/data-visualization-report/skill.yaml` | Skill 契约：变量 + 2 阶段（profile_dataset / recommend_charts） |
| `src/main/resources/skills/data-visualization-report/prompts/phase1_profile.md` | 阶段 1 提示词：数据 → 字段画像 JSON |
| `src/main/resources/skills/data-visualization-report/prompts/phase2_charts.md` | 阶段 2 提示词：画像 → Chart Spec JSON（最多 3 图） |

### 2.2 后端 — dataviz 包（新增）

| 文件 | 职责 |
|------|------|
| `dataviz/Dataset.java` | 解析后的数据集（headers + rows） |
| `dataviz/DatasetParser.java` | CSV / JSON 对象数组 → `Dataset`，含 512KB/1000 行/50 列/200 字符上限 |
| `dataviz/DatasetParseException.java` | 数据契约违规异常（面向用户的拒绝原因） |
| `dataviz/DatasetValidator.java` | 类型推断（time/category/measure）+ 缺失/重复统计 |
| `dataviz/DataQualityReport.java` | 画像结果（行数、字段、缺失数、重复数、warnings） |
| `dataviz/FieldProfile.java` | 单字段画像（名称、语义、是否数值） |
| `dataviz/DatasetStats.java` | 统计结果载体 |
| `dataviz/StatsService.java` | 趋势/排名程序计算（AI 不复算） |
| `dataviz/ChartSpec.java` | 受限图表规格 record（chartType/style/title/encoding/evidence…） |
| `dataviz/ChartSpecValidator.java` | 白名单校验：图型、风格、标题长度、evidence、轴语义 |
| `dataviz/ChartHtmlRenderer.java` | Spec → 内联 SVG HTML；动态文本一律 `escapeHtml` |
| `dataviz/DataVizPostProcessor.java` | SUCCESS 后收尾：解析 → 校验 → 渲染 → 落盘 HTML(+PNG)，静默降级 |
| `dataviz/DataVizStorageService.java` | 产物文件访问：执行 ID 白名单正则 + 文件名越界拦截 |
| `dataviz/DataVizController.java` | 报告产物查询 API |
| `dataviz/DataVizArtifactVO.java` | 就绪状态 VO（htmlReady / pngReady / htmlUrl / pngUrl） |

### 2.3 后端 — 接线（修改）

| 文件 | 改动 |
|------|------|
| `skill/SkillExecutionService.java` | 注入 `DataVizPostProcessor`；Skill SUCCESS 后按 `skillName` 触发 `processAsync` |
| `skill/SkillController.java` | `data-visualization-report` 加入公开技能入口（`getPublicSkill` 白名单） |

### 2.4 前端（修改）

| 文件 | 改动 |
|------|------|
| `frontend/src/config/skill.ts` | 技能中心注册 `data-visualization-report` 条目 |

### 2.5 测试（新增 / 修改）

| 文件 | 说明 |
|------|------|
| `dataviz/DatasetParserTest.java` | 解析器正/异常用例 |
| `dataviz/DatasetParseExceptionTest.java` | 异常契约 |
| `dataviz/DatasetValidatorTest.java` | 画像与告警 |
| `dataviz/StatsServiceTest.java` | 统计计算 |
| `dataviz/ChartSpecValidatorTest.java` | 白名单与边界（含 null 防护） |
| `dataviz/ChartHtmlRendererTest.java` | 渲染与转义 |
| `dataviz/DataVizPostProcessorTest.java` | 收尾落盘 + PNG 降级 |
| `dataviz/DataVizStorageServiceTest.java` | 路径白名单 |
| `dataviz/DataVizControllerTest.java` | 鉴权 / 越权 / 非法标识 / 就绪状态 |
| `dataviz/DataVizSkillRegistrationTest.java` | Skill YAML 结构契约 |
| `dataviz/DataVizSkillSettleTest.java` | SUCCESS 后触发收尾 |
| `skill/SkillExecutionServiceChainAsyncTest.java` | 适配构造器新增依赖 |

---

## 3. Skill 契约

### 3.1 输入变量

| 变量 | 必填 | uiType | 默认值 | 约束 |
|------|------|--------|--------|------|
| `rawData` | ✅ | `textarea` | — | `maxLength: 200000`；CSV（首行表头）或 JSON 对象数组 |
| `dataFormat` | ✅ | `select` | `csv` | 枚举 `csv` / `json` |
| `goal` | ❌ | `input` | — | 分析目标（想说明什么问题、给谁看） |
| `style` | ❌ | `select` | `glance` | 枚举 `mono` / `glance` / `editorial` |

`requiredRoles: [user]`，`multiRound: true`。

### 3.2 阶段

| 阶段 | 输入 | 输出键 | HITL 确认 | Parser | 模型 |
|------|------|--------|-----------|--------|------|
| `profile_dataset` | `rawData` / `dataFormat` / `goal` | `datasetProfile` | ❌ 无确认 | json | agnes |
| `recommend_charts` | `datasetProfile`（ref 上阶段）/ `goal` / `style` | `chartSpecs` | ✅ `requireConfirmation=true` | json | agnes |

`recommend_charts` 的 `datasetProfile` 通过 `ref: profile_dataset` 引用阶段 1 输出，避免重复解析数据。

---

## 4. API 契约

基路径 `/dataviz`（应用 `context-path: /api`，故实际为 `/api/dataviz/...`）。返回统一 `BaseResponse<T>`。

### 4.1 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/{executionId}/artifact` | 查询就绪状态，就绪与否恒定 200（鉴权/存在性错误除外），以 `htmlReady` / `pngReady` 布尔表达 |
| GET | `/{executionId}/html` | 报告 HTML（`text/html;charset=UTF-8` + `X-Content-Type-Options: nosniff`） |
| GET | `/{executionId}/png` | 报告 PNG（`image/png`） |

`/artifact` 响应示例：

```json
{"code":0,"data":{"htmlReady":true,"pngReady":true,
  "htmlUrl":"/api/dataviz/{executionId}/html",
  "pngUrl":"/api/dataviz/{executionId}/png"},"message":"ok"}
```

### 4.2 鉴权与错误码

`/{executionId}/html` 与 `/png` 在产物尚未落盘时返回 `40400`（而非 200 空体）；`/artifact` 用于轮询就绪。

| 场景 | code | message |
|------|------|---------|
| 未登录 | `40100` | 未登录 |
| 已登录但非归属人且非 admin | `40101` | 无权访问该报告 |
| executionId 不存在 | `40400` | 报告不存在 |
| executionId 标识格式非法（不匹配 `[0-9a-fA-F-]{36}`） | `40000` | 非法的报告标识 |
| html 尚未生成 | `40400` | 报告尚未生成，请稍后刷新 |
| png 尚未导出（或渲染引擎不可用） | `40400` | PNG 尚未导出或渲染引擎不可用 |

**归属校验**：按 `skill_execution.user_id` 判定；`admin` 角色放行全部。校验顺序为**先路径白名单 → 再认证 → 再查记录 → 再判归属**，因此非法标识在任何 DB 访问前即被拒（`../` 无从进入查询），未登录也不被 40400 掩盖。

---

## 5. Chart Spec 契约

`ChartSpec` 是 AI 唯一被允许产出的结构；渲染程序只认这些字段。

| 字段 | 约束 |
|------|------|
| `chartType` | 白名单 `line` / `bar` / `table` |
| `style` | 白名单 `mono` / `glance` / `editorial` |
| `title` | 非空且 ≤ 80 字 |
| `evidence` | 非空（图表必须挂在结论上） |
| `encoding.x` / `encoding.y` / `encoding.color` | x/y 语义校验（见下）；`color` 当前保留未校验 |
| `sort` | 可选，结构 `{field, direction}` |
| `annotations` / `subtitle` / `source` / `unit` / `insightId` | 可选（`annotations` 当前为 `List<String>`） |

**轴语义规则**

| 图型 | x 轴 | y 轴 |
|------|------|------|
| `line` | 必须 `time` | 必须 `measure` |
| `bar` | 必须 `category` | 必须 `measure` |
| `table` | 允许 `encoding` 全空（展示全部列）；若声明了 x/y，字段必须真实存在 | — |

**图数上限**：设计默认 6 图（校验层与渲染层均未做强约束）；当前实际约束来自 AI 提示词，最多 3 图（`phase2_charts.md` 规则 6：「最多 3 张图…结论少于 3 条时就少画」）。单图非法仅跳过该图，不拖垮整份报告。

---

## 6. 数据上限

由 `DatasetParser` 强制，超限抛 `DatasetParseException`（面向用户的中文原因）：

| 维度 | 上限 | 超限消息 |
|------|------|---------|
| 输入总长 | 512 KB | 数据超过 512KB 上限，请精简后再试 |
| 行数 | 1000 | 行数超过 1000 上限，请精简后再试 |
| 列数 | 50 | 列数超过 50 上限，请精简后再试 |
| 单元格长度 | 200 字符 | 单元格超过 200 字符上限: 列 {name} |

校验还包括：重复列名拒绝；至少存在一列数值列（否则「未找到数值列，无法生成图表」）。缺失值/重复行/数值列脏值记为 warning，不阻断流程。

---

## 7. 落盘产物

```
{user.dir}/data/dataviz/{executionId}/
├── report.html   # 自包含：内联 <style> + 内联 SVG，无 <script>、无 CDN
└── report.png    # 渲染引擎可用时导出；不可用则只留 HTML
```

- 渲染由 `DataVizPostProcessor` 在 Skill SUCCESS 后 `@Async("skillExecutor")` 执行，**静默降级**：解析失败、图型非法、渲染引擎不可用均只记日志，绝不影响 Skill 主流程（与 `ComicJournalService.processAsync` 同约定）。
- PNG 依赖 `CardRenderPipeline`（Playwright，`playwright.enabled=true`）。
- `DataVizStorageService` 与 `DataVizPostProcessor` 使用同一 `{user.dir}/data` 根，保证读写一致。

---

## 8. 测试结果

### 8.1 全量后端测试

确切命令为**裸 `mvn test`**（不含 `-Dspring.profiles.active=test`）。两者结果不同，复现时请以本命令为准：

```
mvn test
Tests run: 613, Failures: 1, Errors: 4, Skipped: 3
BUILD FAILURE
```

失败 5 项分两类，**均与本功能无关**：

**(a) 环境依赖（需真实 Redis / 外部凭据）**

| 用例 | 原因 |
|------|------|
| `AgentChatIntegrationTest.guestChat_returnsAgentRequestId:74` | `RedisConnectionFailure: Unable to connect to Redis` |
| `AgentChatIntegrationTest.guestChat_missingGuestId_returnsBadRequest:91` | 同上 |
| `AgentChatIntegrationTest.guestChat_differentGuestId_independentRateLimit:122` | 同上 |

> 该测试类为本分支工作区未跟踪文件（`??`），非 phase1 交付内容；CI 中 `spring.session.store-type=none` 规避。

**(b) 创作模块既有缺陷（非 dataviz 引入，已在基线复现）**

| 用例 | 现象 |
|------|------|
| `ArticleFullFlowIntegrationTest.saveArticleContent_persistsAllFields:170` | `JsonSyntaxException: Expected BEGIN_ARRAY but was STRING`（`ArticleVO.objToVo` 解析 `outline` 字段） |
| `ArticleFullFlowIntegrationTest.asyncPhase3_generatesAndPersistsContent:261` | `expected: <COMPLETED> but was: <FAILED>`（阶段 3 内 `GsonUtils.fromJson(article.getOutline(), List<...>)` 解析失败） |

**基线与回归验证**：在 dataviz 改动之前的基线 `2f6eef5` 上单独运行 `ArticleFullFlowIntegrationTest`，得到**完全相同**的 1 failure + 1 error。故这两项是**本次改动之前就存在**的缺陷，dataviz 提交（`2f6eef5..bc54e91`，共 33 文件、2358 行新增）未触碰 `ArticleVO` / `ArticleServiceImpl` / `ArticleAsyncService`（`SkillExecutionService` 仅 +9 行注入收尾钩子）。根因是 `outline` 列在测试数据下被当作 JSON 字符串读回、而反序列化期望数组，属创作模块的既有数据契约不一致。

**dataviz 自身测试**：`dataviz` 包下 11 个测试类全绿（含解析器、画像、统计、Spec 校验、渲染、收尾、存储、控制器、Skill 注册与收尾触发）。

### 8.2 冒烟：应用启动 + API 实测

本地 MySQL(3306) / Redis(6379) 均在运行，应用可启动（`Started AiPassageCreatorApplication in 8.798 s`，Tomcat 8567，context-path `/api`）。
> 注：环境未提供 `AGNES_AI_API_KEY`（Spring AI OpenAI 起步依赖要求该键名），需显式映射后启动；这是环境配置问题，非代码问题。

实测结果（真实 HTTP 请求）：

| 请求 | 期望 | 实测 |
|------|------|------|
| 未登录 `GET /api/dataviz/{uuid}/artifact` | 40100 | `{"code":40100,"message":"未登录"}` ✅ |
| 非法标识 `GET /api/dataviz/not-a-uuid/artifact` | 40000 | `{"code":40000,"message":"非法的报告标识"}` ✅ |
| 路径穿越 `/api/dataviz/..%2f..%2fetc/artifact` | 拒绝 | Tomcat 400（编码斜杠被拒） ✅ |
| 已登录、不存在的 executionId | 40400 | `{"code":40400,"message":"报告不存在"}` ✅ |
| 其他用户访问他人 executionId | 40101 | `{"code":40101,"message":"无权访问该报告"}` ✅ |
| admin 访问他人 executionId | 放行 | `{"code":0,...ready:true...}` ✅ |
| 产物就绪前 `/artifact` | ready=false | `htmlReady:false, pngReady:false` ✅ |
| 落盘后 `/artifact` | ready=true | `htmlReady:true, pngReady:true` ✅ |
| `/html` 响应头 | `text/html;charset=UTF-8` + nosniff | 命中 ✅ |
| `/png` 响应头 | `image/png` | 命中 ✅ |

### 8.3 冒烟：端到端产物管线（真实 Playwright）

技能执行 `POST /api/skill/data-visualization-report/execute` 返回 `RUNNING`，阶段 1（`profile_dataset`，AI 画像）**真实跑通**；随后在阶段 2 落库 HITL 状态时报 `Data truncation: Data too long for column 'status'`，为**本地库 schema 漂移**（见第 9 节），非 dataviz 缺陷。

为绕过该本地环境阻塞并验证产物管线，使用一次性测试（用后即删，未进交付）以**真实组件**驱动收尾：

```
[SMOKE] cardRenderPipeline.isHealthy = true
[SMOKE] html exists = true  size = 1404
[SMOKE] png  exists = true  size = 88114
[SMOKE] html has <script> = false
[SMOKE] html has svg = true
```

生成的 PNG 经校验为合法 `PNG image data, 2160 x 3840, 8-bit/color RGB`。
结论：**解析 → 校验 → 统计 → Spec 校验 → SVG 渲染 → HTML 落盘 → Playwright PNG 导出**全链路在真实渲染引擎下可用。

---

## 9. 已知限制

> 本节记录 **Phase 1 交付时** 的状态。Phase 2（见 `data-visualization-report-phase2-delivery.md`）已消除第 1、5 条中的图表类型与报告风格限制，并把结果渲染器接入前端。下表保留原始记录并标注现状。

1. **无前端工作台**：无可视化选择/编辑图表的工作页面，仅 API + 技能执行链路。~~属 Phase 2~~ → **Phase 2 已接入结果渲染器**（轮询 `/artifact` + iframe 预览 + 下载），但**专属编辑工作台仍未做**。
2. **无 PDF 导出**：仅 HTML / PNG。报告印刷场景依赖 HTML 打印或 PNG。属 Phase 2+（**仍未做**）。
3. **无 RAG 索引**：生成的报告未写入向量库、未参与检索。属 Phase 4。
4. **无文章回流**：报告不能回填到文章/卡片。属 Phase 3。
5. **图表样式为内联 CSS**：~~`style`（mono/glance/editorial）影响字体/色板等轻量差异，未实现三套整页报告模板~~ → **Phase 2 已实现三套报告风格**（`body[data-style]` 作用域：mono 可打印 / glance 快速判断 / editorial 阅读式）。
6. **`/dataviz` 返回的 URL 含 `/api` 前缀**：`htmlUrl` / `pngUrl` 为 `/api/dataviz/{id}/html`（因应用 context-path = `/api`）。前端若使用带 `baseURL` 的 axios/fetch 消费，需确认**不与其 baseURL 双拼**成 `/api/api/...`，否则 404。→ **Phase 2 选择直接用相对路径**（iframe `:src` 与 `<a download>` 不经 axios），已规避该问题。
7. **图数**：设计默认每页最多 6 图，但校验层与渲染层均未做强约束；当前实际约束来自 AI 提示词，最多 3 图。
8. **本地库 schema 漂移（环境性，非代码）**：本地 MySQL `skill_execution.status` 为 `varchar(20)`，而规范 schema（`V1__baseline.sql` 与 `h2-schema.sql`）为 `varchar(30)`。枚举值 `AWAITING_CONFIRMATION` 为 21 字符，故 HITL 阶段在本机落库失败，阻断端到端真跑。修复：本地库 `ALTER TABLE skill_execution MODIFY status varchar(30) NOT NULL DEFAULT 'PENDING'`（规范 schema 本身正确，无需改代码）。
9. **Skill 输入变量持久化位置**：收尾所需的 `rawData` / `dataFormat` 是 INPUT 变量，终态只存在于 `inputData`；阶段输出在 `outputData`。收尾必须合并两者（见 CLAUDE.md 已知暗坑）。

---

## 10. 后续阶段计划

| 阶段 | 内容 |
|------|------|
| ~~**Phase 2 — 前端工作台**~~ | ✅ **已完成**（见 `data-visualization-report-phase2-delivery.md`）：结果渲染器 + 三套报告风格 + pie/scatter/area。**未做**：专属图表编辑工作台、PDF 导出 |
| **Phase 3 — 文章与卡片集成** | 报告回流到文章正文与卡片渲染；复用 `CardRenderPipeline` 的多页/模板能力 |
| **Phase 4 — RAG 与高级数据源** | 报告向量索引与检索；`Insight` 结论结构落地；更多数据源接入（数据库/API/文件）与高级统计 |

---

## 11. 安全与合规

### 11.1 许可证边界（PolyForm Noncommercial 1.0.0）

参考仓库（`lieflat-charts`）使用 **PolyForm Noncommercial License 1.0.0**。Phase 0 结论为「参考设计、自主实现」：本 Phase 1 交付**未复制**任何外部代码、模板、视觉资源、色板或字体，仅参考产品原则、数据契约思想、阅读速度分层与报告编排方法。首期代码、模板、样式、色板、资源全部自研。

### 11.2 报告 HTML 安全

- **无 `<script>`、无 CDN**：报告 HTML 完全自包含（内联 `<style>` + 内联 SVG），不加载任何外部资源，不执行任何脚本。这既是安全要求，也保证 Playwright 禁 JS 仍可截图。
- **所有动态文本经 `escapeHtml`**：标题、副标题、单位、来源、告警、表头、单元格、SVG 文本节点等一律 `escapeHtml`（`& < > " '`），防 HTML 注入。
- **响应头纵深防御**：`/html` 返回 `X-Content-Type-Options: nosniff`，并显式 `charset=UTF-8`（中文内容），避免浏览器按嗅探结果改判类型。

### 11.3 越权与路径穿越

- **executionId 正则白名单**：`DataVizStorageService` 要求 `[0-9a-fA-F-]{36}`，非法即拒（40000），`../` 无法进入文件路径或 DB 查询。
- **文件名越界拦截**：`resolve` 拦截含 `/`、`\`、`..` 的文件名。
- **归属校验**：按 `skill_execution.user_id` 判定，admin 放行；未登录 40100、越权 40101、不存在 40400，语义清晰不互相掩盖。

### 11.4 计算可信性

- **统计与排序由程序计算**：趋势、排名等由 `StatsService` 程序计算，AI 只负责选择图型与撰写标题/结论，**不复算数据**，避免 LLM 数值幻觉污染报告。

---

## 附录：提交记录（Phase 1）

| 提交 | 说明 |
|------|------|
| `6e7bf4e` | 数据集解析器 JSON/CSV → Dataset |
| `712a474` | 解析器边界缺陷修复（列数截断 / null 精度 / 不可变） |
| `fd19236` | 数据校验与字段画像 |
| `ad2fc57` | ChartSpec 模型与白名单校验器 |
| `8f143ff` | ChartSpec 校验器 null 防护与规则测试补全 |
| `26c411b` | 统计分析器（趋势/排名程序计算） |
| `60ad4ef` | ChartSpec 服务端 SVG 渲染器（无 JS 无 CDN） |
| `b4c29d6` | data-visualization-report Skill YAML 与提示词 |
| `140551d` | 公开 data-visualization-report Skill 入口 |
| `3d5a6aa` | Skill SUCCESS 后生成图表报告 HTML/PNG（静默降级） |
| `1c334f3` | 报告产物查询 API（归属校验 + 路径白名单） |
| `bc54e91` | 非法标识映射 40000 + 补越权/html 覆盖 + nosniff |
