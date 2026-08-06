# 左侧栏配图预览 — 实时渲染卡片 + 动态模糊效果

日期: 2026-08-06
状态: 已批准

## Context

创作页左侧栏（320px）目前只有创作流程时间线，配图渲染卡片在中间主内容区。用户希望**在左侧栏创作流程下方**，展示每张配图的实时渲染卡片，每张卡片带标题说明，并实现"官方风格"的动态模糊渲染效果（blur 30px → 0px 过渡）。

后端 `IMAGE_COMPLETE` 事件已推送完整数据（`url` + `keywords` + `sectionTitle` + `description`），但上一轮前端只收集了 `url`，未展示标题说明。

## 方案

### 1. 左侧栏新增配图预览区域

在 `sidebar-left` 的 `flow-timeline` 后新增 `image-preview-section`，包含：
- 标题："配图预览" + 已完成数量（如 "3/5"）
- 每张配图卡片（16:9 比例）
- 跟随创作流程滚动（自然放在左侧栏底部）

### 2. 配图卡片（三态设计）

| 状态 | 图片区域 | 底部说明 |
|------|---------|---------|
| **已完成** | 清晰图片 + 绿色边框 | 标题 + 封面图/第N张 + 绿色 ✓ |
| **渲染中** | CSS blur(30px) 模糊底图 + 蓝色进度条 | 标题 + 标签 + 百分比 |
| **待生成** | 灰色虚线占位 + 等待图标 | "待生成配图" 灰色文字 |

### 3. 动态模糊渲染

图片加载完成后执行 `filter: blur(30px) → 2s ease-out → blur(0px)`，纯 CSS 实现。

### 4. 数据流

`SSEMessage` 补 `sectionTitle`/`keywords` 字段 → 创作页 `IMAGE_COMPLETE` 分支收集 `{url, sectionTitle, keywords}` 到 `imageItems[]` → 传给左侧栏组件。

## 文件清单

| 文件 | 操作 |
|------|------|
| `frontend/src/utils/sse.ts` | SSEMessage 补 `sectionTitle`/`keywords` 字段 |
| `frontend/src/pages/article/ArticleCreatePage.vue` | 新增 `imageItems` ref + 收集 IMAGE_COMPLETE 完整数据 + 传给左侧栏 |
| `frontend/src/pages/article/ArticleCreatePage.vue` 左侧栏 | flow-timeline 后新增配图预览区域 |
| `frontend/src/pages/article/components/ImageGenerationAnimation.vue` | 不改（保留在主内容区的动画，左侧栏新增独立组件） |
| 或新增 `frontend/src/pages/article/components/ImagePreviewSidebar.vue` | 新左侧栏配图组件 |

## 不做的事

- 不改后端（`IMAGE_COMPLETE` 已推送完整数据）
- 不删 `ImageGenerationAnimation`（主内容区保留进度动画，左侧栏是配图预览）
- 不引入新依赖（纯 CSS 模糊）