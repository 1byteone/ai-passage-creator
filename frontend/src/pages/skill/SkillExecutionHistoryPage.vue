<template>
  <div id="skillHistoryPage">
    <header class="page-heading">
      <div>
        <span class="page-kicker">AI 工具</span>
        <h1>Skill 执行历史</h1>
        <p>查看所有 Skill 的执行记录，恢复未完成的任务，或复用成功结果。</p>
      </div>
      <div class="heading-actions">
        <span class="record-count">{{ total }} 条记录</span>
        <a-button :loading="loading" @click="refreshData">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
      </div>
    </header>

    <!-- 筛选 -->
    <section class="filter-bar" aria-label="筛选条件">
      <span class="filter-label">技能</span>
      <a-select
        v-model:value="filterSkillName"
        allow-clear
        show-search
        placeholder="全部技能"
        style="width: 200px"
        :filter-option="filterOption"
        :options="skillOptions"
        @change="onFilterChange"
      />
      <span class="filter-label">状态</span>
      <a-select
        v-model:value="filterStatus"
        allow-clear
        placeholder="全部状态"
        style="width: 160px"
        :options="statusOptions"
        @change="onFilterChange"
      />
    </section>

    <!-- 操作反馈 -->
    <div v-if="operationNotice" class="page-feedback" aria-live="polite">
      <a-alert
        :type="operationNotice.type"
        show-icon
        closable
        :message="operationNotice.message"
        :description="operationNotice.description"
        @close="operationNotice = null"
      />
    </div>

    <section class="history-section" aria-labelledby="history-title">
      <div class="list-heading">
        <div>
          <span class="section-label">执行目录</span>
          <h2 id="history-title">{{ listTitle }}</h2>
        </div>
        <span class="page-range">{{ pageRange }}</span>
      </div>

      <div v-if="initialLoading" class="loading-state">
        <a-skeleton active :paragraph="{ rows: 6 }" />
      </div>

      <a-result
        v-else-if="initialError"
        class="result-state"
        status="error"
        title="暂时无法加载执行记录"
        :sub-title="loadError"
      >
        <template #extra>
          <a-button type="primary" :loading="loading" @click="refreshData">重新加载</a-button>
        </template>
      </a-result>

      <template v-else>
      <a-alert
        v-if="loadError && data.length"
        class="page-feedback"
        type="warning"
        show-icon
        closable
        message="刷新失败，当前仍显示上一次数据"
        :description="loadError"
        @close="loadError = ''"
      />

      <a-empty
        v-else-if="!data.length"
        class="empty-state"
        :description="hasFilters ? '没有符合当前条件的执行记录' : '还没有执行记录'"
      >
        <template v-if="!hasFilters">
          <RouterLink to="/skill">
            <a-button type="primary">去执行 Skill</a-button>
          </RouterLink>
        </template>
        <template v-else>
          <a-button @click="resetFilters">清除筛选</a-button>
        </template>
      </a-empty>

      <template v-else>
        <a-table
          class="desktop-table"
          :columns="columns"
          :data-source="data"
          :loading="loading"
          :pagination="false"
          :expand-icon-column-index="0"
          row-key="skillExecutionId"
          :expandable="{ expandedRowKeys, onExpand: handleExpand }"
          :scroll="{ x: 800 }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'skillName'">
              <span class="skill-name-cell">{{ displaySkillName(record.skillName) }}</span>
            </template>
            <template v-else-if="column.key === 'status'">
              <span class="status-badge" :class="statusClass(record.status)">
                {{ statusLabel(record.status) }}
              </span>
            </template>
            <template v-else-if="column.key === 'phase'">
              <span class="phase-text">{{ record.phase || '—' }}</span>
            </template>
            <template v-else-if="column.key === 'tokenUsage'">
              <span class="mono-text">{{ record.tokenUsage ? `${record.tokenUsage} tokens` : '—' }}</span>
            </template>
            <template v-else-if="column.key === 'duration'">
              <span class="mono-text">{{ formatDuration(record.durationMs) }}</span>
            </template>
            <template v-else-if="column.key === 'time'">
              <a-tooltip :title="formatTime(record.createTime)">
                <time class="time-text">{{ timeAgo(record.createTime) }}</time>
              </a-tooltip>
            </template>
            <template v-else-if="column.key === 'action'">
              <a-button
                v-if="isTerminal(record.status)"
                type="link"
                size="small"
                @click="toggleExpand(record)"
              >
                {{ expandedRowKeys.includes(record.skillExecutionId!) ? '收起' : '详情' }}
              </a-button>
              <RouterLink
                v-else
                :to="`/skill/${encodeURIComponent(record.skillName ?? '')}?executionId=${record.skillExecutionId}`"
              >
                <a-button type="link" size="small">恢复执行</a-button>
              </RouterLink>
            </template>
          </template>

          <!-- 展开行 -->
          <template #expandedRowRender="{ record }">
            <div v-if="expandedResults[record.skillExecutionId!]?.loading" class="expand-loading">
              <a-spin size="small" /> 加载详情中...
            </div>
            <div
              v-else-if="expandedResults[record.skillExecutionId!]?.error"
              class="expand-error"
            >
              加载失败：{{ expandedResults[record.skillExecutionId!]!.error }}
            </div>
            <div v-else-if="expandedResults[record.skillExecutionId!]?.result" class="expand-detail">
              <div class="expand-meta">
                <span v-if="record.tokenUsage">Token 消耗：<strong>{{ record.tokenUsage }}</strong></span>
                <span v-if="record.modelUsed">模型：<strong>{{ record.modelUsed }}</strong></span>
                <span v-if="record.durationMs">耗时：<strong>{{ formatDuration(record.durationMs) }}</strong></span>
              </div>

              <div v-if="record.status === 'FAILED'" class="expand-error-msg">
                <span class="error-label">错误信息</span>
                <pre>{{ record.errorMessage || '未知错误' }}</pre>
              </div>

              <div v-if="expandedResults[record.skillExecutionId!]!.result!.outputData" class="expand-output">
                <span class="error-label">输出结果</span>
                <pre>{{ JSON.stringify(expandedResults[record.skillExecutionId!]!.result!.outputData, null, 2) }}</pre>
              </div>

              <div class="expand-actions">
                <RouterLink
                  target="_blank"
                  :to="`/skill/${encodeURIComponent(record.skillName ?? '')}?executionId=${record.skillExecutionId}`"
                >
                  <a-button size="small">在新标签页打开</a-button>
                </RouterLink>
              </div>
            </div>
            <div v-else-if="!isTerminal(record.status)" class="expand-loading">
              <a-spin size="small" /> 此执行仍在进行中，
              <RouterLink :to="`/skill/${encodeURIComponent(record.skillName ?? '')}?executionId=${record.skillExecutionId}`">
                点击恢复执行
              </RouterLink>
            </div>
          </template>
        </a-table>

        <div class="pagination-bar">
          <span>共 {{ total }} 条记录</span>
          <a-pagination
            v-page-size-label
            :current="pageNum"
            :page-size="pageSize"
            :total="total"
            :show-size-changer="true"
            :page-size-options="['10', '20', '50']"
            :show-less-items="true"
            @change="doPageChange"
            @show-size-change="doPageSizeChange"
          />
        </div>
      </template>
      </template>
    </section>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import relativeTimePlugin from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/zh-cn'
import {
  Alert as AAlert,
  Empty as AEmpty,
  Pagination as APagination,
  Result as AResult,
  Select as ASelect,
  Skeleton as ASkeleton,
  Spin as ASpin,
  Table as ATable,
  Tooltip as ATooltip,
  type TableProps,
} from 'ant-design-vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { listSkills, listSkillExecutions, getSkillResult } from '@/api/skillController'
import { getSkillUiConfig } from '@/config/skill'
import { formatDuration } from '@/utils/date'

dayjs.extend(relativeTimePlugin)
dayjs.locale('zh-cn')

// ── 辅助 ──

const labelPageSizeControl = (element: HTMLElement) => {
  element
    .querySelector<HTMLElement>('.ant-pagination-options-size-changer [role="combobox"]')
    ?.setAttribute('aria-label', '每页显示记录数量')
}

const vPageSizeLabel = {
  mounted: labelPageSizeControl,
  updated: labelPageSizeControl,
}

const formatTime = (value?: string) => {
  if (!value || !dayjs(value).isValid()) return '时间未知'
  return dayjs(value).format('YYYY-MM-DD HH:mm:ss')
}

const timeAgo = (value?: string) => {
  if (!value || !dayjs(value).isValid()) return '—'
  return dayjs(value).fromNow()
}

const displaySkillName = (name?: string) => {
  if (!name) return '—'
  return getSkillUiConfig(name).shortTitle || getSkillUiConfig(name).title || name
}

const statusMap: Record<string, { label: string; cls: string }> = {
  PENDING: { label: '等待中', cls: 'status-pending' },
  RUNNING: { label: '执行中', cls: 'status-running' },
  AWAITING_CONFIRMATION: { label: '待确认', cls: 'status-awaiting' },
  SUCCESS: { label: '成功', cls: 'status-success' },
  FAILED: { label: '失败', cls: 'status-failed' },
}

const statusLabel = (status?: string) => statusMap[status ?? '']?.label ?? status ?? '—'

const statusClass = (status?: string) => statusMap[status ?? '']?.cls ?? ''

const isTerminal = (status?: string) => status === 'SUCCESS' || status === 'FAILED'

// ── 表格列 ──

const columns: TableProps['columns'] = [
  { title: '技能', key: 'skillName', width: 140 },
  { title: '状态', key: 'status', width: 100 },
  { title: '阶段', key: 'phase', width: 120 },
  { title: 'Token', key: 'tokenUsage', width: 100 },
  { title: '耗时', key: 'duration', width: 80 },
  { title: '时间', key: 'time', width: 120 },
  { title: '操作', key: 'action', width: 90 },
]

// ── 筛选 ──

const filterSkillName = ref<string | undefined>(undefined)
const filterStatus = ref<string | undefined>(undefined)

const skillOptions = ref<{ label: string; value: string }[]>([])

const statusOptions = [
  { label: '等待中', value: 'PENDING' },
  { label: '执行中', value: 'RUNNING' },
  { label: '待确认', value: 'AWAITING_CONFIRMATION' },
  { label: '成功', value: 'SUCCESS' },
  { label: '失败', value: 'FAILED' },
]

const filterOption = (input: string, option: { label: string; value: string }) => {
  return option.label.toLowerCase().includes(input.toLowerCase())
}

const hasFilters = computed(() => Boolean(filterSkillName.value) || Boolean(filterStatus.value))

const onFilterChange = () => {
  pageNum.value = 1
  void fetchData()
}

const resetFilters = () => {
  filterSkillName.value = undefined
  filterStatus.value = undefined
  pageNum.value = 1
  void fetchData()
}

const loadSkillOptions = async () => {
  try {
    const res = await listSkills()
    if (res.data.code === 0 && res.data.data) {
      skillOptions.value = res.data.data.map((s) => ({
        label: `${displaySkillName(s.name)} (${s.name})`,
        value: s.name,
      }))
    }
  } catch {
    // 静默失败
  }
}

// ── 列表状态 ──

const data = ref<API.SkillExecutionVO[]>([])
const total = ref(0)
const loading = ref(false)
const loadedOnce = ref(false)
const loadError = ref('')

interface OperationNotice {
  type: 'success' | 'error'
  message: string
  description: string
}
const operationNotice = ref<OperationNotice | null>(null)

const pageNum = ref(1)
const pageSize = ref(10)

// ── 展开行 ──

const expandedRowKeys = ref<string[]>([])

interface ExpandedResult {
  loading?: boolean
  result?: API.SkillResultResponse
  error?: string
}

const expandedResults = reactive<Record<string, ExpandedResult>>({})

const toggleExpand = (record: API.SkillExecutionVO) => {
  const id = record.skillExecutionId!
  if (expandedRowKeys.value.includes(id)) {
    expandedRowKeys.value = expandedRowKeys.value.filter((k) => k !== id)
  } else {
    expandedRowKeys.value.push(id)
    if (!expandedResults[id] && isTerminal(record.status)) {
      void loadDetail(record)
    }
  }
}

const handleExpand = (expanded: boolean, record: API.SkillExecutionVO) => {
  const id = record.skillExecutionId!
  if (expanded) {
    expandedRowKeys.value.push(id)
    if (!expandedResults[id] && isTerminal(record.status)) {
      void loadDetail(record)
    }
  } else {
    expandedRowKeys.value = expandedRowKeys.value.filter((k) => k !== id)
  }
}

const loadDetail = async (record: API.SkillExecutionVO) => {
  const id = record.skillExecutionId!
  expandedResults[id] = { loading: true }
  try {
    const res = await getSkillResult(id)
    if (res.data.code === 0 && res.data.data) {
      expandedResults[id] = { result: res.data.data }
    } else {
      expandedResults[id] = { error: res.data.message || '获取详情失败' }
    }
  } catch {
    expandedResults[id] = { error: '网络或服务暂时不可用' }
  }
}

// ── 数据加载 ──

let fetchSeq = 0

const fetchData = async () => {
  loading.value = true
  loadError.value = ''
  const seq = ++fetchSeq

  try {
    const res = await listSkillExecutions({
      skillName: filterSkillName.value || undefined,
      status: filterStatus.value || undefined,
      current: pageNum.value,
      pageSize: pageSize.value,
    })

    if (seq !== fetchSeq) return

    if (res.data.code !== 0 || !res.data.data) {
      throw new Error(res.data.message || '执行记录返回异常')
    }

    data.value = res.data.data.records ?? []
    total.value = res.data.data.totalRow ?? 0
  } catch (error) {
    if (seq !== fetchSeq) return
    console.error('获取 Skill 执行记录失败:', error)
    loadError.value =
      error instanceof Error ? error.message : '网络或服务暂时不可用，请稍后重试。'
  } finally {
    loading.value = false
    loadedOnce.value = true
  }
}

const refreshData = () => {
  operationNotice.value = null
  void fetchData()
}

const doPageChange = (page: number) => {
  pageNum.value = page
  void fetchData()
}

const doPageSizeChange = (_current: number, size: number) => {
  pageNum.value = 1
  pageSize.value = size
  void fetchData()
}

// ── 计算属性 ──

const initialLoading = computed(() => loading.value && !loadedOnce.value)
const initialError = computed(
  () => loadedOnce.value && Boolean(loadError.value) && data.value.length === 0,
)

const listTitle = computed(() => (hasFilters.value ? '筛选结果' : '全部执行记录'))

const pageRange = computed(() => {
  if (!total.value || !data.value.length) return '暂无记录'
  const start = (pageNum.value - 1) * pageSize.value + 1
  const end = Math.min(start + data.value.length - 1, total.value)
  return `${start}–${end} / ${total.value}`
})

// ── 生命周期 ──

onMounted(() => {
  void loadSkillOptions()
  void fetchData()
})
</script>

<style scoped lang="scss">
#skillHistoryPage {
  min-height: calc(100vh - 64px);
  padding: 40px 24px 72px;
  background: var(--surface-page);
  color: var(--text-body);
}

.page-heading,
.filter-bar,
.page-feedback,
.history-section {
  width: min(100%, 1120px);
  margin-right: auto;
  margin-left: auto;
}

.page-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 32px;
  margin-bottom: 28px;
}

.page-kicker,
.section-label {
  display: block;
  margin-bottom: 7px;
  color: var(--state-success-text);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.page-heading h1 {
  margin: 0 0 8px;
  color: var(--text-strong);
  font-family: var(--font-heading);
  font-size: 36px;
  font-weight: 700;
  letter-spacing: -0.03em;
}

.page-heading p {
  margin: 0;
  color: var(--text-subtle);
  font-size: 14px;
}

.heading-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.record-count,
.page-range {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 12px;
  white-space: nowrap;
}

// ── 筛选条 ──

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  padding: 16px 24px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  background: var(--surface-panel);
}

.filter-label {
  color: var(--text-subtle);
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;

  &:not(:first-child) {
    margin-left: 12px;
  }
}

.page-feedback {
  margin-bottom: 20px;
}

// ── 表格区域 ──

.history-section {
  overflow: hidden;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  background: var(--surface-panel);
}

.list-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  padding: 24px 24px 18px;
  border-bottom: 1px solid var(--border-default);
}

.list-heading h2 {
  margin: 0;
  color: var(--text-strong);
  font-family: var(--font-heading);
  font-size: 20px;
  font-weight: 650;
}

.loading-state,
.empty-state {
  min-height: 360px;
  padding: 48px 32px;
}

.result-state {
  min-height: 360px;
}

.desktop-table :deep(.ant-table) {
  border-radius: 0;
}

.desktop-table :deep(.ant-table-thead > tr > th) {
  padding: 13px 16px;
  border-bottom: 1px solid var(--border-default);
  background: var(--surface-muted);
  color: var(--text-subtle);
  font-size: 12px;
  font-weight: 700;
}

.desktop-table :deep(.ant-table-tbody > tr > td) {
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-subtle);
}

.desktop-table :deep(.ant-table-tbody > tr:hover > td) {
  background: var(--surface-page);
}

.skill-name-cell {
  color: var(--text-strong);
  font-size: 13px;
  font-weight: 600;
}

.phase-text {
  color: var(--text-subtle);
  font-size: 12px;
}

.mono-text {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
}

.time-text {
  color: var(--text-muted);
  font-size: 12px;
}

// ── 状态徽标 ──

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 650;
  white-space: nowrap;

  &::before {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    content: '';
  }
}

.status-success {
  color: var(--state-success-text);
  &::before { background: var(--color-success); }
}

.status-failed {
  color: var(--state-error-text);
  &::before { background: var(--color-error); }
}

.status-running {
  color: var(--state-info-text);
  &::before { background: var(--color-info); }
}

.status-pending {
  color: var(--text-disabled);
  &::before { background: var(--text-disabled); }
}

.status-awaiting {
  color: #d97706;
  &::before { background: #f59e0b; }
}

// ── 展开行 ──

.expand-loading,
.expand-error {
  padding: 12px 0;
  color: var(--text-muted);
  font-size: 13px;
}

.expand-error {
  color: var(--state-error-text);
}

.expand-detail {
  padding: 4px 0;
}

.expand-meta {
  display: flex;
  gap: 24px;
  margin-bottom: 12px;
  color: var(--text-muted);
  font-size: 12px;

  strong {
    color: var(--text-strong);
  }
}

.expand-error-msg {
  margin-bottom: 12px;

  pre {
    margin: 6px 0 0;
    padding: 8px 10px;
    border-radius: var(--radius-sm);
    background: var(--surface-muted);
    color: var(--state-error-text);
    font-family: var(--font-mono);
    font-size: 11px;
    white-space: pre-wrap;
    word-break: break-all;
  }
}

.error-label {
  display: block;
  margin-bottom: 4px;
  color: var(--text-subtle);
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
}

.expand-output {
  pre {
    margin: 6px 0 0;
    padding: 10px 12px;
    border-radius: var(--radius-sm);
    background: var(--surface-muted);
    font-family: var(--font-mono);
    font-size: 11px;
    max-height: 320px;
    overflow: auto;
    white-space: pre-wrap;
    word-break: break-all;
  }
}

.expand-actions {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--border-subtle);
}

// ── 分页 ──

.pagination-bar {
  display: flex;
  min-height: 68px;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 14px 24px;
  border-top: 1px solid var(--border-default);
  color: var(--text-muted);
  font-size: 12px;
}

// ── 响应式 ──

@media (max-width: 768px) {
  #skillHistoryPage {
    padding: 28px 16px 56px;
  }

  .page-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 18px;
  }

  .page-heading h1 {
    font-size: 30px;
  }

  .heading-actions {
    width: 100%;
    justify-content: space-between;
  }

  .filter-bar {
    flex-wrap: wrap;
  }

  .filter-bar :deep(.ant-select) {
    flex: 1;
    min-width: 120px;
  }

  .pagination-bar {
    align-items: flex-start;
    flex-direction: column;
    padding: 16px 20px 20px;
  }

  .pagination-bar :deep(.ant-pagination) {
    width: 100%;
  }

  .pagination-bar :deep(.ant-pagination-options) {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  #skillHistoryPage :deep(*) {
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
