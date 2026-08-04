<template>
  <section class="skill-execute-page" aria-label="AI 技能执行工作台">
    <div class="execute-shell">
      <div class="page-topbar">
        <button class="back-link" type="button" @click="router.push('/skill')">
          <ArrowLeftOutlined aria-hidden="true" />
          返回技能中心
        </button>
        <RouterLink to="/skill/history" class="history-nav">
          <HistoryOutlined aria-hidden="true" />
          执行历史
        </RouterLink>
      </div>
      <SkillExecuteSurface
        ref="surfaceRef"
        :key="skillName"
        :skill-name="skillName"
        :restore-execution-id="restoreExecutionId"
        @execution-change="syncExecutionId"
      />

      <!-- 最近执行 -->
      <section class="recent-runs" aria-labelledby="recent-runs-title">
        <div class="recent-runs-heading">
          <h2 id="recent-runs-title">最近执行</h2>
          <RouterLink to="/skill/history">全部记录</RouterLink>
        </div>

        <div v-if="recentRunsLoading" class="recent-runs-state" aria-live="polite">
          <a-spin size="small" />
          <span>加载最近执行…</span>
        </div>

        <a-alert
          v-else-if="recentRunsError"
          type="warning"
          show-icon
          :message="recentRunsError"
          class="recent-runs-state"
        >
          <template #action>
            <a-button size="small" @click="loadRecentRuns">重试</a-button>
          </template>
        </a-alert>

        <p v-else-if="!recentRuns.length" class="recent-runs-empty">当前技能还没有执行记录</p>

        <ul v-else class="recent-runs-list">
          <li v-for="run in recentRuns" :key="run.skillExecutionId">
            <span class="run-status" :class="`run-status--${statusClass(run.status)}`">
              {{ statusLabel(run.status) }}
            </span>
            <span class="run-meta">
              <span>{{ formatDateTime(run.createTime, 'MM-DD HH:mm', '时间未知') }}</span>
              <template v-if="run.durationMs">· {{ formatDuration(run.durationMs) }}</template>
            </span>
            <RouterLink
              class="run-restore"
              :to="`/skill/${encodeURIComponent(skillName)}?executionId=${encodeURIComponent(run.skillExecutionId ?? '')}`"
            >
              {{ isTerminal(run.status) ? '查看结果' : '恢复执行' }}
              <ArrowRightOutlined aria-hidden="true" />
            </RouterLink>
          </li>
        </ul>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { Alert as AAlert, Modal } from 'ant-design-vue'
import { ArrowLeftOutlined, ArrowRightOutlined, HistoryOutlined } from '@ant-design/icons-vue'
import SkillExecuteSurface from './components/SkillExecuteSurface.vue'
import { listSkillExecutions } from '@/api/skillController'
import { formatDateTime, formatDuration } from '@/utils/date'

const route = useRoute()
const router = useRouter()
const surfaceRef = ref<InstanceType<typeof SkillExecuteSurface> | null>(null)

const skillName = computed(() => String(route.params.skillName || ''))
const restoreExecutionId = computed(() => String(route.query.executionId || ''))

const syncExecutionId = (executionId: string) => {
  const query = {
    ...route.query,
  }
  if (executionId) {
    query.executionId = executionId
  } else {
    delete query.executionId
  }
  router.replace({
    query,
  })
}

// ── 最近执行 ──

const recentRuns = ref<API.SkillExecutionVO[]>([])
const recentRunsLoading = ref(false)
const recentRunsError = ref('')

let recentSeq = 0

const statusMap: Record<string, { label: string; cls: string }> = {
  PENDING: { label: '等待中', cls: 'pending' },
  RUNNING: { label: '执行中', cls: 'running' },
  AWAITING_CONFIRMATION: { label: '待确认', cls: 'awaiting' },
  SUCCESS: { label: '成功', cls: 'success' },
  FAILED: { label: '失败', cls: 'failed' },
}

const statusLabel = (s?: string) => statusMap[s ?? '']?.label ?? s ?? '—'
const statusClass = (s?: string) => statusMap[s ?? '']?.cls ?? 'unknown'
const isTerminal = (s?: string) => s === 'SUCCESS' || s === 'FAILED'

const loadRecentRuns = async () => {
  if (!skillName.value) return
  recentRunsLoading.value = true
  recentRunsError.value = ''
  const seq = ++recentSeq
  try {
    const res = await listSkillExecutions({
      skillName: skillName.value,
      current: 1,
      pageSize: 5,
    })
    if (seq !== recentSeq) return
    if (res.data.code === 0 && res.data.data?.records) {
      recentRuns.value = res.data.data.records
    } else {
      throw new Error(res.data.message || '加载失败')
    }
  } catch (e) {
    if (seq !== recentSeq) return
    recentRunsError.value = e instanceof Error ? e.message : '最近执行加载失败'
  } finally {
    if (seq === recentSeq) recentRunsLoading.value = false
  }
}

watch(skillName, () => {
  recentRuns.value = []
  recentRunsError.value = ''
  // 切换技能时清除可能属于上一技能的 executionId，避免误恢复
  if (route.query.executionId) {
    syncExecutionId('')
  }
  void loadRecentRuns()
})

onMounted(() => {
  void loadRecentRuns()
})

onBeforeRouteLeave(() => {
  if (!surfaceRef.value?.isExecuting) return true
  return new Promise<boolean>((resolve) => {
    Modal.confirm({
      title: '任务仍在执行',
      content: '离开后任务会继续在后台运行，可通过当前执行链接恢复。',
      okText: '继续离开',
      cancelText: '留在页面',
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    })
  })
})
</script>

<style scoped>
.skill-execute-page {
  min-height: calc(100dvh - 64px);
  padding: 30px 20px 72px;
  background: var(--color-background-secondary);
}

.execute-shell {
  max-width: 1040px;
  margin: 0 auto;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 40px;
  margin-bottom: 18px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.back-link:hover,
.back-link:focus-visible {
  color: var(--color-primary-dark);
}

.page-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

.history-nav {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 40px;
  padding: 0 4px;
  color: var(--color-text-secondary);
  font-size: 14px;
  text-decoration: none;

  &:hover {
    color: var(--color-primary-dark);
  }
}

/* ── 最近执行 ── */

.recent-runs {
  margin-top: 32px;
  padding: 20px 24px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: white;
}

.recent-runs-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;

  h2 {
    margin: 0;
    color: var(--color-text);
    font-size: 16px;
  }

  a {
    color: var(--color-primary-dark);
    font-size: 12px;
    font-weight: 600;
    text-decoration: none;

    &:hover {
      text-decoration: underline;
    }
  }
}

.recent-runs-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.recent-runs-state {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.recent-runs-empty {
  margin: 0;
  padding: 10px 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.recent-runs-list li {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 0;
  border-top: 1px solid var(--color-border);

  &:first-child {
    border-top: 0;
  }
}

.run-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  min-width: 48px;

  &::before {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    content: '';
  }
}

.run-status--success { color: var(--state-success-text); &::before { background: var(--color-success); } }
.run-status--failed { color: var(--state-error-text); &::before { background: var(--color-error); } }
.run-status--running { color: var(--state-info-text); &::before { background: var(--color-info); } }
.run-status--pending { color: var(--text-disabled); &::before { background: var(--text-disabled); } }
.run-status--awaiting { color: #d97706; &::before { background: #f59e0b; } }
.run-status--unknown { color: var(--text-disabled); &::before { background: var(--text-disabled); } }

.run-meta {
  color: var(--color-text-muted);
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
  white-space: nowrap;
}

.run-restore {
  margin-left: auto;
  color: var(--color-primary-dark);
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;

  &:hover {
    text-decoration: underline;
  }
}

@media (max-width: 600px) {
  .skill-execute-page {
    padding: 22px 14px 56px;
  }

  .recent-runs {
    padding: 16px;
  }

  .run-meta {
    display: none;
  }
}
</style>
