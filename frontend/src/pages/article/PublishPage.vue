<template>
  <div id="publishPage">
    <header class="page-heading">
      <div>
        <nav class="breadcrumb">
          <RouterLink to="/article/list">文章列表</RouterLink>
          <span>/</span>
          <RouterLink :to="`/article/${taskId}`">{{ articleTitle || taskId }}</RouterLink>
          <span>/</span>
          <span class="current">发布管理</span>
        </nav>
        <span class="page-kicker">内容发布</span>
        <h1>{{ articleTitle || '发布管理' }}</h1>
        <p>管理文章的多平台发布排期，仅审批通过的文章可发布。</p>
      </div>
      <div class="heading-actions">
        <a-button type="primary" @click="openCreateModal">
          <template #icon><PlusOutlined /></template>新建排期
        </a-button>
      </div>
    </header>

    <div v-if="operationNotice" class="page-feedback" aria-live="polite">
      <a-alert :type="operationNotice.type" show-icon closable :message="operationNotice.message" :description="operationNotice.description" @close="operationNotice = null" />
    </div>

    <section class="publish-section" aria-labelledby="publish-title">
      <div class="list-heading">
        <div>
          <span class="section-label">发布排期</span>
          <h2 id="publish-title">{{ listTitle }}</h2>
        </div>
        <span v-if="schedules.length" class="count-badge">{{ schedules.length }} 项</span>
      </div>

      <div v-if="loading" class="loading-state">
        <a-skeleton active :paragraph="{ rows: 4 }" />
      </div>

      <a-result v-else-if="loadError" class="result-state" status="error" title="暂时无法加载" :sub-title="loadError">
        <template #extra>
          <a-button type="primary" :loading="loading" @click="refreshSchedules">重新加载</a-button>
        </template>
      </a-result>

      <a-empty v-else-if="!schedules.length" class="empty-state" description="还没有创建发布排期">
        <span class="empty-hint">文章审批通过后，可在此创建多平台发布排期。</span>
        <a-button type="primary" @click="openCreateModal">新建排期</a-button>
      </a-empty>

      <template v-else>
        <a-table
          class="desktop-table"
          :columns="columns"
          :data-source="schedules"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 700 }"
          row-key="id"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'platform'">
              <span class="platform-badge" :class="`platform-${record.platform}`">
                {{ platformLabel(record.platform) }}
              </span>
            </template>
            <template v-else-if="column.key === 'status'">
              <span class="status-badge" :class="statusClass(record.status)">
                {{ statusLabel(record.status) }}
              </span>
            </template>
            <template v-else-if="column.key === 'publishAt'">
              <span class="mono-text">{{ formatTime(record.publishAt) }}</span>
            </template>
            <template v-else-if="column.key === 'published'">
              <span v-if="record.publishedAt" class="mono-text">{{ formatTime(record.publishedAt) }}</span>
              <span v-else class="muted-text">—</span>
            </template>
            <template v-else-if="column.key === 'action'">
              <a-popconfirm
                v-if="record.status === 'SCHEDULED'"
                title="确定取消此排期？"
                ok-text="确认取消"
                cancel-text="关闭"
                ok-type="danger"
                @confirm="doCancel(record)"
              >
                <a-button type="link" danger :loading="cancellingId === record.id">取消排期</a-button>
              </a-popconfirm>
              <span v-else-if="record.status === 'PUBLISHED'" class="muted-text">已发布</span>
              <span v-else-if="record.status === 'CANCELLED'" class="muted-text">已取消</span>
              <span v-else-if="record.status === 'FAILED'" class="muted-text">失败</span>
              <span v-else class="muted-text">—</span>
            </template>
          </template>
        </a-table>
      </template>
    </section>

    <!-- 创建排期弹窗 -->
    <a-modal v-model:open="createModalOpen" title="新建发布排期" :confirm-loading="creating" ok-text="新建" cancel-text="取消" @ok="doCreate" @cancel="resetCreateForm">
      <a-form ref="createFormRef" layout="vertical" :model="createForm">
        <a-form-item label="目标平台" name="platform" :rules="[{ type: 'string' as const, required: true, message: '请选择平台', trigger: 'change' }]">
          <a-select v-model:value="createForm.platform" :options="platformOptions" />
        </a-form-item>
        <a-form-item label="发布时间" name="publishAt" :rules="[{ type: 'string' as const, required: true, message: '请选择发布时间', trigger: 'change' }]">
          <a-date-picker v-model:value="createForm.publishAt" show-time format="YYYY-MM-DD HH:mm" :disabled-date="disabledDate" style="width: 100%" placeholder="选择发布时间" />
        </a-form-item>
        <a-form-item label="方法论（可选）" name="methodologyName">
          <a-input v-model:value="createForm.methodologyName" placeholder="留空则使用文章默认" :maxlength="64" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, ref, reactive } from 'vue'
import { useRoute } from 'vue-router'
import dayjs from 'dayjs'
import type { Dayjs } from 'dayjs'
import { Alert as AAlert, DatePicker as ADatePicker, Empty as AEmpty, Form as AForm, FormItem as AFormItem, Modal as AModal, Popconfirm as APopconfirm, Select as ASelect, Skeleton as ASkeleton, Table as ATable, message, type TableProps } from 'ant-design-vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { getArticlePublish, schedulePublish, cancelPublish } from '@/api/publishController'
import { getArticle } from '@/api/articleController'

const route = useRoute()
const taskId = computed(() => route.params.taskId as string)

// ── 文章信息 ──
const articleTitle = ref('')
const loadArticle = async () => {
  if (!taskId.value) return
  try {
    const res = await getArticle({ taskId: taskId.value })
    if (res.data.code === 0 && res.data.data) {
      articleTitle.value = res.data.data.mainTitle || res.data.data.topic || ''
    }
  } catch {
    // 标题加载失败时保持 taskId 兜底展示，不影响主流程
  }
}

// ── 平台 ──
const platformOptions = [
  { label: '微信公众号', value: 'wechat' },
  { label: '抖音', value: 'douyin' },
  { label: '小红书', value: 'xiaohongshu' },
]

const platformLabel = (p?: string) => platformOptions.find(o => o.value === p)?.label || p || '—'

// ── 状态 ──
const statusMap: Record<string, { label: string; cls: string }> = {
  SCHEDULED: { label: '待发布', cls: 'status-scheduled' },
  PUBLISHED: { label: '已发布', cls: 'status-published' },
  CANCELLED: { label: '已取消', cls: 'status-cancelled' },
  FAILED: { label: '失败', cls: 'status-failed' },
}

const statusLabel = (s?: string) => statusMap[s ?? '']?.label ?? s ?? '—'
const statusClass = (s?: string) => statusMap[s ?? '']?.cls ?? ''

// ── 表格列 ──
const columns: TableProps<API.PublishSchedule>['columns'] = [
  { title: '平台', key: 'platform', width: 120 },
  { title: '状态', key: 'status', width: 100 },
  { title: '计划时间', key: 'publishAt', width: 150 },
  { title: '发布时间', key: 'published', width: 150 },
  { title: '操作', key: 'action', width: 120, align: 'right' },
]

// ── 列表 ──
const schedules = ref<API.PublishSchedule[]>([])
const loading = ref(false)
const loadError = ref('')

interface OperationNotice { type: 'success' | 'error'; message: string; description?: string }
const operationNotice = ref<OperationNotice | null>(null)

const listTitle = computed(() => schedules.value.length ? '全部排期' : '暂无排期')

const formatTime = (v?: string) => {
  if (!v || !dayjs(v).isValid()) return '—'
  return dayjs(v).format('YYYY-MM-DD HH:mm')
}

const disabledDate = (current: Dayjs) => current.isBefore(dayjs().startOf('day'))

const fetchSchedules = async () => {
  if (!taskId.value) return
  loading.value = true
  loadError.value = ''
  try {
    const res = await getArticlePublish(taskId.value)
    if (res.data.code !== 0) throw new Error(res.data.message || '加载失败')
    schedules.value = res.data.data ?? []
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : '网络或服务暂时不可用'
  } finally {
    loading.value = false
  }
}

const refreshSchedules = () => { operationNotice.value = null; void fetchSchedules() }

// ── 取消 ──
const cancellingId = ref<number | null>(null)
const doCancel = async (record: API.PublishSchedule) => {
  if (!record.id || cancellingId.value) return
  cancellingId.value = record.id
  try {
    const res = await cancelPublish(record.id)
    if (res.data.code !== 0) throw new Error(res.data.message || '取消失败')
    operationNotice.value = { type: 'success', message: '排期已取消' }
    await fetchSchedules()
  } catch (e) {
    operationNotice.value = { type: 'error', message: '取消失败', description: e instanceof Error ? e.message : '' }
  } finally {
    cancellingId.value = null
  }
}

// ── 创建 ──
const createModalOpen = ref(false)
const creating = ref(false)
const createFormRef = ref()

const createForm = reactive({
  platform: 'wechat' as string,
  methodologyName: '',
  publishAt: undefined as Dayjs | undefined,
})

const openCreateModal = () => {
  resetCreateForm()
  createModalOpen.value = true
}

const resetCreateForm = () => {
  createForm.platform = 'wechat'
  createForm.methodologyName = ''
  createForm.publishAt = undefined
  createFormRef.value?.clearValidate()
}

const doCreate = async () => {
  try { await createFormRef.value?.validate() } catch { return }
  if (!taskId.value || creating.value || !createForm.publishAt) return
  creating.value = true
  try {
    const params: API.PublishScheduleRequest = {
      taskId: taskId.value,
      platform: createForm.platform,
      publishAt: createForm.publishAt.format('YYYY-MM-DDTHH:mm:ss'),
    }
    if (createForm.methodologyName.trim()) params.methodologyName = createForm.methodologyName.trim()

    const res = await schedulePublish(params)
    if (res.data.code !== 0) throw new Error(res.data.message || '创建失败')

    createModalOpen.value = false
    operationNotice.value = { type: 'success', message: '排期已创建' }
    await fetchSchedules()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '创建失败，请稍后重试')
  } finally {
    creating.value = false
  }
}

// ── 生命周期 ──
onMounted(() => { void loadArticle(); void fetchSchedules() })
</script>

<style scoped lang="scss">
#publishPage {
  min-height: calc(100vh - 64px);
  padding: 40px 24px 72px;
  background: var(--surface-page);
  color: var(--text-body);
}

.page-heading, .page-feedback, .publish-section {
  width: min(100%, 1120px);
  margin-right: auto; margin-left: auto;
}

.page-heading {
  display: flex; align-items: flex-end; justify-content: space-between; gap: 32px; margin-bottom: 28px;
}

.breadcrumb {
  display: flex; align-items: center; gap: 6px; margin-bottom: 10px; color: var(--text-muted); font-size: 12px;
  a { color: var(--text-subtle); text-decoration: none; &:hover { color: var(--color-primary); } }
  .current { color: var(--text-strong); font-weight: 600; }
}

.page-kicker, .section-label { display: block; margin-bottom: 7px; color: var(--state-success-text); font-size: 12px; font-weight: 700; letter-spacing: 0.08em; }

.page-heading h1 { margin: 0 0 8px; color: var(--text-strong); font-family: var(--font-heading); font-size: 36px; font-weight: 700; letter-spacing: -0.03em; }
.page-heading p { margin: 0; color: var(--text-subtle); font-size: 14px; }

.heading-actions { display: flex; align-items: center; gap: 12px; }

.count-badge { color: var(--text-muted); font-family: var(--font-mono); font-size: 12px; white-space: nowrap; }

.publish-section {
  overflow: hidden; border: 1px solid var(--border-default); border-radius: var(--radius-lg); background: var(--surface-panel);
}

.list-heading {
  display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 24px 24px 18px; border-bottom: 1px solid var(--border-default);
  h2 { margin: 0; color: var(--text-strong); font-family: var(--font-heading); font-size: 20px; font-weight: 650; }
}

.loading-state, .empty-state { min-height: 200px; padding: 48px 32px; }
.result-state { min-height: 200px; }
.empty-hint { display: block; margin-top: 8px; color: var(--text-muted); font-size: 12px; }

.page-feedback { margin-bottom: 20px; }

.desktop-table :deep(.ant-table) { border-radius: 0; }
.desktop-table :deep(.ant-table-thead > tr > th) { padding: 13px 16px; border-bottom: 1px solid var(--border-default); background: var(--surface-muted); color: var(--text-subtle); font-size: 12px; font-weight: 700; }
.desktop-table :deep(.ant-table-tbody > tr > td) { padding: 14px 16px; border-bottom: 1px solid var(--border-subtle); }
.desktop-table :deep(.ant-table-tbody > tr:hover > td) { background: var(--surface-page); }

.platform-badge {
  display: inline-flex; padding: 2px 10px; border-radius: 100px; font-size: 12px; font-weight: 600;
  &.platform-wechat { background: #dbeafe; color: #1e40af; }
  &.platform-douyin { background: #fce7f3; color: #9d174d; }
  &.platform-xiaohongshu { background: #fef3c7; color: #92400e; }
}

.status-badge {
  display: inline-flex; align-items: center; gap: 6px; font-size: 12px; font-weight: 650;
  &::before { width: 7px; height: 7px; border-radius: 50%; content: ''; }
  &.status-scheduled { color: #2563eb; &::before { background: #3b82f6; } }
  &.status-published { color: var(--state-success-text); &::before { background: var(--color-success); } }
  &.status-cancelled { color: var(--text-muted); &::before { background: var(--text-disabled); } }
  &.status-failed { color: var(--state-error-text); &::before { background: var(--color-error); } }
}

.mono-text { color: var(--text-muted); font-family: var(--font-mono); font-size: 11px; }
.muted-text { color: var(--text-disabled); font-size: 12px; }

@media (max-width: 768px) {
  #publishPage { padding: 28px 16px 56px; }
  .page-heading { flex-direction: column; align-items: flex-start; gap: 18px; }
  .page-heading h1 { font-size: 30px; }
  .heading-actions { width: 100%; }
}

@media (prefers-reduced-motion: reduce) {
  #publishPage :deep(*) { scroll-behavior: auto !important; transition-duration: 0.01ms !important; }
}
</style>