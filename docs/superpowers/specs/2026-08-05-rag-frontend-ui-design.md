# 前端 RAG 相关文章 UI — 设计文档

> 日期：2026-08-05
> 状态：已通过 3 轮审计，设计定稿
> 类型：纯前端需求，后端零改动

## 1. 需求背景

后端 RAG 向量检索已全栈就绪（`POST /api/rag/search` → `RagHit[]`，用户隔离/type 白名单已完成），
但前端无任何页面调用。CLAUDE.md 路由表已声明详情页「相关文章」与创作页「历史参考」两个区块，均未实现。

本次交付这两个纯前端 UI，复用同一套组件 + 检索逻辑。

## 2. 需求确认（与用户逐项确认）

| 项 | 决定 |
|----|------|
| 创作页「历史参考」触发 | INPUT 阶段，选题输入实时显示 |
| 详情页「相关文章」触发 | 页面加载自动显示 |
| 历史参考点击行为 | **套用选题**：文章标题填入选题框，可编辑后走正常流程 |
| 相关文章展示形态 | 横向卡片列表，点击跳转对应详情 |
| 组件方案 | 方案 1：共享组件 `RagHitsPanel` + 检索 composable `useRagSearch` |

## 3. 架构与文件

```
frontend/src/
├── composables/useRagSearch.ts      # 新增 — 检索逻辑（防抖+竞态+去重+清理）
├── components/RagHitsPanel.vue      # 新增 — 展示层（加载/双空态/卡片列表）
├── api/ragController.ts             # 已存在，不动
├── pages/article/ArticleCreatePage.vue   # 改 — INPUT 阶段右侧栏挂历史参考
└── pages/article/ArticleDetailPage.vue   # 改 — 底部挂相关文章
```

两处都消费 `RagHit[]`，形态相近 → 共享组件避免 Copy-Paste Sprawl（反模式 #2）。

## 4. 检索逻辑 `useRagSearch.ts`

```ts
const { hits, loading, empty, search, clear } = useRagSearch({ debounceMs: 500 })

search(query, { type: 'article', topK: 5 })
```

### 4.1 行为规范

| 行为 | 实现 |
|------|------|
| **防抖 500ms** | 输入停止后才检索，避免每键一请求 |
| **竞态保护** | 内部 `fetchSeq` 递增，非最新响应丢弃（暗坑 #9） |
| **空 query 短路** | `query.trim()` 为空 → `clear()`，不发请求 |
| **错误静默** | 检索失败仅记录日志，不打扰用户（对齐 RAG 后端哲学） |
| **按 refId 去重** | 同一文章多 chunk 命中 → 保留 score 最高那条，按 score 降序（Bug 1.1） |
| **type 过滤** | 统一过滤 `type !== 'article'`，创作页语义（Bug 1.3） |
| **排除自身** | 详情页场景过滤 `refId && refId !== taskId`，通过参数传入（Bug 1.3） |

### 4.2 生命周期管理（Bug 2.2）

composable 内部用 `getCurrentInstance()` + `onBeforeUnmount` **自动注册清理**：

- 内部 `disposed` 标志：disposed 后不再启动新请求；响应回写前检查
- 防抖 timer 句柄：`onBeforeUnmount` 中 `clearTimeout` 并置 null（暗坑 #5）

调用方零额外代码，规则内聚在 composable。遵守暗坑 #3「await 后检查 unmounted」。

### 4.3 防抖期间不闪 loading（Bug 3.2）

防抖计时期间**保留上一次结果**（不置 loading），真正发出请求时才 `loading=true`。

### 4.4 score 语义（Bug 1.2）

score 在不同 VectorStore 底层可能为相似度或距离，不保证 0-1 范围。**仅用于排序**，
不显示精确百分比。

## 5. 展示组件 `RagHitsPanel.vue`

### 5.1 Props / Emits

```ts
props: {
  hits: API.RagHit[]
  loading?: boolean
  noInput?: boolean           // 创作页 topic 为空时置 true，显示「未输入」提示（Bug 3.3）
  emptyText?: string
}
emits: ['select']   // select(hit)
```

### 5.2 渲染分支

| 状态 | 显示 |
|------|------|
| `loading` | skeleton |
| `noInput` | 提示「输入选题后，将展示相关历史文章」（创作页 topic 为空） |
| 非以上且 `hits.length === 0` | `emptyText`（如「暂无相关历史，换个选题试试」） |
| 有 hits | 横向卡片列表：`title` + 相对标签，点击 emit `select` |

> 空态（`hits.length === 0`）由面板自身根据 hits 计算，父层只需传 `noInput` 区分「未输入」。

### 5.3 安全（Bug 3.1）

**全部文本插值 `{{ }}`，禁止 `v-html`**。`RagHit.content` 来自向量库回读的不可信正文分块，
文本插值天然被 Vue 转义，从根上消除 XSS 面。若将来渲染富文本，走 `@/utils/markdown`。

### 5.4 可访问性（Bug 3.4）

卡片用 `<button type="button">` 或 `<a>`，天然可聚焦、可键盘触发。列表容器加 `aria-label`。

## 6. 两处接入

### 6.1 创作页（历史参考）

- 挂 `currentPhase === 'INPUT'` 时右侧 `sidebar-right`，「热门选题」下方
- `watch(topic)` → 空则 `clear()`（且面板切 no-input 态）；非空则 `search(topic, { type: 'article', topK: 5 })`
- watch 回调加 `if (currentPhase.value !== 'INPUT') return` 守卫（Bug 2.3）
- 点击卡片：`topic.value = hit.title` + `message.info('已套用历史标题，可编辑后开始创作')`

### 6.2 详情页（相关文章）

- 挂 `ArticleReadingView` 之后、执行信息面板之前，标题「相关文章」
- **在 `loadArticle` 成功、`article.value` 赋值之后调用 `loadRelated()`**（Bug 2.1），
  天然覆盖「重新加载」重试路径
- query：`mainTitle + ' ' + subTitle`，`topK: 5`，排除自身（参数传入 taskId）
- 点击卡片：`router.push('/article/' + encodeURIComponent(hit.refId))`

## 7. 测试

### 7.1 单元测试 `frontend/tests/rag-search.test.ts`（`node --test`，mock `searchRag`）

| 用例 | 验证 |
|------|------|
| 防抖合并 | 快速连续调用只发最后一次请求 |
| 竞态丢弃 | 旧响应晚到被丢弃 |
| 空 query 短路 | 空输入不发请求且清空 hits |
| refId 去重 | 同文章多 chunk 保留最高分 |
| 错误静默 | 检索失败不抛错、hits 保持上一次 |
| 生命周期清理 | disposed 后响应不写入 |

### 7.2 E2E `frontend/tests/ui/rag-hits.spec.ts`（Playwright，route mock `/rag/search`）

| 场景 | 断言 |
|------|------|
| 创作页输入选题 → 参考面板出现 | 面板展示 mock 标题 |
| 点击参考卡片 → 选题框填入 | topic 值等于点击项 title |
| 详情页加载 → 相关文章出现 | 面板展示、排除自身 |
| 点击相关文章 → 跳转详情 | URL 变为目标 refId |

## 8. 验证 & 提交

按 CLAUDE.md Post-implement 流程：

```bash
cd frontend && npm run type-check    # 零容忍 TS 错误
cd frontend && npm run build         # vite build 才能发现纯 CSS // 注释等编译错误
cd frontend && npm run test          # test:skill（新单测）+ test:ui（新 E2E）
```

通过后 Conventional Commits（`feat(frontend): ...`）+ Co-Authored-By: Claude，推送双远程。

## 9. 暗坑遵守清单

| 暗坑 | 本设计对应 |
|------|-----------|
| #1 XSS | RagHitsPanel 纯文本插值，禁 v-html |
| #2 SSE 泄漏 | 本需求无 SSE，不涉及 |
| #3 await 后检查 | useRagSearch 响应回写前查 disposed |
| #4 SSE 重连 | 不涉及 |
| #5 timer 清理 | onBeforeUnmount clearTimeout |
| #9 分页竞态 | fetchSeq 竞态保护 |
| #13 双重提交 | 卡片点击无异步，不涉及 |

## 10. 非目标（本期不做）

- 后端排除自身 / 服务端去重（留给后端优化，本期前端兜底）
- score 百分比显示（语义不保证，仅排序）
- 历史参考套用大纲（用户确认本期仅套用选题）
- skill 类型内容在创作页展示
