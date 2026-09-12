# 数据可视化与报告生成 Skill 二次开发设计规格

- 状态：待用户审阅
- 日期：2026-09-10
- 范围：当前 `ai-passage-creator` 项目
- 目标 Skill：`data-visualization-report`
- 依据：Lieflat Charts 设计思想、当前项目 Skill 引擎、ECharts 分析页、Playwright 卡片渲染和 RAG 能力

## 1. 背景与目标

当前项目已经具备文章创作、声明式 Skill 编排、SSE 进度推送、ECharts 数据分析、HTML 到 PNG 渲染和 RAG 索引能力，但这些能力尚未形成“结构化数据 → 洞察 → 图表 → 报告 → 文章资产”的完整链路。

本功能将数据可视化与报告生成能力实现为项目内的一个独立 Skill：用户提供 CSV、JSON 或 Markdown 表格，并描述分析目标、受众和输出形式；系统完成数据契约识别、质量检查、结论规划、图表推荐、报告编排和 HTML/PNG/PDF 导出。

目标不是复制外部 Skill 的代码或模板，而是吸收其可复用的产品原则：

1. 先理解数据契约，再选择图表。
2. 每张图表达一个独立结论，不为凑数量生成图表。
3. 用阅读速度区分视觉风格，而不是仅按图表类型分类。
4. 单图优先，多个结论再编排为报告。
5. HTML 作为可编辑、可复现和可导出的中间产物。

## 2. 非目标与边界

第一期不做以下事项：

- 不直接复制 `lieflat-charts` 的代码、模板、样式或资源。
- 不一次实现全部 49 个图型；首期只覆盖高频图型。
- 不允许模型直接生成并执行任意 JavaScript 或 ECharts 配置。
- 不接入任意外部数据库、URL 或第三方数据源；先完成本地结构化数据闭环。
- 不把报告原始数据默认暴露给其他用户或全站 RAG 检索。
- 不把当前固定的 `AnalyticsPage` 改造成通用数据工作台；固定运营分析与用户生成报告保持边界。

## 3. 用户价值与使用场景

### 3.1 数据简报

用户粘贴一份周报数据，系统在几分钟内生成一张排名、趋势或异常图，并附带简短结论，适合团队汇报和运营复盘。

### 3.2 研究报告

用户提供调研数据，系统生成带数据来源、方法说明、关键洞察和图表的 HTML 报告，并可导出 PDF。

### 3.3 数据驱动文章

报告完成后，将摘要、结论和图表以 Markdown/卡片资产形式回流现有文章创作流程，减少人工从图表到文章的重复整理。

## 4. 总体架构

```text
Vue DataReportPage
        │
        │ POST /skill/data-visualization-report/execute
        ▼
SkillController / SkillExecutionService
        │
        ▼
声明式 data-visualization-report Skill
        ├─ parseDataset：解析 CSV/JSON/Markdown
        ├─ validateDataset：字段、类型、缺失值、重复值检查
        ├─ analyzeDataset：统计量、趋势、排名、异常
        ├─ planInsights：生成带证据的结论计划
        ├─ recommendCharts：生成受限 Chart Spec
        ├─ HITL：用户确认结论、图型、风格和模板
        ├─ renderCharts：程序生成 ECharts 配置和 HTML
        ├─ composeReport：程序套用报告模板
        └─ exportReport：复用 Playwright 输出 PNG/PDF
        │
        ├─ SSE：阶段进度与确认事件
        ├─ SkillExecution：保存执行历史
        ├─ RAG：索引报告摘要与可追溯结论
        └─ Article/Card：后续回流文章与卡片资产
```

### 4.1 责任分界

AI 负责：

- 识别字段语义。
- 根据数据形态和用户目标规划结论。
- 在白名单图型中选择合适图型。
- 生成标题、摘要、旁注和限制说明。

程序负责：

- 解析和规范化数据。
- 空值、重复值、类型和大小校验。
- 统计量、同比、环比、排名和异常计算。
- Chart Spec 白名单校验。
- ECharts 配置、HTML 模板和导出渲染。
- 权限、文件隔离、资源清理和审计记录。

## 5. Skill 阶段设计

### Phase 1：数据契约识别

输入为 `rawData`、`dataFormat`、`goal`、`audience`、`language`、`style` 和 `outputMode`。输出字段角色、数据类型、粒度、行数、指标和数据质量摘要。

### Phase 2：数据质量检查

程序完成以下检查：

- 文件大小、行数和列数上限。
- 必须存在表头和至少一列有效数据。
- 数值、日期和类别字段类型推断。
- 缺失值、重复行、非法数值和异常日期。
- 用户可见的警告与阻断错误。

警告可以继续生成，但必须在报告中列出；阻断错误必须停止后续渲染并通过 SSE 返回中文错误。

### Phase 3：结论规划

模型只能基于程序提供的统计结果和字段证据生成结论计划。每个结论必须包含 `claim`、`evidence`、`importance` 和预计图表数量。程序在落库前检查结论引用的字段和行范围确实存在。

### Phase 4：图表推荐

模型从白名单图型中选择图表，并输出 Chart Spec。推荐阶段必须说明选择理由、阅读目标和数据映射。用户可以在 HITL 阶段修改图型、数量、风格、主题色和报告模板。

### Phase 5：确认与修改

默认在图表渲染前暂停一次。确认内容包括：

- 关键结论是否保留。
- 图表类型是否接受。
- 视觉风格和主题色。
- 单图、简报或完整报告模式。
- 是否显示异常点和数据限制。

### Phase 6：渲染与报告编排

程序依据通过校验的 Chart Spec 生成图表和报告。模型不得注入 HTML 标签、脚本或任意模板路径。报告包含摘要、关键指标、图表、结论、数据来源、方法和限制说明。

### Phase 7：导出与收尾

首期支持 HTML、PNG 和 PDF。HTML 是主产物；PNG/PDF 为导出产物。成功后保存执行输出和产物元数据，报告摘要和带来源结论异步进入 RAG；原始明细数据默认不进入跨用户检索。

## 6. Chart Spec 设计

Chart Spec 是 AI 和渲染程序之间的稳定边界，示例：

```json
{
  "chartType": "line",
  "style": "glance",
  "title": "近半年销售额趋势",
  "subtitle": "第三季度增长明显",
  "source": "用户上传数据",
  "unit": "元",
  "data": {
    "dimensions": ["month"],
    "measures": ["sales"],
    "rows": [
      {"month": "2026-07", "sales": 820000},
      {"month": "2026-08", "sales": 910000}
    ]
  },
  "encoding": {
    "x": "month",
    "y": "sales",
    "color": null
  },
  "annotations": [
    {"type": "peak", "field": "sales", "value": 910000}
  ]
}
```

### 6.1 首期白名单图型

- `bar`：分类排名、对比。
- `line`：时间趋势。
- `area`：累计或连续变化。
- `pie`：低基数构成，类别数量受限。
- `scatter`：两个数值指标关系。
- `table`：数据明细和不可压缩的事实。

首期不支持网络图、桑基图、复杂地理图和任意自定义脚本。

### 6.2 校验规则

- `chartType`、`style` 和 annotation 类型必须在枚举白名单中。
- `encoding` 引用的字段必须来自数据契约。
- 数值轴必须绑定数值字段；时间轴必须绑定日期字段。
- 图表数据只能来自已解析的数据快照，不接受模型新造的行。
- 标题、旁注、来源和单位进行长度和 HTML 清洗。
- 单图只能承担一个主要结论。
- 单页默认最多 6 张图。

## 7. 视觉风格与报告模板

首期实现三种风格：

| 风格 | 目标 | 视觉特征 |
|---|---|---|
| `mono` | 可靠保底 | 黑白灰、低装饰、适合数据质量不确定的场景 |
| `glance` | 快速判断 | 强层级、大数字、明显排序、适合周报和管理汇报 |
| `editorial` | 细节阅读 | 留白、旁注、真实单位、适合研究报告和长文 |

首期实现三种报告模板：

- `basic-data`：单图或数据简报。
- `glance-business`：业务周报/月报。
- `editorial-research`：研究和调研报告。

同一份报告只能使用一套色彩系统。自定义品牌色只允许传入经过校验的色值，不允许模型生成任意 CSS。

## 8. API 与领域模型建议

### 8.1 MVP API

沿用现有 Skill API，不新增第二套异步执行协议：

- `POST /skill/data-visualization-report/execute`
- `GET /skill/{executionId}/events` 或现有 SSE 入口
- 现有确认、重试和结果查询接口
- `POST /data-reports/{reportId}/export`
- `GET /data-reports/{reportId}`

MVP 可以先使用 `SkillExecution` 的 JSON 输出和产物路径，不立即引入完整报告表。

### 8.2 后续领域模型

当 MVP 验证通过后新增：

- `DataReport`：报告元数据、用户、工作空间、状态和版本。
- `DataReportChart`：图表 Spec、结论和排序。
- `DataReportSource`：数据快照、来源、字段契约和校验摘要。
- `DataReportExport`：导出格式、文件路径、状态和过期时间。

Controller 只负责参数校验和派发；解析、校验、渲染和事务逻辑放在 Service 层；不向前端直接暴露 Entity。

## 9. 前端工作台

建议新增 `frontend/src/pages/data-report/DataReportPage.vue`，而不是扩展现有 `AnalyticsPage.vue`。页面阶段：

1. 数据输入：粘贴、上传和格式选择。
2. 数据预览：字段类型、行数和质量警告。
3. 目标配置：问题、受众、语言、风格和输出模式。
4. 生成进度：复用 Skill SSE 和确认事件。
5. 洞察确认：展示结论及证据字段。
6. 图表预览：图表、数据来源、单位和图型调整。
7. 报告预览：HTML 报告和导出操作。
8. 资产回流：复制 Markdown、创建文章草稿或生成卡片。

所有报告富文本必须使用项目已有 Markdown 清洗工具；不直接使用 raw `marked` 或未经清洗的 `v-html`。

## 10. 安全、隐私与可追溯性

### 10.1 数据安全

- 限制单次文件大小、行数、列数和单元格长度。
- 禁止公式注入和任意脚本执行。
- 文件路径使用服务端生成的资源 ID，不接受客户端路径。
- 报告和导出物绑定 `userId`、`workspaceId`，查询时再次校验归属。
- 资源下载使用受保护接口和短时 URL，不暴露内部存储路径。

### 10.2 XSS 与模板安全

- AI 文本只作为文本或经过清洗的 Markdown。
- 图表配置采用 JSON 白名单映射，不执行模型生成的代码。
- 模板路径只能来自固定枚举。
- 所有外部链接按 scheme 白名单处理。

### 10.3 数据血缘

每条结论至少保存：

- 数据源标识和版本。
- 使用字段。
- 使用行范围或过滤条件。
- 计算公式和程序计算结果。
- 生成时间和模型信息。

示例：

```json
{
  "claim": "第三季度销售额较第二季度增长 24.8%",
  "formula": "(q3 - q2) / q2",
  "sourceColumns": ["quarter", "sales"],
  "sourceRows": [6, 7, 8, 9]
}
```

## 11. RAG 与文章创作集成

### 11.1 RAG

异步索引以下内容：

- 报告摘要。
- 关键结论。
- 指标定义。
- 数据来源说明。
- 方法和限制。
- 图表标题和旁注。

默认不索引原始明细表，除非用户明确选择并且权限范围允许。普通用户只能检索自己的报告；管理员是否可检索全站沿用当前 RAG 权限规则。

### 11.2 文章创作

二期提供“回流文章”动作：将报告摘要、结论和图表引用转换为文章草稿输入或 Markdown 内容。图表渲染产物复用现有卡片渲染链路，避免引入新的渲染技术栈。

## 12. 分阶段交付

### Phase 0：许可证与样例验证

交付：许可证审查记录、3 个代表性数据样例、Chart Spec 草案和图型范围确认。

验收：明确外部仓库哪些内容只能参考、哪些内容不可复制；自研 Spec 可表达首期样例。

### Phase 1：Skill MVP

交付：Skill YAML、解析与校验、六类图表、三种风格、三套模板、SSE 进度、HTML/PNG/PDF 导出、后端单测和集成测试。

### Phase 2：前端工作台

交付：输入、预览、确认、报告预览、导出、历史报告和基础 E2E。

### Phase 3：文章与卡片集成

交付：报告回流文章、图表嵌入正文、卡片生成和发布链路衔接。

### Phase 4：RAG 与高级数据源

交付：跨报告检索和对比、外部 URL/API 数据源、报告版本和数据血缘管理。

## 13. 测试与验收标准

### 功能

- CSV、JSON、Markdown 表格均可解析。
- 可识别时间、类别和数值字段。
- 可检测缺失值、重复值和非法数值。
- 可给出有理由的图表推荐。
- 可生成首期六类图表和三类报告模板。
- 可输出 HTML、PNG 和 PDF。
- 可在确认阶段修改图型和风格。

### 正确性

- 图表数据与输入数据快照一致。
- 计算结果可由公式复核。
- 每条关键结论都有字段和数据证据。
- 空数据、异常数据和质量警告有明确表现。
- 同一输入和配置可复现相同的 Chart Spec 和渲染结果。

### 工程质量

- `cd frontend && npm run type-check` 通过。
- `cd frontend && npm run build` 通过。
- `mvn test` 通过。
- 报告 Skill 单元测试覆盖解析、校验、推荐约束和导出失败。
- Playwright 覆盖输入、确认、预览和导出主流程。
- 图表实例、定时器、SSE 连接和浏览器资源在卸载/失败时正确释放。

## 14. 关键外部参考

- Lieflat Charts 文章：https://www.jamecling.com/archives/6510
- Lieflat Charts 仓库：https://github.com/larashero3-dotcom/lieflat-charts
- AntV Chart Visualization Skills：https://github.com/antvis/chart-visualization-skills
- AntV MCP Chart：https://github.com/antvis/mcp-server-chart
- Apache ECharts API：https://echarts.apache.org/en/api.html
- Apache ECharts Dataset：https://apache.github.io/echarts-handbook/en/concepts/dataset/
- Apache ECharts 服务端渲染：https://apache.github.io/echarts-handbook/en/how-to/cross-platform/server/
- Vega-Lite：https://vega.github.io/vega-lite/
- Playwright Java Page API：https://playwright.dev/java/docs/api/class-page
- Spring AI Structured Output：https://docs.spring.io/spring-ai/reference/api/structured-output/converters.html

## 15. 设计决策摘要

1. 采用独立 Skill，不改造固定 Analytics 页面为通用工作台。
2. 复用现有 Skill、SSE、ECharts、Playwright、RAG 和文章卡片基础设施。
3. 使用自有 Chart Spec 作为模型和渲染程序的边界。
4. 程序负责数据计算、校验和渲染，模型负责语义理解和表达。
5. 先做六类高频图表和三套模板，再根据真实使用数据扩展。
6. HTML 是主产物，PNG/PDF 是导出格式。
7. 先完成本地结构化数据闭环，再接入外部数据源和跨报告 RAG。
8. 外部仓库只作为产品和设计参考；任何代码、模板和资源复用必须先通过许可证审查。
