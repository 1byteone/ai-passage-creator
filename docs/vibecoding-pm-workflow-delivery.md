# Vibecoding PM 工作流 交付与二次开发经验

- 日期：2026-09-12
- 分支：`dev`
- 相关技能：`skills/vibecoding-pm-workflow/SKILL.md`
- 相关约定：`AGENTS.md`（PM 原型交付标准 / 页面全流程走查标准 / 可复用技能）
- 对应提交：`a9a7df8`（原型纵向平铺）→ `916cdb1`（工作台页面与入口）→ 本次文档

---

## 1. 交付概览

把两个外部 Prompt —— **低保真原型生成** 与 **页面全流程巡查** —— 收敛为项目内一个可复用工作台：`/vibecoding`。

交付形态是**前端纯本地能力**，不引入后端、不调用大模型：

| 维度 | 选择 | 理由 |
|---|---|---|
| 运行位置 | 浏览器本地 | 两个 Prompt 本质是结构化方法论，规则确定，不需要模型参与即可产出可复现结果 |
| 状态存储 | `localStorage`（key `vibecoding-workflow-state-v1`） | 单机即用，刷新不丢；无后端依赖 |
| 原型产物 | 单文件 HTML（内联 CSS，无脚本、无 CDN） | Prompt 原文要求「双击即可查看」，也便于在评审会上直接打开 |
| 走查产物 | Markdown / JSON 导出 | Markdown 交给 AI 或开发，JSON 可被程序再次导入 |

**明确不在本次范围**（避免与其他工作线混淆）：

- 不接入 AI 解析 PRD —— `analyzePrd` 是确定性关键词提取
- 不做后端产物存储与分享链接
- 不做 PDF 导出
- 不改造为项目内 Skill（`skill.yaml` + SSE）形态

---

## 2. 自研边界

两个 Prompt 是**方法论输入**，不是代码来源。本次交付：

- **未复制任何外部代码、模板、样式或资源**
- 原型 HTML 的 DOM 结构、CSS、连接线、序号体系全部为本项目自研
- 走查页面的三态标记、回归轮次、脱敏导出均为自研实现

---

## 3. 已落地能力

| 能力 | 实现位置 | 说明 |
|---|---|---|
| 先确认再生成闸门 | `VibecodingWorkflowPage.vue` `allConfirmed` | 流程项未全部勾选时「生成低保真原型」按钮禁用 |
| 流程草案提取 | `analyzePrd` | 按 PRD 行序提取页面/状态/操作/下一状态，自动补首次使用、权限、加载、无数据、失败恢复等状态 |
| 流程项逐条编辑确认 | 页面 `.flow-row` | 可改页面名、勾选确认，未确认项带「已改动」标记 |
| 原型纵向平铺 | `buildPrototypeHtml` | 按流程顺序自上而下排列；分组按连续同名分段；屏间/组间连接线贯通；每屏含全局序号、组内序号、状态、操作、下一状态 |
| 单文件无依赖 | `PROTOTYPE_STYLE` | 内联 CSS，无 `<script>`、无 `<link>`、无外部字体 |
| 待确认可见区分 | `.is-unconfirmed` / `.field.is-pending` | 屏级虚线边框 + 提示条；字段级灰底 + 虚线标记 |
| 原型预览与下载 | iframe `data:text/html` + Blob 下载 | 页面内预览，下载后可直接打开 |
| 走查清单生成 | `toAuditItems` | 每项含复现步骤、预期、实际、反馈与三态标记 |
| 三态标记与反馈 | `setAuditStatus` + `@input="touchSaved"` | `可以 / 待改 / 阻塞`，输入即存 |
| 回归轮次 | `newRound` | 保留上一轮反馈原话（`previousFeedback`），标记已改动，重置为未检查 |
| 导出与脱敏 | `exportAuditMarkdown` / `redactSecrets` | Markdown + JSON；导出前脱敏 `password/ token/ secret/ api_key/ cookie` |
| 持久化 | `saveWorkflowState` / `loadWorkflowState` | 防抖 300ms 写入 localStorage |

---

## 4. 契约

### 4.1 数据结构

```ts
interface FlowItem {
  id: string; group: string; pageName: string; state: string
  action: string; next: string; confirmed: boolean; changed?: boolean
}

interface AuditItem {
  id: string; group: string; pageName: string; state: string
  reproduce: string; expected: string; actual: string; feedback: string
  status: 'unreviewed' | 'pass' | 'todo' | 'blocked'
  changed: boolean; previousFeedback?: string
}
```

### 4.2 原型 HTML 结构契约

- 顶层：`<main class="wrap">` → `<h1>` / `.note` / `.summary` / `<section class="flow">`
- 分组：每个连续同名分组渲染一个 `<section class="group">`，含 `.group-head`（`.group-badge`「分组 NN」/ `<h2>` / `.group-meta` 步骤区间）
- 屏幕：`<article class="screen" data-step="N">`，未确认时类名为 `screen is-unconfirmed`
- 连接线：组内屏间与组间均插入 `.connector`
- 硬约束：**分组只合并连续同名 run，不重排**（数组顺序即流程顺序）

### 4.3 安全不变量

- 所有动态文本经 `escapeHtml` 进入**元素文本节点**，不出现在属性中
  （`escapeHtml` 不转义单引号，因此动态值禁止进属性）
- `data-step` 只接受 JS 计算的数字
- 导出前经 `redactSecrets` 脱敏

---

## 5. 验收

### 5.1 单元测试

```bash
cd frontend && npm run test:skill
# # tests 21
# # pass 21
# # fail 0
```

其中 `tests/vibecoding-workflow.test.ts` 新增 6 个用例，覆盖：流程顺序、分组不重排、未确认标记、XSS 转义、空状态、`analyzePrd` 顺序一致性。

### 5.2 构建

```bash
cd frontend && npm run build-only
# ✓ built in 16.54s
```

### 5.3 页面测试

```bash
cd frontend && npm run test:ui -- vibecoding-workflow
# 3 passed (27.6s)
```

覆盖：确认闸门 → 生成 → 预览 → 下载；走查反馈保存 → 回归轮次保留原话 → 导出；375/768/1024/1440 四档宽度无横向溢出。

### 5.4 静态检查

```bash
cd frontend && npx eslint src/services/vibecodingWorkflow.ts tests/vibecoding-workflow.test.ts
# 退出码 0
```

### 5.5 ⚠️ type-check 当前不绿（与本次交付无关）

```bash
cd frontend && npm run type-check
# 29 处报错，全部来自 src/pages/skill/components/SkillResultVibecoding.vue
```

该文件（621 行）不属于本次交付，是另一条工作线（应用内 Skill 形态）正在生成的产物，当前**文件内反引号全部缺失**，导致其中所有模板字符串语法非法。本次交付的文件零报错。**在该文件修复前，`npm run check` 与 CI 的 type-check 门禁无法通过。**

---

## 6. 已知限制

| # | 限制 | 归属 |
|---|---|---|
| 1 | `analyzePrd` 为确定性关键词提取、最多 30 行，结果需人工确认（页面已明示「结果必须人工确认后才能生成」） | 设计取舍 |
| 2 | 分组仅按「连续同名」合并；PRD 里分组名交错时会产生多个分组段 | 设计取舍 |
| 3 | 无后端产物存储，原型与走查结果不跨设备、不跨用户共享 | 后续阶段 |
| 4 | 无 PDF 导出 | 后续阶段 |
| 5 | `previousFeedback` 依赖 localStorage，清缓存即丢 | 后续阶段 |
| 6 | 图表/原型无真机截图对照，走查仍靠人工逐屏 | 设计取舍 |

---

## 7. 后续路线

1. **接入 AI 解析 PRD** —— 用模型替换 `analyzePrd` 的关键词规则，同时保留确定性降级
2. **走查结果后端化** —— 落库 + 团队共享链接 + 跨轮次历史对比
3. **与项目 Skill 引擎打通** —— 以 `skill.yaml` + SSE 形态提供，复用 HITL 确认机制
4. **原型产物接入能力** —— 与 dataviz 报告、卡片渲染共用导出链路

---

## 8. 二次开发经验沉淀

本次交付验证了 `CLAUDE.md` 新增的「外部能力二次开发工作流」五步法：

| 步骤 | 本次实践 |
|---|---|
| 确认来源与许可证 | 两个 Prompt 为方法论输入，无代码许可问题；对照 dataviz 的 PolyForm 教训，明确「只吸收方法论、不搬运实现」 |
| 读产品原则 | 提取「先确认再生成」「按流程顺序平铺」「异常状态必须覆盖」「反馈闭环」四条 |
| 写设计规格 | 先核查发现能力已存在但未交付，据此把范围从「新建」改为「补齐缺口 + 收敛提交」 |
| 转实施计划 | 明确提交边界（只提交 vibecoding，隔离英语训练工作线）与硬约束（不得重排分组、动态文本不进属性） |
| 交付与沉淀 | 本文档记录真实命令输出；`CLAUDE.md` 新增工作流章节与暗坑 #17/#18 |

**过程中最有价值的一次核查**：初次判断「功能已存在且完整」，实际逐一核对后才发现原型布局与 Prompt 原文不符（网格 vs 纵向平铺），以及页面文件存在未闭合的多行字符串。**「看起来能用」与「符合契约」之间的距离，只能靠逐条核对和真实执行来度量。**
