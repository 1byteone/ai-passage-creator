import assert from 'node:assert/strict'
import test from 'node:test'
import { useRagSearch } from '../src/composables/useRagSearch.ts'

const noLifecycle = { onBeforeUnmount: () => {} }
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

test('keeps all backend types and excludes refId (type filter is backend-owned, M12)', async () => {
  const searchFn = async () =>
    ({ data: { code: 0, data: [hit('self', '自己', 0.9), hit('skill-1', '技能', 0.8, 'skill'), hit('good', '好文', 0.7)] } }) as API.BaseResponseListRagHit
  const { hits, search } = useRagSearch({ debounceMs: 10, searchFn, lifecycle: noLifecycle })
  search('q', { type: 'article', topK: 5, excludeRefId: 'self' })
  await sleep(30)
  // M12：前端不再过滤 type（后端 buildFilter 按 type 契约返回，前端过滤会掩盖契约不一致
  // 且丢弃共享文档命中）；本层只负责 excludeRefId 排除与按 score 去重。
  assert.equal(hits.value.length, 2)
  assert.deepEqual(hits.value.map((h) => h.refId).sort(), ['good', 'skill-1'])
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
