import { computed, onBeforeUnmount, ref } from 'vue'
// 注意：不能用顶层 import '../api/ragController' —— 该文件内部 `import request from '@/request'`，
// `@/` 别名在 Node `--experimental-strip-types` 下不解析，单测 import 本 composable 即失败。
// 默认 searchFn 改为运行时动态 import；测试注入 searchFn 时永远不触发该 import。

interface UseRagSearchOptions {
  debounceMs?: number
  searchFn?: SearchRagFn
  lifecycle?: { onBeforeUnmount: (cb: () => void) => void }
}

type SearchRagFn = (params: {
  query: string
  type?: string
  topK?: number
}) => Promise<{ data: API.BaseResponseListRagHit }>

interface SearchOptions {
  type?: string
  topK?: number
  excludeRefId?: string
}

let cachedSearchRag: SearchRagFn | null = null
const loadSearchRag = async (): Promise<SearchRagFn> => {
  if (!cachedSearchRag) {
    const mod = await import('../api/ragController')
    cachedSearchRag = mod.searchRag
  }
  return cachedSearchRag
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
      const searchFn = options.searchFn ?? (await loadSearchRag())
      const res = await searchFn({ query, type: opts.type, topK: opts.topK })
      if (disposed || seq !== fetchSeq) return
      // 后端业务/运行时错误走 HTTP 200 + code!=0，失败时不能让空数组冒充"检索成功零命中"
      if (res.data && res.data.code !== 0) throw new Error(res.data.message || 'RAG 检索失败')
      let list = res.data?.data ?? []
      // 不再前端二次过滤 type：后端 buildFilter 已按 type 契约返回（type=article 只回个人文章，
      // 未指定 type 回个人+共享文档），前端过滤会掩盖契约不一致且丢弃共享文档命中。
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
