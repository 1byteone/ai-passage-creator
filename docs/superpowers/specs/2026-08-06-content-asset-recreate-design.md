# 内容资产库 + 历史文章一键再创作 — 需求设计文档

日期: 2026-08-06
状态: 已对齐（按推荐方案）

## Context

基于竞品调研（壹伴助手/新媒体管家/Notion AI/Monica 等），项目已完成创作全流程（选题→标题→大纲→正文→配图→卡片→审批→发布），但存在两大竞品差距：
1. **内容资产库** — 竞品有统一资产管理视图，项目列表缺卡片/配图缩略图预览
2. **历史文章再创作** — 竞品支持旧内容一键裂变/改写，项目缺"基于旧文章生成新文章"

项目现状基础良好：文章表字段完整（topic/style/methodology/characterStyle/images/coverImage），创作页已支持 `route.query.topic` 预填，后端 `ArticleCreateRequest` 已有 `methodology` 字段（前端类型缺）。

## 方案

### 1. 内容资产库增强（列表缩略图列）

**ArticleListPage.vue** 表格新增"封面"列：
- 有 `coverImage` 的文章显示缩略图（80×60 圆角）
- 无封面显示灰色占位（PictureOutlined 图标）
- 移动端卡片在标题上方显示缩略图

### 2. 历史文章一键再创作（列表新增"再创作"按钮）

**ArticleActions** 新增"再创作"按钮（COMPLETED 状态可用），点击后：
1. 读取旧文章字段：`topic`、`style`、`methodology`、`characterStyle`、`enabledImageMethods`
2. 跳转创作页 `/create?topic=xxx&style=xxx&methodology=xxx&characterStyle=xxx&imageMethods=xxx`
3. 创作页 `onMounted` 读取这些 query 参数，预填表单（选题/风格/方法论文档/插画子风格/配图方式）
4. 用户确认后点"开始创作"，正常走创作流程（复用 RAG 历史上下文，生成新变体）

### 3. 前端类型补齐 + 创作页 query 预填扩展 + 方法论选择器

**typings.d.ts** `ArticleCreateRequest` 补 `methodology?: string` 字段（后端已有）。

**ArticleCreatePage.vue**：
1. 新增 `selectedMethodology` ref（默认 `'default'`）
2. 新增方法论文档选择器 UI（下拉，选项来自现有 Methodology 体系：default/douyin/xiaohongshu/wechat）
3. `onMounted` 从 `route.query` 读取并预填：
```typescript
// 再创作预填：读取路由参数
if (route.query.topic) topic.value = route.query.topic as string
if (route.query.style) selectedStyle.value = route.query.style as string
if (route.query.methodology) selectedMethodology.value = route.query.methodology as string
if (route.query.characterStyle) selectedCharacterStyle.value = route.query.characterStyle as string
if (route.query.imageMethods) selectedImageMethods.value = (route.query.imageMethods as string).split(',')
```
4. `createArticle` 调用补 `methodology: selectedMethodology.value`

> 说明：后端 `ArticleCreateRequest.methodology` 已存在（回退 default），前端此前未透传。再创作时需要透传方法论，故在创作页补充选择器（默认选旧文章的 methodology，用户可改）。

## 数据流

```
ArticleListPage 点"再创作"
    → 读 record 字段 → 构造 query
    → router.push(`/create?topic=..&style=..&methodology=..`)
    → ArticleCreatePage onMounted 预填表单
    → 用户确认 → POST /article/create（含 methodology）
    → 正常创作流程 → 新 taskId
```

## 文件清单

| 文件 | 操作 |
|------|------|
| `frontend/src/pages/article/ArticleListPage.vue` | 新增封面缩略图列 + ArticleActions 加"再创作"按钮 |
| `frontend/src/pages/article/ArticleCreatePage.vue` | onMounted 扩展 query 预填 + 新增方法论选择器 + createArticle 透传 methodology |
| `frontend/src/api/typings.d.ts` | ArticleCreateRequest 补 `methodology?: string` |

## 不做的事

- 不新增后端接口（复用 `/article/create`，methodology 后端已支持）
- 不做"直接重新生成"（保留用户确认，预填可修改）
- 不做网格卡片视图（先做缩略图列，成本低见效快）
- 不做数据回流（P1，后续）

## 验证

```bash
cd frontend && npm run type-check && npm run build
```
- 文章列表显示封面缩略图
- COMPLETED 文章点"再创作"→ 创作页预填选题/风格/方法论文档
- 用户修改后点创作 → 正常生成新文章
