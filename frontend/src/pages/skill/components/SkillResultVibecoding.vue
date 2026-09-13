<template>
  <section class="vibecoding-result" aria-label="Vibecoding PM 工作流结果">
    <!-- 阶段一：流程大纲 -->
    <template v-if="flowGroups.length">
      <header class="section-head">
        <div>
          <p class="kicker">阶段一 · 流程大纲</p>
          <h2>{{ flowProjectName || '流程草案' }}</h2>
          <p class="summary">
            {{ flowGroups.length }} 个分组 / {{ flowItemCount }} 个页面状态
          </p>
        </div>
      </header>
      <p v-if="flowSummary" class="flow-summary">{{ flowSummary }}</p>

      <div v-for="(group, gi) in flowGroups" :key="`flow-${gi}`" class="flow-group">
        <h3>{{ group.name }}</h3>
        <ol class="flow-list">
          <li v-for="(item, ii) in group.items" :key="`flow-${gi}-${ii}`" class="flow-item">
            <span class="flow-index">{{ String(ii + 1).padStart(2, '0') }}</span>
            <div class="flow-body">
              <div class="flow-title">
                <strong>{{ item.pageName }}</strong>
                <a-tag v-if="item.state">{{ item.state }}</a-tag>
              </div>
              <p class="flow-meta">
                <span>操作：{{ item.action || '—' }}</span>
                <span>→ {{ item.next || '—' }}</span>
              </p>
            </div>
          </li>
        </ol>
      </div>

      <a-alert
        v-if="unconfirmed.length"
        type="warning"
        show-icon
        class="unconfirmed"
        message="待确认事项（PRD 未说明，需人工补充）"
        :description="unconfirmed.join('；')"
      />
    </template>

    <!-- 阶段二：巡查清单 -->
    <template v-if="auditGroups.length">
      <header class="section-head">
        <div>
          <p class="kicker">阶段二 · 上线前巡查</p>
          <h2>{{ auditTitle || '巡查清单' }}</h2>
          <p class="summary">{{ auditItemCount }} 个待验证项 · 已标记 {{ markedCount }}</p>
        </div>
        <div class="head-actions">
          <a-button size="small" @click="copyAll">复制</a-button>
          <a-button size="small" type="primary" @click="downloadMarkdown">导出 Markdown</a-button>
          <a-button size="small" @click="downloadJson">导出 JSON</a-button>
        </div>
      </header>

      <div v-for="(group, gi) in auditGroups" :key="`audit-${gi}`" class="audit-group">
        <h3>{{ group.name }}</h3>
        <article v-for="item in group.items" :key="item.id" class="audit-card">
          <header class="audit-head">
            <div>
              <p class="kicker">{{ item.state || '状态' }}</p>
              <h4>{{ item.pageName || item.id }}</h4>
            </div>
            <a-tag :color="verdictColor(verdicts[item.id])">{{ verdictLabel(verdicts[item.id]) }}</a-tag>
          </header>

          <dl class="audit-fields">
            <div><dt>复现</dt><dd>{{ item.reproduce || '—' }}</dd></div>
            <div><dt>预期</dt><dd>{{ item.expected || '—' }}</dd></div>
          </dl>

          <a-textarea
            v-model:value="feedback[item.id]"
            :rows="2"
            placeholder="记录实际结果、问题证据或验收意见"
            :aria-label="`${item.pageName || item.id} 的反馈`"
          />

          <footer class="audit-foot">
            <div class="verdict-actions">
              <a-button size="small" :type="verdicts[item.id] === 'pass' ? 'primary' : 'default'" @click="setVerdict(item.id, 'pass')">
                可以
              </a-button>
              <a-button size="small" :type="verdicts[item.id] === 'todo' ? 'primary' : 'default'" @click="setVerdict(item.id, 'todo')">
                待改
              </a-button>
              <a-button size="small" danger :type="verdicts[item.id] === 'blocked' ? 'primary' : 'default'" @click="setVerdict(item.id, 'blocked')">
                阻塞
              </a-button>
            </div>
            <span v-if="item.priority" class="priority">优先级 {{ item.priority }}</span>
          </footer>
        </article>
      </div>
    </template>

    <a-empty
      v-if="!flowGroups.length && !auditGroups.length"
      description="尚无结构化结果"
    >
      <span class="empty-hint">阶段输出需要是合法 JSON；解析失败时请查看执行历史中的原始输出。</span>
    </a-empty>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive } from 'vue'
import { Alert as AAlert, Button as AButton, Empty as AEmpty, Tag as ATag, Textarea as ATextarea, message } from 'ant-design-vue'
import { copyResultText } from '@/utils/resultActions'

type Verdict = 'pass' | 'todo' | 'blocked'

interface FlowItem {
  pageName?: string
  state?: string
  action?: string
  next?: string
}

interface FlowGroup {
  name?: string
  items?: FlowItem[]
}

interface AuditItem {
  id: string
  pageName?: string
  state?: string
  reproduce?: string
  expected?: string
  priority?: string
}

interface AuditGroup {
  name?: string
  items?: AuditItem[]
}

const props = defineProps<{
  outputData: Record<string, unknown>
}>()

const verdicts = reactive<Record<string, Verdict>>({})
const feedback = reactive<Record<string, string>>({})

/** 阶段输出可能是对象，也可能是被 parser 包了一层的字符串，统一解包 */
function asRecord(value: unknown): Record<string, unknown> | null {
  if (value && typeof value === 'object') return value as Record<string, unknown>
  return null
}

const flow = computed(() => asRecord(props.outputData.flowDraft))
const audit = computed(() => asRecord(props.outputData.auditPlan))

const flowGroups = computed<FlowGroup[]>(() => {
  const groups = flow.value?.groups
  return Array.isArray(groups) ? (groups as FlowGroup[]) : []
})
const flowProjectName = computed(() => String(flow.value?.projectName ?? ''))
const flowSummary = computed(() => String(flow.value?.summary ?? ''))
const flowItemCount = computed(() =>
  flowGroups.value.reduce((sum, g) => sum + (g.items?.length ?? 0), 0),
)
const unconfirmed = computed<string[]>(() => {
  const list = flow.value?.unconfirmed
  return Array.isArray(list) ? list.map(String) : []
})

const auditGroups = computed<AuditGroup[]>(() => {
  const groups = audit.value?.groups
  return Array.isArray(groups) ? (groups as AuditGroup[]) : []
})
const auditTitle = computed(() => String(audit.value?.auditTitle ?? ''))
const auditItemCount = computed(() =>
  auditGroups.value.reduce((sum, g) => sum + (g.items?.length ?? 0), 0),
)
const markedCount = computed(() => Object.keys(verdicts).length)

const verdictLabel = (v?: Verdict) =>
  v === 'pass' ? '可以' : v === 'todo' ? '待改' : v === 'blocked' ? '阻塞' : '未标记'
const verdictColor = (v?: Verdict) =>
  v === 'pass' ? 'green' : v === 'todo' ? 'orange' : v === 'blocked' ? 'red' : 'default'

function setVerdict(id: string, verdict: Verdict) {
  // 再次点击同一标记视为取消，避免误点后无法回退
  verdicts[id] = verdicts[id] === verdict ? (undefined as unknown as Verdict) : verdict
}

function buildMarkdown(): string {
  const lines: string[] = []
  if (flowGroups.value.length) {
    lines.push(`# ${flowProjectName.value || '流程大纲'}`, '')
    if (flowSummary.value) lines.push(flowSummary.value, '')
    flowGroups.value.forEach((g) => {
      lines.push(`## ${g.name ?? '未分组'}`, '')
      ;(g.items ?? []).forEach((item, i) => {
        lines.push(
          `${i + 1}. **${item.pageName ?? '—'}**（${item.state ?? '—'}）`,
          `   - 操作：${item.action ?? '—'}`,
          `   - 下一步：${item.next ?? '—'}`,
        )
      })
      lines.push('')
    })
    if (unconfirmed.value.length) {
      lines.push('### 待确认', ...unconfirmed.value.map((u) => `- ${u}`), '')
    }
  }
  if (auditGroups.value.length) {
    lines.push(`# ${auditTitle.value || '巡查清单'}`, '')
    auditGroups.value.forEach((g) => {
      lines.push(`## ${g.name ?? '未分组'}`, '')
      ;(g.items ?? []).forEach((item) => {
        lines.push(
          `### ${item.pageName ?? item.id} · ${item.state ?? '—'}`,
          `- 标记：${verdictLabel(verdicts[item.id])}`,
          `- 复现：${item.reproduce ?? '—'}`,
          `- 预期：${item.expected ?? '—'}`,
          `- 反馈：${feedback[item.id] || '未填写'}`,
          '',
        )
      })
    })
  }
  return lines.join('\n')
}

const copyAll = async () => {
  await copyResultText(buildMarkdown())
  message.success('已复制到剪贴板')
}

function download(name: string, content: string, type: string) {
  const blob = new Blob([content], { type })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  document.body.appendChild(a)
  a.click()
  window.setTimeout(() => {
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }, 100)
}

const downloadMarkdown = () =>
  download(`${auditTitle.value || 'vibecoding-audit'}.md`, buildMarkdown(), 'text/markdown;charset=utf-8')

const downloadJson = () =>
  download(
    `${auditTitle.value || 'vibecoding-audit'}.json`,
    JSON.stringify({ flowDraft: flow.value, auditPlan: audit.value, verdicts, feedback }, null, 2),
    'application/json;charset=utf-8',
  )
</script>

<style scoped>
.vibecoding-result {
  display: grid;
  gap: 20px;
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.section-head h2 {
  margin: 0;
  font-size: 20px;
}

.kicker {
  margin: 0 0 4px;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.summary,
.flow-summary {
  margin: 4px 0 0;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.head-actions {
  display: flex;
  gap: 8px;
}

.flow-group h3,
.audit-group h3 {
  margin: 8px 0 10px;
  font-size: 15px;
}

.flow-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 8px;
}

.flow-item {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border);
}

.flow-index {
  padding-top: 3px;
  color: var(--color-text-secondary);
  font-family: var(--font-mono, monospace);
  font-size: 12px;
}

.flow-title {
  display: flex;
  align-items: center;
  gap: 8px;
}

.flow-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 6px 0 0;
  color: var(--color-text-secondary);
  font-size: 12px;
}

.unconfirmed {
  margin-top: 12px;
}

.audit-card {
  padding: 14px 0;
  border-bottom: 1px solid var(--color-border);
}

.audit-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.audit-head h4 {
  margin: 0;
  font-size: 15px;
}

.audit-fields {
  display: grid;
  gap: 6px;
  margin: 12px 0;
  font-size: 13px;
}

.audit-fields > div {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr);
  gap: 8px;
}

.audit-fields dt {
  color: var(--color-text-secondary);
}

.audit-fields dd {
  margin: 0;
}

.audit-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
}

.verdict-actions {
  display: flex;
  gap: 8px;
}

.priority {
  color: var(--color-text-secondary);
  font-size: 12px;
}

.empty-hint {
  display: block;
  margin-top: 8px;
  color: var(--color-text-secondary);
  font-size: 12px;
}

@media (max-width: 560px) {
  .section-head {
    flex-direction: column;
  }

  .head-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .audit-foot {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
