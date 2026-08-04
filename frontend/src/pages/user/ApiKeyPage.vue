<template>
  <div id="apiKeyPage">
    <header class="page-heading">
      <div>
        <span class="page-kicker">开发者工具</span>
        <h1>API Key 管理</h1>
        <p>创建和管理 API 访问凭证，用于外部程序调用平台接口。</p>
      </div>
      <div class="heading-actions">
        <span class="key-count">{{ total }} 个 Key</span>
        <a-button type="primary" @click="openCreateModal">
          <template #icon><PlusOutlined /></template>
          创建 Key
        </a-button>
      </div>
    </header>

    <!-- Admin 用户筛选 -->
    <section v-if="isAdmin" class="filter-bar" aria-label="用户筛选">
      <span class="filter-label">归属用户</span>
      <a-select
        v-model:value="filterUserId"
        allow-clear
        show-search
        placeholder="全部（本人）"
        style="width: 240px"
        :filter-option="filterUserOption"
        :options="userOptions"
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

    <section class="key-section" aria-labelledby="key-list-title">
      <div class="list-heading">
        <div>
          <span class="section-label">凭证目录</span>
          <h2 id="key-list-title">全部 Key</h2>
        </div>
        <span class="page-range">{{ pageRange }}</span>
      </div>

      <div v-if="initialLoading" class="loading-state" aria-live="polite">
        <a-skeleton active :paragraph="{ rows: 6 }" />
      </div>

      <a-result
        v-else-if="initialError"
        class="result-state"
        status="error"
        title="暂时无法加载 API Key"
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
        :description="emptyDescription"
      >
        <a-button v-if="!filterUserId" type="primary" @click="openCreateModal">创建第一个 Key</a-button>
        <a-button v-else @click="filterUserId = undefined; onFilterChange()">清除用户筛选</a-button>
      </a-empty>

      <template v-else>
        <a-table
          class="desktop-table"
          :columns="columns"
          :data-source="data"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 1000 }"
          row-key="id"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'">
              <span class="key-name">{{ record.name }}</span>
            </template>
            <template v-else-if="column.key === 'prefix'">
              <code class="key-prefix-code">{{ record.apiKeyPrefix }}</code>
              <a-button
                type="link"
                size="small"
                class="copy-btn"
                aria-label="复制 Key 前缀"
                @click="copyPrefix(record.apiKeyPrefix)"
              >
                <CopyOutlined />
              </a-button>
            </template>
            <template v-else-if="column.key === 'created'">
              <time class="time-text" :datetime="record.createTime">
                {{ formatDateTime(record.createTime, undefined, '时间未知') }}
              </time>
            </template>
            <template v-else-if="column.key === 'lastUsed'">
              <time v-if="record.lastUsedAt" class="time-text" :datetime="record.lastUsedAt">
                {{ formatDateTime(record.lastUsedAt, undefined, '时间未知') }}
              </time>
              <span v-else class="muted-text">—</span>
            </template>
            <template v-else-if="column.key === 'expires'">
              <span v-if="!record.expiresAt" class="muted-text">永不过期</span>
              <time
                v-else
                class="time-text"
                :class="{ 'expired-text': isExpired(record.expiresAt) }"
                :datetime="record.expiresAt"
              >
                {{ formatDateTime(record.expiresAt, undefined, '时间未知') }}
              </time>
            </template>
            <template v-else-if="column.key === 'status'">
              <span class="status-badge" :class="statusClass(record)">
                {{ statusLabel(record) }}
              </span>
            </template>
            <template v-else-if="column.key === 'action'">
              <a-popconfirm
                title="确定吊销此 API Key？"
                description="吊销后使用此 Key 的请求将立即失效，且不可恢复。"
                ok-text="吊销"
                cancel-text="取消"
                ok-type="danger"
                @confirm="doRevoke(record)"
              >
                <a-button type="link" danger :loading="revokingId === record.id">吊销</a-button>
              </a-popconfirm>
            </template>
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

    <!-- 创建 Key 弹窗 -->
    <a-modal
      v-model:open="createModalOpen"
      title="创建 API Key"
      :confirm-loading="creating"
      ok-text="创建"
      cancel-text="取消"
      @ok="doCreate"
      @cancel="resetCreateForm"
    >
      <a-form
        ref="createFormRef"
        layout="vertical"
        :model="createForm"
      >
        <a-form-item label="名称" name="name" :rules="[{ required: true, message: '请输入 Key 名称' }, { max: 64, message: '名称最长 64 字符' }]">
          <a-input
            v-model:value="createForm.name"
            :maxlength="64"
            show-count
            placeholder="例如：我的博客发布工具"
          />
        </a-form-item>
        <a-form-item v-if="isAdmin" label="归属用户">
          <a-select
            v-model:value="createForm.userId"
            allow-clear
            show-search
            placeholder="留空则为本人创建"
            :filter-option="filterUserOption"
            :options="userOptions"
          />
        </a-form-item>
        <a-form-item label="过期时间" name="expiresAt">
          <a-date-picker
            v-model:value="createForm._expiresAt"
            show-time
            format="YYYY-MM-DD HH:mm:ss"
            placeholder="留空则永不过期"
            :disabled-date="disabledDate"
            style="width: 100%"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 明文展示弹窗 -->
    <a-modal
      v-model:open="secretModalOpen"
      title="API Key 创建成功"
      :footer="null"
      :closable="false"
      :mask-closable="false"
      :keyboard="false"
    >
      <a-alert
        type="warning"
        show-icon
        message="请立即复制并安全保存"
        description="此 Key 仅显示一次，关闭后无法再次查看明文。请将其保存到安全的密码管理器中。"
        style="margin-bottom: 16px"
      />
      <div class="secret-display">
        <a-input-password
          :value="newSecret"
          readonly
          :visibility-toggle="true"
          class="secret-input"
        />
        <a-button type="primary" @click="copySecret">
          <template #icon><CopyOutlined /></template>
          复制
        </a-button>
      </div>
      <div class="secret-meta">
        <span>前缀：<code>{{ newSecretPrefix }}</code></span>
        <span v-if="newSecretExpires">过期：{{ newSecretExpires }}</span>
        <span v-else>永不过期</span>
      </div>
      <div class="secret-confirm">
        <a-button type="primary" block size="large" @click="confirmSaved">
          我已安全保存
        </a-button>
      </div>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import type { Dayjs } from 'dayjs'
import {
  Alert as AAlert,
  DatePicker as ADatePicker,
  Empty as AEmpty,
  Form as AForm,
  FormItem as AFormItem,
  Input as AInput,
  InputPassword as AInputPassword,
  Modal as AModal,
  Pagination as APagination,
  Popconfirm as APopconfirm,
  Result as AResult,
  Select as ASelect,
  Skeleton as ASkeleton,
  Table as ATable,
  type TableProps,
} from 'ant-design-vue'
import { CopyOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { createApiKey, listApiKeys, revokeApiKey } from '@/api/apikeyController'
import { listUserVoByPage } from '@/api/userController'
import { useLoginUserStore } from '@/stores/loginUser'
import { formatDateTime } from '@/utils/date'
import type { OperationNotice } from '@/types/operationNotice'

// ── 辅助 ──

const labelPageSizeControl = (element: HTMLElement) => {
  element
    .querySelector<HTMLElement>('.ant-pagination-options-size-changer [role="combobox"]')
    ?.setAttribute('aria-label', '每页显示 Key 数量')
}

const vPageSizeLabel = {
  mounted: labelPageSizeControl,
  updated: labelPageSizeControl,
}

const isExpired = (expiresAt: string) => {
  return dayjs(expiresAt).isBefore(dayjs())
}

const statusLabel = (record: API.ApiKeyVO) => {
  if (record.expiresAt && isExpired(record.expiresAt)) return '已过期'
  return '活跃'
}

const statusClass = (record: API.ApiKeyVO) => {
  if (record.expiresAt && isExpired(record.expiresAt)) return 'status-expired'
  return 'status-active'
}

// ── 表格列 ──

const columns: TableProps<API.ApiKeyVO>['columns'] = [
  { title: '名称', key: 'name', width: 200 },
  { title: 'Key 前缀', key: 'prefix', width: 240 },
  { title: '创建时间', key: 'created', width: 150 },
  { title: '最后使用', key: 'lastUsed', width: 150 },
  { title: '过期时间', key: 'expires', width: 150 },
  { title: '状态', key: 'status', width: 90 },
  { title: '操作', key: 'action', width: 90, align: 'right' },
]

// ── 登录用户 ──

const loginUserStore = useLoginUserStore()
const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')

// ── 列表状态 ──

const data = ref<API.ApiKeyVO[]>([])
const total = ref(0)
const loading = ref(false)
const loadedOnce = ref(false)
const loadError = ref('')
const operationNotice = ref<OperationNotice | null>(null)
const pageNum = ref(1)
const pageSize = ref(10)

// ── Admin 用户筛选 ──

const filterUserId = ref<number | undefined>(undefined)
const userOptions = ref<{ label: string; value: number }[]>([])

const filterUserOption = (input: string, option: { label: string; value: number }) => {
  return option.label.toLowerCase().includes(input.toLowerCase())
}

const loadUserOptions = async () => {
  if (!isAdmin.value) return
  try {
    const res = await listUserVoByPage({ current: 1, pageSize: 200 })
    if (res.data.code === 0 && res.data.data?.records) {
      userOptions.value = res.data.data.records.map((u) => ({
        label: `${u.userName ?? '未命名'} (${u.userAccount ?? u.id})`,
        value: Number(u.id),
      }))
    }
  } catch {
    // 静默失败，不影响主流程
  }
}

const onFilterChange = () => {
  pageNum.value = 1
  operationNotice.value = null
  void fetchData()
}

// ── 数据加载 ──

let fetchSeq = 0

const fetchData = async () => {
  loading.value = true
  loadError.value = ''
  const seq = ++fetchSeq

  try {
    const res = await listApiKeys({
      userId: filterUserId.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })

    if (seq !== fetchSeq) return

    if (res.data.code !== 0 || !res.data.data) {
      throw new Error(res.data.message || 'API Key 数据返回异常')
    }

    data.value = res.data.data.records ?? []
    total.value = res.data.data.totalRow ?? 0
  } catch (error) {
    if (seq !== fetchSeq) return
    console.error('获取 API Key 列表失败:', error)
    loadError.value = error instanceof Error ? error.message : '网络或服务暂时不可用，请稍后重试。'
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

const pageRange = computed(() => {
  if (!total.value || !data.value.length) return '暂无记录'
  const start = (pageNum.value - 1) * pageSize.value + 1
  const end = Math.min(start + data.value.length - 1, total.value)
  return `${start}–${end} / ${total.value}`
})

const emptyDescription = computed(() => {
  if (filterUserId.value) return '该用户还没有任何 API Key'
  return '还没有创建任何 API Key'
})

// ── 复制前缀 ──

const copyPrefix = async (prefix?: string) => {
  if (!prefix) return
  try {
    await navigator.clipboard.writeText(prefix)
    const { default: message } = await import('ant-design-vue/es/message')
    message.success('已复制到剪贴板')
  } catch {
    // fallback silently
  }
}

// ── 吊销 ──

const revokingId = ref<number | null>(null)

const doRevoke = async (record: API.ApiKeyVO) => {
  if (!record.id) return
  revokingId.value = record.id
  operationNotice.value = null
  try {
    const res = await revokeApiKey(record.id)
    if (res.data.code !== 0 || res.data.data !== true) {
      throw new Error(res.data.message || '吊销失败')
    }
    // 如果当前页删光了且不是第一页，回退一页
    if (data.value.length === 1 && pageNum.value > 1) {
      pageNum.value = pageNum.value - 1
    }
    operationNotice.value = {
      type: 'success',
      message: 'Key 已吊销',
      description: `「${record.name}」已被吊销，使用此 Key 的请求将立即失效。`,
    }
    await fetchData()
  } catch (error) {
    console.error('吊销 API Key 失败:', error)
    operationNotice.value = {
      type: 'error',
      message: '吊销失败',
      description: error instanceof Error ? error.message : '网络或服务暂时不可用，请稍后重试。',
    }
  } finally {
    revokingId.value = null
  }
}

// ── 创建 Key ──

const createModalOpen = ref(false)
const creating = ref(false)
const createFormRef = ref()

const createForm = reactive({
  name: '',
  userId: undefined as number | undefined,
  expiresAt: undefined as string | undefined,
  _expiresAt: undefined as Dayjs | undefined,
})

const disabledDate = (current: Dayjs) => {
  return current.isBefore(dayjs().startOf('day'))
}

const openCreateModal = () => {
  resetCreateForm()
  if (isAdmin.value && filterUserId.value) {
    createForm.userId = filterUserId.value
  }
  createModalOpen.value = true
}

const resetCreateForm = () => {
  createForm.name = ''
  createForm.userId = undefined
  createForm.expiresAt = undefined
  createForm._expiresAt = undefined
  createFormRef.value?.clearValidate()
}

const doCreate = async () => {
  // 防双重提交
  if (creating.value) return
  creating.value = true

  try {
    await createFormRef.value?.validate()
  } catch {
    creating.value = false
    return
  }
  try {
    const params: API.ApiKeyCreateRequest = {
      name: createForm.name.trim(),
    }
    if (isAdmin.value && createForm.userId) {
      params.userId = createForm.userId
    }
    if (createForm._expiresAt) {
      params.expiresAt = createForm._expiresAt.format('YYYY-MM-DD HH:mm:ss')
    }

    const res = await createApiKey(params)
    if (res.data.code !== 0 || !res.data.data) {
      throw new Error(res.data.message || '创建失败')
    }

    // 保存明文信息用于展示
    const created = res.data.data
    newSecret.value = created.apiKey ?? ''
    newSecretPrefix.value = created.apiKeyPrefix ?? ''
    newSecretExpires.value = created.expiresAt
      ? formatDateTime(created.expiresAt, undefined, '时间未知')
      : ''

    createModalOpen.value = false
    resetCreateForm()
    secretModalOpen.value = true
    operationNotice.value = null
    await fetchData()
  } catch (error) {
    console.error('创建 API Key 失败:', error)
    const { default: message } = await import('ant-design-vue/es/message')
    message.error(error instanceof Error ? error.message : '创建失败，请稍后重试')
  } finally {
    creating.value = false
  }
}

// ── 明文展示弹窗 ──

const secretModalOpen = ref(false)
const newSecret = ref('')
const newSecretPrefix = ref('')
const newSecretExpires = ref('')

const copySecret = async () => {
  try {
    await navigator.clipboard.writeText(newSecret.value)
    const { default: message } = await import('ant-design-vue/es/message')
    message.success('已复制到剪贴板')
  } catch {
    // fallback silently
  }
}

const confirmSaved = () => {
  secretModalOpen.value = false
  newSecret.value = ''
  newSecretPrefix.value = ''
  newSecretExpires.value = ''
}

// ── 生命周期 ──

onMounted(() => {
  void loadUserOptions()
  void fetchData()
})
</script>

<style scoped lang="scss">
#apiKeyPage {
  min-height: calc(100vh - 64px);
  padding: 40px 24px 72px;
  background: var(--surface-page);
  color: var(--text-body);
}

.page-heading,
.filter-bar,
.page-feedback,
.key-section {
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

.key-count,
.page-range {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 12px;
  white-space: nowrap;
}

// ── Admin 筛选 ──

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
}

.page-feedback {
  margin-bottom: 20px;
}

// ── 表格区域 ──

.key-section {
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
  padding: 16px;
  border-bottom: 1px solid var(--border-subtle);
}

.desktop-table :deep(.ant-table-tbody > tr:hover > td) {
  background: var(--surface-page);
}

.key-name {
  color: var(--text-strong);
  font-size: 14px;
  font-weight: 600;
}

.key-prefix-code {
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  background: var(--surface-muted);
  color: var(--text-subtle);
  font-family: var(--font-mono);
  font-size: 12px;
}

.copy-btn {
  margin-left: 4px;
  padding: 0 4px;
  color: var(--text-muted);

  &:hover {
    color: var(--color-primary);
  }
}

.time-text {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
}

.muted-text {
  color: var(--text-disabled);
  font-size: 12px;
}

.expired-text {
  color: var(--state-error-text);
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

.status-active {
  color: var(--state-success-text);

  &::before {
    background: var(--color-success);
  }
}

.status-expired {
  color: var(--state-error-text);

  &::before {
    background: var(--color-error);
  }
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

// ── 明文展示弹窗 ──

.secret-display {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.secret-input {
  flex: 1;
}

.secret-input :deep(input) {
  font-family: var(--font-mono);
  font-size: 13px;
}

.secret-meta {
  display: flex;
  gap: 20px;
  margin-bottom: 20px;
  color: var(--text-muted);
  font-size: 12px;

  code {
    color: var(--text-subtle);
    font-family: var(--font-mono);
  }
}

.secret-confirm {
  margin-top: 8px;
}

// ── 响应式 ──

@media (max-width: 768px) {
  #apiKeyPage {
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
    flex-direction: column;
    align-items: flex-start;
  }

  .filter-bar :deep(.ant-select) {
    width: 100% !important;
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
  #apiKeyPage :deep(*) {
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
