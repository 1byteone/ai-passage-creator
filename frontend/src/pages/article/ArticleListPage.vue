<template>
  <section class="history-page" aria-labelledby="history-title">
    <header class="page-heading">
      <div>
        <p>内容资产</p>
        <h1 id="history-title">历史文章</h1>
        <span>查找、继续阅读或导出过去的创作成果。</span>
      </div>
      <a-button type="primary" size="large" @click="goToCreate">
        <template #icon><PlusOutlined /></template>
        创作新文章
      </a-button>
    </header>

    <div class="history-shell">
      <section class="filter-section" aria-labelledby="filter-title">
        <div class="filter-heading">
          <div>
            <h2 id="filter-title">筛选文章</h2>
            <p>{{ resultCountLabel }}</p>
          </div>
          <button
            class="filter-toggle"
            type="button"
            :aria-expanded="filtersExpanded"
            aria-controls="history-filters"
            @click="filtersExpanded = !filtersExpanded"
          >
            <FilterOutlined />
            {{ filtersExpanded ? '收起筛选' : '展开筛选' }}
          </button>
        </div>

        <div id="history-filters" class="filter-controls" :class="{ expanded: filtersExpanded }">
          <label class="filter-field search-field">
            <span>标题或选题</span>
            <a-input-search
              v-model:value="searchKeyword"
              placeholder="搜索当前页文章"
              allow-clear
              size="large"
              @search="applyLocalFilters"
              @change="handleSearchChange"
            >
              <template #prefix><SearchOutlined /></template>
            </a-input-search>
          </label>

          <label class="filter-field">
            <span>创建日期</span>
            <a-range-picker
              v-model:value="dateRange"
              size="large"
              :placeholder="['开始日期', '结束日期']"
              @change="applyLocalFilters"
            />
          </label>

          <label class="filter-field status-field">
            <span>任务状态</span>
            <a-select v-model:value="statusFilter" size="large" @change="handleStatusChange">
              <a-select-option value="">全部状态</a-select-option>
              <a-select-option value="COMPLETED">已完成</a-select-option>
              <a-select-option value="PROCESSING">生成中</a-select-option>
              <a-select-option value="PENDING">等待中</a-select-option>
              <a-select-option value="FAILED">失败</a-select-option>
            </a-select>
          </label>

          <a-button v-if="hasFilters" class="clear-filter" @click="clearFilters">清除筛选</a-button>
        </div>
      </section>

      <div v-if="errorMessage && !records.length" class="page-state" role="alert">
        <a-result status="warning" title="历史文章暂时无法加载" :sub-title="errorMessage">
          <template #extra><a-button type="primary" @click="loadData">重新加载</a-button></template>
        </a-result>
      </div>

      <template v-else>
        <div v-if="errorMessage" class="stale-notice" role="status">
          <span>刷新失败，当前仍显示上一次加载的文章。</span>
          <a-button size="small" @click="loadData">重试</a-button>
        </div>

        <div v-if="loading && !records.length" class="loading-list" aria-label="文章加载中">
          <a-skeleton v-for="index in 4" :key="index" active :paragraph="{ rows: 2 }" />
        </div>

        <div v-else-if="!visibleRecords.length" class="page-state">
          <IllustrationEmpty :description="emptyDescription">
            <a-button v-if="hasFilters" @click="clearFilters">清除筛选</a-button>
            <a-button v-else type="primary" @click="goToCreate">创作第一篇文章</a-button>
          </IllustrationEmpty>
        </div>

        <template v-else>
          <div class="desktop-table">
            <a-table
              v-page-size-label
              :columns="columns"
              :data-source="visibleRecords"
              :loading="loading"
              :pagination="pagination"
              row-key="id"
              @change="handleTableChange"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'title'">
                  <button class="title-cell" type="button" @click="viewArticle(record)">
                    <strong>{{ record.mainTitle || record.topic || '未命名文章' }}</strong>
                    <span>{{ record.subTitle || record.topic || '暂无副标题' }}</span>
                  </button>
                </template>
                <template v-else-if="column.key === 'status'">
                  <StatusBadge :status="record.status || 'PENDING'" />
                </template>
                <template v-else-if="column.key === 'createTime'">
                  <time :datetime="record.createTime">{{ formatDate(record.createTime) }}</time>
                </template>
                <template v-else-if="column.key === 'action'">
                  <ArticleActions :record="record" />
                </template>
              </template>
            </a-table>
          </div>

          <div class="mobile-list">
            <article v-for="record in visibleRecords" :key="record.id || record.taskId">
              <div class="article-heading">
                <StatusBadge :status="record.status || 'PENDING'" />
                <time :datetime="record.createTime">{{ formatDate(record.createTime) }}</time>
              </div>
              <button class="mobile-title" type="button" @click="viewArticle(record)">
                <strong>{{ record.mainTitle || record.topic || '未命名文章' }}</strong>
                <span>{{ record.subTitle || record.topic || '暂无副标题' }}</span>
              </button>
              <ArticleActions :record="record" />
            </article>

            <a-pagination
              v-page-size-label
              v-model:current="pagination.current"
              :page-size="pagination.pageSize"
              :total="pagination.total"
              :show-size-changer="false"
              simple
              @change="handleMobilePageChange"
            />
          </div>
        </template>
      </template>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  Button,
  Modal,
  Pagination as APagination,
  Popconfirm,
  RangePicker as ARangePicker,
  Select as ASelect,
  SelectOption as ASelectOption,
  Table as ATable,
  message,
} from 'ant-design-vue'
import {
  DeleteOutlined,
  DownloadOutlined,
  EyeOutlined,
  FilterOutlined,
  PlusOutlined,
  RedoOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import dayjs, { type Dayjs } from 'dayjs'
import 'dayjs/locale/zh-cn'
import StatusBadge from '@/components/StatusBadge.vue'
import IllustrationEmpty from '@/components/IllustrationEmpty.vue'
import { deleteArticle as deleteArticleApi, getArticle, listArticle } from '@/api/articleController'
import { exportAsMarkdown } from '@/utils/article'

dayjs.locale('zh-cn')

// a-table 内置分页 / simple 分页的输入控件（size-changer Select、quick-jumper、simple-pager）无原生
// aria-label，指令在渲染后补齐，供屏幕阅读器识别（与 UserManagePage 等页面的 v-page-size-label 同模式）
const labelPaginationControls = (element: HTMLElement) => {
  element
    .querySelector<HTMLElement>('.ant-pagination-options-size-changer [role="combobox"]')
    ?.setAttribute('aria-label', '每页显示数量')
  element
    .querySelector<HTMLElement>('.ant-pagination-options-quick-jumper input')
    ?.setAttribute('aria-label', '跳转到指定页')
  element
    .querySelector<HTMLElement>('.ant-pagination-simple-pager input')
    ?.setAttribute('aria-label', '跳转到指定页')
}

const vPageSizeLabel = {
  mounted: labelPaginationControls,
  updated: labelPaginationControls,
}

const router = useRouter()
const searchKeyword = ref('')
const dateRange = ref<[Dayjs, Dayjs]>()
const statusFilter = ref('')
const filtersExpanded = ref(false)
const loading = ref(false)
const records = ref<API.ArticleVO[]>([])
const visibleRecords = ref<API.ArticleVO[]>([])
const errorMessage = ref('')
const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showQuickJumper: true,
  showTotal: (total: number) => `共 ${total} 条`,
  pageSizeOptions: ['10', '20', '50', '100'],
})

const columns = [
  { title: '文章', key: 'title' },
  { title: '状态', key: 'status', width: 112 },
  { title: '创建时间', key: 'createTime', width: 168 },
  { title: '操作', key: 'action', width: 250 },
]

const hasLocalFilters = computed(() => Boolean(searchKeyword.value || dateRange.value))
const hasFilters = computed(() => Boolean(hasLocalFilters.value || statusFilter.value))
const resultCountLabel = computed(() =>
  hasLocalFilters.value
    ? `本页匹配 ${visibleRecords.value.length} 篇，全部记录 ${pagination.value.total} 篇`
    : `共 ${pagination.value.total} 篇文章`,
)
const emptyDescription = computed(() =>
  hasFilters.value ? '当前筛选条件下没有匹配文章' : '还没有历史文章',
)

const applyLocalFilters = () => {
  let result = [...records.value]
  const keyword = searchKeyword.value.trim().toLowerCase()
  if (keyword) {
    result = result.filter(
      (item) =>
        item.mainTitle?.toLowerCase().includes(keyword) ||
        item.subTitle?.toLowerCase().includes(keyword) ||
        item.topic?.toLowerCase().includes(keyword),
    )
  }
  if (dateRange.value) {
    const [start, end] = dateRange.value
    result = result.filter((item) => {
      const createdAt = dayjs(item.createTime)
      return !createdAt.isBefore(start.startOf('day')) && !createdAt.isAfter(end.endOf('day'))
    })
  }
  visibleRecords.value = result
}

let loadSeq = 0

const loadData = async () => {
  loading.value = true
  errorMessage.value = ''
  const seq = ++loadSeq
  try {
    const response = await listArticle({
      pageNum: pagination.value.current,
      pageSize: pagination.value.pageSize,
      status: statusFilter.value || undefined,
    })
    if (seq !== loadSeq) return
    if (response.data.code !== 0 || !response.data.data) {
      throw new Error(response.data.message || '服务未返回文章列表')
    }
    records.value = response.data.data.records || []
    pagination.value.total = response.data.data.totalRow || 0
    applyLocalFilters()
  } catch (error) {
    if (seq !== loadSeq) return
    errorMessage.value = error instanceof Error ? error.message : '请稍后重试'
  } finally {
    loading.value = false
  }
}

const handleSearchChange = () => {
  if (!searchKeyword.value) applyLocalFilters()
}
const handleStatusChange = () => {
  pagination.value.current = 1
  loadData()
}
const handleTableChange = (page: { current?: number; pageSize?: number }) => {
  pagination.value.current = page.current || 1
  pagination.value.pageSize = page.pageSize || pagination.value.pageSize
  loadData()
}
const handleMobilePageChange = (page: number) => {
  pagination.value.current = page
  loadData()
}
const clearFilters = () => {
  searchKeyword.value = ''
  dateRange.value = undefined
  const requiresReload = Boolean(statusFilter.value)
  statusFilter.value = ''
  pagination.value.current = 1
  if (requiresReload) loadData()
  else applyLocalFilters()
}

const viewArticle = (record: API.ArticleVO) => {
  if (record.taskId) router.push(`/article/${record.taskId}`)
}
const goToCreate = () => router.push('/create')
const formatDate = (date?: string) => (date ? dayjs(date).format('YYYY-MM-DD HH:mm') : '时间未知')

const exportArticle = async (record: API.ArticleVO) => {
  if (!record.taskId) return
  try {
    const response = await getArticle({ taskId: record.taskId })
    if (response.data.code !== 0 || !response.data.data) {
      throw new Error(response.data.message || '文章数据不存在')
    }
    const article = response.data.data
    exportAsMarkdown({
      title: article.mainTitle || article.topic || '文章',
      subTitle: article.subTitle,
      content: article.content,
      fullContent: article.fullContent,
    })
    message.success('文章已导出')
  } catch (error) {
    message.error(error instanceof Error ? error.message : '导出失败，请稍后重试')
  }
}

const deleteArticle = async (record: API.ArticleVO) => {
  if (!record.id) return
  try {
    const response = await deleteArticleApi({ id: record.id })
    if (response.data.code !== 0 || !response.data.data) {
      throw new Error(response.data.message || '删除失败')
    }
    message.success('文章已删除')
    if (records.value.length === 1 && pagination.value.current > 1) pagination.value.current -= 1
    await loadData()
  } catch (error) {
    message.error(error instanceof Error ? error.message : '删除失败，请稍后重试')
  }
}

const retryArticle = (record: API.ArticleVO) => {
  Modal.confirm({
    title: '重新创建这篇文章？',
    content: `将保留选题“${record.topic || '未命名选题'}”并返回创作页，你可以先调整设置再提交。`,
    okText: '返回创作页',
    cancelText: '取消',
    onOk: () =>
      router.push({
        path: '/create',
        query: { topic: record.topic || '', style: record.userDescription || '' },
      }),
  })
}

const ArticleActions = defineComponent({
  props: { record: { type: Object as () => API.ArticleVO, required: true } },
  setup(props) {
    return () =>
      h('div', { class: 'article-actions' }, [
        h(
          Button,
          { type: 'link', size: 'small', onClick: () => viewArticle(props.record) },
          { icon: () => h(EyeOutlined), default: () => '查看' },
        ),
        props.record.status === 'FAILED'
          ? h(
              Button,
              { type: 'link', size: 'small', onClick: () => retryArticle(props.record) },
              { icon: () => h(RedoOutlined), default: () => '重试' },
            )
          : h(
              Button,
              {
                type: 'link',
                size: 'small',
                disabled: props.record.status !== 'COMPLETED',
                onClick: () => exportArticle(props.record),
              },
              { icon: () => h(DownloadOutlined), default: () => '导出' },
            ),
        h(
          Popconfirm,
          {
            title: '确定删除这篇文章？',
            description: '删除后无法恢复。',
            okText: '删除',
            cancelText: '取消',
            onConfirm: () => deleteArticle(props.record),
          },
          {
            default: () =>
              h(
                Button,
                { type: 'link', size: 'small', danger: true },
                { icon: () => h(DeleteOutlined), default: () => '删除' },
              ),
          },
        ),
      ])
  },
})

onMounted(loadData)
</script>

<style scoped>
.history-page {
  min-height: calc(100dvh - 64px);
  padding: 42px 20px 72px;
  background: var(--surface-page);
}

.page-heading,
.history-shell {
  width: min(1160px, 100%);
  margin-inline: auto;
}

.page-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 28px;
}

.page-heading p {
  margin: 0 0 5px;
  color: var(--color-primary-dark);
  font-size: 13px;
  font-weight: 600;
}

.page-heading h1 {
  margin: 0 0 8px;
  font-size: 30px;
}

.page-heading span {
  color: var(--text-muted);
  font-size: 14px;
}

.filter-section {
  margin-bottom: 16px;
  padding: 18px 20px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--surface-panel);
}

.filter-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.filter-heading h2 {
  margin: 0;
  font-size: 16px;
}

.filter-heading p {
  margin: 4px 0 0;
  color: var(--text-muted);
  font-size: 12px;
}

.filter-toggle {
  display: none;
  min-height: var(--touch-target-min);
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-primary-dark);
  font-weight: 600;
}

.filter-controls {
  display: grid;
  grid-template-columns: minmax(220px, 1.3fr) minmax(280px, 1fr) 150px auto;
  align-items: end;
  gap: 12px;
}

.filter-field {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.filter-field > span {
  color: var(--text-muted);
  font-size: 12px;
}

.status-field :deep(.ant-select) {
  width: 100%;
}

.clear-filter {
  align-self: end;
}

.desktop-table {
  overflow: hidden;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--surface-panel);
}

.desktop-table :deep(.ant-table-thead th) {
  background: var(--surface-muted);
  color: var(--text-subtle);
  font-size: 12px;
}

.title-cell,
.mobile-title {
  display: grid;
  width: 100%;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
}

.title-cell strong,
.mobile-title strong {
  color: var(--text-strong);
  font-size: 14px;
  line-height: 1.5;
}

.title-cell span,
.mobile-title span {
  overflow: hidden;
  margin-top: 4px;
  color: var(--text-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.title-cell:hover strong,
.mobile-title:hover strong {
  color: var(--color-primary-dark);
}

time {
  color: var(--text-muted);
  font-size: 12px;
}

:deep(.article-actions) {
  display: flex;
  align-items: center;
  gap: 2px;
}

.mobile-list {
  display: none;
}

.page-state,
.loading-list {
  min-height: 420px;
  padding: 32px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--surface-panel);
}

.page-state {
  display: grid;
  place-items: center;
}

.loading-list {
  display: grid;
  align-content: start;
  gap: 22px;
}

.stale-notice {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  padding: 12px 16px;
  border: 1px solid #fde68a;
  border-radius: var(--radius-md);
  background: var(--state-warning-bg);
  color: var(--state-warning-text);
  font-size: 13px;
}

@media (max-width: 820px) {
  .filter-controls {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .history-page {
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

  .filter-heading {
    margin-bottom: 0;
  }

  .filter-toggle {
    display: inline-flex;
    align-items: center;
    gap: 8px;
  }

  .filter-controls {
    display: none;
    grid-template-columns: 1fr;
    margin-top: 16px;
  }

  .filter-controls.expanded {
    display: grid;
  }

  .desktop-table {
    display: none;
  }

  .mobile-list {
    display: grid;
    gap: 12px;
  }

  .mobile-list article {
    padding: 18px 16px;
    border: 1px solid var(--border-default);
    border-radius: var(--radius-md);
    background: var(--surface-panel);
  }

  .article-heading {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 14px;
  }

  .mobile-title strong {
    font-size: 16px;
  }

  .mobile-title span {
    white-space: normal;
  }

  .mobile-list :deep(.article-actions) {
    justify-content: space-between;
    margin-top: 16px;
    padding-top: 12px;
    border-top: 1px solid var(--border-subtle);
  }

  .mobile-list > :deep(.ant-pagination) {
    justify-self: center;
    margin-top: 12px;
  }

  .page-state,
  .loading-list {
    min-height: 320px;
    padding: 24px 16px;
  }

  .stale-notice {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
