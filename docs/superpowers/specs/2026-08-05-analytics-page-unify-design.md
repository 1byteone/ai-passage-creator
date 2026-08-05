# 数据分析页统一 — 消除双入口跳转歧义

日期: 2026-08-05
状态: 已批准

## Context

首页存在**两个"数据分析"入口**跳转两个不同页面，admin 用户从不同入口点击会落到完全不同的页面，视觉上就是"点击跳转错误"：

| 入口 | 菜单位置 | 跳转路由 | 页面 | 内容 |
|------|---------|---------|------|------|
| 数据分析 | 顶部导航栏（admin 可见） | `/admin/statistics` | 旧页 StatisticsPage | 创作量趋势 + 用户构成 + 性能资源 |
| 数据分析 | 用户下拉菜单（仅 admin） | `/analytics` | 新页 AnalyticsPage | 内容分析 6 图 + 指标卡 |

用户困惑的核心是"两个入口跳两个页面"。根因是历史遗留：`30c584d` 引入的新页 AnalyticsPage 没有替换旧页 StatisticsPage，两者菜单并存。

## 决策（已与用户对齐）

1. **保留新页** AnalyticsPage（更丰富，支持 admin/个人双视角），统一跳 `/analytics`
2. **迁移旧页独有数据**到新页：创作量趋势、用户构成、性能与资源
3. **保留后端旧接口** `/statistics/overview`，新页额外调用获取系统统计数据（后端零改动）
4. **只留顶部导航**入口 → 改为 `/analytics`；下拉菜单的重复"数据分析"项移除

## 方案

### 1. GlobalHeader.vue — 菜单统一

- **顶部导航**（L160-164）：`key: '/admin/statistics'` → `key: '/analytics'`，label 不变"数据分析"
- **下拉菜单**（L181）：移除 `items.push({ key: '/analytics', ... })` 项（避免重复入口）

### 2. AnalyticsPage.vue — 迁移旧页数据（admin 视角）

- **admin 视角**：加载 `/analytics/content`（现有）+ `/statistics/overview`（新增，`getStatistics()`）
- **新增 3 块数据**（仅 admin 展示）：
  - **创作量趋势**：今日/本周/本月/累计 → 摘要卡片或柱状图（`todayCount/weekCount/monthCount/totalCount`）
  - **用户构成**：VIP 用户/本周活跃/其他用户 → 饼图（`vipUserCount/activeUserCount/totalUserCount`）
  - **性能与资源**：平均耗时/累计配额/成功率 → 摘要列表（`avgDurationMs/quotaUsed/successRate`）
- **个人视角不变**：仅 `/analytics/mine` 内容分析
- 新增字段复用 `API.StatisticsVO` 类型（typings.d.ts 已定义）

### 3. 路由与清理

- **router/index.ts**：删除 `/admin/statistics` 路由（L104-107）
- **删除** `frontend/src/pages/admin/StatisticsPage.vue` 及 `ChartPanel.vue`（仅被其引用）
- **前端类型清理**：若 `statisticsController.ts` / `index.ts` 无其它引用则一并处理（`api/index.ts` L8 引用需移除）

### 4. 测试更新

- **core-routes.spec.ts**：
  - L10 从 `protectedRoutes` 移除 `/admin/statistics`
  - L94-101 删除 `/admin/statistics` 段
  - （可选）在公开/受保护路由加 `/analytics` 覆盖
- **analytics-nav-repro.spec.ts**：选择器从"点击顶部导航数据分析"改为验证 `/analytics` 直访（因下拉入口已移除，原测试路径失效）

## 错误处理

- `/statistics/overview` 加载失败 → 仅隐藏系统统计数据块，不影响内容分析图（`loadError` 独立处理，页面不整体报错）
- 非 admin 用户不触发 `getStatistics()`（避免 403）

## 不做的事

- 不改后端（复用 `/statistics/overview`）
- 不合并 AnalyticsVO/StatisticsVO（保留旧接口，方案已确认）
- 不新增搜索功能（页面本无搜索，用户确认现象是双入口）

## 验证

```bash
cd frontend && npm run type-check   # TS 零错误
cd frontend && npm run build        # vite build 通过
cd frontend && npm run test:ui      # Playwright 回归
```
- admin 登录 → 顶部导航"数据分析" → 落在 `/analytics`，新页显示内容分析 6 图 + 系统统计 3 块
- 普通用户 → 无"数据分析"入口（顶部 admin 项过滤 + 下拉无该项）

## 文件清单

| 文件 | 操作 |
|------|------|
| `frontend/src/components/GlobalHeader.vue` | 改顶部导航 key + 移除下拉项 |
| `frontend/src/pages/analytics/AnalyticsPage.vue` | admin 视角新增 3 块系统统计 |
| `frontend/src/router/index.ts` | 删除 `/admin/statistics` 路由 |
| `frontend/src/pages/admin/StatisticsPage.vue` | 删除 |
| `frontend/src/components/ChartPanel.vue` | 删除（仅 StatisticsPage 引用） |
| `frontend/src/api/statisticsController.ts` | 保留（新页复用） |
| `frontend/src/api/index.ts` | 移除 statisticsController 引用（若仅旧页用） |
| `frontend/tests/ui/core-routes.spec.ts` | 更新路由覆盖 |
| `frontend/tests/ui/analytics-nav-repro.spec.ts` | 更新选择器/路径 |
