<template>
  <div id="analyticsPage">
    <header class="page-heading">
      <div>
        <span class="page-kicker">{{ isAdmin ? '管理后台' : '个人中心' }}</span>
        <h1>数据分析</h1>
        <p>{{ isAdmin ? '全站内容分析与创作趋势' : '我的创作数据与趋势分析' }}</p>
      </div>
      <div class="heading-actions">
        <a-button :loading="loading" @click="refreshData">
          <template #icon><ReloadOutlined /></template>刷新数据
        </a-button>
      </div>
    </header>

    <div v-if="loadError" class="page-feedback" aria-live="polite">
      <a-alert type="error" show-icon closable :message="loadError" @close="loadError = ''" />
    </div>

    <template v-if="data">
      <!-- 指标卡片 -->
      <section class="metric-grid" aria-label="关键指标">
        <div class="metric-card"><span class="metric-value">{{ data.totalArticles ?? 0 }}</span><span class="metric-label">总文章</span></div>
        <div class="metric-card"><span class="metric-value">{{ formatPercent(data.successRate) }}</span><span class="metric-label">成功率</span></div>
        <div class="metric-card"><span class="metric-value">{{ data.avgQualityScore?.toFixed(1) ?? '—' }}</span><span class="metric-label">平均质量分</span></div>
        <div class="metric-card"><span class="metric-value">{{ formatToken(data.totalTokenUsage) }}</span><span class="metric-label">Token 消耗</span></div>
      </section>

      <!-- 图表行 -->
      <section class="chart-grid" aria-label="分析图表">
        <!-- 风格分布 -->
        <div class="chart-card">
          <h3>文章风格分布</h3>
          <div ref="styleChartRef" class="chart-container" />
          <div v-if="!hasData(data.styleDistribution)" class="chart-empty">暂无数据</div>
        </div>

        <!-- 配图方法分布 -->
        <div class="chart-card">
          <h3>配图方法分布</h3>
          <div ref="imageChartRef" class="chart-container" />
          <div v-if="!hasData(data.imageMethodDistribution)" class="chart-empty">暂无数据</div>
        </div>

        <!-- 质量趋势 -->
        <div class="chart-card chart-wide">
          <h3>质量评分趋势（最近 20 条）</h3>
          <div ref="qualityChartRef" class="chart-container" />
          <div v-if="!data.qualityTrend?.length" class="chart-empty">暂无数据</div>
        </div>

        <!-- 技能使用排行 -->
        <div class="chart-card">
          <h3>技能使用排行</h3>
          <div ref="skillChartRef" class="chart-container" />
          <div v-if="!hasData(data.skillUsageTop)" class="chart-empty">暂无数据</div>
        </div>

        <!-- 模型使用分布 -->
        <div class="chart-card">
          <h3>模型使用分布</h3>
          <div ref="modelChartRef" class="chart-container" />
          <div v-if="!hasData(data.modelUsage)" class="chart-empty">暂无数据</div>
        </div>

        <!-- 每日活跃 -->
        <div class="chart-card chart-wide">
          <h3>每日创作量（近 7 天）</h3>
          <div ref="dailyChartRef" class="chart-container" />
          <div v-if="!hasData(data.dailyActiveUsers)" class="chart-empty">暂无数据</div>
        </div>
      </section>

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
    </template>

    <div v-else-if="loading" class="loading-state" aria-live="polite">
      <a-skeleton active :paragraph="{ rows: 6 }" />
    </div>

    <div v-else-if="!data" class="empty-state">
      <a-empty description="暂无分析数据">
        <span class="empty-hint">创作文章后，将在此展示分析数据。</span>
      </a-empty>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Alert as AAlert, Empty as AEmpty } from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getMyAnalytics, getContentAnalytics } from '@/api/analyticsController'
import { getStatistics } from '@/api/statisticsController'
import { useLoginUserStore } from '@/stores/loginUser'

echarts.use([BarChart, LineChart, PieChart, TooltipComponent, LegendComponent, GridComponent, CanvasRenderer])

const loginUserStore = useLoginUserStore()
const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')

const data = ref<API.AnalyticsVO | null>(null)
const loading = ref(false)
const loadError = ref('')
const statData = ref<API.StatisticsVO | null>(null)
const statError = ref('')

const styleChartRef = ref<HTMLElement>()
const imageChartRef = ref<HTMLElement>()
const qualityChartRef = ref<HTMLElement>()
const skillChartRef = ref<HTMLElement>()
const modelChartRef = ref<HTMLElement>()
const dailyChartRef = ref<HTMLElement>()
const userChartRef = ref<HTMLElement>()

const formatPercent = (v?: number) => v != null ? `${v.toFixed(1)}%` : '—'
const formatToken = (v?: number) => v != null ? (v >= 1000 ? `${(v / 1000).toFixed(1)}k` : String(v)) : '—'
const formatDuration = (ms: number) => (ms < 1000 ? `${ms} 毫秒` : `${(ms / 1000).toFixed(1)} 秒`)
const formatNumber = (v: number) => new Intl.NumberFormat('zh-CN').format(v)
const hasData = (m?: Record<string, number>) => m && Object.keys(m).length > 0

// ── 图表渲染 ──

const chartInstances: echarts.ECharts[] = []
let unmounted = false
let renderTimer: ReturnType<typeof setTimeout> | null = null

const renderPie = (el: HTMLElement, values: Record<string, number>) => {
  if (!Object.keys(values).length) return
  const chart = echarts.init(el)
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, type: 'scroll', textStyle: { fontSize: 11 } },
    series: [{ type: 'pie', radius: ['35%', '55%'], center: ['50%', '42%'], data: Object.entries(values).map(([k, v]) => ({ name: k, value: v })), label: { show: false }, emphasis: { label: { show: true, fontSize: 12 } } }],
  })
  chartInstances.push(chart)
}

const renderBar = (el: HTMLElement, values: Record<string, number>) => {
  if (!Object.keys(values).length) return
  const chart = echarts.init(el)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { top: 15, right: 15, bottom: 40, left: 60 },
    xAxis: { type: 'category', data: Object.keys(values), axisLabel: { rotate: 30, fontSize: 10 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ type: 'bar', data: Object.values(values), itemStyle: { borderRadius: [4, 4, 0, 0], color: '#22c55e' } }],
  })
  chartInstances.push(chart)
}

const renderLine = (el: HTMLElement, values: number[]) => {
  if (!values.length) return
  const chart = echarts.init(el)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { top: 15, right: 15, bottom: 25, left: 45 },
    xAxis: { type: 'category', data: Array.from({ length: values.length }, (_, i) => `#${i + 1}`) },
    yAxis: { type: 'value' },
    series: [{ type: 'line', data: values, smooth: true, areaStyle: { opacity: 0.15, color: '#22c55e' }, lineStyle: { color: '#22c55e', width: 2 }, showSymbol: true, symbolSize: 6 }],
  })
  chartInstances.push(chart)
}

const renderDailyLine = (el: HTMLElement, values: Record<string, number>) => {
  if (!Object.keys(values).length) return
  const chart = echarts.init(el)
  const sorted = Object.entries(values).sort(([a], [b]) => a.localeCompare(b))
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { top: 15, right: 15, bottom: 30, left: 45 },
    xAxis: { type: 'category', data: sorted.map(([k]) => k.slice(5, 10)), axisLabel: { fontSize: 10 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{ type: 'line', data: sorted.map(([, v]) => v), smooth: true, areaStyle: { opacity: 0.15, color: '#22c55e' }, lineStyle: { color: '#22c55e', width: 2 }, showSymbol: true, symbolSize: 6 }],
  })
  chartInstances.push(chart)
}

const renderAllCharts = () => {
  // 销毁旧的
  chartInstances.forEach(c => c.dispose())
  chartInstances.length = 0

  const d = data.value
  if (!d || unmounted) return

  // 延迟到 DOM 更新后再渲染
  if (renderTimer) clearTimeout(renderTimer)
  renderTimer = setTimeout(() => {
    renderTimer = null
    if (unmounted || !data.value) return
    if (styleChartRef.value && data.value.styleDistribution) renderPie(styleChartRef.value, data.value.styleDistribution)
    if (imageChartRef.value && data.value.imageMethodDistribution) renderPie(imageChartRef.value, data.value.imageMethodDistribution)
    if (qualityChartRef.value && data.value.qualityTrend) renderLine(qualityChartRef.value, data.value.qualityTrend)
    if (skillChartRef.value && data.value.skillUsageTop) renderBar(skillChartRef.value, data.value.skillUsageTop)
    if (modelChartRef.value && data.value.modelUsage) renderBar(modelChartRef.value, data.value.modelUsage)
    if (dailyChartRef.value && data.value.dailyActiveUsers) renderDailyLine(dailyChartRef.value, data.value.dailyActiveUsers)
    // admin 系统统计：用户构成饼图（复用 renderPie，实例统一入 chartInstances 管理）
    if (isAdmin.value && userChartRef.value && statData.value?.totalUserCount) {
      renderPie(userChartRef.value, {
        'VIP 用户': statData.value.vipUserCount ?? 0,
        '本周活跃': statData.value.activeUserCount ?? 0,
        '其他用户': Math.max(0, (statData.value.totalUserCount ?? 0)
          - (statData.value.vipUserCount ?? 0) - (statData.value.activeUserCount ?? 0)),
      })
    }
  }, 50)
}

const handleResize = () => {
  chartInstances.forEach(c => c.resize())
}

// ── 数据加载 ──

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

const refreshData = () => { void fetchData() }

// ── 生命周期 ──

onMounted(() => {
  void fetchData()
  window.addEventListener('resize', handleResize)
})

watch(data, () => {
  if (unmounted) return
  renderAllCharts()
})

watch(statData, () => {
  if (unmounted) return
  renderAllCharts()
})

onBeforeUnmount(() => {
  unmounted = true
  if (renderTimer) {
    clearTimeout(renderTimer)
    renderTimer = null
  }
  window.removeEventListener('resize', handleResize)
  chartInstances.forEach(c => c.dispose())
  chartInstances.length = 0
})
</script>

<style scoped lang="scss">
#analyticsPage {
  min-height: calc(100vh - 64px);
  padding: 40px 24px 72px;
  background: var(--surface-page);
  color: var(--text-body);
}

.page-heading, .page-feedback, .chart-grid, .empty-state {
  width: min(100%, 1120px);
  margin-right: auto; margin-left: auto;
}

.page-feedback { margin-bottom: 24px; }

// ── 指标卡片 ──

.metric-grid {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px;
  width: min(100%, 1120px); margin: 0 auto 28px;
}

.metric-card {
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  padding: 24px 16px; border: 1px solid var(--border-default); border-radius: var(--radius-lg);
  background: var(--surface-panel);
}

.metric-value {
  font-size: 28px; font-weight: 700; color: var(--text-strong); font-family: var(--font-heading);
}

.metric-label {
  font-size: 12px; color: var(--text-muted); font-weight: 600; text-transform: uppercase;
}

// ── 图表 ──

.chart-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 20px;
}

.chart-card {
  padding: 20px; border: 1px solid var(--border-default); border-radius: var(--radius-lg);
  background: var(--surface-panel);

  h3 {
    margin: 0 0 16px; color: var(--text-strong); font-size: 14px; font-weight: 650;
  }
}

.chart-wide {
  grid-column: 1 / -1;
}

.chart-container {
  width: 100%; height: 240px;
}

.chart-wide .chart-container {
  height: 280px;
}

.chart-empty {
  display: flex; align-items: center; justify-content: center;
  height: 200px; color: var(--text-disabled); font-size: 13px;
}

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

.empty-state {
  display: flex; align-items: center; justify-content: center;
  min-height: 400px;
}

.empty-hint { display: block; margin-top: 8px; color: var(--text-muted); font-size: 12px; }

@media (max-width: 768px) {
  #analyticsPage { padding: 28px 16px 56px; }

  .page-heading { flex-direction: column; align-items: flex-start; gap: 18px; }
  .page-heading h1 { font-size: 30px; }
  .heading-actions { width: 100%; justify-content: space-between; }

  .metric-grid { grid-template-columns: repeat(2, 1fr); gap: 12px; }
  .metric-value { font-size: 24px; }

  .chart-grid { grid-template-columns: 1fr; }
  .chart-wide { grid-column: auto; }
  .chart-container { height: 200px; }
}

@media (prefers-reduced-motion: reduce) {
  #analyticsPage :deep(*) { scroll-behavior: auto !important; transition-duration: 0.01ms !important; }
}
</style>