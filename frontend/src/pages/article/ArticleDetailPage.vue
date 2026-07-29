<template>
  <section class="detail-page" aria-label="文章详情">
    <div class="action-bar">
      <a-button @click="goBack"><template #icon><ArrowLeftOutlined /></template>返回历史</a-button>
      <div class="primary-actions">
        <a-button v-if="hasContent" @click="skillLauncherOpen = true">
          <template #icon><ShareAltOutlined /></template>转为社交文案
        </a-button>
        <a-button v-if="article?.status === 'FAILED'" danger @click="handleRetry">
          <template #icon><RedoOutlined /></template>重新创建
        </a-button>
        <a-button type="primary" :disabled="!hasContent" @click="exportMarkdown">
          <template #icon><DownloadOutlined /></template>导出 Markdown
        </a-button>
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
      </template>
    </div>

    <SkillLauncher
      v-if="article"
      v-model:open="skillLauncherOpen"
      skill-name="article-to-x"
      :initial-inputs="{ articleContent: article.fullContent || article.content || '' }"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Alert as AAlert, Modal, message } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  ClockCircleOutlined,
  DownOutlined,
  DownloadOutlined,
  RedoOutlined,
  ShareAltOutlined,
} from '@ant-design/icons-vue'
import ArticleReadingView from '@/components/ArticleReadingView.vue'
import SkillLauncher from '@/pages/skill/components/SkillLauncher.vue'
import { getArticle, getExecutionLogs } from '@/api/articleController'
import { exportAsMarkdown } from '@/utils/article'
import { formatDate } from '@/utils/date'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const article = ref<API.ArticleVO | null>(null)
const errorMessage = ref('')
const executionStats = ref<API.AgentExecutionStats | null>(null)
const logsLoading = ref(false)
const logsError = ref('')
const showExecutionLogs = ref(false)
const skillLauncherOpen = ref(false)
const hasContent = computed(() => Boolean(article.value?.fullContent || article.value?.content))
const taskId = computed(() => (typeof route.params.taskId === 'string' ? route.params.taskId : ''))

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
    if (article.value) void loadExecutionLogs()
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
