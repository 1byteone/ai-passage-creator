<template>
  <div id="approvalPage">
    <header class="page-heading">
      <div>
        <span class="page-kicker">内容管理</span>
        <h1>审批工作台</h1>
        <p>{{ isAdmin ? '审核用户提交的文章，通过或驳回。' : '查看你提交的文章审批状态。' }}</p>
      </div>
      <div class="heading-actions">
        <a-tabs v-model:activeKey="activeTab" :items="tabItems" />
      </div>
    </header>

    <!-- 操作反馈 -->
    <div v-if="operationNotice" class="page-feedback" aria-live="polite">
      <a-alert
        :type="operationNotice.type"
        show-icon
        closable
        :message="operationNotice.message"
        @close="operationNotice = null"
      />
    </div>

    <section class="queue-section" aria-labelledby="queue-title">
      <div class="list-heading">
        <div>
          <span class="section-label">{{ activeTab === 'pending' ? '待审核队列' : '我的提交' }}</span>
          <h2 id="queue-title">{{ activeTab === 'pending' ? '待审核文章' : '已提交审批的文章' }}</h2>
        </div>
        <span v-if="total" class="page-range">{{ `${total} 篇` }}</span>
      </div>

      <div v-if="initialLoading" class="loading-state">
        <a-skeleton active :paragraph="{ rows: 6 }" />
      </div>

      <a-result
        v-else-if="initialError"
        class="result-state"
        status="error"
        title="暂时无法加载列表"
        :sub-title="loadError"
      >
        <template #extra>
          <a-button type="primary" :loading="loading" @click="refreshData">重新加载</a-button>
        </template>
      </a-result>

      <a-empty
        v-else-if="!articles.length"
        class="empty-state"
        :description="activeTab === 'pending' ? '当前没有待审核的文章' : '还没有提交过审批'"
      />

      <template v-else>
        <a-table
          class="desktop-table"
          :columns="columns"
          :data-source="articles"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 700 }"
          row-key="id"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'article'">
              <div class="article-cell">
                <a-avatar shape="square" :size="48" :src="record.coverImage || undefined" class="article-thumb">
                  {{ record.mainTitle?.slice(0, 1) || '文' }}
                </a-avatar>
                <div>
                  <div class="article-title">{{ record.mainTitle || record.topic || '未命名文章' }}</div>
                  <span v-if="record.topic" class="article-topic">{{ record.topic }}</span>
                </div>
              </div>
            </template>
            <template v-else-if="column.key === 'time'">
              <span class="mono-text">{{ formatDateTime(record.completedTime || record.createTime) }}</span>
            </template>
            <template v-else-if="column.key === 'action'">
              <RouterLink :to="`/article/${record.taskId}`">
                <a-button type="primary" size="small">
                  {{ activeTab === 'pending' ? '去审核' : '查看详情' }}
                </a-button>
              </RouterLink>
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
    </section>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, ref, watch } from 'vue'
import {
  Alert as AAlert,
  Empty as AEmpty,
  Pagination as APagination,
  Result as AResult,
  Skeleton as ASkeleton,
  Table as ATable,
  Tabs as ATabs,
  type TableProps,
} from 'ant-design-vue'
import { listArticle } from '@/api/articleController'
import { useLoginUserStore } from '@/stores/loginUser'
import { formatDateTime } from '@/utils/date'
import type { OperationNotice } from '@/types/operationNotice'

const loginUserStore = useLoginUserStore()
const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')

// ── Tabs ──

const activeTab = ref(isAdmin.value ? 'pending' : 'mine')

const tabItems = computed(() => {
  const items = []
  if (isAdmin.value) {
    items.push({ key: 'pending', tab: '待审核' })
  }
  items.push({ key: 'mine', tab: '我的提交' })
  return items
})

// ── 表格列 ──

const columns: TableProps<API.ArticleVO>['columns'] = [
  { title: '文章', key: 'article', width: 300 },
  { title: '时间', key: 'time', width: 150 },
  { title: '操作', key: 'action', width: 100, align: 'right' },
]

// ── 列表状态 ──

const articles = ref<API.ArticleVO[]>([])
const total = ref(0)
const loading = ref(false)
const loadedOnce = ref(false)
const loadError = ref('')

const operationNotice = ref<OperationNotice | null>(null)

const pageNum = ref(1)
const pageSize = ref(10)

const initialLoading = computed(() => loading.value && !loadedOnce.value)
const initialError = computed(
  () => loadedOnce.value && Boolean(loadError.value) && articles.value.length === 0,
)

const labelPageSizeControl = (element: HTMLElement) => {
  element
    .querySelector<HTMLElement>('.ant-pagination-options-size-changer [role="combobox"]')
    ?.setAttribute('aria-label', '每页显示数量')
}

const vPageSizeLabel = {
  mounted: labelPageSizeControl,
  updated: labelPageSizeControl,
}

// ── 数据加载 ──

let fetchSeq = 0

const fetchData = async () => {
  loading.value = true
  loadError.value = ''
  const seq = ++fetchSeq

  try {
    const params: API.ArticleQueryRequest = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      status: 'COMPLETED',
      sortField: 'createTime',
      sortOrder: 'descend',
    }

    // 非 admin 只看自己的文章
    if (activeTab.value === 'mine' || !isAdmin.value) {
      params.userId = Number(loginUserStore.loginUser.id)
    }

    const res = await listArticle(params)

    if (seq !== fetchSeq) return

    if (res.data.code !== 0 || !res.data.data) {
      throw new Error(res.data.message || '列表加载失败')
    }

    articles.value = res.data.data.records ?? []
    total.value = res.data.data.totalRow ?? 0
  } catch (error) {
    if (seq !== fetchSeq) return
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

// ── 生命周期 ──

onMounted(() => {
  void fetchData()
})

// 切换 Tab 时重新加载对应数据
watch(activeTab, () => {
  pageNum.value = 1
  void fetchData()
})
</script>

<style scoped lang="scss">
#approvalPage {
  min-height: calc(100vh - 64px);
  padding: 40px 24px 72px;
  background: var(--surface-page);
  color: var(--text-body);
}

.page-heading,
.page-feedback,
.queue-section {
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

.heading-actions :deep(.ant-tabs) {
  margin-bottom: -18px;

  .ant-tabs-nav { margin-bottom: 0; }
  .ant-tabs-tab { font-size: 14px; font-weight: 600; padding: 8px 16px; }
}

// ── 表格 ──

.queue-section {
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

  h2 {
    margin: 0;
    color: var(--text-strong);
    font-family: var(--font-heading);
    font-size: 20px;
    font-weight: 650;
  }
}

.page-range {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 12px;
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
  padding: 14px 16px;
  border-bottom: 1px solid var(--border-subtle);
}

.desktop-table :deep(.ant-table-tbody > tr:hover > td) {
  background: var(--surface-page);
}

.article-cell {
  display: flex;
  align-items: center;
  gap: 14px;
}

.article-thumb {
  border-radius: 6px;
  flex-shrink: 0;
  background: var(--surface-muted);
  color: var(--text-muted);
  font-size: 14px;
  font-weight: 700;
}

.article-title {
  color: var(--text-strong);
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 260px;
}

.article-topic {
  display: block;
  margin-top: 2px;
  color: var(--text-muted);
  font-size: 12px;
}

.mono-text {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
}

.page-feedback {
  margin-bottom: 20px;
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

@media (max-width: 768px) {
  #approvalPage {
    padding: 28px 16px 56px;
  }

  .page-heading {
    flex-direction: column;
    align-items: flex-start;
    gap: 18px;
  }

  .page-heading h1 { font-size: 30px; }

  .pagination-bar {
    flex-direction: column;
    align-items: flex-start;
    padding: 16px 20px 20px;
  }

  .pagination-bar :deep(.ant-pagination) { width: 100%; }
  .pagination-bar :deep(.ant-pagination-options) { display: none; }
}

@media (prefers-reduced-motion: reduce) {
  #approvalPage :deep(*) {
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
