# 安全红线 — 零容忍

> **加载条件**: 始终加载 (安全规则全局适用)

---

## XSS 防护 🔴

| 规则 | 说明 |
|------|------|
| **v-html 必须清洗** | 所有 `v-html` 内容必须经过 `DOMPurify.sanitize()` → 用 `@/utils/markdown` 包装 |
| **禁止 raw marked** | 永远不要 `import { marked } from 'marked'` 后直接渲染到 v-html |
| **AI 输出不可信** | AI 生成内容视为用户输入，必须清洗后再渲染 |
| **URL 必须编码** | `encodeURIComponent()` 用于 URL 路径参数，防注入 |

```typescript
// ❌ XSS 漏洞
import { marked } from 'marked'
<div v-html="marked(content)" />

// ✅ 安全
import { markdownToHtml } from '@/utils/markdown'
<div v-html="markdownToHtml(content)" />
```

---

## SSR/SSE 资源泄漏 🔴

| 规则 | 说明 |
|------|------|
| **EventSource 必须 close** | 每次新连接前关闭旧连接，组件卸载时关闭 |
| **Timer 必须 clear** | `setTimeout`/`setInterval` 在 `onBeforeUnmount` 中清除 |
| **await 后必须检查** | 任何 `await` 后继续操作前检查 `unmounted` / `closed` 标记 |
| **Sortable 必须 destroy** | 拖拽实例在 `onBeforeUnmount` 中调用 `destroy()` |
| **ECharts 必须 dispose** | 图表实例在组件卸载时调用 `dispose()` |

---

## 认证 & 授权 🔴

| 规则 | 说明 |
|------|------|
| **Session 过期清零** | `loginUser.fetchLoginUser()` 返回 code≠0 时必须重置用户状态 |
| **fetch 必须超时** | 原生 `fetch` 必须加 AbortController + timeout |
| **路由守卫不可绕过** | 所有需要认证的路由必须有 `meta.requiresAuth` 或 `/admin/*` 前缀校验 |
| **敏感操作二次确认** | 删除/发布/支付等操作必须有确认步骤 |

---

## 数据校验 🔴

| 规则 | 说明 |
|------|------|
| **后端必须二次校验** | 前端校验只是 UX 优化，后端 Controller 必须用 `@Valid` 校验 |
| **不信任客户端** | 所有来自前端的参数 (包括路径参数、query、body) 必须校验 |
| **SQL 注入防护** | 使用 MyBatis-Flex 参数化查询，禁止拼接 SQL |

---

## 第三方集成 🔴

| 规则 | 说明 |
|------|------|
| **Webhook 验签** | Stripe Webhook 必须验证签名后才处理 |
| **URL 白名单** | `window.location.assign()` 的目标 URL 必须验证 scheme |
| **预签名 URL** | COS 文件下载使用预签名 URL，设置合理过期时间 |

---

## 敏感信息

- ❌ 不要在代码、注释、CLAUDE.md 中硬编码密钥/密码/Token
- ❌ 不要在 git commit 中包含 `.env` / `.env.local`
- ✅ 所有密钥通过环境变量或 Spring 配置外部化注入
