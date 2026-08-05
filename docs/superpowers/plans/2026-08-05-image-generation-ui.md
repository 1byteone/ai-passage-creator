# 生图状态 UI 改造 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 生图过程中从抽象图标占位改为真实配图预览，每张图片加载后执行 CSS 渐进模糊过渡（blur 30px → 0px）。

**Architecture:** 3 层改动 — SSEMessage 类型补 image 字段 → 创作页 SSE 处理收集 URL → 组件渲染真实图片 + CSS blur 过渡。不改后端，不引入新依赖。

**Tech Stack:** Vue 3 (Composition API) / TypeScript / SCSS / Ant Design Vue

## Global Constraints
- TypeScript strict，`npm run type-check` 零错误
- CSS 用 `<style scoped lang="scss">`，禁 `//` 注释（无 lang 时用 `/* */`）
- 图片 `onerror` 处理必须覆盖网络失败场景
- 用户可见消息中文，简洁直接
- Conventional Commits + Co-Authored-By: Claude

---

### Task 1: SSEMessage 类型补 image 字段

**Files:**
- Modify: `frontend/src/utils/sse.ts:6-17`（SSEMessage 接口加 image）

**Interfaces:**
- Consumes: 现有 `SSEMessage` 接口（type/streaming/fullContent/imageRequirements/...）
- Produces: `SSEMessage.image` 可选字段，类型为 `{ position?, url?, method?, keywords?, sectionTitle?, description? }`，匹配后端 `ImageResult`

- [ ] **Step 1: 在 SSEMessage 接口加 image 字段**

```typescript
export interface SSEMessage {
  type: string
  content?: string
  fullContent?: string
  imageRequirements?: unknown[]
  images?: API.ImageItem[]
  // 单张配图完成事件携带的图片数据（后端 ImageResult）
  image?: { position?: number; url?: string; method?: string; keywords?: string; sectionTitle?: string; description?: string }
  message?: string
  outline?: Array<{ section: number; title: string; points: string[] }>
  titleOptions?: Array<{ mainTitle: string; subTitle: string }>
}
```

- [ ] **Step 2: type-check 确认**

Run: `cd frontend && npm run type-check`
Expected: 零错误

- [ ] **Step 3: Commit**

```bash
git add frontend/src/utils/sse.ts
git commit -m "fix(sse): SSEMessage 接口加 image 字段 — 接收 IMAGE_COMPLETE 图片数据

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: 创作页 SSE 处理 — 收集图片 URL 传给组件

**Files:**
- Modify: `frontend/src/pages/article/ArticleCreatePage.vue`（IMAGE_COMPLETE 分支 + 组件 props）

**Interfaces:**
- Consumes: `SSEMessage.image.url`（Task 1）、现有 `imageCount`/`totalImages`/`imagePhase`/`allImagesDone`
- Produces: `imageUrls` ref（string[]），传给 `ImageGenerationAnimation` 的 `imageUrls` prop

- [ ] **Step 1: 新增 imageUrls ref + 修改 IMAGE_COMPLETE 分支**

在 `imageCount` 声明附近（L905）追加：
```typescript
const imageUrls = ref<string[]>([])
```

在 `IMAGE_COMPLETE` 分支（L1087-1091）追加 URL 收集：
```typescript
case 'IMAGE_COMPLETE':
  // 单张配图完成
  imageCount.value++
  if (msg.image?.url) {
    imageUrls.value.push(msg.image.url)
  }
  addLog(`配图生成中 ${imageCount.value}/${totalImages.value}`, 'info')
  break
```

在 `startCreate` 重置区（L958-963）追加重置：
```typescript
imageUrls.value = []
```

- [ ] **Step 2: 传给 ImageGenerationAnimation 组件**

在 L297-303 的组件调用处新增 `imageUrls` prop：
```html
<ImageGenerationAnimation
  v-if="currentStep >= 3 && currentStep <= 5 && totalImages > 0"
  :total="totalImages"
  :done-count="imageCount"
  :image-urls="imageUrls"
  :phase="imagePhase"
  :completed="allImagesDone"
/>
```

- [ ] **Step 3: type-check**

Run: `cd frontend && npm run type-check`
Expected: 零错误

- [ ] **Step 4: Commit**

```bash
git add frontend/src/pages/article/ArticleCreatePage.vue
git commit -m "feat(create): IMAGE_COMPLETE 收集图片 URL 传入动画组件

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: ImageGenerationAnimation 组件 — 真实图片 + CSS 渐进模糊

**Files:**
- Modify: `frontend/src/pages/article/components/ImageGenerationAnimation.vue`（template + script + style）

**Interfaces:**
- Consumes: `imageUrls` prop（string[]，Task 2 传入）
- Produces: 每张卡片渲染真实图片，带 CSS blur 过渡

- [ ] **Step 1: 新增 imageUrls prop + 图片加载状态**

```typescript
const props = defineProps<{
  total: number
  doneCount: number
  imageUrls: string[]
  phase: 'analyzing' | 'generating' | 'done'
  completed: boolean
}>()

// 记录每张图片是否已加载完成（触发 blur→clear 过渡）
const loadedFlags = reactive<Record<number, boolean>>({})
const onImageLoad = (index: number) => { loadedFlags[index] = true }
// 移除未使用的 headerText 和 progressPercent 或保留
```

- [ ] **Step 2: 模板改造 — 卡片渲染真实图片**

```html
<div class="card-grid">
  <div v-for="(_, index) in total" :key="index"
    :class="['image-card', {
      'card-done': index < doneCount && imageUrls[index],
      'card-generating': index < doneCount && !imageUrls[index],
      'card-pending': index >= doneCount,
    }]"
  >
    <!-- 已完成：显示真实图片（渐进模糊） -->
    <img v-if="index < doneCount && imageUrls[index]"
      :src="imageUrls[index]"
      :class="['card-image', { loaded: loadedFlags[index] }]"
      @load="onImageLoad(index)"
      @error="onImageLoad(index)"
      alt="配图"
    />
    <!-- 待生成：灰色占位 -->
    <div v-else class="card-pending-content">
      <PictureOutlined class="card-pending-icon" />
      <span class="card-index">{{ index + 1 }}</span>
    </div>
  </div>
</div>
```

> 注：`@error` 时也触发 `loadedFlags`（即使图片加载失败也停止 blur 过渡，避免永远模糊）

- [ ] **Step 3: CSS 渐进模糊过渡**

```scss
// 图片布局
.image-card {
  overflow: hidden;
  &.card-done {
    background: rgba(34, 197, 94, 0.1);
    animation: card-appear 0.4s ease-out;
  }
  &.card-generating {
    background: rgba(34, 197, 94, 0.1);
  }
  &.card-pending {
    background: var(--surface-muted, var(--color-background-tertiary));
    animation: pulse-skeleton 1.6s ease-in-out infinite;
  }
}

.card-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: blur(30px);
  opacity: 0.7;
  transition: filter 2s ease-out, opacity 2s ease-out;
  &.loaded {
    filter: blur(0);
    opacity: 1;
  }
}
```

- [ ] **Step 4: type-check**

Run: `cd frontend && npm run type-check`
Expected: 零错误

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/article/components/ImageGenerationAnimation.vue
git commit -m "feat(ui): 生图组件渲染真实配图 + CSS 渐进模糊过渡

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: 端到端验证

**Files:** 无（验证）

- [ ] **Step 1: type-check + build**

Run: `cd frontend && npm run type-check && npm run build`
Expected: 两者零错误

- [ ] **Step 2: 逻辑验证**

启动前端 + 后端（当前已在跑 PID 29132），创建一篇文章：
1. 配图需求分析阶段 → 显示"分析中"（灰色占位 + 骨架脉冲）
2. 每张图片完成 → 卡片从灰色占位变为真实图片，从模糊逐渐变清晰
3. 全部完成 → 显示"全部配图生成完成" + 绿色对勾

## Self-Review

**Spec 覆盖：**
- ✅ SSEMessage 类型补 image 字段（Task 1）
- ✅ 创作页 SSE 处理收集 URL（Task 2）
- ✅ 组件渲染真实图片 + CSS blur 过渡（Task 3）
- ✅ 错误处理（onerror 触发 loadedFlags 避免永远模糊）
- ✅ 无后端改动，无新依赖

**已知限制：**
- CSS blur 过渡在 `@error` 时也触发（避免永远模糊，但图片实际是裂图），这是刻意选择（裂图仍存在，后续可加 error 态增强）