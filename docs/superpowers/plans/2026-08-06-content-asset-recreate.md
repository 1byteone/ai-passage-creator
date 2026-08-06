# 内容资产库 + 历史文章一键再创作 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 文章列表显示封面缩略图 + COMPLETED 文章支持"再创作"（预填选题/风格/方法论/配图方式 → 创作页确认后重新生成）。

**Architecture:** 3 层改动 — 类型补 methodology → 创作页扩展 query 预填 + 方法论选择器 → 列表加缩略图列 + 再创作按钮。不新增后端接口（复用 `/article/create`）。

**Tech Stack:** Vue 3 (Composition API) / TypeScript / Ant Design Vue / SCSS

## Global Constraints
- TypeScript strict，`npm run type-check` 零错误
- 用户可见消息中文，简洁直接
- `v-html` 必须经 DOMPurify（本任务无新增）
- Conventional Commits + Co-Authored-By: Claude
- 只改前端，不改后端（methodology 后端已支持）

---

### Task 1: 类型补齐 — ArticleCreateRequest 加 methodology

**Files:**
- Modify: `frontend/src/api/typings.d.ts`（ArticleCreateRequest 加 `methodology?: string`）

**Interfaces:**
- Consumes: 现有 `ArticleCreateRequest` 类型（topic/style/enabledImageMethods/characterStyle）
- Produces: `methodology?: string` 字段，后端 `ArticleCreateRequest.methodology` 已有

- [ ] **Step 1: 改类型**

在 `frontend/src/api/typings.d.ts` L69-74 的 `ArticleCreateRequest` 补字段：
```typescript
type ArticleCreateRequest = {
  topic?: string
  style?: string
  methodology?: string   // 方法论文档（default/douyin/xiaohongshu/wechat），后端回退 default
  enabledImageMethods?: string[]
  characterStyle?: string
}
```

- [ ] **Step 2: type-check**

Run: `cd frontend && npm run type-check`
Expected: 零错误

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api/typings.d.ts
git commit -m "feat(create): ArticleCreateRequest 补 methodology 字段 — 方法论文档透传

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: 创作页 — 方法论选择器 + query 预填 + createArticle 透传

**Files:**
- Modify: `frontend/src/pages/article/ArticleCreatePage.vue`

**Interfaces:**
- Consumes: `route.query`（再创作传入 topic/style/methodology/characterStyle/imageMethods）、现有 selectedStyle/selectedImageMethods/selectedCharacterStyle
- Produces: `selectedMethodology` ref；onMounted 预填全部参数；createArticle 透传 methodology

- [ ] **Step 1: 新增 selectedMethodology ref**

在 `selectedStyle` 声明（L866）附近追加：
```typescript
const selectedMethodology = ref('default')  // 方法论文档（默认 default）
```

- [ ] **Step 2: 新增方法论文档选择器 UI**

在 `style-section`（L122-136）和 `image-methods-section`（L139）之间插入：
```html
<section class="methodology-section setting-panel">
  <div class="section-header">
    <div>
      <span class="section-title">创作方法论文档</span>
      <span class="section-tip">决定内容生成框架，默认通用文档</span>
    </div>
  </div>
  <a-radio-group v-model:value="selectedMethodology" class="methodology-group">
    <a-radio value="default">通用</a-radio>
    <a-radio value="douyin">抖音爆款</a-radio>
    <a-radio value="xiaohongshu">小红书种草</a-radio>
    <a-radio value="wechat">公众号长文</a-radio>
  </a-radio-group>
</section>
```

- [ ] **Step 3: onMounted 扩展 query 预填**

在 `onMounted`（L1369-1375）扩展：
```typescript
onMounted(() => {
  if (route.query.topic) topic.value = route.query.topic as string
  if (route.query.style) selectedStyle.value = route.query.style as string
  if (route.query.methodology) selectedMethodology.value = route.query.methodology as string
  if (route.query.characterStyle) selectedCharacterStyle.value = route.query.characterStyle as string
  if (route.query.imageMethods) {
    selectedImageMethods.value = (route.query.imageMethods as string).split(',')
  }
  loadRecommendedTopics(false)
})
```

- [ ] **Step 4: createArticle 透传 methodology**

在 `createArticle` 调用（L1061-1066）补：
```typescript
const res = await createArticle({
  topic: topic.value,
  style: selectedStyle.value || undefined,
  methodology: selectedMethodology.value === 'default' ? undefined : selectedMethodology.value,
  enabledImageMethods: selectedImageMethods.value.length > 0 ? selectedImageMethods.value : undefined,
  characterStyle: selectedCharacterStyle.value || undefined,
})
```

- [ ] **Step 5: 样式补 methodology-group**

在 `image-methods-section` 样式（L1746）附近追加：
```scss
.methodology-group {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
}
```

- [ ] **Step 6: type-check**

Run: `cd frontend && npm run type-check`
Expected: 零错误

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/article/ArticleCreatePage.vue
git commit -m "feat(create): 方法论选择器 + 再创作 query 预填 + methodology 透传

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: 列表页 — 封面缩略图列 + 再创作按钮

**Files:**
- Modify: `frontend/src/pages/article/ArticleListPage.vue`

**Interfaces:**
- Consumes: `record.coverImage`（旧文章封面）、`record.topic/style/methodology/characterStyle/enabledImageMethods`
- Produces: 缩略图列 + ArticleActions 加"再创作"按钮（router.push 带 query）

- [ ] **Step 1: columns 加封面列**

在 `columns`（L227-232）`title` 列前插入：
```typescript
const columns = [
  { title: '封面', key: 'cover', width: 110 },
  { title: '文章', key: 'title' },
  { title: '状态', key: 'status', width: 112 },
  { title: '创建时间', key: 'createTime', width: 168 },
  { title: '操作', key: 'action', width: 280 },
]
```

- [ ] **Step 2: bodyCell 加封面渲染**

在 `#bodyCell`（L108-114）`title` 分支前插入：
```html
<template v-if="column.key === 'cover'">
  <img
    v-if="record.coverImage"
    :src="record.coverImage"
    class="cover-thumb"
    alt="封面"
  />
  <div v-else class="cover-placeholder">
    <PictureOutlined />
  </div>
</template>
```

- [ ] **Step 3: 再创作方法 + 按钮**

在 `ArticleActions` setup 中新增 `recreateArticle` 方法（在 `viewArticle` 附近）：
```typescript
const recreateArticle = (record: API.ArticleVO) => {
  const query: Record<string, string> = {}
  if (record.topic) query.topic = record.topic
  if (record.style) query.style = record.style
  if (record.methodology && record.methodology !== 'default') query.methodology = record.methodology
  if (record.characterStyle) query.characterStyle = record.characterStyle
  if (record.enabledImageMethods) query.imageMethods = record.enabledImageMethods.join(',')
  router.push({ path: '/create', query })
}
```

在 `ArticleActions` 返回的 h() 数组中，`查看` 按钮后加：
```typescript
props.record.status === 'COMPLETED'
  ? h(
      Button,
      { type: 'link', size: 'small', onClick: () => recreateArticle(props.record) },
      { icon: () => h(CopyOutlined), default: () => '再创作' },
    )
  : null,
```

- [ ] **Step 4: 缩略图样式**

在样式区追加：
```scss
.cover-thumb {
  width: 80px;
  height: 48px;
  object-fit: cover;
  border-radius: var(--radius-sm, 6px);
  border: 1px solid var(--color-border);
  display: block;
}

.cover-placeholder {
  width: 80px;
  height: 48px;
  border-radius: var(--radius-sm, 6px);
  background: var(--color-background-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-disabled, #d1d5db);
}
```

- [ ] **Step 5: type-check**

Run: `cd frontend && npm run type-check`
Expected: 零错误

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/article/ArticleListPage.vue
git commit -m "feat(ui): 文章列表封面缩略图 + 历史文章一键再创作按钮

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: 端到端验证

**Files:** 无（验证）

- [ ] **Step 1: type-check + build**

Run: `cd frontend && npm run type-check && npm run build`
Expected: 两者零错误

- [ ] **Step 2: 逻辑验证**

启动前后端：
1. 文章列表显示封面缩略图（有 coverImage 的显示图，无的显示占位）
2. COMPLETED 文章点"再创作" → 跳转 `/create?topic=..&style=..&methodology=..`
3. 创作页预填选题/风格/方法论，用户修改后点创作 → 正常生成

## Self-Review

**Spec 覆盖：**
- ✅ 封面缩略图列（Task 3）
- ✅ 再创作按钮 + query 构造（Task 3）
- ✅ query 预填（Task 2 Step 3）
- ✅ 方法论选择器 + 透传（Task 2 Step 2/4）
- ✅ 类型补齐（Task 1）
- ✅ 无后端改动

**已知注意：**
- `selectedMethodology === 'default'` 时不传（后端回退 default），避免多余传参
- `enabledImageMethods` 用 `join(',')` 传 query，创作页 `split(',')` 还原
- 移动端缩略图列在 `@media` 下需隐藏（表格列 110px 可能挤）