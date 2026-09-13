<template>
  <section class="detail-page" aria-label="文章详情">
    <div class="action-bar">
      <a-button @click="goBack"><template #icon><ArrowLeftOutlined /></template>返回历史</a-button>
      <div class="primary-actions">
        <a-button v-if="hasContent" @click="detoxLauncherOpen = true">
          <template #icon><FileTextOutlined /></template>表达质量优化
        </a-button>
        <a-button v-if="hasContent" @click="seedLauncherOpen = true">
          <template #icon><SmileOutlined /></template>种草文案
        </a-button>
        <a-button v-if="hasContent" @click="skillLauncherOpen = true">
          <template #icon><ShareAltOutlined /></template>转为社交文案
        </a-button>
        <a-button v-if="hasContent" @click="seoLauncherOpen = true">
          <template #icon><SearchOutlined /></template>SEO 优化
        </a-button>
        <a-button v-if="article?.status === 'FAILED'" danger @click="handleRetry">
          <template #icon><RedoOutlined /></template>重新创建
        </a-button>
        <a-button type="primary" :disabled="!hasContent" @click="exportMarkdown">
          <template #icon><DownloadOutlined /></template>导出 Markdown
        </a-button>
        <RouterLink v-if="article?.status === 'COMPLETED'" :to="`/article/${encodeURIComponent(taskId)}/cards`">
          <a-button>
            <template #icon><PictureOutlined /></template>卡片管理
          </a-button>
        </RouterLink>
        <RouterLink v-if="article?.status === 'COMPLETED'" :to="`/article/${encodeURIComponent(taskId)}/publish`">
          <a-button>
            <template #icon><SendOutlined /></template>发布管理
          </a-button>
        </RouterLink>
      </div>
    </div>

    <div class="detail-shell">
      <div v-if="loading" class="page-state" aria-label="文章加载中">
        <a-skeleton active :title="{ width: '70%' }" :paragraph="{ rows: 12 }" />
      </div>

      <a-result
        v-else-if="errorMessage"
        status="warning"
        title="文章暂时无法加载"
        :sub-title="errorMessage"
        class="page-state"
      >
        <template #extra>
          <a-button type="primary" @click="loadArticle">重新加载</a-button>
          <a-button @click="goBack">返回历史</a-button>
        </template>
      </a-result>

      <a-result
        v-else-if="!article"
        status="404"
        title="没有找到这篇文章"
        sub-title="记录可能已被删除，或链接已经失效。"
        class="page-state"
      >
        <template #extra><a-button type="primary" @click="goBack">返回历史</a-button></template>
      </a-result>

      <template v-else>
        <ArticleReadingView :article="article" title-id="article-detail-title" />

        <!-- 相关文章 -->
        <section v-if="hasContent" class="related-panel" aria-labelledby="related-title">
          <h2 id="related-title" class="related-title">
            <LinkOutlined />
            相关文章
          </h2>
          <RagHitsPanel
            :hits="relatedHits"
            :loading="relatedLoading"
            empty-text="暂无相关文章"
            @select="handleOpenRelated"
          />
        </section>

        <!-- 参考来源：创作时注入的 RAG 参考溯源 -->
        <section v-if="ragReferences.length" class="related-panel" aria-labelledby="references-title">
          <h2 id="references-title" class="related-title">
            <ReadOutlined />
            参考来源
          </h2>
          <div v-if="referencesLoading" class="references-loading">加载中…</div>
          <ul v-else class="references-list">
            <li v-for="ref in ragReferences" :key="ref.id" class="reference-item">
              <span class="reference-stage">{{ stageLabel(ref.stage) }}</span>
              <span class="reference-title">{{ ref.refTitle }}</span>
              <span class="reference-score">{{ ref.score != null ? ref.score.toFixed(2) : '' }}</span>
            </li>
          </ul>
        </section>

        <section class="execution-panel" aria-labelledby="execution-title">
          <button
            type="button"
            :aria-expanded="showExecutionLogs"
            aria-controls="execution-content"
            @click="showExecutionLogs = !showExecutionLogs"
          >
            <span>
              <ClockCircleOutlined />
              <strong id="execution-title">执行信息</strong>
              <small v-if="executionStats?.logs?.length">{{ executionStats.logs.length }} 个步骤</small>
            </span>
            <DownOutlined :class="{ expanded: showExecutionLogs }" />
          </button>

          <div v-if="showExecutionLogs" id="execution-content" class="execution-content">
            <a-skeleton v-if="logsLoading" active :paragraph="{ rows: 3 }" />
            <a-alert
              v-else-if="logsError"
              type="warning"
              :message="logsError"
              show-icon
            >
              <template #action><a-button size="small" @click="loadExecutionLogs">重试</a-button></template>
            </a-alert>
            <a-empty
              v-else-if="!executionStats?.logs?.length"
              description="当前没有可查看的执行记录"
            />
            <template v-else>
              <dl class="execution-summary">
                <div><dt>总耗时</dt><dd>{{ formatDuration(executionStats.totalDurationMs) }}</dd></div>
                <div><dt>执行步骤</dt><dd>{{ executionStats.agentCount ?? executionStats.logs.length }}</dd></div>
                <div><dt>整体状态</dt><dd>{{ executionStats.overallStatus || '未知' }}</dd></div>
              </dl>
              <ol class="execution-list">
                <li v-for="log in executionStats.logs" :key="log.id">
                  <div>
                    <strong>{{ getAgentDisplayName(log.agentName || '') }}</strong>
                    <span>{{ formatDuration(log.durationMs) }}</span>
                  </div>
                  <small>{{ log.startTime ? formatDate(log.startTime) : '时间未知' }}</small>
                  <p v-if="log.errorMessage">{{ log.errorMessage }}</p>
                </li>
              </ol>
            </template>
          </div>
        </section>

        <!-- 审批面板 -->
        <section v-if="article?.status === 'COMPLETED'" class="approval-panel" aria-labelledby="approval-title">
          <button
            type="button"
            :aria-expanded="showApproval"
            aria-controls="approval-content"
            @click="toggleApproval"
          >
            <span>
              <AuditOutlined />
              <strong id="approval-title">审批状态</strong>
              <small v-if="approvalStatus">{{ approvalStatusLabel }}</small>
            </span>
            <DownOutlined :class="{ expanded: showApproval }" />
          </button>

          <div v-if="showApproval" id="approval-content" class="approval-content">
            <a-skeleton v-if="approvalLoading" active :paragraph="{ rows: 2 }" />
            <a-alert
              v-else-if="approvalError"
              type="warning"
              :message="approvalError"
              show-icon
            >
              <template #action>
                <a-button size="small" @click="loadApprovalHistory">重试</a-button>
              </template>
            </a-alert>
            <template v-else>
              <!-- 审批操作区 -->
              <div v-if="isOwnArticle && !approvalRecords.length" class="approval-action">
                <p>文章已完成，可以提交审批。审批通过后方可排期发布。</p>
                <a-button type="primary" :loading="submittingApproval" @click="handleSubmitApproval">
                  <template #icon><SendOutlined /></template>提交审批
                </a-button>
              </div>

              <div v-else-if="isOwnArticle && latestApproval?.status === 'REJECTED'" class="approval-action">
                <a-alert type="error" show-icon :message="`审批驳回：${latestApproval.comment || '未提供原因'}`" />
                <a-button style="margin-top: 12px" :loading="submittingApproval" @click="handleSubmitApproval">
                  <template #icon><RedoOutlined /></template>重新提交
                </a-button>
              </div>

              <div v-else-if="isAdmin && !isOwnArticle && latestApproval?.status === 'PENDING'" class="approval-action">
                <div class="approval-review">
                  <a-input
                    v-model:value="reviewComment"
                    placeholder="审批意见（可选）"
                    :maxlength="500"
                    size="large"
                    style="margin-bottom: 8px"
                  />
                  <div class="approval-review-buttons">
                    <a-button type="primary" :loading="approvingAction" @click="handleApprove">
                      <template #icon><CheckOutlined /></template>通过
                    </a-button>
                    <a-popconfirm
                      title="确定驳回此文章？"
                      :description="reviewComment || undefined"
                      ok-text="驳回"
                      cancel-text="取消"
                      ok-type="danger"
                      @confirm="handleReject"
                    >
                      <a-button danger :loading="approvingAction">驳回</a-button>
                    </a-popconfirm>
                  </div>
                </div>
              </div>

              <div v-else-if="isAdmin && !isOwnArticle && !approvalRecords.length" class="approval-action">
                <p>该文章尚未提交审批。</p>
              </div>

              <!-- 当前审批状态 -->
              <div v-if="latestApproval" class="approval-status-card">
                <span class="approval-badge" :class="`approval-${latestApproval.status?.toLowerCase()}`">
                  {{ statusText(latestApproval.status) }}
                </span>
                <span v-if="latestApproval.comment" class="approval-comment">{{ latestApproval.comment }}</span>
                <span class="approval-time">
                  {{ latestApproval.status === 'PENDING' ? '提交于' : '' }}
                  {{ formatDate(latestApproval.submitTime || '') }}
                  <template v-if="latestApproval.reviewTime">
                    · 审批于 {{ formatDate(latestApproval.reviewTime || '') }}
                  </template>
                </span>
              </div>

              <!-- 审批历史 -->
              <div v-if="approvalRecords.length > 1" class="approval-history">
                <span class="history-label">历史记录</span>
                <div v-for="record in approvalRecords.slice(0, -1)" :key="record.id" class="history-item">
                  <span class="approval-badge approval-history-badge" :class="`approval-${record.status?.toLowerCase()}`">
                    {{ statusText(record.status) }}
                  </span>
                  <span v-if="record.comment" class="approval-comment">{{ record.comment }}</span>
                  <span class="approval-time">{{ formatDate((record.reviewTime || record.submitTime) || '') }}</span>
                </div>
              </div>
            </template>
          </div>
        </section>
      </template>
    </div>

    <SkillLauncher
      v-if="article"
      v-model:open="detoxLauncherOpen"
      skill-name="ai-detox"
      :initial-inputs="{ articleContent: article.fullContent || article.content || '', intensity: 'medium' }"
    />
    <SkillLauncher
      v-if="article"
      v-model:open="seedLauncherOpen"
      skill-name="seeding-copy"
      :initial-inputs="{ productInfo: article.mainTitle || article.topic || '', platform: 'xiaohongshu', tone: '种草推荐' }"
    />
    <SkillLauncher
      v-if="article"
      v-model:open="skillLauncherOpen"
      skill-name="article-to-x"
      :initial-inputs="{ articleContent: article.fullContent || article.content || '' }"
    />
    <SkillLauncher
      v-if="article"
      v-model:open="seoLauncherOpen"
      skill-name="seo-optimizer"
      :initial-inputs="{ articleContent: article.fullContent || article.content || '', primaryKeyword: article.topic || '' }"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Alert as AAlert, Modal, Popconfirm as APopconfirm, message } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  AuditOutlined,
  CheckOutlined,
  ClockCircleOutlined,
  DownOutlined,
  DownloadOutlined,
  FileTextOutlined,
  PictureOutlined,
  RedoOutlined,
  SendOutlined,
  ShareAltOutlined,
  LinkOutlined,
  ReadOutlined,
  SmileOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import ArticleReadingView from '@/components/ArticleReadingView.vue'
import RagHitsPanel from '@/components/RagHitsPanel.vue'
import SkillLauncher from '@/pages/skill/components/SkillLauncher.vue'
import { useRagSearch } from '@/composables/useRagSearch'
import { getRagReferences } from '@/api/ragController'
import { getArticle, getExecutionLogs } from '@/api/articleController'
import { submitForApproval, approveArticle, rejectArticle, getApprovalHistory } from '@/api/approvalController'
import { exportAsMarkdown } from '@/utils/article'
import { formatDate } from '@/utils/date'
import { useLoginUserStore } from '@/stores/loginUser'

const router = useRouter()
const route = useRoute()
const loginUserStore = useLoginUserStore()
const loading = ref(false)
const article = ref<API.ArticleVO | null>(null)
const errorMessage = ref('')
const executionStats = ref<API.AgentExecutionStats | null>(null)
const logsLoading = ref(false)
const logsError = ref('')
const showExecutionLogs = ref(false)
const skillLauncherOpen = ref(false)
const seoLauncherOpen = ref(false)
const detoxLauncherOpen = ref(false)
const seedLauncherOpen = ref(false)
const hasContent = computed(() => Boolean(article.value?.fullContent || article.value?.content))
const taskId = computed(() => (typeof route.params.taskId === 'string' ? route.params.taskId : ''))
// 相关文章：文章加载成功后用标题语义检索（详情页只发一次，无需防抖）
const { hits: relatedHits, loading: relatedLoading, search: relatedSearch } = useRagSearch({ debounceMs: 0 })

// 参考来源：创作时注入的 RAG 参考溯源
const ragReferences = ref<API.RagReference[]>([])
const referencesLoading = ref(false)
const loadReferences = async () => {
  if (!taskId.value) return
  referencesLoading.value = true
  try {
    const res = await getRagReferences(taskId.value)
    if (res.data.code !== 0) throw new Error(res.data.message || '参考溯源加载失败')
    ragReferences.value = res.data.data ?? []
  } catch (e) {
    console.error('参考溯源加载失败（静默）:', e)
  } finally {
    referencesLoading.value = false
  }
}

const stageLabel = (stage?: string) =>
  stage === 'title' ? '标题' : stage === 'outline' ? '大纲' : stage === 'content' ? '正文' : (stage ?? '')

const loadArticle = async () => {
  if (!taskId.value) {
    errorMessage.value = '文章链接缺少任务编号。'
    return
  }
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await getArticle({ taskId: taskId.value })
    if (response.data.code !== 0) throw new Error(response.data.message || '文章加载失败')
    article.value = response.data.data || null
    if (article.value) {
      void loadExecutionLogs()
      loadRelated()
      void loadReferences()
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '请稍后重试'
  } finally {
    loading.value = false
  }
}

const loadExecutionLogs = async () => {
  if (!taskId.value) return
  logsLoading.value = true
  logsError.value = ''
  try {
    const response = await getExecutionLogs({ taskId: taskId.value })
    if (response.data.code !== 0) throw new Error(response.data.message || '执行记录加载失败')
    executionStats.value = response.data.data || null
  } catch (error) {
    logsError.value = error instanceof Error ? error.message : '执行记录暂时不可用'
  } finally {
    logsLoading.value = false
  }
}

const loadRelated = () => {
  const a = article.value
  if (!a) return
  const query = [a.mainTitle, a.subTitle].filter(Boolean).join(' ')
  if (!query.trim()) return
  relatedSearch(query, { type: 'article', topK: 5, excludeRefId: taskId.value })
}

const handleOpenRelated = (hit: API.RagHit) => {
  if (hit.refId) router.push(`/article/${encodeURIComponent(hit.refId)}`)
}

const goBack = () => router.push('/article/list')
const exportMarkdown = () => {
  if (!article.value || !hasContent.value) return
  exportAsMarkdown({
    title: article.value.mainTitle || article.value.topic || '文章',
    subTitle: article.value.subTitle,
    content: article.value.content,
    fullContent: article.value.fullContent,
    outline: article.value.outline?.map((item, index) => ({
      section: item.section ?? index + 1,
      title: item.title || `第 ${index + 1} 节`,
    })),
    images: article.value.images
      ?.filter((image) => Boolean(image.url))
      .map((image) => ({
        description: image.description || image.keywords || '文章配图',
        url: image.url || '',
      })),
  })
  message.success('文章已导出')
}
const handleRetry = () => {
  if (!article.value) return
  Modal.confirm({
    title: '重新创建这篇文章？',
    content: '将保留当前选题并返回创作页，你可以先调整设置再提交。',
    okText: '返回创作页',
    cancelText: '取消',
    onOk: () => router.push({ path: '/create', query: { topic: article.value?.topic || '' } }),
  })
}
const formatDuration = (value?: number) => {
  const ms = value ?? 0
  return ms < 1000 ? `${ms} 毫秒` : `${(ms / 1000).toFixed(1)} 秒`
}
const getAgentDisplayName = (name: string) => {
  const labels: Record<string, string> = {
    agent1_generate_titles: '生成标题',
    agent2_generate_outline: '生成大纲',
    agent3_generate_content: '生成正文',
    agent4_analyze_image_requirements: '分析配图需求',
    agent5_generate_images: '生成配图',
    agent6_merge_content: '图文合成',
    ai_modify_outline: '调整大纲',
  }
  return labels[name] || name || '未知步骤'
}

// ── 审批状态 ──

const showApproval = ref(false)
const approvalLoading = ref(false)
const approvalError = ref('')
const approvalRecords = ref<API.ApprovalRecord[]>([])
const submittingApproval = ref(false)
const approvingAction = ref(false)
const reviewComment = ref('')

const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')
const isOwnArticle = computed(() =>
  article.value ? String(article.value.userId) === String(loginUserStore.loginUser.id) : false,
)

const latestApproval = computed(() =>
  approvalRecords.value.length ? approvalRecords.value[approvalRecords.value.length - 1] : null,
)

const approvalStatus = computed(() => latestApproval.value?.status)

const STATUS_TEXT: Record<string, string> = { PENDING: '待审批', APPROVED: '已通过', REJECTED: '已驳回' }

const approvalStatusLabel = computed(() => STATUS_TEXT[approvalStatus.value ?? ''] || '')

const statusText = (status?: string) => STATUS_TEXT[status ?? ''] || status || '—'

const loadApprovalHistory = async () => {
  if (!taskId.value) return
  approvalLoading.value = true
  approvalError.value = ''
  try {
    const res = await getApprovalHistory(taskId.value)
    if (res.data.code === 0) {
      approvalRecords.value = res.data.data ?? []
    } else {
      // 40400 = 还没有审批记录
      if (res.data.code === 40400) {
        approvalRecords.value = []
      } else {
        throw new Error(res.data.message || '加载失败')
      }
    }
  } catch (e) {
    approvalError.value = e instanceof Error ? e.message : '审批记录暂时不可用'
  } finally {
    approvalLoading.value = false
  }
}

const toggleApproval = () => {
  showApproval.value = !showApproval.value
  if (showApproval.value && !approvalRecords.value.length && !approvalLoading.value) {
    void loadApprovalHistory()
  }
}

const handleSubmitApproval = async () => {
  if (!taskId.value || submittingApproval.value) return
  submittingApproval.value = true
  try {
    const res = await submitForApproval({ taskId: taskId.value })
    if (res.data.code === 0) {
      message.success('已提交审批')
      await loadApprovalHistory()
    } else {
      throw new Error(res.data.message || '提交失败')
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '提交审批失败，请稍后重试')
  } finally {
    submittingApproval.value = false
  }
}

const handleApprove = async () => {
  if (!taskId.value || approvingAction.value) return
  approvingAction.value = true
  try {
    const res = await approveArticle({ taskId: taskId.value, comment: reviewComment.value.trim() || undefined })
    if (res.data.code === 0) {
      message.success('已审批通过')
      reviewComment.value = ''
      await loadApprovalHistory()
    } else {
      throw new Error(res.data.message || '审批失败')
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '审批操作失败')
  } finally {
    approvingAction.value = false
  }
}

const handleReject = async () => {
  if (!taskId.value || approvingAction.value) return
  approvingAction.value = true
  try {
    const res = await rejectArticle({ taskId: taskId.value, comment: reviewComment.value.trim() || '未提供原因' })
    if (res.data.code === 0) {
      message.success('已驳回')
      reviewComment.value = ''
      await loadApprovalHistory()
    } else {
      throw new Error(res.data.message || '驳回失败')
    }
  } catch (e) {
    message.error(e instanceof Error ? e.message : '驳回操作失败')
  } finally {
    approvingAction.value = false
  }
}

onMounted(loadArticle)
</script>

<style scoped>
.detail-page {
  min-height: calc(100dvh - 64px);
  padding: 24px 20px 72px;
  background: var(--surface-page);
}

.action-bar,
.detail-shell {
  width: min(1080px, 100%);
  margin-inline: auto;
}

.action-bar {
  position: sticky;
  z-index: 5;
  top: 72px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
  padding: 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.96);
  box-shadow: var(--shadow-subtle);
}

.primary-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.detail-shell {
  padding: 36px 48px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--surface-panel);
}

.page-state {
  min-height: 520px;
  display: grid;
  align-content: center;
}

.execution-panel {
  width: min(760px, 100%);
  margin: 56px auto 0;
  border-top: 1px solid var(--border-default);
}

.execution-panel > button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 64px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-body);
}

.execution-panel > button > span {
  display: flex;
  align-items: center;
  gap: 10px;
}

.execution-panel > button small {
  color: var(--text-muted);
  font-weight: 400;
}

.execution-panel .anticon-down {
  transition: transform var(--transition-fast);
}

.execution-panel .anticon-down.expanded {
  transform: rotate(180deg);
}

.execution-content {
  padding: 18px 0 4px;
  border-top: 1px solid var(--border-subtle);
}

/* ── 审批面板 ── */

.approval-panel {
  margin-top: 24px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);

  > button {
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 100%;
    padding: 14px 20px;
    border: 0;
    background: none;
    color: var(--text-body);
    font-size: 14px;
    cursor: pointer;
    transition: background var(--transition-fast);

    &:hover { background: var(--surface-muted); }

    > span {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    small {
      color: var(--text-muted);
      font-size: 12px;
    }
  }

  .anticon-down {
    transition: transform var(--transition-fast);
    font-size: 12px;
    color: var(--text-muted);
    &.expanded { transform: rotate(180deg); }
  }
}

.approval-content {
  padding: 0 20px 20px;
  border-top: 1px solid var(--border-subtle);
}

.approval-action {
  padding: 16px 0;

  p {
    margin: 0 0 12px;
    color: var(--text-subtle);
    font-size: 13px;
  }
}

.approval-review {
  padding: 12px 0;
}

.approval-review-buttons {
  display: flex;
  gap: 8px;
}

.approval-status-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid var(--border-subtle);
}

.approval-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 100px;
  font-size: 12px;
  font-weight: 650;
  white-space: nowrap;
}

.approval-pending { background: var(--state-warning-bg); color: var(--state-warning-text); }
.approval-approved { background: #d1fae5; color: #065f46; }
.approval-rejected { background: var(--state-error-bg); color: var(--state-error-text); }

.approval-history-badge {
  font-size: 11px;
  padding: 1px 8px;
}

.approval-comment {
  color: var(--text-subtle);
  font-size: 12px;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.approval-time {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
  white-space: nowrap;
}

.approval-history {
  padding: 12px 0 0;

  .history-label {
    display: block;
    margin-bottom: 8px;
    color: var(--text-muted);
    font-size: 11px;
    font-weight: 600;
    text-transform: uppercase;
  }
}

.history-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
}

/* ── 旧样式 ── */

.execution-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin: 0 0 24px;
  border: 1px solid var(--border-default);
}

.execution-summary div {
  padding: 14px;
  border-right: 1px solid var(--border-default);
}

.execution-summary div:last-child {
  border-right: 0;
}

.execution-summary dt {
  color: var(--text-muted);
  font-size: 11px;
}

.execution-summary dd {
  margin: 4px 0 0;
  font-size: 15px;
  font-weight: 600;
}

.execution-list {
  margin: 0;
  padding-left: 24px;
}

.execution-list li {
  margin-bottom: 18px;
  padding-left: 6px;
}

.execution-list li > div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.execution-list span,
.execution-list small {
  color: var(--text-muted);
  font-size: 12px;
}

.execution-list p {
  margin: 8px 0 0;
  padding: 8px 10px;
  background: var(--state-error-bg);
  color: var(--state-error-text);
  font-size: 12px;
}

/* ── 相关文章 ── */
.related-panel {
  width: min(760px, 100%);
  margin: 24px auto 0;
  padding-top: 20px;
  border-top: 1px solid var(--border-default);
}

.related-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 14px;
  font-size: 16px;
  font-weight: 650;
  color: var(--text-strong);
}

/* 参考来源列表 */
.references-loading {
  color: var(--text-weak);
  font-size: 13px;
}

.references-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.reference-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: var(--bg-soft, rgba(0, 0, 0, 0.03));
  border-radius: 8px;
  font-size: 13px;
}

.reference-stage {
  flex-shrink: 0;
  padding: 1px 8px;
  border-radius: 4px;
  font-size: 12px;
  color: var(--color-primary);
  background: var(--color-primary-bg, rgba(64, 128, 255, 0.1));
}

.reference-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-strong);
}

.reference-score {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--text-weak);
}

@media (max-width: 700px) {
  .detail-page {
    padding: 16px 0 56px;
  }

  .action-bar {
    position: static;
    align-items: stretch;
    flex-direction: column;
    width: calc(100% - 32px);
  }

  .primary-actions {
    display: grid;
    grid-template-columns: 1fr;
  }

  .primary-actions .ant-btn {
    width: 100%;
  }

  .detail-shell {
    padding: 28px 20px;
    border-right: 0;
    border-left: 0;
    border-radius: 0;
  }

  .execution-summary {
    grid-template-columns: 1fr;
  }

  .execution-summary div {
    border-right: 0;
    border-bottom: 1px solid var(--border-default);
  }

  .execution-summary div:last-child {
    border-bottom: 0;
  }
}
</style>
