# 创作页配图加载动画 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在创作页正文生成完成后，底部立即显示配图加载动画组件，用「卡片逐张浮现」的视觉反馈消除配图空窗期，让用户感知配图正在生成。

**Architecture:** 新建独立组件 `ImageGenerationAnimation.vue`（Props: total/doneCount/phase/completed），替换 `ArticleCreatePage` 中现有的 `.image-progress-box`。SSE 事件驱动三态流转（analyzing→generating→done）。

**Tech Stack:** Vue 3 Composition API / TypeScript strict / Ant Design Vue 4 / Playwright + Node test

## Global Constraints

- 组件：Vue 3 `<script setup lang="ts">`，TypeScript strict，零容忍 vue-tsc 错误
- 状态：局部用 `ref`/`computed`；不碰 Pinia
- 样式：SCSS scoped，Ant Design Vue 组件优先
- XSS：不涉及 v-html
- SSE 生命周期：组件不持有 SSE 连接，状态变更集中在 ArticleCreatePage
- 测试运行：`cd frontend && npx playwright test tests/ui/article-create-flow.spec.ts`
- 提交格式：Conventional Commits + `Co-Authored-By: Claude <noreply@anthropic.com>`

---

### Task 1: 创建 ImageGenerationAnimation 组件

**Files:**
- Create: `frontend/src/pages/article/components/ImageGenerationAnimation.vue`

**Interfaces:**
- Consumes: 无（纯展示组件，收 Props）
- Produces: Props `{ total: number; doneCount: number; phase: 'analyzing'|'generating'|'done'; completed: boolean }`

- [ ] **Step 1: 创建组件文件**

创建 `frontend/src/pages/article/components/ImageGenerationAnimation.vue`：

```vue
<template>
  <div class="image-gen-animation" role="status" aria-live="polite">
    <!-- 标题区 -->
    <div class="animation-header">
      <PictureOutlined class="header-icon" />
      <span class="header-title">{{ headerText }}</span>
      <span v-if="phase === 'generating'" class="header-count">
        {{ doneCount }}/{{ total }}
      </span>
      <CheckCircleOutlined v-if="completed" class="header-check" />
    </div>

    <!-- 卡片区 -->
    <div class="card-grid">
      <div
        v-for="(_, index) in total"
        :key="index"
        :class="['image-card', {
          'card-done': index < doneCount,
          'card-pending': index >= doneCount,
        }]"
      >
        <div v-if="index < doneCount" class="card-done-content">
          <PictureOutlined class="card-done-icon" />
          <CheckCircleOutlined class="card-check-badge" />
        </div>
        <div v-else class="card-pending-content">
          <PictureOutlined class="card-pending-icon" />
          <span class="card-index">{{ index + 1 }}</span>
        </div>
      </div>
    </div>

    <!-- 进度条 -->
    <a-progress
      :percent="progressPercent"
      :status="completed ? 'success' : 'active'"
      :stroke-color="completed ? undefined : { from: '#22C55E', to: '#16A34A' }"
      class="animation-progress"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Progress as AProgress } from 'ant-design-vue'
import { CheckCircleOutlined, PictureOutlined } from '@ant-design/icons-vue'

const props = defineProps<{
  total: number
  doneCount: number
  phase: 'analyzing' | 'generating' | 'done'
  completed: boolean
}>()

// 阶段文案
const headerText = computed(() => {
  if (props.completed) return '全部配图生成完成'
  if (props.phase === 'analyzing') return '正在分析配图需求'
  if (props.phase === 'generating') return '正在生成配图'
  return '正在生成配图'
})

// 进度百分比（0-100）
const progressPercent = computed(() => {
  if (props.total <= 0) return 0
  return Math.round((props.doneCount / props.total) * 100)
})
</script>

<style scoped lang="scss">
.image-gen-animation {
  background: var(--color-background-secondary);
  border: 1px solid var(--border-subtle, var(--color-border-light));
  border-radius: var(--radius-lg);
  padding: 20px 24px;
  margin-top: 24px;

  .animation-header {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    margin-bottom: 16px;
    font-size: 15px;
    font-weight: 600;
    color: var(--color-text);

    .header-icon {
      color: var(--color-primary);
    }

    .header-count {
      color: var(--color-text-muted);
      font-variant-numeric: tabular-nums;
      font-weight: 500;
    }

    .header-check {
      color: var(--color-success);
      font-size: 16px;
    }
  }

  .card-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(72px, 1fr));
    gap: 12px;
    margin-bottom: 16px;
  }

  .image-card {
    aspect-ratio: 1;
    border-radius: var(--radius-md);
    overflow: hidden;
    display: flex;
    align-items: center;
    justify-content: center;

    &.card-pending {
      background: var(--surface-muted, var(--color-background-tertiary));
      animation: pulse-skeleton 1.6s ease-in-out infinite;
    }

    &.card-done {
      background: rgba(34, 197, 94, 0.1);
      animation: card-appear 0.4s ease-out;
    }
  }

  .card-pending-content,
  .card-done-content {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 100%;
    position: relative;
  }

  .card-pending-icon {
    font-size: 22px;
    color: var(--color-text-muted);
  }

  .card-index {
    position: absolute;
    bottom: 6px;
    right: 8px;
    font-size: 11px;
    color: var(--color-text-muted);
    font-variant-numeric: tabular-nums;
  }

  .card-done-icon {
    font-size: 26px;
    color: var(--color-primary);
  }

  .card-check-badge {
    position: absolute;
    top: 4px;
    right: 4px;
    font-size: 14px;
    color: var(--color-success);
  }

  .animation-progress {
    :deep(.ant-progress-inner) {
      background: var(--color-background-tertiary);
    }
  }
}

/* 骨架脉动 */
@keyframes pulse-skeleton {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

/* 卡片浮现 */
@keyframes card-appear {
  from {
    opacity: 0;
    transform: translateY(12px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

/* 移动端：卡片栅格更紧凑 */
@media (max-width: 768px) {
  .image-gen-animation {
    padding: 16px;
  }

  .card-grid {
    grid-template-columns: repeat(4, 1fr);
    gap: 8px;
  }
}
</style>
```

- [ ] **Step 2: type-check**

Run: `cd frontend && npm run type-check`
Expected: 零错误（组件尚未被引用，仅编译检查）

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/article/components/ImageGenerationAnimation.vue
git commit -m "feat(frontend): 配图加载动画组件 — 卡片逐张浮现 + 阶段文案 + 进度条

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: 集成到 ArticleCreatePage

**Files:**
- Modify: `frontend/src/pages/article/ArticleCreatePage.vue`

**Interfaces:**
- Consumes: `ImageGenerationAnimation` 组件（Task 1）
- Produces: `imagePhase` ref（'analyzing'|'generating'|'done'）、`allImagesDone` ref（boolean）；SSE 事件流转逻辑

- [ ] **Step 1: 引入组件**

在 `<script setup>` 顶部 import：

```typescript
import ImageGenerationAnimation from './components/ImageGenerationAnimation.vue'
```

- [ ] **Step 2: 新增状态**

在 `// 配图进度` 附近（imageCount/totalImages/imageProgress 声明处）新增：

```typescript
// 配图阶段状态（驱动动画组件）
const imagePhase = ref<'analyzing' | 'generating' | 'done'>('analyzing')
const allImagesDone = ref(false)
```

- [ ] **Step 3: 替换模板中的 image-progress-box**

找到模板中第 296-304 行的配图进度块：

```html
<!-- 配图进度 -->
<div v-if="currentStep === 4 && imageProgress > 0" class="image-progress-box">
  ...
</div>
```

替换为：

```html
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

- [ ] **Step 4: SSE 事件流转**

在 `handleSSEMessage` 中更新：

`AGENT3_COMPLETE` case（正文完成）——当前只有 `currentStep.value = 3`，增加重置动画态：

```typescript
case 'AGENT3_COMPLETE':
  // 正文完成，进入配图分析步骤
  isStreaming.value = false
  currentStep.value = 3
  imagePhase.value = 'analyzing'
  allImagesDone.value = false
  addLog('正文生成完成', 'success')
  break
```

`AGENT4_COMPLETE` case——增加 phase='generating'：

```typescript
case 'AGENT4_COMPLETE':
  // 配图分析完成，进入配图生成步骤
  currentStep.value = 4
  totalImages.value = msg.imageRequirements?.length || 5
  imagePhase.value = 'generating'
  addLog(`配图需求分析完成，共 ${totalImages.value} 张`, 'success')
  break
```

`AGENT5_COMPLETE` case——增加 done 态：

```typescript
case 'AGENT5_COMPLETE':
  // 所有配图完成，进入图文合成步骤
  currentStep.value = 5
  article.value.images = msg.images
  allImagesDone.value = true
  imagePhase.value = 'done'
  addLog('所有配图生成完成', 'success')
  break
```

- [ ] **Step 5: resetCreate 重置动画态**

在 `resetCreate` 中，`imageProgress.value = 0` 附近增加：

```typescript
imagePhase.value = 'analyzing'
allImagesDone.value = false
```

- [ ] **Step 6: 清理旧 CSS**

删除 `.image-progress-box` 的 CSS 块（第 1852-1875 行），避免死代码。

- [ ] **Step 7: type-check**

Run: `cd frontend && npm run type-check`
Expected: 零错误

- [ ] **Step 8: Commit**

```bash
git add frontend/src/pages/article/ArticleCreatePage.vue
git commit -m "feat(frontend): 创作页集成配图加载动画 — 替换 image-progress-box + SSE 三态流转

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: 扩展 E2E 测试 + 全量验证

**Files:**
- Modify: `frontend/tests/ui/article-create-flow.spec.ts`

**Interfaces:**
- Consumes: 组件渲染结果（Task 1/2）

- [ ] **Step 1: 扩展现有完整流程测试**

在 `article-create-flow.spec.ts` 的完整流程测试中，`AGENT3_COMPLETE` 推送后、`AGENT4_COMPLETE` 前插入断言（动画组件出现，分析态）：

```typescript
// 5. 确认大纲 → 推送正文流式 → 完成
await page.getByRole('button', { name: /确认并生成正文/ }).click()
sse.push({ type: 'AGENT3_STREAMING', content: '## 第一章\nAI 正在改变职场的每个角落。' })
sse.push({ type: 'AGENT3_COMPLETE' })

// 5.1 正文完成 → 配图动画组件出现（分析态）
await expect(page.getByText('正在分析配图需求')).toBeVisible()

sse.push({ type: 'AGENT4_COMPLETE', imageRequirements: [{ position: 1 }, { position: 2 }, { position: 3 }] })
// 5.2 配图分析完成 → 生成态文案 + 3 张卡片
await expect(page.getByText('正在生成配图')).toBeVisible()
await expect(page.locator('.image-card')).toHaveCount(3)

sse.push({ type: 'IMAGE_COMPLETE', image: { position: 1, url: 'https://img.example.com/1.png' } })
// 5.3 首张完成 → 1 张已完成卡（勾选角标出现）
await expect(page.locator('.image-card.card-done')).toHaveCount(1)
await expect(page.locator('.image-card.card-pending')).toHaveCount(2)

sse.push({ type: 'IMAGE_COMPLETE', image: { position: 2, url: 'https://img.example.com/2.png' } })
sse.push({ type: 'IMAGE_COMPLETE', image: { position: 3, url: 'https://img.example.com/3.png' } })
sse.push({ type: 'AGENT5_COMPLETE', images: [
  { position: 1, url: 'https://img.example.com/1.png' },
  { position: 2, url: 'https://img.example.com/2.png' },
  { position: 3, url: 'https://img.example.com/3.png' },
] })

// 5.4 全部完成 → 成功态文案
await expect(page.getByText('全部配图生成完成')).toBeVisible()

sse.push({ type: 'MERGE_COMPLETE', fullContent: '## 第一章\nAI 正在改变职场的每个角落。' })
sse.push({ type: 'QUALITY_CHECKED', score: 92, passed: true, detoxed: false, violations: [] })
sse.push({ type: 'ALL_COMPLETE' })
```

> **注**：原测试中 `AGENT4_COMPLETE` 的 `imageRequirements: []` 改为 `[{position:1},{position:2},{position:3}]`，`total=3` 便于断言卡片数量。`IMAGE_COMPLETE` 从 1 张改为 3 张（逐个推送，验证逐张浮现）。

- [ ] **Step 2: 运行 E2E**

Run: `cd frontend && npx playwright test tests/ui/article-create-flow.spec.ts`
Expected: 全部通过（含新增配图动画断言）

- [ ] **Step 3: build 验证**

Run: `cd frontend && npm run build`
Expected: 通过（vite build 才能发现纯 CSS `//` 注释等编译错误）

- [ ] **Step 4: 全量前端测试回归**

Run: `cd frontend && npm run test`
Expected: 全部通过（skill 单测 + Playwright E2E）

- [ ] **Step 5: Commit**

```bash
git add frontend/tests/ui/article-create-flow.spec.ts
git commit -m "test(frontend): 配图加载动画 E2E — 卡片逐张浮现 + 三态断言

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Self-Review

**1. Spec coverage:**
- 组件 ImageGenerationAnimation.vue → Task 1 ✅
- 替换 image-progress-box + SSE 三态流转 → Task 2 ✅
- resetCreate 重置动画态 → Task 2 Step 5 ✅
- 清理旧 CSS → Task 2 Step 6 ✅
- E2E 断言卡片浮现/进度条/完成态 → Task 3 ✅
- 验收标准（type-check/build/E2E）→ Task 3 Steps 2-4 ✅

**2. Placeholder scan:** 无 TBD/TODO。所有代码块完整，无"类似 Task N"引用。

**3. Type consistency:** `imagePhase`（'analyzing'|'generating'|'done'）在 Task 2 定义并注入组件；组件 Props `phase` 同类型。`allImagesDone` 驱动 `completed`。组件 `headerText` 处理 completed 优先。CSS class 名 `.image-card`/`.card-done`/`.card-pending` 在 Task 1 组件与 Task 3 测试断言一致。
