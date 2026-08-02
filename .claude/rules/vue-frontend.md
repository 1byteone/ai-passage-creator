# Vue 3 / TypeScript 前端规范

> **加载条件**: 当修改 `frontend/src/**/*.vue`, `*.ts`, `*.scss` 时适用。

---

## 技术栈约束

| 库 | 版本 | 用途 |
|---|---|---|
| Vue 3 | 3.5+ | Composition API + `<script setup lang="ts">` |
| TypeScript | 5.8 (strict) | NEVER `any` — 用 `unknown` + 类型守卫 |
| Vite | 7 | 构建工具 |
| Ant Design Vue | 4 | UI 组件库 |
| Pinia | 3 | 状态管理 |
| Vue Router | 4.5 | 路由 |
| Axios | 1.11 | HTTP (通过 `@/request` 封装) |
| marked | 17 | Markdown 解析 (必须配合 DOMPurify!) |
| DOMPurify | 3.4 | XSS 清洗 |
| ECharts | 6 | 图表 (注意实例缓存与 DOM 重建) |
| Day.js | 1.11 | 日期处理 |
| Sortablejs | 1.15 | 拖拽排序 |

---

## 组件规范

### 结构

```vue
<script setup lang="ts">
// 1. imports
// 2. props / emits
// 3. composables / stores
// 4. reactive state (ref, computed, reactive)
// 5. methods
// 6. lifecycle (onMounted, onBeforeUnmount)
// 7. watch
</script>

<template>
  <!-- template here -->
</template>

<style scoped lang="scss">
/* scoped SCSS */
</style>
```

### Props & Emits

```typescript
// Props — 使用 TypeScript 泛型
const props = defineProps<{ items: Item[]; loading?: boolean }>()

// Emits — 使用 TypeScript 泛型 (Vue 3.3+)
const emit = defineEmits<{ (e: 'confirm', payload: Payload): void }>()
```

### 响应式状态

- 局部状态: `ref<T>()` / `computed<T>()` / `reactive<T>()`
- 跨组件: Pinia store (`useXxxStore()`)
- 跨页面: `sessionStorage` (草稿) / Pinia (用户信息)
- **禁止**直接修改 props。如需编辑 props 数据，深拷贝到局部 `ref`:

```typescript
// ✅ 深拷贝
const localData = ref(props.data.map(item => ({ ...item, nested: [...item.nested] })))
// ❌ 浅拷贝 (共享嵌套引用)
const localData = ref([...props.data])
```

---

## SSE / EventSource 规范 (极其重要!)

```typescript
// ✅ 完整模式 — 重连 + 生命周期管理
let connection: SSEConnection | null = null
let unmounted = false

const start = async () => {
  closeOld() // 先关闭旧连接!
  connection = connectSSE(id, {
    onMessage: handleMessage,
    onError: handleError,
    onComplete: handleComplete,
  })
}

const poll = async () => {
  pollTimer = null  // ← 先清 timer 再 await!
  const result = await fetchResult()
  if (unmounted || terminal) return  // ← await 后检查是否已卸载!
  pollTimer = window.setTimeout(poll, 2000)
}

onBeforeUnmount(() => {
  unmounted = true    // ← 必须最先设置!
  connection?.close()
  if (pollTimer !== null) {
    window.clearTimeout(pollTimer)
    pollTimer = null
  }
})
```

**规则**:
1. 每次 `connectSSE()` 前必须 `closeSSE()` 旧连接
2. 任何 `await` 后继续操作前检查 `unmounted` / `closed` 标记
3. `setTimeout` 前 set timer 为 null → await → 检查后重新 setTimeout
4. `onBeforeUnmount` 中 `unmounted = true` 必须**最先执行**

---

## XSS 防护 (零容忍)

```typescript
// ✅ 必须使用 @/utils/markdown 的 DOMPurify 清洗版本
import { markdownToHtml } from '@/utils/markdown'
<div v-html="markdownToHtml(content)" />

// ❌ 禁止! raw marked 绕过 DOMPurify
import { marked } from 'marked'
marked(content) // ← XSS 漏洞!
```

**任何时候添加 `v-html` 都必须经过 DOMPurify.sanitize()。**

---

## TypeScript

- 启用 strict 模式，零容忍 `vue-tsc` 错误
- `as` 类型断言仅在不安全的 JSON 解析处使用，注释原因
- API 类型由 `npm run openapi2ts` 自动生成至 `api/typings.d.ts`
- 不写 `export default {}` — 使用命名导出

---

## API 调用

- 统一用 `import request from '@/request'` (Axios 封装, 60s 超时)
- 每个 API 函数定义在 `api/xxxController.ts`
- URL 参数必须编码: ``` ` `/skill/${encodeURIComponent(skillName)}/execute` ``` 
- 错误由 Axios 响应拦截器统一处理 (401 跳登录), 调用方只处理业务逻辑

---

## 异步 & 竞态

```typescript
// 列表请求防乱序 — fetchSeq 模式
let fetchSeq = 0

const loadData = async () => {
  loading.value = true
  const seq = ++fetchSeq
  try {
    const res = await api()
    if (seq !== fetchSeq) return  // ← 不是最新请求，忽略
    data.value = res
  } catch (e) {
    if (seq !== fetchSeq) return
    error.value = e.message
  } finally {
    loading.value = false
  }
}
```

---

## 下载文件

```typescript
// 必须 append 到 DOM + 延迟 revoke
const download = (content: string, filename: string) => {
  const blob = new Blob([content], { type: 'text/markdown' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.style.display = 'none'
  a.href = url
  a.download = filename.replace(/[\\/:*?"<>|]/g, '_') // 清理非法字符
  document.body.appendChild(a)
  a.click()
  setTimeout(() => {
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }, 100)
}
```

---

## 路由 & 权限

- 路由定义: `router/index.ts` 使用懒加载 `() => import(...)`
- 权限守卫: `access.ts` → `router.beforeEach`
- `meta.requiresAuth` 标记需登录页面
- `/admin/*` 额外校验 `userRole === 'admin'`
- 404: `/:pathMatch(.*)*` → redirect `/`
