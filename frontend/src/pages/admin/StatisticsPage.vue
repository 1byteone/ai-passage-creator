<template>
  <section class="statistics-page" aria-labelledby="statistics-title">
    <header class="page-heading">
      <div>
        <p>运营后台</p>
        <h1 id="statistics-title">数据分析</h1>
        <span>查看内容生产效率、用户构成和配额消耗。</span>
      </div>
      <a-button :loading="loading" :disabled="loading" @click="loadData">
        <template #icon><ReloadOutlined /></template>
        {{ loading ? '正在刷新' : '刷新数据' }}
      </a-button>
    </header>

    <div v-if="errorMessage && !stats" class="page-state" role="alert">
      <a-result status="warning" title="统计数据暂时不可用" :sub-title="errorMessage">
        <template #extra>
          <a-button type="primary" @click="loadData">重新加载</a-button>
        </template>
      </a-result>
    </div>

    <template v-else>
      <div v-if="errorMessage" class="stale-notice" role="status">
        <span>刷新失败，当前仍显示上一次成功加载的数据。</span>
        <a-button size="small" @click="loadData">重试</a-button>
      </div>

      <section class="metric-section" aria-labelledby="metric-title">
        <div class="section-heading">
          <div>
            <p>关键指标</p>
            <h2 id="metric-title">创作运行概览</h2>
          </div>
          <span>{{ updatedAtLabel }}</span>
        </div>

        <div class="metric-grid" :aria-busy="loading">
          <article v-for="metric in metrics" :key="metric.label" class="metric">
            <span>{{ metric.label }}</span>
            <strong>{{ metric.value }}</strong>
            <small>{{ metric.detail }}</small>
          </article>
        </div>
      </section>

      <div class="insight-grid">
        <ChartPanel
          title="创作数量趋势"
          title-id="trend-chart-title"
          range="今日 / 本周 / 本月 / 累计"
          :summary="trendSummary"
          :loading="loading && !stats"
          :empty="totalCount === 0"
          :error="stats ? '' : errorMessage"
          @retry="loadData"
        >
          <div ref="trendChartRef" class="chart" role="img" :aria-label="trendSummary"></div>
          <dl class="data-summary">
            <div v-for="item in trendData" :key="item.label">
              <dt>{{ item.label }}</dt>
              <dd>{{ item.value }}</dd>
            </div>
          </dl>
        </ChartPanel>

        <ChartPanel
          title="用户构成"
          title-id="user-chart-title"
          range="当前用户快照"
          :summary="userSummary"
          :loading="loading && !stats"
          :empty="totalUserCount === 0"
          :error="stats ? '' : errorMessage"
          @retry="loadData"
        >
          <div ref="userChartRef" class="chart" role="img" :aria-label="userSummary"></div>
          <dl class="data-summary">
            <div v-for="item in userData" :key="item.label">
              <dt>{{ item.label }}</dt>
              <dd>{{ item.value }}</dd>
            </div>
          </dl>
        </ChartPanel>
      </div>

      <section class="operations" aria-labelledby="operations-title">
        <div class="section-heading">
          <div>
            <p>运行效率</p>
            <h2 id="operations-title">性能与资源</h2>
          </div>
        </div>
        <dl class="operation-list">
          <div>
            <dt>平均生成耗时</dt>
            <dd>{{ formatDuration(stats?.avgDurationMs ?? 0) }}</dd>
            <small>统计范围内单次创作的平均处理时间</small>
          </div>
          <div>
            <dt>累计配额消耗</dt>
            <dd>{{ formatNumber(stats?.quotaUsed ?? 0) }}</dd>
            <small>后端返回的真实累计使用量，不推算剩余配额</small>
          </div>
          <div>
            <dt>创作成功率</dt>
            <dd>{{ formatPercent(stats?.successRate ?? 0) }}</dd>
            <small>{{ successSummary }}</small>
          </div>
        </dl>
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { BarChart, PieChart, type BarSeriesOption, type PieSeriesOption } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TooltipComponent,
  type GridComponentOption,
  type LegendComponentOption,
  type TooltipComponentOption,
} from 'echarts/components'
import { init, use, type ComposeOption, type ECharts } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import ChartPanel from '@/components/ChartPanel.vue'
import { getStatistics } from '@/api/statisticsController'

use([BarChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

type StatisticsChartOption = ComposeOption<
  | BarSeriesOption
  | PieSeriesOption
  | GridComponentOption
  | LegendComponentOption
  | TooltipComponentOption
>

const loading = ref(false)
const stats = ref<API.StatisticsVO | null>(null)
const errorMessage = ref('')
const updatedAt = ref<Date | null>(null)
const trendChartRef = ref<HTMLElement>()
const userChartRef = ref<HTMLElement>()
let trendChart: ECharts | null = null
let userChart: ECharts | null = null

const totalCount = computed(() => stats.value?.totalCount ?? 0)
const totalUserCount = computed(() => stats.value?.totalUserCount ?? 0)
const trendData = computed(() => [
  { label: '今日', value: stats.value?.todayCount ?? 0 },
  { label: '本周', value: stats.value?.weekCount ?? 0 },
  { label: '本月', value: stats.value?.monthCount ?? 0 },
  { label: '累计', value: totalCount.value },
])
const otherUserCount = computed(() =>
  Math.max(
    0,
    totalUserCount.value -
      (stats.value?.activeUserCount ?? 0) -
      (stats.value?.vipUserCount ?? 0),
  ),
)
const userData = computed(() => [
  { label: 'VIP 用户', value: stats.value?.vipUserCount ?? 0 },
  { label: '本周活跃', value: stats.value?.activeUserCount ?? 0 },
  { label: '其他用户', value: otherUserCount.value },
])
const metrics = computed(() => [
  {
    label: '今日创作',
    value: formatNumber(stats.value?.todayCount ?? 0),
    detail: '今天发起的创作任务',
  },
  {
    label: '本周创作',
    value: formatNumber(stats.value?.weekCount ?? 0),
    detail: '本周累计创作任务',
  },
  {
    label: '本月创作',
    value: formatNumber(stats.value?.monthCount ?? 0),
    detail: '本月累计创作任务',
  },
  {
    label: '成功率',
    value: formatPercent(stats.value?.successRate ?? 0),
    detail: '创作任务成功完成比例',
  },
])
const trendSummary = computed(() =>
  totalCount.value
    ? `累计完成 ${formatNumber(totalCount.value)} 次创作，其中本月 ${formatNumber(stats.value?.monthCount ?? 0)} 次、本周 ${formatNumber(stats.value?.weekCount ?? 0)} 次。`
    : '当前还没有可用于分析的创作记录。',
)
const userSummary = computed(() =>
  totalUserCount.value
    ? `当前共有 ${formatNumber(totalUserCount.value)} 位用户，其中本周活跃 ${formatNumber(stats.value?.activeUserCount ?? 0)} 位，VIP 用户 ${formatNumber(stats.value?.vipUserCount ?? 0)} 位。`
    : '当前还没有可用于分析的用户记录。',
)
const successSummary = computed(() => {
  const rate = stats.value?.successRate ?? 0
  if (rate >= 95) return '任务完成情况稳定'
  if (rate >= 80) return '建议继续关注失败任务原因'
  return '成功率偏低，建议优先排查执行日志'
})
const updatedAtLabel = computed(() =>
  updatedAt.value
    ? `更新于 ${updatedAt.value.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`
    : '等待首次加载',
)

const formatNumber = (value: number) => new Intl.NumberFormat('zh-CN').format(value)
const formatPercent = (value: number) => `${value.toFixed(1)}%`
const formatDuration = (ms: number) =>
  ms < 1000 ? `${ms} 毫秒` : `${(ms / 1000).toFixed(1)} 秒`

const loadData = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await getStatistics()
    if (response.data.code !== 0 || !response.data.data) {
      throw new Error(response.data.message || '服务未返回统计数据')
    }
    stats.value = response.data.data
    updatedAt.value = new Date()
    await nextTick()
    renderCharts()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '请稍后重试'
  } finally {
    loading.value = false
  }
}

const renderCharts = () => {
  renderTrendChart()
  renderUserChart()
}

const renderTrendChart = () => {
  if (!trendChartRef.value || !stats.value || totalCount.value === 0) return
  trendChart ||= init(trendChartRef.value)
  const option: StatisticsChartOption = {
    tooltip: { trigger: 'axis' },
    grid: { left: 10, right: 10, top: 18, bottom: 8, containLabel: true },
    xAxis: {
      type: 'category',
      data: trendData.value.map((item) => item.label),
      axisLine: { lineStyle: { color: '#cbd5e1' } },
      axisLabel: { color: '#64748b' },
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: '#f1f5f9' } },
      axisLabel: { color: '#64748b' },
    },
    series: [
      {
        name: '创作数量',
        type: 'bar',
        data: trendData.value.map((item) => item.value),
        itemStyle: { color: '#22c55e', borderRadius: [4, 4, 0, 0] },
        barMaxWidth: 44,
      },
    ],
  }
  trendChart.setOption(option, true)
}

const renderUserChart = () => {
  if (!userChartRef.value || !stats.value || totalUserCount.value === 0) return
  userChart ||= init(userChartRef.value)
  const option: StatisticsChartOption = {
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    legend: { bottom: 0, textStyle: { color: '#64748b' } },
    series: [
      {
        name: '用户构成',
        type: 'pie',
        radius: ['46%', '70%'],
        center: ['50%', '44%'],
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        data: [
          { value: stats.value.vipUserCount ?? 0, name: 'VIP 用户', itemStyle: { color: '#22c55e' } },
          { value: stats.value.activeUserCount ?? 0, name: '本周活跃', itemStyle: { color: '#3b82f6' } },
          { value: otherUserCount.value, name: '其他用户', itemStyle: { color: '#94a3b8' } },
        ],
      },
    ],
  }
  userChart.setOption(option, true)
}

const handleResize = () => {
  trendChart?.resize()
  userChart?.resize()
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  userChart?.dispose()
})
</script>

<style scoped>
.statistics-page {
  min-height: calc(100dvh - 64px);
  padding: 42px 20px 72px;
  background: var(--surface-page);
}

.page-heading,
.metric-section,
.insight-grid,
.operations,
.page-state,
.stale-notice {
  width: min(1240px, 100%);
  margin-inline: auto;
}

.page-heading,
.section-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
}

.page-heading {
  margin-bottom: 30px;
}

.page-heading p,
.section-heading p {
  margin: 0 0 5px;
  color: var(--color-primary-dark);
  font-size: 13px;
  font-weight: 600;
}

.page-heading h1 {
  margin: 0 0 8px;
  font-size: 30px;
}

.page-heading span,
.section-heading > span {
  color: var(--text-muted);
  font-size: 13px;
}

.page-state {
  min-height: 440px;
  display: grid;
  place-items: center;
  border: 1px solid var(--border-default);
  background: var(--surface-panel);
}

.stale-notice {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
  padding: 12px 16px;
  border: 1px solid #fde68a;
  border-radius: var(--radius-md);
  background: var(--state-warning-bg);
  color: var(--state-warning-text);
  font-size: 13px;
}

.metric-section,
.operations {
  padding: 24px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--surface-panel);
}

.section-heading {
  margin-bottom: 20px;
}

.section-heading h2 {
  margin: 0;
  font-size: 18px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border-top: 1px solid var(--border-default);
}

.metric {
  min-width: 0;
  padding: 22px 20px 4px;
  border-right: 1px solid var(--border-default);
}

.metric:first-child {
  padding-left: 0;
}

.metric:last-child {
  border-right: 0;
}

.metric span,
.operation-list dt {
  color: var(--text-muted);
  font-size: 12px;
}

.metric strong {
  display: block;
  margin: 6px 0;
  color: var(--text-strong);
  font-size: 30px;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.metric small,
.operation-list small {
  color: var(--text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.insight-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  margin-top: 20px;
}

.chart {
  width: 100%;
  height: 280px;
}

.data-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin: 12px 0 0;
  padding-top: 14px;
  border-top: 1px solid var(--border-subtle);
}

.data-summary div {
  text-align: center;
}

.data-summary dt {
  color: var(--text-muted);
  font-size: 11px;
}

.data-summary dd {
  margin: 3px 0 0;
  color: var(--text-body);
  font-size: 14px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.operations {
  margin-top: 20px;
}

.operation-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0;
  margin: 0;
  border-top: 1px solid var(--border-default);
}

.operation-list > div {
  padding: 20px;
  border-right: 1px solid var(--border-default);
}

.operation-list > div:first-child {
  padding-left: 0;
}

.operation-list > div:last-child {
  border-right: 0;
}

.operation-list dd {
  margin: 6px 0;
  color: var(--text-strong);
  font-size: 24px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 900px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .metric {
    border-bottom: 1px solid var(--border-default);
  }

  .metric:nth-child(2) {
    border-right: 0;
  }

  .metric:nth-child(3),
  .metric:nth-child(4) {
    border-bottom: 0;
  }

  .metric:nth-child(3) {
    padding-left: 0;
  }

  .insight-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 600px) {
  .statistics-page {
    padding: 28px 16px 56px;
  }

  .page-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .page-heading h1 {
    font-size: 26px;
  }

  .page-heading .ant-btn {
    width: 100%;
  }

  .metric-section,
  .operations {
    padding: 20px 16px;
  }

  .metric-grid,
  .operation-list {
    grid-template-columns: 1fr;
  }

  .metric,
  .metric:first-child,
  .metric:nth-child(3),
  .operation-list > div,
  .operation-list > div:first-child {
    padding: 16px 0;
    border-right: 0;
    border-bottom: 1px solid var(--border-default);
  }

  .metric:last-child,
  .operation-list > div:last-child {
    border-bottom: 0;
  }

  .metric strong {
    font-size: 26px;
  }

  .chart {
    height: 240px;
  }

  .data-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    row-gap: 12px;
  }

  .stale-notice {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
