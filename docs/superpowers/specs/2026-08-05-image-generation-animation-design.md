# 创作页配图加载动画 — 设计文档

> **日期**: 2026-08-05
> **分支**: dev
> **状态**: 已批准（头脑风暴对齐 6 项决策）
> **前置依赖**: 创作全链路已完成（ArticleCreatePage / SSE 事件流 / 配图进度条雏形）

---

## 1. 背景与目标

创作页正文生成完成后，到第一张配图完成之间存在**空窗期**：用户看到正文停在底部，`currentStep=3` 但没有任何视觉反馈，以为页面阻塞卡住。现有 `.image-progress-box`（第 297-304 行）仅在 `currentStep===4 && imageProgress>0` 时显示——即收到第一个 `IMAGE_COMPLETE` 后才出现，空窗期无反馈。

目标：在正文生成完成后，底部立即显示配图加载动画，用「卡片逐张浮现」的视觉反馈告知用户配图正在生成，消除"卡住"的错觉。

### 1.1 核心痛点

| 问题 | 现状 |
|---|---|
| 空窗期无反馈 | 正文完成（step 3）→ 配图分析（step 4）→ 首张完成之间，页面静止 |
| 进度条出现晚 | 仅在 `imageProgress > 0`（收到首张 IMAGE_COMPLETE）后显示 |
| 无视觉动画 | 只有静态进度条，无卡片浮现等动效 |
| 阶段文案缺失 | 无法区分"分析中" vs "生成中" vs "完成" |

---

## 2. 需求对齐（头脑风暴沉淀）

| 维度 | 决策 |
|---|---|
| 触发时机 | 正文生成完成（`currentStep=3`）即显示，覆盖配图分析+生成全程，消除空窗期 |
| 动画形态 | 卡片逐张浮现（对应真实配图） |
| 信息层级 | 标题 + 卡片动画 + 进度条 |
| 完成态 | 全部完成后动画区变成功态（✓），自然过渡到图文合成 |
| 卡片语义 | 每张卡片对应一张待生成配图，收到 `IMAGE_COMPLETE` 时该卡变「已生成」态 |
| 异常处理 | 沿用现有 SSE 机制，不额外加超时判断 |

---

## 3. 技术方案（方案 A：独立组件）

**决策**: 新建 `ImageGenerationAnimation.vue` 独立组件，由 `ArticleCreatePage` 引入。

**理由**:
- `ArticleCreatePage.vue` 已 2710 行（超载），组件化避免继续膨胀
- 逐张对应配图 + 成功态 + 进度条需要状态逻辑，封装成组件职责清晰、可测试
- 未来其他页面（Skill 执行）可复用
- 符合 "Simplicity First"（组件内部纯模板 + CSS，无复杂状态机）

**Trust Spectrum**: 🟡 业务逻辑（SSE 状态驱动）+ 🟢 样板代码（CSS 动画），测试覆盖。

---

## 4. 组件设计（ImageGenerationAnimation.vue）

### 4.1 Props

| Prop | 类型 | 默认 | 说明 |
|---|---|---|---|
| `total` | number | 5 | 总配图数（AGENT4_COMPLETE 的 imageRequirements.length） |
| `doneCount` | number | 0 | 已完成数（imageCount） |
| `phase` | 'analyzing' \| 'generating' \| 'done' | 'analyzing' | 当前阶段 |
| `completed` | boolean | false | 全部完成（AGENT5_COMPLETE） |

### 4.2 模板结构

```
ImageGenerationAnimation
├── 标题区：PictureOutlined + 阶段文案
│   ├── analyzing: 正在分析配图需求…
│   ├── generating: 正在生成配图 {doneCount}/{total}
│   └── done: 全部配图生成完成 ✓
├── 卡片区：v-for 渲染 total 张
│   ├── 未完成卡（index >= doneCount）：骨架占位符
│   │   ├── 灰底 + PictureOutlined 图标 + 序号
│   │   └── 脉动动画（@keyframes pulse-skeleton）
│   └── 已完成卡（index < doneCount）：浮现动画
│       ├── 渐显 + 上移 + 缩放（@keyframes card-appear）
│       └── 右上角 CheckCircle 角标（淡入）
└── 进度条：a-progress（percent = doneCount/total*100, status active → success）
```

### 4.3 关键动画

| 动画 | 实现 | 触发 |
|---|---|---|
| 骨架脉动 | `@keyframes pulse-skeleton`（opacity 0.5↔1 呼吸） | 未完成卡 |
| 卡片浮现 | `@keyframes card-appear`（opacity 0→1 + translateY 12px→0 + scale 0.95→1） | doneCount 变化（index < doneCount 的卡） |
| 勾选角标 | 完成卡右上角 CheckCircle 图标，淡入 | 该卡完成 |
| 进度条 | a-progress status='active' → 'success' | completed=true |

### 4.4 卡片语义

- 每张卡片 `index` 对应一张配图（0 基）
- `index < doneCount` → 已完成卡（浮现 + 勾选）
- `index >= doneCount` → 未完成卡（骨架脉动）
- `doneCount === total` → 全部完成，进度条变 success + 成功态文案

---

## 5. ArticleCreatePage 集成

### 5.1 替换现有进度条

现有 `.image-progress-box`（第 297-304 行）**替换**为组件：

```vue
<!-- 配图生成动画（正文完成即显示，覆盖分析+生成+合成前） -->
<ImageGenerationAnimation
  v-if="currentStep >= 3 && currentStep < 5 && totalImages > 0"
  :total="totalImages"
  :done-count="imageCount"
  :phase="imagePhase"
  :completed="allImagesDone"
  class="image-animation-area"
/>
```

### 5.2 新增状态

```typescript
// 配图阶段状态
const imagePhase = ref<'analyzing' | 'generating' | 'done'>('analyzing')
const allImagesDone = ref(false)
```

### 5.3 SSE 事件驱动的阶段流转

| SSE 事件 | 状态变更 |
|---|---|
| `AGENT3_COMPLETE`（正文完成） | `currentStep=3` → 组件出现，`imagePhase='analyzing'` |
| `AGENT4_COMPLETE`（分析完成） | `totalImages` 更新，`imagePhase='generating'` |
| `IMAGE_COMPLETE`（单张完成） | `imageCount++` → 卡片浮现 + 进度条推进 |
| `AGENT5_COMPLETE`（全部完成） | `allImagesDone=true`，`imagePhase='done'` |
| `MERGE_COMPLETE`（合成完成） | `currentStep=5` → 组件消失 |

### 5.4 边界处理

| 边界 | 处理 |
|---|---|
| `totalImages` 未更新（AGENT4 前） | 默认 `total=5`（现有逻辑），组件显示 5 张骨架 |
| 文章无配图（total=0） | 组件不显示（`v-if` 加 `totalImages > 0` 守卫） |
| `imagePhase` 初始 | `analyzing`，正文完成即显示 |
| 重新创作 `resetCreate` | 重置 `imagePhase='analyzing'`、`allImagesDone=false`、`imageCount=0` |
| 移动端 | 卡片栅格自适应（2 列 → 4 列），复用现有响应式断点 |

### 5.5 现有进度条去留

- 现有 `.image-progress-box`（含 a-progress + 计数文案）**移除**，由组件内进度条替代
- 现有 `.image-progress-box` CSS **清理**（避免死代码）

---

## 6. 测试策略

### 6.1 测试方式

| 层级 | 测试内容 | 方式 |
|---|---|---|
| 组件渲染 | 渲染 N 张卡片、骨架/已生成/完成三态切换 | 扩展创作全流程 E2E |
| 状态流转 | SSE 事件驱动阶段流转（AGENT3→4→IMAGE_COMPLETE→5） | 扩展现有创作 E2E（`b26f03b` 已有 Mock SSE 驱动阶段流转） |
| 边界 | total=0 不显示、重新创作重置 | E2E |

### 6.2 验收标准

1. `npm run type-check` 零错误
2. `npm run build` 通过
3. 新增 E2E 测试通过（Mock SSE 驱动配图阶段，断言卡片浮现 + 进度条 + 完成态）
4. 现有测试无回归

---

## 7. 目录结构

```
新增:
  frontend/src/pages/article/components/ImageGenerationAnimation.vue   # 配图加载动画组件

修改:
  frontend/src/pages/article/ArticleCreatePage.vue                     # 集成组件 + 状态管理（imagePhase/allImagesDone）
  frontend/tests/ui/<创作全流程>.spec.ts                               # 扩展配图阶段断言
```

---

## 8. 风险与缓解

| 风险 | 缓解 |
|---|---|
| 卡片动画性能（total 大） | total 通常 ≤5，v-for 渲染开销可忽略；必要时限流 |
| SSE 事件乱序 | 沿用现有 currentStep 驱动，事件按后端顺序到达 |
| 组件与页面耦合 | 组件只收 Props + 发事件，不碰 SSE；状态变更集中在 ArticleCreatePage |
| CSS 动画兼容 | 仅用 opacity/transform（GPU 加速），无兼容问题 |
| 重新创作脏状态 | resetCreate 统一重置 imagePhase/allImagesDone |
