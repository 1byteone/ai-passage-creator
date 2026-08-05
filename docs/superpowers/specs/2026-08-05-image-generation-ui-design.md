# 生图状态 UI 改造 — 真实配图预览 + 渐进模糊特效

日期: 2026-08-05
状态: 设计稿

## Context

当前 `ImageGenerationAnimation` 组件仅显示抽象图标占位格（`PictureOutlined` + 骨架脉冲），不展示真实配图。用户期望像 ChatGPT/GPT-Image 那样：**图片生成完成后，从模糊逐渐过渡到清晰**。

**根因**：后端 `IMAGE_COMPLETE` 事件已推送完整 `ImageResult { url, position, ... }`，但：
1. 前端 `SSEMessage` 类型缺 `image` 字段，TypeScript 无法接收 `msg.image.url`
2. 组件只接收 `doneCount`（数字），不接收图片 URL 数组
3. 组件只有 `phase: 'analyzing'|'generating'|'done'` 三态，`'generating'` 阶段始终显示灰色占位

## 方案

### 1. 类型修复

`frontend/src/utils/sse.ts` 的 `SSEMessage` 接口新增：
```ts
image?: { position?: number; url?: string; method?: string; keywords?: string; sectionTitle?: string; description?: string }
```

### 2. 创作页 SSE 处理改造

`ArticleCreatePage.vue` 的 `IMAGE_COMPLETE` 分支（L1087-1091）：
```ts
case 'IMAGE_COMPLETE':
  imageCount.value++
  if (msg.image?.url) {
    imageUrls.value.push(msg.image.url)
  }
  addLog(`配图生成中 ${imageCount.value}/${totalImages.value}`, 'info')
  break
```

新增 `imageUrls` ref（string[]），传给 `ImageGenerationAnimation`。

### 3. ImageGenerationAnimation 组件改造

**新增 props**：
```ts
imageUrls: string[]  // 已完成图片的 URL 数组（实时追加）
```

**卡片渲染逻辑**（从抽象图标改为真实图片）：
- 待生成（`card-pending`）：灰色占位块 + 骨架脉冲（现状）
- 生成中（`card-generating`）：图片已加载，**CSS blur(30px) → 2s 过渡到 blur(0)**
- 已完成（`card-done`）：清晰图片 + 绿色对勾角标

**CSS 渐进模糊**：
```scss
.card-generating img {
  filter: blur(30px);
  opacity: 0.7;
  transition: filter 2s ease-out, opacity 2s ease-out;
}
.card-generating img.loaded {
  filter: blur(0);
  opacity: 1;
}
```

图片加载完成后通过 `onLoad` 事件添加 `loaded` 类触发过渡。

### 4. 错误处理

- 图片 URL 加载失败（`onerror`）：卡片显示红色错误图标 + 文字"生成失败"
- 某张图失败但其他成功：`IMAGE_COMPLETE` 未推送该图 URL，卡片停留在"待生成"状态，由 `AGENT5_COMPLETE` 事件统一处理
- 所有图都失败：`imageUrls` 为空，组件显示"配图生成失败"提示

## 不做的事

- 不引入 Three.js/WebGL（额外 150KB，后端无 streaming 中间步骤无法同步）
- 不改后端（`IMAGE_COMPLETE` 已推送完整数据，只是前端忽略）
- 不做 Canvas 2D 噪声（纯 CSS 达到类似效果，零依赖）
- 不做"逐张重试"（后端不支持单张重试，仅在 AGENT5_COMPLETE 失败时整体重试）

## 文件清单

| 文件 | 操作 |
|------|------|
| `frontend/src/utils/sse.ts` | SSEMessage 加 `image` 字段 |
| `frontend/src/pages/article/ArticleCreatePage.vue` | IMAGE_COMPLETE 分支追加 imageUrls + 传给组件 |
| `frontend/src/pages/article/components/ImageGenerationAnimation.vue` | 新增 imageUrls prop + 图片渲染 + CSS blur 过渡 |