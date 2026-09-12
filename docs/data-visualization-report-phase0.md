# 数据可视化报告 Skill Phase 0 研究交付

- 日期：2026-09-10
- 状态：研究完成，待确认是否进入实施计划
- 对应设计：`docs/data-visualization-report-design.md`

## 1. Phase 0 结论

Phase 0 建议通过，但只能以“参考设计、自主实现”的方式进入下一阶段，不能把 `lieflat-charts` 代码、模板或视觉资源直接带入当前项目。

原因：目标仓库根目录许可证为 **PolyForm Noncommercial License 1.0.0**。许可证允许非商业目的的学习、修改、分享和基于修改的作品；商业目的需要另行取得许可。仓库还声明 Chart.js、Apache ECharts 和 Inter 字体分别受各自许可证约束，不能把它们视为 PolyForm 许可证覆盖的内容。

当前项目如果存在收费会员、企业服务、对外商业部署或未来商业化计划，建议默认按“不可直接复用外部实现”处理。可以借鉴产品原则、数据契约思想、阅读速度分层和报告编排方法；首期代码、模板、样式、色板和资源全部自研。

## 2. 外部仓库核查结果

### 2.1 目录结构

仓库公开目录包含：

```text
agents/
docs/assets/
examples/
scripts/
templates/
LICENSE
SKILL.md
THIRD_PARTY_NOTICES.md
catalog.md
color-presets.js
mono-tokens.js
report-catalog.md
```

其中：

- `SKILL.md`：Agent 工作流、输出模式和选型约束。
- `catalog.md`：图型数据形状、场景、阅读时间和引擎索引。
- `report-catalog.md`：12 套中英文整页报告模板和模板契约。
- `templates/`：gallery、色彩样张、报告模板和交互大图。
- `mono-tokens.js`、`color-presets.js`：视觉 token 与色彩预设。
- `examples/`：公开数据案例。
- `scripts/validate.mjs`：发布前检查。
- `docs/assets/`：预览图片和动态资源。

这些文件都属于复用审查范围，不能因为是 Markdown、HTML 或 JavaScript 就默认可以复制进本项目。

### 2.2 可借鉴的设计原则

1. 默认图表模式，只有明确要求报告时才生成整页报告。
2. 先审计数据契约，再按场景和阅读速度选择图型。
3. 单个问题通常只生成一张图；多个独立结论才增加图数。
4. Mono 是视觉和语义不明确时的保底方案。
5. 同一交付物锁定一套色彩系统。
6. 报告模板保留完整页面骨架，不随意拼接多个模板。
7. 图表标题、旁注、来源、单位和留白都属于表达内容。
8. 交互只服务真实记录，不增加虚假装饰行为。

### 2.3 不直接复用的内容

- 外部 `SKILL.md` 原文或大段规则文本。
- `catalog.md` 和 `report-catalog.md` 的原始表格、名称体系和模板契约文本。
- `templates/` 下 HTML、SVG、CSS、脚本、报告版式和交互实现。
- `mono-tokens.js`、`color-presets.js` 的具体 token 和色值组合。
- `docs/assets/` 图片、GIF、字体和预览资源。
- 外部 examples 的数据、文案和成品页面。

### 2.4 可独立使用的第三方依赖

外部仓库声明：

| 依赖 | 用途 | 许可证处理 |
|---|---|---|
| Apache ECharts | 图表与交互网络图 | 当前项目已有 ECharts，按自身依赖清单管理 |
| Chart.js | 部分 Glance 图表 | 首期不新增，避免第二套图表栈 |
| Inter | 页面字体 | 首期使用项目已有字体方案或系统字体；若引入需保留 OFL 声明 |

## 3. 三份代表性样例数据

### 3.1 样例 A：业务月度趋势

用途：验证时间序列、趋势结论、折线图和业务简报。

```csv
month,orders,revenue,active_users
2026-01,820,164000,5100
2026-02,860,172000,5350
2026-03,910,191000,5680
2026-04,980,208000,6020
2026-05,1040,229000,6410
2026-06,1180,271000,7100
```

预期数据契约：

```json
{
  "dimensions": ["month"],
  "measures": ["orders", "revenue", "active_users"],
  "grain": "monthly",
  "timeField": "month",
  "quality": {
    "missingValues": 0,
    "duplicateRows": 0
  }
}
```

可验证结论：

- 订单量从 820 增长到 1180。
- 6 月订单量较 1 月增长约 43.9%。
- 收入与订单量同向变化。

推荐图表：

- 主图：`line`，x=`month`，y=`orders` 或 `revenue`。
- 可选第二图：`bar`，按月比较订单量。
- 不建议同时画出所有指标，除非用户明确要求多指标仪表盘。

### 3.2 样例 B：内容运营排名

用途：验证分类排名、排序、Top N、Glance 风格和文章运营分析。

```json
[
  {"title":"AI 写作入门","publishedAt":"2026-06-01","views":18200,"likes":920,"favorites":680},
  {"title":"RAG 实战指南","publishedAt":"2026-06-05","views":24100,"likes":1350,"favorites":1120},
  {"title":"Spring Boot 性能优化","publishedAt":"2026-06-08","views":15600,"likes":760,"favorites":510},
  {"title":"Vue 3 工程实践","publishedAt":"2026-06-12","views":19800,"likes":1100,"favorites":830},
  {"title":"数据可视化设计","publishedAt":"2026-06-18","views":27600,"likes":1680,"favorites":1450}
]
```

预期数据契约：

```json
{
  "dimensions": ["title", "publishedAt"],
  "measures": ["views", "likes", "favorites"],
  "grain": "article",
  "quality": {
    "missingValues": 0,
    "duplicateRows": 0
  }
}
```

可验证结论：

- `数据可视化设计`阅读量最高。
- `数据可视化设计`收藏量最高。
- 收藏率和点赞率可以作为派生指标，但必须由程序计算，而不是由模型估算。

推荐图表：

- 主图：`bar`，x=`title`，y=`views`，按 views 降序。
- 可选第二图：`bar`，x=`title`，y=`favorites`。
- 如比较阅读量与收藏量，使用双图或明确归一化，不直接把不同单位混在一个轴中。

### 3.3 样例 C：研究统计与构成

用途：验证类别构成、百分比、样本数、研究报告和 Editorial 风格。

```csv
segment,respondents,satisfaction_rate,conversion_rate
新用户,420,0.68,0.12
活跃用户,310,0.81,0.24
回流用户,180,0.74,0.19
企业用户,90,0.88,0.31
```

预期数据契约：

```json
{
  "dimensions": ["segment"],
  "measures": ["respondents", "satisfaction_rate", "conversion_rate"],
  "grain": "segment",
  "percentageFields": ["satisfaction_rate", "conversion_rate"],
  "quality": {
    "missingValues": 0,
    "duplicateRows": 0
  }
}
```

可验证结论：

- 企业用户满意度和转化率最高。
- 新用户样本量最大，但转化率最低。
- 满意度与转化率存在同向关系的观察现象，但不能在没有统计检验时宣称因果关系。

推荐图表：

- 主图：`bar`，分组比较 satisfaction 与 conversion 时必须统一百分比显示。
- 可选图：`pie` 只用于 respondents 构成，类别数量为 4，适合低基数构成。
- 可选图：`scatter`，x=`satisfaction_rate`，y=`conversion_rate`，点大小=`respondents`；标题必须说明是观察关系，不宣称因果。

## 4. 首期 Chart Spec 覆盖性验证

### 4.1 趋势场景

使用 `line` 足以表达：

- 时间字段到 x 轴。
- 数值指标到 y 轴。
- 单序列或有限多序列。
- 峰值、最低值和增长区间旁注。

### 4.2 排名场景

使用 `bar` 足以表达：

- 类别到 x 轴。
- 指标到 y 轴。
- 程序排序后生成 Top N。
- Glance 风格下的重点项标注。

### 4.3 研究统计场景

使用 `bar`、`pie`、`scatter` 和 `table` 足以表达首期样例：

- 构成：pie 或 table。
- 指标对比：bar。
- 两个连续指标的观察关系：scatter。
- 原始样本和方法限制：table + 报告说明。

### 4.4 推荐的首期 Spec 最小字段

```json
{
  "version": "1.0",
  "chartType": "line|bar|pie|scatter|table",
  "style": "mono|glance|editorial",
  "title": "string",
  "subtitle": "string|null",
  "source": "string",
  "unit": "string|null",
  "dataRef": "dataset-snapshot-id",
  "encoding": {
    "x": "field|null",
    "y": "field|null",
    "color": "field|null",
    "size": "field|null"
  },
  "filters": [],
  "sort": {"field":"string|null","direction":"asc|desc|null"},
  "annotations": [],
  "evidence": [],
  "options": {
    "topN": 10,
    "showLegend": true
  }
}
```

建议把实际行数据通过 `dataRef` 绑定到服务端数据快照，而不是让模型重复输出整份原始数据。这样可以减少 Token、避免数据篡改，并让图表和结论共享同一数据版本。

## 5. Phase 0 风险清单

| 风险 | 影响 | 建议 |
|---|---|---|
| 外部仓库商用许可限制 | 商业部署产生合规风险 | 不复制代码、模板和资源；必要时取得书面许可 |
| 模型生成错误统计 | 报告结论失真 | 统计、排序和派生指标全部程序计算 |
| 模型直接生成脚本 | XSS、任意执行、不可测试 | 只接受白名单 Chart Spec |
| 多图堆砌 | 报告信息密度过高 | 每图绑定一个独立结论，默认最多 6 图 |
| ECharts 与新图表栈并存 | 维护和导出复杂度上升 | 首期只复用 ECharts |
| 外部 CDN 不可用 | PDF/HTML 离线渲染失败 | 首期报告资源内置或使用现有本地依赖 |
| 数据来源不清 | 用户无法复核 | 保存数据快照、字段、公式、行范围和来源 |
| 将研究相关性写成因果 | 内容质量和可信度下降 | 结论模板中增加“观察关系/不能据此判断因果”限制 |

## 6. Phase 0 验收结果

- [x] 已核查目标仓库公开目录结构。
- [x] 已核查根目录许可证为 PolyForm Noncommercial 1.0.0。
- [x] 已核查第三方依赖声明。
- [x] 已明确可借鉴与不可直接复用的边界。
- [x] 已准备趋势、排名、研究统计三份样例数据。
- [x] 已验证首期 Chart Spec 可以覆盖三类场景。
- [x] 已确认首期使用 ECharts，不新增第二套图表渲染栈。
- [x] 已确认程序计算、AI 解释、Chart Spec 校验的责任边界。

## 7. 进入 Phase 1 前需要确认的事项

1. 是否按自主实现方式推进，不直接复制外部仓库代码和模板？
2. 是否采用 `dataRef` 绑定服务端数据快照，而不是让模型重复输出行数据？
3. 是否先实现 `line`、`bar`、`table` 三类，再扩展 `pie`、`scatter`、`area`？
4. 是否接受 Phase 1 先做“JSON/CSV → 校验 → 图表 → HTML/PNG”的最小闭环？
5. 是否暂缓完整报告数据库表、RAG 和文章回流，等 MVP 验证后再做？

## 8. 参考链接

- 目标文章：https://www.jamecling.com/archives/6510
- 目标仓库：https://github.com/larashero3-dotcom/lieflat-charts
- 许可证：https://polyformproject.org/licenses/noncommercial/1.0.0
- 外部 Skill 原文：https://raw.githubusercontent.com/larashero3-dotcom/lieflat-charts/main/SKILL.md
- 图表目录：https://raw.githubusercontent.com/larashero3-dotcom/lieflat-charts/main/catalog.md
- 报告目录：https://raw.githubusercontent.com/larashero3-dotcom/lieflat-charts/main/report-catalog.md
- 第三方声明：https://raw.githubusercontent.com/larashero3-dotcom/lieflat-charts/main/THIRD_PARTY_NOTICES.md
- Apache ECharts Dataset：https://apache.github.io/echarts-handbook/en/concepts/dataset/
- Apache ECharts SSR：https://apache.github.io/echarts-handbook/en/how-to/cross-platform/server/
- Vega-Lite：https://vega.github.io/vega-lite/
