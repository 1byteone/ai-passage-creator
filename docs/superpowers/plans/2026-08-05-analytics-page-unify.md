# 数据分析页统一 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 消除"数据分析"双入口跳转歧义 —— 顶部导航统一跳新页 `/analytics`，旧页 StatisticsPage 删除，其独有数据（创作量趋势/用户构成/性能资源）迁移到新页 admin 视角。

**Architecture:** 前端菜单收敛 + 新页增强。GlobalHeader 顶部导航 key 改 `/analytics`、下拉菜单移除重复项；AnalyticsPage admin 视角额外调用保留的 `/statistics/overview` 获取系统统计；删除 `/admin/statistics` 路由与旧页组件；更新两个 Playwright 测试。

**Tech Stack:** Vue 3 (Composition API) / TypeScript / Ant Design Vue / ECharts / Playwright

## Global Constraints

- TypeScript strict，`npm run type-check` 零错误
- 前端样式用 SCSS scoped（`.vue` 的 `<style>` 必须 `lang="scss"` 或用 `/* */` 注释，禁 `//`）
- ECharts 实例在组件卸载时 `dispose()`（暗坑 #14/#5）
- 所有 v-html 必须经 DOMPurify 清洗（本任务无新增 v-html）
- 用户可见消息中文，简洁直接
- Conventional Commits + Co-Authored-By: Claude

---

### Task 1: 菜单统一 — GlobalHeader 改 key + 移除下拉重复项

**Files:**
- Modify: `frontend/src/components/GlobalHeader.vue:159-164`（顶部导航数据项）
- Modify: `frontend/src/components/GlobalHeader.vue:179-182`（下拉菜单项）

**Interfaces:**
- Consumes: 现有 `originItems` 数组结构 `{ key, icon, label, admin }`，`dropdownItems` computed
- Produces: 顶部导航 `数据分析` key = `/analytics`；下拉菜单不再含"数据分析"

- [ ] **Step 1: 改顶部导航 key**

将 L160 的 `key: '/admin/statistics'` 改为 `key: '/analytics'`：

```ts
  {
    key: '/analytics',
    icon: BarChartOutlined,
    label: '数据分析',
    admin: true,
  },
```

- [ ] **Step 2: 移除下拉菜单重复项**

在 `dropdownItems` computed（L179-182）中删除 `items.push({ key: '/analytics', ... })` 行，保留 `approval` 项：

```ts
  if (loginUser && loginUser.userRole === 'admin') {
    items.push({ key: '/approval', icon: AuditOutlined, label: '审批工作台' })
  }
```

- [ ] **Step 3: type-check**

Run: `cd frontend && npm run type-check`
Expected: 零错误

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/GlobalHeader.vue
git commit -m "refactor(header): 数据分析菜单统一跳 /analytics，移除下拉重复入口

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: 新页 admin 视角接入系统统计（创作量趋势/用户构成/性能资源）

**Files:**
- Modify: `frontend/src/pages/analytics/AnalyticsPage.vue`（template + script）

**Interfaces:**
- Consumes: `getStatistics()`（`frontend/src/api/statisticsController.ts`，返回 `API.BaseResponseStatisticsVO`，含 `todayCount/weekCount/monthCount/totalCount/activeUserCount/totalUserCount/vipUserCount/avgDurationMs/quotaUsed/successRate`）
- Produces: admin 视角渲染 3 块系统统计；`statData` ref 供模板消费；ECharts 实例统一走 `chartInstances` 管理

- [ ] **Step 1: script — 引入 getStatistics 并加载 admin 系统统计**

在 AnalyticsPage.vue `<script setup>` 的 import 区（L95）追加：

```ts
import { getStatistics } from '@/api/statisticsController'
```

在 `data`/`loading` 声明区（L103-105 附近）追加：

```ts
const statData = ref<API.StatisticsVO | null>(null)
const statError = ref('')
```

在 `fetchData`（L203-216）中，admin 分支并行加载系统统计：

```ts
const fetchData = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const res = isAdmin.value ? await getContentAnalytics() : await getMyAnalytics()
    if (res.data.code !== 0) throw new Error(res.data.message || '数据加载失败')
    data.value = res.data.data ?? null
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '网络或服务暂时不可用'
    data.value = null
  } finally {
    loading.value = false
  }
  // admin 额外加载系统统计（失败仅隐藏该块，不影响内容分析）
  if (isAdmin.value) {
    void fetchStatData()
  }
}

const fetchStatData = async () => {
  try {
    const res = await getStatistics()
    if (res.data.code !== 0) throw new Error(res.data.message || '统计数据加载失败')
    statData.value = res.data.data ?? null
    statError.value = ''
  } catch (e) {
    statError.value = e instanceof Error ? e.message : '网络或服务暂时不可用'
    statData.value = null
  }
}
```

在 `renderAllCharts`（L175-195）中，admin 且 `statData` 有值时追加渲染用户构成饼图。当前方法体在 `dailyChartRef` 渲染后结束，在定时器内末尾追加：

```ts
    if (userChartRef.value && data.value.dailyActiveUsers) renderDailyLine(dailyChartRef.value, data.value.dailyActiveUsers)
    // admin 系统统计：用户构成饼图（复用 renderPie，实例统一入 chartInstances 管理）
    if (isAdmin.value && userChartRef.value && statData.value?.totalUserCount) {
      renderPie(userChartRef.value, {
        'VIP 用户': statData.value.vipUserCount ?? 0,
        '本周活跃': statData.value.activeUserCount ?? 0,
        '其他用户': Math.max(0, (statData.value.totalUserCount ?? 0)
          - (statData.value.vipUserCount ?? 0) - (statData.value.activeUserCount ?? 0)),
      })
    }
```

> **为什么统一在 `renderAllCharts` 渲染用户图**：若另起 `watch(statData)` 并用同一个 `renderTimer`，两次定时会互相 `clearTimeout` 取消，导致内容图或用户图丢失渲染。统一入口后，`data` 或 `statData` 任一更新都整体重渲染，ECharts 实例仍由 `chartInstances` 统一 dispose 管理。

新增 `userChartRef` 声明（L107-112 区）：

```ts
const userChartRef = ref<HTMLElement>()
```

在 `watch(data, ...)`（L227-230）后追加一个 watch，触发整体重渲染：

```ts
watch(statData, () => {
  if (unmounted) return
  renderAllCharts()
})
```

在 `onMounted` 中（L222-225），现有 `void fetchData()` 已会触发 `fetchStatData`（admin 时），无需额外调用。

- [ ] **Step 2: template — admin 视角新增 3 块系统统计**

在图表 section（L72 `</section>` 之后、`</template>` 之前）插入：

```html
      <!-- 系统统计（仅 admin）：创作量趋势 + 用户构成 + 性能与资源 -->
      <section v-if="isAdmin" class="metric-grid" aria-label="系统统计指标">
        <div class="metric-card">
          <span class="metric-value">{{ statData?.todayCount ?? 0 }}</span>
          <span class="metric-label">今日创作</span>
        </div>
        <div class="metric-card">
          <span class="metric-value">{{ statData?.weekCount ?? 0 }}</span>
          <span class="metric-label">本周创作</span>
        </div>
        <div class="metric-card">
          <span class="metric-value">{{ statData?.monthCount ?? 0 }}</span>
          <span class="metric-label">本月创作</span>
        </div>
        <div class="metric-card">
          <span class="metric-value">{{ formatPercent(statData?.successRate) }}</span>
          <span class="metric-label">成功率</span>
        </div>
      </section>

      <section v-if="isAdmin" class="chart-grid" aria-label="系统统计图表">
        <div class="chart-card">
          <h3>用户构成</h3>
          <div ref="userChartRef" class="chart-container" />
          <div v-if="!statData?.totalUserCount" class="chart-empty">暂无数据</div>
        </div>
        <div class="chart-card">
          <h3>性能与资源</h3>
          <div class="chart-container stat-list">
            <div class="stat-row"><span>平均生成耗时</span><strong>{{ formatDuration(statData?.avgDurationMs ?? 0) }}</strong></div>
            <div class="stat-row"><span>累计配额消耗</span><strong>{{ formatNumber(statData?.quotaUsed ?? 0) }}</strong></div>
            <div class="stat-row"><span>累计创作</span><strong>{{ formatNumber(statData?.totalCount ?? 0) }}</strong></div>
          </div>
        </div>
      </section>

      <a-alert v-if="isAdmin && statError" type="warning" show-icon closable class="page-feedback" :message="statError" @close="statError = ''" />
```

- [ ] **Step 3: script — 补充格式化工具函数**

在 `formatToken`（L115）后追加：

```ts
const formatDuration = (ms: number) => (ms < 1000 ? `${ms} 毫秒` : `${(ms / 1000).toFixed(1)} 秒`)
const formatNumber = (v: number) => new Intl.NumberFormat('zh-CN').format(v)
```

- [ ] **Step 4: style — 新增 stat-list 样式**

在 `<style scoped lang="scss">` 的 `.chart-empty` 规则后追加：

```scss
.stat-list {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 12px;
  height: 100%;
  min-height: 200px;
}

.stat-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 0 8px;
  border-bottom: 1px solid var(--border-subtle);

  span { color: var(--text-muted); font-size: 13px; }
  strong { color: var(--text-strong); font-size: 18px; font-weight: 700; }
}
```

- [ ] **Step 5: type-check + build**

Run: `cd frontend && npm run type-check && npm run build`
Expected: 两者零错误（build 能发现纯 CSS 语法问题）

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/analytics/AnalyticsPage.vue
git commit -m "feat(analytics): admin 视角迁移系统统计 — 创作量/用户构成/性能资源

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: 删除旧页 — 路由 + StatisticsPage + ChartPanel + api 引用

**Files:**
- Modify: `frontend/src/router/index.ts:103-107`（删除 `/admin/statistics` 路由）
- Delete: `frontend/src/pages/admin/StatisticsPage.vue`
- Delete: `frontend/src/components/ChartPanel.vue`（仅 StatisticsPage 引用）
- Modify: `frontend/src/api/index.ts:8,23`（移除 statisticsController import 与导出）
- Keep: `frontend/src/api/statisticsController.ts`（新页 Task 2 复用）

**Interfaces:**
- Consumes: Task 1 已无菜单指向 `/admin/statistics`；Task 2 已用 `getStatistics` 但仍需要 `statisticsController.ts` 文件存在
- Produces: 代码库无 StatisticsPage/ChartPanel 残留，`/admin/statistics` 不再路由可达

- [ ] **Step 1: 删除路由**

删除 `frontend/src/router/index.ts` L103-107：

```ts
    {
      path: '/admin/statistics',
      name: '数据分析',
      component: () => import('@/pages/admin/StatisticsPage.vue'),
    },
```

- [ ] **Step 2: 删除旧页与 ChartPanel**

```bash
git rm frontend/src/pages/admin/StatisticsPage.vue frontend/src/components/ChartPanel.vue
```

- [ ] **Step 3: 移除 api/index.ts 的 statisticsController 引用**

删 L8 `import * as statisticsController from './statisticsController'` 与 L23 `statisticsController,`

- [ ] **Step 4: 全局搜索确认无残留引用**

Run: `grep -rn "StatisticsPage\|ChartPanel\|admin/statistics\|statisticsController" frontend/src --include="*.vue" --include="*.ts" | grep -v "api/statisticsController.ts"`
Expected: 无匹配（除 `api/statisticsController.ts` 自身定义与新页 `import { getStatistics }`）

- [ ] **Step 5: type-check**

Run: `cd frontend && npm run type-check`
Expected: 零错误

- [ ] **Step 6: Commit**

```bash
git add -A frontend/src/router/index.ts frontend/src/api/index.ts
git commit -m "refactor(admin): 删除旧 StatisticsPage 与 ChartPanel，移除 /admin/statistics 路由

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: 测试更新 — core-routes + analytics-nav-repro

**Files:**
- Modify: `frontend/tests/ui/core-routes.spec.ts:10`（从 protectedRoutes 移除 `/admin/statistics`）
- Modify: `frontend/tests/ui/core-routes.spec.ts:94-101`（删除该路由段）
- Modify: `frontend/tests/ui/analytics-nav-repro.spec.ts`（改为验证顶部导航跳 `/analytics`）

**Interfaces:**
- Consumes: Task 1 菜单 key = `/analytics`；Task 3 路由 `/admin/statistics` 已删
- Produces: 测试覆盖顶部导航"数据分析"→ `/analytics` 的路径；无对已删路由的引用

- [ ] **Step 1: core-routes — 移除旧路由**

删除 L10 `/admin/statistics` 行，删除 L94-101 段：

```ts
  '/admin/statistics': {
    heading: '数据分析',
    ready: async (page) => {
      await expect(page.locator('.statistics-page .page-state[role="alert"]')).toHaveCount(0)
      await expect(page.locator('.metric-grid')).toHaveAttribute('aria-busy', 'false')
      await expect(page.getByRole('heading', { level: 2, name: '创作运行概览' })).toBeVisible()
    },
  },
```

- [ ] **Step 2: analytics-nav-repro — 改顶部导航路径**

将整个测试改为通过顶部导航点击"数据分析"（不再用下拉菜单）：

```ts
import { expect, test } from '@playwright/test'

const adminAccount = process.env.UI_TEST_ADMIN_ACCOUNT ?? 'admin_test'
const adminPassword = process.env.UI_TEST_ADMIN_PASSWORD ?? 'AdminTest@2026'

// 回归：顶部导航"数据分析"应跳转到 /analytics（原 bug：菜单 key 指向旧页 /admin/statistics）
test('admin 点击顶部导航数据分析跳转到 /analytics', async ({ page }) => {
  await page.goto('/user/login')
  await expect(page.getByRole('heading', { level: 1, name: '欢迎回来' })).toBeVisible()
  await page.getByLabel('账号', { exact: false }).fill(adminAccount)
  await page.getByLabel('密码', { exact: false }).fill(adminPassword)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL('/')

  // 顶部导航点击"数据分析"（admin 可见，nav 内非下拉菜单）
  await page.locator('.nav-center').getByText('数据分析').click()
  await expect(page).toHaveURL(/analytics/)
})
```

- [ ] **Step 3: 全量测试搜索无残留**

Run: `grep -rn "admin/statistics\|StatisticsPage\|statistics-page" frontend/tests/`
Expected: 无匹配

- [ ] **Step 4: type-check + lint**

Run: `cd frontend && npm run type-check && npm run lint:check`
Expected: 零错误

- [ ] **Step 5: Commit**

```bash
git add frontend/tests/ui/core-routes.spec.ts frontend/tests/ui/analytics-nav-repro.spec.ts
git commit -m "test(frontend): 数据分析导航测试改顶部入口，移除旧路由断言

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: 端到端验证

**Files:** 无（验证）

- [ ] **Step 1: 前端完整质量闸门**

Run: `cd frontend && npm run check`
Expected: lint → build → test → 性能全部通过

- [ ] **Step 2: 手动验证双视角**

启动后端 + 前端，admin 登录：
- 顶部导航"数据分析" → URL 为 `/analytics`，新页显示内容分析 6 图 + 系统统计 3 块（创作量/用户构成/性能资源）
- 普通用户登录 → 顶部无"数据分析"项（admin 过滤），下拉菜单无该项
- 直接访问 `/admin/statistics` → 404 兜底回首页

## Self-Review

**Spec 覆盖检查：**
- ✅ 菜单统一（Task 1）— spec §方案 1
- ✅ 数据迁移（Task 2）— spec §方案 2
- ✅ 路由与清理（Task 3）— spec §方案 3
- ✅ 测试更新（Task 4）— spec §方案 4
- ✅ 错误处理（statError 独立，不阻断内容分析）— Task 2 Step 1/2
- ✅ 非 admin 不调 getStatistics（isAdmin 守卫）— Task 2 Step 1 watch + fetchStatData 条件
