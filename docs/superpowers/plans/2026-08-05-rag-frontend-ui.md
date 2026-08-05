# 前端 RAG 相关文章 UI 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付两个前端 RAG UI —— 创作页「历史参考」面板（选题输入时实时检索、点击套用选题）+ 详情页「相关文章」区块（加载自动检索、点击跳转）。

**Architecture:** 共享 `useRagSearch` composable（防抖 + fetchSeq 竞态 + refId 去重 + onBeforeUnmount 自动清理）与 `RagHitsPanel` 展示组件（纯文本插值、双空态、`<button>` 卡片）。两个接入页面零重复逻辑。composable 的生命周期钩子与 `searchRag` 均为**可注入依赖**，使 node:test 单测可跑。

**Tech Stack:** Vue 3.5 `<script setup lang="ts">`、TypeScript strict、Ant Design Vue 4、node:test 单测 + Playwright E2E。

## Global Constraints

- TypeScript strict，**零容忍 `vue-tsc` 错误**，禁止 `any`
- **禁止 `v-html`**：RagHit.content 来自向量库回读的不可信正文，一律文本插值
- 变量名 ≤3 个单词（anti-ai-flavor）
- 用户可见文案用中文、简洁直接
- 组件：`<script setup lang="ts">`，无 `export default {}`
- `frontend/src/composables/` 目录尚不存在，Task 1 随文件创建
- 后端零改动：`/api/rag/search` 已就绪，前端 `searchRag` 已封装，两者均不修改
- composable 内**必须用相对路径** `../api/ragController` 导入 `searchRag`（`@/` 别名在 Node `--experimental-strip-types` 下不解析，而 composable 需被 node:test 直接 import）
- 提交用 Conventional Commits + `Co-Authored-By: Claude <noreply@anthropic.com>`
- 前次遗留的 `ArticleAgentOrchestrator.java` 改动**不提交**（不属本计划范围）

---

### Task 1: `useRagSearch` composable（检索核心）+ 单测脚本接入

**Files:**
- Create: `frontend/src/composables/useRagSearch.ts`
- Create: `frontend/tests/rag-search.test.ts`
- Modify: `frontend/package.json`（`test:skill` 脚本加入新测试）

**Interfaces:**
- Consumes: `searchRag` from `../api/ragController`，类型 `API.BaseResponseListRagHit` / `API.RagHit`
- Produces:
  ```ts
  useRagSearch(options?: {
    debounceMs?: number
    searchFn?: typeof searchRag                  // 测试注入 mock，默认真实 searchRag
    lifecycle?: { onBeforeUnmount: (cb: () => void) => void }   // 测试注入空钩子
  }): {
    hits: Ref<API.RagHit[]>       // 已按 refId 去重、降序
    loading: Ref<boolean>
    empty: Ref<boolean>           // computed: hits 为空且非 loading
    search: (query: string, opts?: { type?: string; topK?: number; excludeRefId?: string }) => void
    clear: () => void
  }
  ```

- [ ] **Step 1: 写失败测试** `frontend/tests/rag-search.test.ts`

  完整测试文件（7 用例）：

  ```ts
  import assert from 'node:assert/strict'
  import test from 'node:test'
  import { useRagSearch } from '../src/composables/useRagSearch.ts'

  const noLifecycle = { onBeforeUnmount: (_cb: () => void) => {} }
  const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms))
  const hit = (refId: string, title: string, score: number, type = 'article'): API.RagHit =>
    ({ refId, title, content: `${title} 的摘要`, score, type })

  test('debounce: rapid calls fire only the last query', async () => {
    const calls: string[] = []
    const searchFn = async (params: { query: string }) => {
      calls.push(params.query)
      return { data: { code: 0, data: [hit('a', 'A', 0.9)] } } as API.BaseResponseListRagHit
    }
    const { search } = useRagSearch({ debounceMs: 30, searchFn, lifecycle: noLifecycle })
    search('第一')
    await sleep(10)
    search('第二')
    await sleep(10)
    search('第三')
    await sleep(60)
    assert.deepEqual(calls, ['第三'])
  })

  test('race: stale response is discarded', async () => {
    let resolve1!: (v: API.BaseResponseListRagHit) => void
    const p1 = new Promise<API.BaseResponseListRagHit>((r) => (resolve1 = r))
    const p2 = Promise.resolve({ data: { code: 0, data: [hit('new', '新结果', 0.9)] } } as API.BaseResponseListRagHit)
    let call = 0
    const searchFn = () => (call++ === 0 ? p1 : p2)
    const { hits, search } = useRagSearch({ debounceMs: 10, searchFn, lifecycle: noLifecycle })
    search('q1')
    await sleep(20)
    search('q2')
    await sleep(20)
    resolve1({ data: { code: 0, data: [hit('old', '旧结果', 0.9)] } } as API.BaseResponseListRagHit)
    await sleep(10)
    assert.equal(hits.value.length, 1)
    assert.equal(hits.value[0].refId, 'new')
  })

  test('empty query short-circuits without request', async () => {
    let calls = 0
    const searchFn = async () => {
      calls++
      return { data: { code: 0, data: [] } } as API.BaseResponseListRagHit
    }
    const { hits, search } = useRagSearch({ debounceMs: 10, searchFn, lifecycle: noLifecycle })
    search('')
    search('   ')
    await sleep(30)
    assert.equal(calls, 0)
    assert.deepEqual(hits.value, [])
  })

  test('dedupe: keeps highest score per refId', async () => {
    const searchFn = async () =>
      ({ data: { code: 0, data: [hit('a', 'A', 0.5), hit('a', 'A', 0.9), hit('b', 'B', 0.7)] } }) as API.BaseResponseListRagHit
    const { hits, search } = useRagSearch({ debounceMs: 10, searchFn, lifecycle: noLifecycle })
    search('q')
    await sleep(30)
    assert.equal(hits.value.length, 2)
    assert.equal(hits.value[0].refId, 'a')
    assert.equal(hits.value[0].score, 0.9)
  })

  test('filters non-article and excluded refId', async () => {
    const searchFn = async () =>
      ({ data: { code: 0, data: [hit('self', '自己', 0.9), hit('skill-1', '技能', 0.8, 'skill'), hit('good', '好文', 0.7)] } }) as API.BaseResponseListRagHit
    const { hits, search } = useRagSearch({ debounceMs: 10, searchFn, lifecycle: noLifecycle })
    search('q', { type: 'article', topK: 5, excludeRefId: 'self' })
    await sleep(30)
    assert.equal(hits.value.length, 1)
    assert.equal(hits.value[0].refId, 'good')
  })

  test('errors are silent and keep previous hits', async () => {
    let shouldFail = false
    const searchFn = async () => {
      if (shouldFail) throw new Error('网络错误')
      return { data: { code: 0, data: [hit('a', 'A', 0.9)] } } as API.BaseResponseListRagHit
    }
    const { hits, search } = useRagSearch({ debounceMs: 10, searchFn, lifecycle: noLifecycle })
    search('ok')
    await sleep(30)
    assert.equal(hits.value.length, 1)
    shouldFail = true
    search('fail')
    await sleep(30)
    assert.equal(hits.value.length, 1)
    assert.equal(hits.value[0].refId, 'a')
  })

  test('disposed: no writes after unmount', async () => {
    let resolve1!: (v: API.BaseResponseListRagHit) => void
    const pending = new Promise<API.BaseResponseListRagHit>((r) => (resolve1 = r))
    const searchFn = () => pending
    let unmountCb: (() => void) | null = null
    const { hits, search } = useRagSearch({
      debounceMs: 10,
      searchFn,
      lifecycle: { onBeforeUnmount: (cb) => (unmountCb = cb) },
    })
    search('q')
    await sleep(20)
    unmountCb!()
    resolve1({ data: { code: 0, data: [hit('a', 'A', 0.9)] } } as API.BaseResponseListRagHit)
    await sleep(10)
    assert.equal(hits.value.length, 0)
  })
  ```

  > `API.RagHit` 类型存在于 `frontend/src/api/typings.d.ts`，node:test 环境下是全局声明（`typings.d.ts` 在 tsconfig include 内），运行期被 strip-types 移除，不报错。

- [ ] **Step 2: 运行测试验证失败**

  Run: `cd frontend && node --experimental-strip-types --test tests/rag-search.test.ts`
  Expected: FAIL —— `ERR_MODULE_NOT_FOUND`（`../src/composables/useRagSearch.ts` 不存在）

- [ ] **Step 3: 创建 composable** `frontend/src/composables/useRagSearch.ts`

  ```ts
  import { computed, onBeforeUnmount, ref, type Ref } from 'vue'
  // 相对路径导入：Node `--experimental-strip-types` 单测不解析 `@/` 别名，
  // 而本 composable 需被 node:test 直接 import；Vite 构建同样支持相对路径。
  import { searchRag as defaultSearchRag } from '../api/ragController'

  interface UseRagSearchOptions {
    debounceMs?: number
    searchFn?: typeof defaultSearchRag
    lifecycle?: { onBeforeUnmount: (cb: () => void) => void }
  }

  interface SearchOptions {
    type?: string
    topK?: number
    excludeRefId?: string
  }

  const dedupe = (hits: API.RagHit[]): API.RagHit[] => {
    const best = new Map<string, API.RagHit>()
    for (const hit of hits) {
      if (!hit.refId) continue
      const prev = best.get(hit.refId)
      if (!prev || (hit.score ?? 0) > (prev.score ?? 0)) best.set(hit.refId, hit)
    }
    return [...best.values()].sort((a, b) => (b.score ?? 0) - (a.score ?? 0))
  }

  export function useRagSearch(options: UseRagSearchOptions = {}) {
    const debounceMs = options.debounceMs ?? 500
    const searchFn = options.searchFn ?? defaultSearchRag
    const unmountCb = options.lifecycle?.onBeforeUnmount ?? onBeforeUnmount

    const hits = ref<API.RagHit[]>([])
    const loading = ref(false)
    const empty = computed(() => hits.value.length === 0 && !loading.value)

    let timer: ReturnType<typeof setTimeout> | null = null
    let fetchSeq = 0
    let disposed = false

    const doSearch = async (query: string, opts: SearchOptions) => {
      const seq = ++fetchSeq
      loading.value = true
      try {
        const res = await searchFn({ query, type: opts.type, topK: opts.topK })
        if (disposed || seq !== fetchSeq) return
        let list = res.data?.data ?? []
        list = list.filter((h) => h.type === 'article')
        if (opts.excludeRefId) list = list.filter((h) => h.refId !== opts.excludeRefId)
        hits.value = dedupe(list)
      } catch (e) {
        console.error('RAG 检索失败（静默）:', e)
        if (disposed || seq !== fetchSeq) return
      } finally {
        if (!disposed && seq === fetchSeq) loading.value = false
      }
    }

    const search = (query: string, opts: SearchOptions = {}) => {
      if (disposed) return
      if (!query.trim()) {
        clear()
        return
      }
      if (timer !== null) clearTimeout(timer)
      timer = setTimeout(() => {
        timer = null
        void doSearch(query.trim(), opts)
      }, debounceMs)
    }

    const clear = () => {
      fetchSeq++
      if (timer !== null) {
        clearTimeout(timer)
        timer = null
      }
      hits.value = []
      loading.value = false
    }

    unmountCb(() => {
      disposed = true
      if (timer !== null) {
        clearTimeout(timer)
        timer = null
      }
    })

    return { hits, loading, empty, search, clear }
  }
  ```

- [ ] **Step 4: 运行测试验证通过**

  Run: `cd frontend && node --experimental-strip-types --test tests/rag-search.test.ts`
  Expected: `pass 7`

- [ ] **Step 5: `test:skill` 脚本接入新测试**

  Modify `frontend/package.json`：

  ```json
  "test:skill": "node --experimental-strip-types --test tests/skill-state.test.ts tests/rag-search.test.ts"
  ```

  验证：`cd frontend && npm run test:skill` → `pass 7`（新增）且 skill-state 原有用例仍绿。

- [ ] **Step 6: 提交**

  ```bash
  git add frontend/src/composables/useRagSearch.ts frontend/tests/rag-search.test.ts frontend/package.json
  git commit -m "feat(rag): useRagSearch composable — 防抖/竞态/去重/生命周期清理 + 单测"
  ```

---

### Task 2: `RagHitsPanel` 展示组件

**Files:**
- Create: `frontend/src/components/RagHitsPanel.vue`

**Interfaces:**
- Consumes: `API.RagHit` 类型
- Produces:
  ```ts
  props: { hits: API.RagHit[]; loading?: boolean; noInput?: boolean; emptyText?: string }
  emits: { (e: 'select', hit: API.RagHit): void }
  ```

- [ ] **Step 1: 创建组件** `frontend/src/components/RagHitsPanel.vue`

  ```vue
  <template>
    <div class="rag-hits-panel" aria-label="相关文章">
      <a-skeleton v-if="loading" active :paragraph="{ rows: 2 }" />
      <div v-else-if="noInput" class="rag-hits-hint">
        <BulbOutlined />
        <span>输入选题后，将展示相关历史文章</span>
      </div>
      <a-empty v-else-if="hits.length === 0" :description="emptyText" class="rag-hits-empty" />
      <ul v-else class="rag-hits-list">
        <li v-for="hit in hits" :key="hit.refId">
          <button type="button" class="rag-hit-card" @click="emit('select', hit)">
            <strong class="rag-hit-title">{{ hit.title || '未命名文章' }}</strong>
            <span class="rag-hit-tag">相关</span>
          </button>
        </li>
      </ul>
    </div>
  </template>

  <script setup lang="ts">
  import { BulbOutlined } from '@ant-design/icons-vue'

  const props = withDefaults(
    defineProps<{
      hits: API.RagHit[]
      loading?: boolean
      noInput?: boolean
      emptyText?: string
    }>(),
    { loading: false, noInput: false, emptyText: '暂无相关历史，换个选题试试' },
  )

  const emit = defineEmits<{ (e: 'select', hit: API.RagHit): void }>()
  </script>

  <style scoped>
  .rag-hits-panel { min-height: 40px; }
  .rag-hits-hint {
    display: flex; align-items: center; gap: 8px;
    padding: 12px; border-radius: var(--radius-md);
    background: var(--color-background-secondary);
    color: var(--color-text-muted); font-size: 12px; line-height: 1.5;
  }
  .rag-hits-empty { margin: 0; }
  .rag-hits-list { margin: 0; padding: 0; list-style: none; display: grid; gap: 8px; }
  .rag-hit-card {
    display: flex; align-items: center; justify-content: space-between; gap: 10px;
    width: 100%; padding: 10px 12px; border: 1px solid var(--color-border);
    border-radius: var(--radius-md); background: var(--color-background-secondary);
    text-align: left; cursor: pointer; transition: all var(--transition-fast);
  }
  .rag-hit-card:hover { border-color: var(--color-primary); background: rgba(34, 197, 94, 0.05); }
  .rag-hit-title {
    font-size: 13px; color: var(--color-text); line-height: 1.5;
    overflow: hidden; text-overflow: ellipsis;
    display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
  }
  .rag-hit-tag {
    flex: none; padding: 1px 8px; border-radius: var(--radius-full);
    background: rgba(34, 197, 94, 0.1); color: var(--color-primary-dark); font-size: 11px;
  }
  </style>
  ```

  > 无 `v-html`，全部文本插值（安全红线）；卡片用 `<button>` 天然可键盘操作。

- [ ] **Step 2: 验证类型检查**

  Run: `cd frontend && npx vue-tsc --build`
  Expected: PASS（`API.RagHit` 已存在于 `api/typings.d.ts`）

- [ ] **Step 3: 提交**

  ```bash
  git add frontend/src/components/RagHitsPanel.vue
  git commit -m "feat(rag): RagHitsPanel 展示组件 — 加载/双空态/卡片列表"
  ```

---

### Task 3: 创作页接入「历史参考」

**Files:**
- Modify: `frontend/src/pages/article/ArticleCreatePage.vue`

**Interfaces:**
- Consumes: `useRagSearch`（Task 1）、`RagHitsPanel`（Task 2）
- Produces: 无新接口

- [ ] **Step 1: 引入 composable 与组件**

  `ArticleCreatePage.vue` script 内新增 import（第 630 行 `import { ref, onBeforeUnmount, onMounted, nextTick, computed } from 'vue'` 中**补上 `watch`**）：

  ```ts
  import { ref, watch, onBeforeUnmount, onMounted, nextTick, computed } from 'vue'
  ```

  并新增：

  ```ts
  import RagHitsPanel from '@/components/RagHitsPanel.vue'
  import { useRagSearch } from '@/composables/useRagSearch'
  ```

  在 `const loginUserStore = useLoginUserStore()` 之后：

  ```ts
  const { hits: ragHits, loading: ragLoading, search: ragSearch, clear: ragClear } = useRagSearch({ debounceMs: 500 })
  ```

- [ ] **Step 2: 监听 topic（带 INPUT 阶段守卫）**

  在 `onMounted` 定义之前新增：

  ```ts
  // 历史参考：选题输入防抖检索，仅在 INPUT 阶段触发
  watch(topic, (val) => {
    if (currentPhase.value !== 'INPUT') return
    if (!val.trim()) {
      ragClear()
      return
    }
    ragSearch(val, { type: 'article', topK: 5 })
  })
  ```

- [ ] **Step 3: 挂载面板到右侧栏**

  在「热门选题」`panel-section` 结束 `</div>` 之后、「创作技巧」`panel-section` 之前插入：

  ```vue
  <!-- 历史参考 -->
  <div v-if="currentPhase === 'INPUT'" class="panel-section">
    <h4 class="panel-title">
      <HistoryOutlined />
      历史参考
    </h4>
    <RagHitsPanel
      :hits="ragHits"
      :loading="ragLoading"
      :no-input="!topic.trim()"
      empty-text="暂无相关历史，换个选题试试"
      @select="handleSelectReference"
    />
  </div>
  ```

- [ ] **Step 4: 点击套用选题 + 图标 + reset 清理**

  图标 import 列表补 `HistoryOutlined`（在 `FileTextOutlined,` 之后）：

  ```ts
      FileTextOutlined,
      HistoryOutlined,
  ```

  新增 handler（`resetCreate` 之前）：

  ```ts
  // 历史参考：套用选题（填入选题框，可编辑后走正常流程）
  const handleSelectReference = (hit: API.RagHit) => {
    if (hit.title) {
      topic.value = hit.title
      message.info('已套用历史标题，可编辑后开始创作')
    }
  }
  ```

  `resetCreate()` 函数体**开头**新增：

  ```ts
  ragClear()
  ```

  > 说明：`resetCreate` 先置 `currentPhase='INPUT'` 再清 `topic=''`，watch 守卫会放行并自动 `ragClear`；此处显式 `ragClear()` 是防御性（若未来调整赋值顺序，清理仍确定执行）。

- [ ] **Step 5: 验证类型检查 + 构建**

  Run: `cd frontend && npx vue-tsc --build`
  Run: `cd frontend && npm run build`
  Expected: 两者均 PASS

- [ ] **Step 6: 提交**

  ```bash
  git add frontend/src/pages/article/ArticleCreatePage.vue
  git commit -m "feat(rag): 创作页历史参考面板 — 选题输入实时检索 + 点击套用选题"
  ```

---

### Task 4: 详情页接入「相关文章」

**Files:**
- Modify: `frontend/src/pages/article/ArticleDetailPage.vue`

**Interfaces:**
- Consumes: `useRagSearch`（Task 1）、`RagHitsPanel`（Task 2）
- Produces: 无新接口

> 生命周期清理说明：`useRagSearch` 内部已用 vue 的 `onBeforeUnmount` 自动注册清理（disposed + timer），**详情页无需**手动调用 `relatedClear` 或新增 `onBeforeUnmount`。

- [ ] **Step 1: 引入 composable 与组件**

  `ArticleDetailPage.vue` script 新增 import：

  ```ts
  import RagHitsPanel from '@/components/RagHitsPanel.vue'
  import { useRagSearch } from '@/composables/useRagSearch'
  ```

  在 `const taskId = computed(...)` 之后：

  ```ts
  // 相关文章：文章加载成功后用标题语义检索（详情页只发一次，无需防抖）
  const { hits: relatedHits, loading: relatedLoading, search: relatedSearch } = useRagSearch({ debounceMs: 0 })
  ```

- [ ] **Step 2: 加载成功后触发检索**

  `loadArticle` 的 `try` 块内，把 `if (article.value) void loadExecutionLogs()` 改为：

  ```ts
  if (article.value) {
    void loadExecutionLogs()
    loadRelated()
  }
  ```

  新增 `loadRelated`（`loadExecutionLogs` 之后）：

  ```ts
  const loadRelated = () => {
    const a = article.value
    if (!a) return
    const query = [a.mainTitle, a.subTitle].filter(Boolean).join(' ')
    if (!query.trim()) return
    relatedSearch(query, { type: 'article', topK: 5, excludeRefId: taskId.value })
  }
  ```

- [ ] **Step 3: 挂载面板到底部**

  在 `<ArticleReadingView :article="article" title-id="article-detail-title" />` 之后、`<section class="execution-panel"` 之前插入：

  ```vue
  <!-- 相关文章 -->
  <section v-if="hasContent" class="related-panel" aria-labelledby="related-title">
    <h2 id="related-title" class="related-title">
      <LinkOutlined />
      相关文章
    </h2>
    <RagHitsPanel
      :hits="relatedHits"
      :loading="relatedLoading"
      empty-text="暂无相关文章"
      @select="handleOpenRelated"
    />
  </section>
  ```

- [ ] **Step 4: 点击跳转 + 图标注册**

  图标 import 列表补 `LinkOutlined`（在 `ShareAltOutlined,` 之后）：

  ```ts
      ShareAltOutlined,
      LinkOutlined,
  ```

  新增 handler（`goBack` 附近）：

  ```ts
  const handleOpenRelated = (hit: API.RagHit) => {
    if (hit.refId) router.push(`/article/${encodeURIComponent(hit.refId)}`)
  }
  ```

- [ ] **Step 5: 样式**

  在 `<style scoped>` 中 `@media (max-width: 700px)` 块之前追加：

  ```css
  /* ── 相关文章 ── */
  .related-panel {
    width: min(760px, 100%);
    margin: 24px auto 0;
    padding-top: 20px;
    border-top: 1px solid var(--border-default);
  }

  .related-title {
    display: flex;
    align-items: center;
    gap: 8px;
    margin: 0 0 14px;
    font-size: 16px;
    font-weight: 650;
    color: var(--text-strong);
  }
  ```

  > 本项目 style 块为纯 CSS（无 `lang="scss"`），**禁止 `//` 注释**，一律 `/* */`（暗坑 #14）。

- [ ] **Step 6: 验证类型检查 + 构建**

  Run: `cd frontend && npx vue-tsc --build`
  Run: `cd frontend && npm run build`
  Expected: 两者均 PASS

- [ ] **Step 7: 提交**

  ```bash
  git add frontend/src/pages/article/ArticleDetailPage.vue
  git commit -m "feat(rag): 详情页相关文章区块 — 加载自动检索 + 点击跳转"
  ```

---

### Task 5: E2E 测试

**Files:**
- Create: `frontend/tests/ui/rag-hits.spec.ts`

**Interfaces:**
- Consumes: 创作页/详情页的 RAG 面板（Task 3/4）
- Produces: 无

- [ ] **Step 1: 写 E2E 测试** `frontend/tests/ui/rag-hits.spec.ts`

  ```ts
  import { expect, test, type Page } from '@playwright/test'

  /** Mock 登录态 */
  async function mockAuth(page: Page) {
    await page.route('**/api/user/get/login', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: { id: 1, userName: '测试用户', userRole: 'admin', quota: 5 },
        }),
      }),
    )
  }

  /** Mock RAG 检索：返回自身 + 2 篇相关文章（验证详情页排除自身） */
  async function mockRagSearch(page: Page, selfRefId: string) {
    await page.route('**/api/rag/search', (route) => {
      const body: API.RagHit[] = [
        { refId: selfRefId, title: '这是自身文章', content: '自身摘要', score: 0.95, type: 'article' },
        { refId: 'ref-1', title: 'AI 重塑职场', content: '历史文章摘要', score: 0.9, type: 'article' },
        { refId: 'ref-2', title: '远程办公指南', content: '另一篇摘要', score: 0.7, type: 'article' },
      ]
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 0, data: body }),
      })
    })
  }

  const collectRuntimeProblems = (page: Page) => {
    const problems: string[] = []
    page.on('pageerror', (error) => problems.push(`pageerror: ${error.message}`))
    page.on('response', (response) => {
      if (response.url().includes('/api/') && response.status() >= 400) {
        problems.push(`http ${response.status()} ${response.url()}`)
      }
    })
    return problems
  }

  test.describe('RAG 相关文章 UI', () => {
    test('创作页 — 输入选题显示历史参考，点击套用选题', async ({ page }) => {
      await page.setViewportSize({ width: 1440, height: 960 })
      const problems = collectRuntimeProblems(page)
      await mockAuth(page)
      await mockRagSearch(page, '')

      await page.goto('/create', { waitUntil: 'domcontentloaded' })
      await expect(page.getByRole('heading', { level: 1, name: '创作新文章' })).toBeVisible()

      // 未输入时显示提示
      await expect(page.getByText('输入选题后，将展示相关历史文章')).toBeVisible()

      // 输入选题 → 防抖(500ms)后出现参考卡片
      await page.locator('#article-topic-input').fill('AI 如何改变职场')
      await expect(page.getByRole('button', { name: /AI 重塑职场/ })).toBeVisible({ timeout: 3_000 })

      // 点击套用选题 → 选题框填入
      await page.getByRole('button', { name: /AI 重塑职场/ }).click()
      await expect(page.locator('#article-topic-input')).toHaveValue('AI 重塑职场')

      expect(problems, '创作页 RAG 不应产生页面或 HTTP 错误').toEqual([])
    })

    test('详情页 — 加载显示相关文章（排除自身），点击跳转', async ({ page }) => {
      await page.setViewportSize({ width: 1440, height: 960 })
      const problems = collectRuntimeProblems(page)
      await mockAuth(page)
      await mockRagSearch(page, 'task-detail-1')

      // mock 文章详情
      await page.route('**/api/article/task-detail-1', (route) =>
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 0,
            data: {
              taskId: 'task-detail-1',
              mainTitle: 'AI 重塑职场',
              subTitle: '2026 年全景展望',
              fullContent: '## 第一章\n正文内容',
              status: 'COMPLETED',
              userId: 1,
            },
          }),
        }),
      )
      // mock 执行日志（空）
      await page.route('**/api/article/execution-logs/**', (route) =>
        route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ code: 0, data: null }),
        }),
      )

      await page.goto('/article/task-detail-1', { waitUntil: 'domcontentloaded' })
      await expect(page.getByRole('heading', { level: 1, name: 'AI 重塑职场' })).toBeVisible()

      // 相关文章区块出现，且排除了自身（'这是自身文章' 不显示）
      await expect(page.getByRole('heading', { level: 2, name: '相关文章' })).toBeVisible()
      await expect(page.getByRole('button', { name: /远程办公指南/ })).toBeVisible({ timeout: 3_000 })
      await expect(page.getByText('这是自身文章')).not.toBeVisible()

      // 点击卡片 → 跳转到该文章详情
      await page.getByRole('button', { name: /远程办公指南/ }).click()
      await page.waitForURL('**/article/ref-2')

      expect(problems, '详情页 RAG 不应产生页面或 HTTP 错误').toEqual([])
    })
  })
  ```

  > 跳转目标 `ref-2` 详情未 mock，落地页会显示「没有找到这篇文章」——不影响跳转行为断言。若想更真实可再 mock `ref-2` 详情，但按 YAGNI 不必。

- [ ] **Step 2: 运行 E2E**

  Run: `cd frontend && npx playwright test tests/ui/rag-hits.spec.ts`
  Expected: `2 passed`

- [ ] **Step 3: 提交**

  ```bash
  git add frontend/tests/ui/rag-hits.spec.ts
  git commit -m "test(rag): RAG 相关文章 UI E2E — 历史参考套用 + 详情页排除自身与跳转"
  ```

---

### Task 6: 全量验证

**Files:**
- 无代码文件

- [ ] **Step 1: 全量质量闸门**

  ```bash
  cd frontend && npm run type-check
  cd frontend && npm run build
  cd frontend && npm run lint:check
  cd frontend && npm run test
  ```

  Expected: type-check 零错误、build 通过、lint 无新增告警、test:skill 含新单测 + E2E 全绿。

- [ ] **Step 2: 验证工作区**

  Run: `git status --short`
  Expected: 仅本计划新增/修改的 6 个文件 + 未提交的 `ArticleAgentOrchestrator.java`（保留不提交）。

- [ ] **Step 3: 手动点验（可选）**

  若后端可运行：`mvn spring-boot:run` + `npm run dev`，创作页输入选题看参考面板、进详情页看相关文章。无法运行后端则以 E2E 为准。

---

## Self-Review 记录

- **Spec 覆盖**：历史参考（Task 3）、相关文章（Task 4）、共享组件+composable（Task 1/2）、refId 去重/type 过滤/排除自身（Task 1）、score 仅排序不显示百分比（Task 2 只显示「相关」标签）、双空态（Task 2）、生命周期清理（Task 1 内部）、防抖保留旧结果不闪 loading（Task 1 `loading` 仅 doSearch 时置 true）、纯文本插值（Task 2）、单测（Task 1）+ E2E（Task 5）、test:skill 脚本接入（Task 1 Step 5）。
- **占位符扫描**：无 TBD/TODO；每个 Step 含完整代码或精确命令。
- **类型一致性**：`useRagSearch` 返回 `{ hits, loading, empty, search, clear }`，Task 3 解构 `{ hits: ragHits, loading: ragLoading, search: ragSearch, clear: ragClear }`，Task 4 解构 `{ hits: relatedHits, loading: relatedLoading, search: relatedSearch }` —— 一致；`search(query, { type, topK, excludeRefId })` 两处用法一致；`RagHitsPanel` props/emit 一致。
- **已修正的自查发现**：
  - composable 内 `@/` 别名 Node 不解析 → 改用相对路径 `../api/ragController`
  - `test:skill` 需加入新单测文件才会被 `npm run test` 覆盖 → Task 1 Step 5
  - Task 4 原先冗余的 `onBeforeUnmount`/`relatedClear` → 移除（composable 内部自动清理）
  - E2E 增加「排除自身」回归断言 + 精确跳转 URL（`waitForURL('**/article/ref-2')`）
  - 执行日志接口路径修正为 `/article/execution-logs/{taskId}`
