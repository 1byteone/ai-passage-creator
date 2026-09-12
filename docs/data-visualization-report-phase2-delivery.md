# 数据可视化报告 Phase 2 交付记录（让成果物可达）

- 日期：2026-09-12
- 分支：`dev`
- 对应设计：`docs/data-visualization-report-design.md`
- 前置交付：`docs/data-visualization-report-phase1-delivery.md`
- 提交：`858d89b` → `7fb8fe3` → `491594b` → `0a799ba` → `22dabd4` → `bbc382b`

---

## 1. 交付概览

Phase 1 已让后端能从 JSON/CSV 产出图表报告，但留下一个致命缺口：**用户看不到成果物**。

`data-visualization-report` 没有前端结果渲染器，分发链落到 `SkillResultDefault`，其输出是 `<pre>{{ JSON.stringify(outputData, null, 2) }}</pre>` —— **执行一个「数据可视化」技能，用户看到的是 JSON 串**。

Phase 2 解决这个问题，并补齐设计文档承诺的三种报告风格与三种图型。

**明确不在 Phase 2**：专属图表编辑工作台、PDF 导出、RAG 索引、文章回流（属 Phase 3+）。

---

## 2. 新增与修改文件

### 后端

| 文件 | 改动 |
|---|---|
| `dataviz/ChartSpecValidator.java` | `CHART_TYPES` 扩展为六种；横轴规则改为 `switch` 分发 + `requireSemantic` helper |
| `dataviz/ChartHtmlRenderer.java` | 新增 `renderPie` / `renderScatter` / `renderArea`；`renderPage` 增加 `style` 参数；抽出 `BASE_CSS` / `STYLE_CSS`；新增 `appendXAxisLabels` 消除折线与面积图的重复 |
| `dataviz/DataVizPostProcessor.java` | 新增 `resolveStyle(output, specs)`；调用点传 style |

### Skill 资源

| 文件 | 改动 |
|---|---|
| `skills/data-visualization-report/prompts/phase2_charts.md` | chartType 白名单扩为六种；按图型分别声明横轴语义；补选型指引 |

### 前端

| 文件 | 改动 |
|---|---|
| `src/pages/skill/components/SkillResultDataViz.vue` | **新增**：轮询产物就绪 → iframe 预览 → 下载 |
| `src/pages/skill/components/SkillResultRenderer.vue` | 新增 `executionId` prop 与 dataviz 分发分支 |
| `src/pages/skill/components/SkillExecuteSurface.vue` | 传入 `:execution-id` |
| `src/utils/datavizState.ts` | **新增**：URL 构造 / 轮询判定 / 超时（纯函数，便于 Node 单测） |
| `src/api/datavizController.ts` | **新增**：`getDataVizArtifact` |
| `src/api/typings.d.ts` | 新增 `DataVizArtifactVO` 与 `BaseResponseDataVizArtifactVO` |
| `src/config/skill.ts` | 补齐 `SKILL_UI_CONFIG` / `PHASE_LABELS` / `FALLBACK_FIELDS` 三处 |

---

## 3. 关键设计决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 报告 HTML 展示 | `<iframe :src="'/api/dataviz/{id}/html'" sandbox="">` 直连端点 | 后端端点已带 `nosniff` + `charset` + 归属校验；浏览器原生请求自带 session cookie。**顺带规避了 Phase 1 记录的 `/api` 前缀双拼风险**——iframe 与 `<a download>` 都不经 axios |
| `executionId` 来源 | 提升为 `SkillResultRenderer` 的 prop | **产物不进 `outputData`**（comic-journal 的注释已记录该教训）。`SkillExecuteSurface` 内部有 `executionId` ref，是唯一可靠来源 |
| 风格落地层级 | `renderPage` 的 CSS，按 `body[data-style]` 作用域 | 字体、页面留白、标题层级是整页属性，不属于单张卡片 |
| 风格取值优先级 | INPUT 变量 `style` → 首图 `style()` → `glance` 兜底 | 用户在下单时选的风格优先于模型给单张图填的值 |
| pie 类别过多 | 超 10 类合并「其他」并在报告内注明 | 避免 validator 因几千类别而拒绝，产品上更顺 |
| 轮询上限 | 40 次 × 1.5s ≈ 60s | 防止后端无产物时无限转圈 |
| 纯逻辑外置 | `datavizState.ts` | Node 无法 import `.vue`，抽出来才能单测 |

---

## 4. 契约

### 4.1 Chart Spec 白名单（更新）

| 图型 | x 语义 | y 语义 |
|---|---|---|
| `line` | `time` | `measure` |
| `area` | `time` | `measure` |
| `bar` | `category` | `measure` |
| `pie` | `category` | `measure` |
| `scatter` | `measure` | `measure` |
| `table` | 允许 encoding 全空；已声明字段必须存在 | — |

### 4.2 新增错误消息（中文，面向用户）

- `饼图的横轴必须为分类字段`
- `散点图的横轴必须为数值字段`
- `面积图的横轴必须为时间字段`

### 4.3 报告风格

`renderPage(title, style, fragments, warnings)`，style ∈ `mono` / `glance` / `editorial`，非法值兜底 `glance`。

| 风格 | 视觉 |
|---|---|
| `mono` | 高对比黑白、细边框、可打印（720px 版心） |
| `glance` | 浅灰底、圆角卡片、细阴影（860px 版心） |
| `editorial` | 衬线字体、无边框、顶部 3px 粗线、大留白（680px 版心） |

全部使用系统字体栈，**无外部字体、无 CDN、无 `<script>`**。

### 4.4 前端状态工具

```ts
buildArtifactUrl(id, 'html' | 'png')  // 标识经 encodeURIComponent 防路径穿越
shouldStopPolling({ htmlReady })      // 仅在 htmlReady 时停止
isPollExhausted(attempts)             // >= MAX_POLL_ATTEMPTS(40)
```

---

## 5. 验收

### 5.1 后端

```bash
mvn test -Dtest='com.example.aipassagecreator.dataviz.*Test'
# BUILD SUCCESS — 103 个测试 / 11 个测试类全绿
```

用例分布：

| 测试类 | 用例数 |
|---|---|
| `ChartSpecValidatorTest` | 22（Phase 1: 14，新增 8） |
| `ChartHtmlRendererTest` | 14（Phase 1: 4，新增 10） |
| `DataVizPostProcessorTest` | 12（Phase 1: 9，新增 3） |
| 其余 8 类 | 55 |

### 5.2 前端

```bash
cd frontend
npm run type-check      # 0 错误
npm run lint:check      # 0 问题
npm run build-only      # 成功
npm run test:skill      # 26/26（含新增 dataviz-view.test.ts 的 5 个用例）
npm run test:ui -- dataviz-preview   # 2/2
```

### 5.3 ⚠️ 未经真机验证的部分

**「AI 产出真实 Chart Spec → 报告落盘 → iframe 显示图表」这条完整链路未做真机冒烟**。当前环境不具备 AI 凭据与 Playwright Chromium。

已验证的替代覆盖：
- SVG 结构断言（扇区数量、路径闭合、点坐标范围、超限合并）
- 组件级逻辑（URL 构造、路径穿越编码、轮询停止与超时判定）
- 执行页入口渲染与无运行时错误

建议按以下步骤人工冒烟：

```bash
mvn spring-boot:run            # 起后端
cd frontend && npm run dev     # 起前端
# 浏览器打开 /skill/data-visualization-report
# 1) 粘贴 CSV → 选 style=glance → 执行 → 阶段标签应显示中文
# 2) 确认后等待 → 报告应以内嵌 iframe 呈现（不是 JSON 串）
# 3) 检查 iframe 内：图表、单位、来源、警告条
# 4) 切 style=mono / editorial 重跑，确认报告整体视觉变化
# 5) 下载 HTML（双击可离线打开）、下载 PNG
```

---

## 6. 已知限制

| # | 限制 | 归属 |
|---|---|---|
| 1 | 无专属图表编辑工作台（只能看结果，不能改图型/配色） | Phase 3+ |
| 2 | 无 PDF 导出 | Phase 3+ |
| 3 | `scatter` 不做抽稀/jitter，密集数据会重叠 | 设计取舍 |
| 4 | `pie` 超 10 类合并为「其他」，不展示明细 | 设计取舍 |
| 5 | `area` 基线取数据最小值而非 0（放大波动，与 `line` 一致） | 设计取舍 |
| 6 | Playwright 只覆盖入口级，结果态依赖 Node 单测 + 组件逻辑 | 覆盖缺口 |
| 7 | 报告 HTML 若新增动态插值，必须过 `escapeHtml`——否则 `/html` 端点会变成同源 XSS 出口 | 维护约束 |
| 8 | 本地库 `skill_execution.status` 需为 `varchar(30)`，否则 HITL 落库失败（环境性，见 Phase 1 文档第 8 条） | 环境 |

---

## 7. 后续路线

| 阶段 | 内容 |
|---|---|
| **Phase 3 — 文章与卡片集成** | 报告回流到文章正文与卡片渲染；复用 `CardRenderPipeline` 的多页/模板能力 |
| **Phase 4 — RAG 与高级数据源** | 报告向量索引与检索；`Insight` 结论结构落地；数据库/API 数据源接入与高级统计 |
