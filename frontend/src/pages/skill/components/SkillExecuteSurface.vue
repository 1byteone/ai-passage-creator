<template>
  <section class="execute-surface" :class="{ embedded }">
    <a-skeleton v-if="loadingDefinition" active :paragraph="{ rows: 7 }" />

    <a-result
      v-else-if="definitionError"
      status="404"
<<<<<<< HEAD
      sub-title="技能可能尚未公开，或定义加载失败。"
    >
      <template #title>
        <component :is="embedded ? 'h2' : 'h1'" class="result-title">
          这个技能暂不可用
        </component>
      </template>
=======
      title="这个技能暂不可用"
      sub-title="技能可能尚未公开，或定义加载失败。"
    >
>>>>>>> master
      <template #extra>
        <a-button @click="loadDefinition">重新加载</a-button>
      </template>
    </a-result>

    <template v-else-if="definition">
<<<<<<< HEAD
      <header v-if="!embedded && state !== 'INPUT'" class="surface-context">
        <span>{{ uiConfig.categoryLabel }}</span>
        <h1>{{ uiConfig.title }}</h1>
      </header>

      <div v-if="state === 'INPUT'" class="input-layout">
        <div class="surface-intro">
          <span>{{ uiConfig.categoryLabel }}</span>
          <component :is="embedded ? 'h2' : 'h1'" class="surface-title">
            {{ uiConfig.title }}
          </component>
=======
      <div v-if="state === 'INPUT'" class="input-layout">
        <div class="surface-intro">
          <span>{{ uiConfig.categoryLabel }}</span>
          <h2>{{ uiConfig.title }}</h2>
>>>>>>> master
          <p>{{ definition.description || uiConfig.description }}</p>
        </div>
        <div class="form-panel">
          <SkillInputForm
            v-model="inputs"
            :definition="definition"
            :skill-name="skillName"
            :action-label="uiConfig.actionLabel"
            :loading="submitting"
            @submit="startExecution"
          />
        </div>
      </div>

      <SkillProgress
        v-else-if="state === 'EXECUTING'"
        :phases="definition.phases || []"
        :phase-index="phaseIndex"
        :streamed-text="streamedText"
        :status-text="executionStatusText"
      />

<<<<<<< HEAD
      <div v-else-if="state === 'AWAITING_CONFIRMATION'" class="awaiting-layout">
        <div class="awaiting-card">
          <div class="awaiting-header">
            <ClockCircleOutlined class="awaiting-icon" />
            <h3>等待确认</h3>
          </div>
          <p class="awaiting-phase-label">
            当前阶段：<strong>{{ getPhaseLabel(awaitingPhase) }}</strong>
          </p>
          <p class="awaiting-hint">
            请审阅上一步的产出，确认后继续执行，或修改后重新生成。
          </p>

          <div v-if="pendingOutput" class="pending-output">
            <pre class="pending-json">{{ formatPendingOutput(pendingOutput) }}</pre>
          </div>

          <div class="awaiting-actions">
            <a-button
              v-if="supportsAction('approve')"
              type="primary"
              :loading="confirming"
              @click="handleConfirm('approve')"
            >
              批准并继续
            </a-button>
            <a-button
              v-if="supportsAction('modify')"
              :loading="confirming"
              @click="handleConfirm('modify')"
            >
              修改后继续
            </a-button>
            <a-button
              danger
              :disabled="confirming"
              @click="returnToInput"
            >
              取消
            </a-button>
          </div>
        </div>
      </div>

=======
>>>>>>> master
      <a-result
        v-else-if="state === 'FAILED'"
        status="error"
        title="执行没有完成"
        :sub-title="errorMessage"
      >
        <template #extra>
          <a-button type="primary" @click="returnToInput">调整输入</a-button>
          <a-button v-if="executionId" @click="resumeExecution(executionId)">重新检查状态</a-button>
        </template>
        <template #default>
          <button
            v-if="executionId"
            class="execution-reference"
            type="button"
            @click="copyExecutionId"
          >
            执行编号：{{ executionId }}
          </button>
        </template>
      </a-result>

      <div v-else-if="state === 'COMPLETED'" class="completed-layout">
        <div class="completion-bar">
          <div>
            <span><CheckCircleOutlined /> 已完成</span>
            <button type="button" @click="copyExecutionId">
              {{ executionId }}
            </button>
          </div>
          <a-button @click="returnToInput">
            <template #icon><EditOutlined /></template>
            调整输入
          </a-button>
        </div>
        <SkillResultRenderer
          :skill-name="skillName"
          :inputs="inputs"
          :output-data="outputData"
          :embedded="embedded"
          @select-topic="(option) => emit('topicSelect', option)"
        />
      </div>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
<<<<<<< HEAD
import { CheckCircleOutlined, ClockCircleOutlined, EditOutlined } from '@ant-design/icons-vue'
import { executeSkill, getSkillDefinition, getSkillResult, confirmSkill } from '@/api/skillController'
=======
import { CheckCircleOutlined, EditOutlined } from '@ant-design/icons-vue'
import { executeSkill, getSkillDefinition, getSkillResult } from '@/api/skillController'
>>>>>>> master
import { getFieldDefinition, getPhaseLabel, getSkillUiConfig } from '@/config/skill'
import { connectSkillSSE, type SkillSSEConnection } from '@/utils/sse'
import {
  applySkillProgressEvent,
  type SkillRuntimeSnapshot,
} from '@/utils/skillExecutionState'
import SkillInputForm from './SkillInputForm.vue'
import SkillProgress from './SkillProgress.vue'
import SkillResultRenderer from './SkillResultRenderer.vue'

<<<<<<< HEAD
type ExecutionState = 'INPUT' | 'EXECUTING' | 'AWAITING_CONFIRMATION' | 'COMPLETED' | 'FAILED'
=======
type ExecutionState = 'INPUT' | 'EXECUTING' | 'COMPLETED' | 'FAILED'
>>>>>>> master

const props = withDefaults(
  defineProps<{
    skillName: string
    initialInputs?: Record<string, unknown>
    restoreExecutionId?: string
    embedded?: boolean
  }>(),
  {
    initialInputs: () => ({}),
    restoreExecutionId: '',
    embedded: false,
  },
)

const emit = defineEmits<{
  stateChange: [state: ExecutionState, executionId?: string]
  executionChange: [executionId: string]
  complete: [payload: {
    skillName: string
    executionId: string
    inputs: Record<string, unknown>
    outputData: Record<string, unknown>
  }]
  topicSelect: [option: API.TopicOption]
  notFound: []
}>()

const definition = ref<API.SkillDefinition | null>(null)
const loadingDefinition = ref(true)
const definitionError = ref(false)
const inputs = ref<Record<string, unknown>>({})
const state = ref<ExecutionState>('INPUT')
const submitting = ref(false)
const executionId = ref('')
const currentPhase = ref('')
const phaseIndex = ref(1)
const streamedText = ref('')
const outputData = ref<Record<string, unknown>>({})
const errorMessage = ref('')
const pollingStartedAt = ref(0)
<<<<<<< HEAD
const awaitingPhase = ref('')
const pendingOutput = ref<unknown>(null)
const supportedActions = ref<Array<'approve' | 'modify'>>([])
const confirming = ref(false)
=======
>>>>>>> master

let sseConnection: SkillSSEConnection | null = null
let pollTimer: number | null = null

const uiConfig = computed(() => getSkillUiConfig(props.skillName))
const draftKey = computed(() => `skill:draft:${props.skillName}`)
const executionKey = computed(() => `skill:execution:${props.skillName}`)

const executionStatusText = computed(() => {
  if (Date.now() - pollingStartedAt.value > 10 * 60 * 1000 && pollingStartedAt.value) {
    return '任务仍在后台运行，页面会继续低频检查结果'
  }
  if (currentPhase.value) {
    return `正在${getPhaseLabel(currentPhase.value)}`
  }
  return '正在准备执行'
})

const setState = (nextState: ExecutionState) => {
  state.value = nextState
  emit('stateChange', nextState, executionId.value || undefined)
}

const readStorage = <T,>(key: string): T | null => {
  try {
    const value = sessionStorage.getItem(key)
    return value ? (JSON.parse(value) as T) : null
  } catch {
    return null
  }
}

const buildInitialInputs = (skillDefinition: API.SkillDefinition) => {
  const defaults = Object.entries(skillDefinition.variables || {}).reduce<Record<string, unknown>>(
    (result, [fieldName, rawDefinition]) => {
      const field = getFieldDefinition(props.skillName, fieldName, rawDefinition)
      result[fieldName] = field.defaultValue ?? ''
      return result
    },
    {},
  )
  const draft = readStorage<Record<string, unknown>>(draftKey.value) || {}
  inputs.value = {
    ...defaults,
    ...draft,
    ...props.initialInputs,
  }
}

const loadDefinition = async () => {
  loadingDefinition.value = true
  definitionError.value = false
  stopConnections()
  try {
    const response = await getSkillDefinition(props.skillName)
    if (response.data.code !== 0 || !response.data.data) {
      throw new Error(response.data.message || '技能定义加载失败')
    }
    definition.value = response.data.data
    buildInitialInputs(response.data.data)

    const restoreId =
      props.restoreExecutionId || readStorage<string>(executionKey.value) || ''
    if (restoreId) {
      await resumeExecution(restoreId)
    } else {
      setState('INPUT')
    }
  } catch {
    definitionError.value = true
    emit('notFound')
  } finally {
    loadingDefinition.value = false
  }
}

<<<<<<< HEAD
const supportsAction = (action: 'approve' | 'modify'): boolean => {
  return supportedActions.value.includes(action)
}

const handleConfirm = async (action: 'approve' | 'modify') => {
  if (!executionId.value) return
  confirming.value = true
  try {
    let modifiedData: string | undefined
    if (action === 'modify' && pendingOutput.value) {
      // modify 时将 pendingOutput 的 JSON 序列化后传回供用户修改
      modifiedData = JSON.stringify(pendingOutput.value, null, 2)
    }
    const response = await confirmSkill(executionId.value, {
      action,
      modifiedData,
    })
    if (response.data.code !== 0) {
      throw new Error(response.data.message || '确认失败')
    }
    // 确认成功，回到执行态继续接收 SSE 事件
    setState('EXECUTING')
    openSSE()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '确认失败，请重试'
    setState('FAILED')
  } finally {
    confirming.value = false
  }
}

const formatPendingOutput = (data: unknown): string => {
  try {
    return JSON.stringify(data, null, 2)
  } catch {
    return String(data)
  }
}

=======
>>>>>>> master
const startExecution = async () => {
  submitting.value = true
  errorMessage.value = ''
  outputData.value = {}
  streamedText.value = ''
  currentPhase.value = ''
  phaseIndex.value = 1
  stopConnections()
  try {
    const response = await executeSkill(props.skillName, {
      inputs: inputs.value,
    })
    if (response.data.code !== 0 || !response.data.data?.skillExecutionId) {
      throw new Error(response.data.message || '执行启动失败')
    }
    executionId.value = response.data.data.skillExecutionId
    sessionStorage.setItem(executionKey.value, JSON.stringify(executionId.value))
    emit('executionChange', executionId.value)
    setState('EXECUTING')
    openSSE()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '执行启动失败，请稍后重试'
    setState('FAILED')
  } finally {
    submitting.value = false
  }
}

const openSSE = () => {
  sseConnection?.close()
  sseConnection = connectSkillSSE(executionId.value, {
    onMessage: handleProgressEvent,
    onFallback: startPolling,
    onParseError: () => {
      errorMessage.value = '收到无法识别的进度消息，正在改用结果查询'
    },
  })
}

const handleProgressEvent = (event: API.SkillProgressEvent) => {
  const snapshot = applySkillProgressEvent(
    {
      phase: currentPhase.value,
      phaseIndex: phaseIndex.value,
      streamedText: streamedText.value,
      outputData: outputData.value,
      errorMessage: errorMessage.value,
      terminalState: null,
<<<<<<< HEAD
      awaiting: null,
=======
>>>>>>> master
    } satisfies SkillRuntimeSnapshot,
    event,
    definition.value?.phases || [],
  )

  currentPhase.value = snapshot.phase
  phaseIndex.value = snapshot.phaseIndex
  streamedText.value = snapshot.streamedText
  outputData.value = snapshot.outputData
  errorMessage.value = snapshot.errorMessage

<<<<<<< HEAD
  if (snapshot.awaiting) {
    awaitingPhase.value = snapshot.awaiting.phase
    pendingOutput.value = snapshot.awaiting.pendingOutput ?? null
    supportedActions.value = snapshot.awaiting.supportedActions
    setState('AWAITING_CONFIRMATION')
  } else if (snapshot.terminalState === 'COMPLETED') {
=======
  if (snapshot.terminalState === 'COMPLETED') {
>>>>>>> master
    completeExecution(snapshot.outputData)
  } else if (snapshot.terminalState === 'FAILED') {
    setState('FAILED')
  }
}

const resumeExecution = async (id: string) => {
  executionId.value = id
  sessionStorage.setItem(executionKey.value, JSON.stringify(id))
  emit('executionChange', id)
  setState('EXECUTING')
  stopConnections()
  const terminal = await refreshResult()
  if (!terminal) {
    openSSE()
  }
}

const refreshResult = async (): Promise<boolean> => {
  try {
    const response = await getSkillResult(executionId.value)
    const result = response.data.data
    if (response.data.code !== 0 || !result) {
      return false
    }
    if (result.status === 'SUCCESS') {
      if (result.inputData) {
        inputs.value = result.inputData
      }
      completeExecution(result.outputData || {})
      return true
    }
    if (result.status === 'FAILED') {
      errorMessage.value = result.errorMessage || '执行失败，请调整输入后重试'
      setState('FAILED')
      return true
    }
    if (result.status === 'NOT_FOUND') {
      errorMessage.value = '执行记录不存在或已经过期'
      setState('FAILED')
      return true
    }
<<<<<<< HEAD
    if (result.status === 'AWAITING_CONFIRMATION') {
      // 轮询恢复时发现执行处于等待确认态
      awaitingPhase.value = result.phase || ''
      pendingOutput.value = result.outputData ?? null
      supportedActions.value = ['approve', 'modify']
      setState('AWAITING_CONFIRMATION')
      return true
    }
=======
>>>>>>> master
    if (result.phase) {
      currentPhase.value = result.phase
      const index = definition.value?.phases?.findIndex((phase) => phase.name === result.phase) ?? -1
      phaseIndex.value = index >= 0 ? index + 1 : phaseIndex.value
    }
    return false
  } catch {
    return false
  }
}

const startPolling = () => {
  if (pollTimer !== null) return
  pollingStartedAt.value = Date.now()

  const poll = async () => {
    pollTimer = null
    const terminal = await refreshResult()
    if (terminal || state.value !== 'EXECUTING') return
    const elapsed = Date.now() - pollingStartedAt.value
    pollTimer = window.setTimeout(poll, elapsed > 10 * 60 * 1000 ? 5000 : 2000)
  }

  pollTimer = window.setTimeout(poll, 500)
}

const completeExecution = (result: Record<string, unknown>) => {
  outputData.value = result
  sessionStorage.removeItem(draftKey.value)
  streamedText.value = ''
  phaseIndex.value = definition.value?.phases?.length
    ? definition.value.phases.length + 1
    : phaseIndex.value
  stopConnections()
  setState('COMPLETED')
  emit('complete', {
    skillName: props.skillName,
    executionId: executionId.value,
    inputs: inputs.value,
    outputData: outputData.value,
  })
}

const returnToInput = () => {
  stopConnections()
  errorMessage.value = ''
<<<<<<< HEAD
  awaitingPhase.value = ''
  pendingOutput.value = null
  supportedActions.value = []
  confirming.value = false
=======
>>>>>>> master
  sessionStorage.setItem(draftKey.value, JSON.stringify(inputs.value))
  sessionStorage.removeItem(executionKey.value)
  executionId.value = ''
  emit('executionChange', '')
  setState('INPUT')
}

const copyExecutionId = async () => {
  if (!executionId.value) return
  try {
    await navigator.clipboard.writeText(executionId.value)
    message.success('执行编号已复制')
  } catch {
    message.error('复制失败')
  }
}

const stopConnections = () => {
  sseConnection?.close()
  sseConnection = null
  if (pollTimer !== null) {
    window.clearTimeout(pollTimer)
    pollTimer = null
  }
}

const beforeUnload = (event: BeforeUnloadEvent) => {
  if (state.value === 'EXECUTING') {
    event.preventDefault()
  }
}

watch(
  inputs,
  (value) => {
    if (state.value === 'INPUT') {
      sessionStorage.setItem(draftKey.value, JSON.stringify(value))
    }
  },
  { deep: true },
)

watch(
  () => props.skillName,
  () => loadDefinition(),
)

watch(
  () => props.initialInputs,
  (value) => {
    inputs.value = { ...inputs.value, ...value }
  },
  { deep: true },
)

onMounted(() => {
  window.addEventListener('beforeunload', beforeUnload)
  loadDefinition()
})

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', beforeUnload)
  stopConnections()
})

defineExpose({
  isExecuting: computed(() => state.value === 'EXECUTING'),
  executionId,
})
</script>

<style scoped>
.execute-surface {
  width: 100%;
}

.input-layout,
<<<<<<< HEAD
.completed-layout,
.awaiting-layout {
=======
.completed-layout {
>>>>>>> master
  display: grid;
  gap: 24px;
}

.surface-intro span {
  color: var(--color-primary-dark);
  font-size: 12px;
  font-weight: 600;
}

<<<<<<< HEAD
.surface-title {
=======
.surface-intro h2 {
>>>>>>> master
  margin: 6px 0 8px;
  color: var(--color-text);
  font-size: 24px;
}

<<<<<<< HEAD
.result-title {
  margin: 0;
  color: inherit;
  font-size: inherit;
  font-weight: inherit;
}

.surface-context {
  margin-bottom: 18px;
}

.surface-context span {
  color: var(--color-primary-dark);
  font-size: 12px;
  font-weight: 600;
}

.surface-context h1 {
  margin: 6px 0 0;
  color: var(--color-text);
  font-size: 24px;
}

=======
>>>>>>> master
.surface-intro p {
  max-width: 680px;
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.form-panel {
  padding: 24px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: white;
}

<<<<<<< HEAD
.awaiting-card {
  padding: 32px 24px 24px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: white;
  text-align: center;
}

.awaiting-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-bottom: 8px;
}

.awaiting-header h3 {
  margin: 0;
  font-size: 18px;
  color: var(--color-text);
}

.awaiting-icon {
  font-size: 22px;
  color: var(--color-primary, #fa8c16);
}

.awaiting-phase-label {
  color: var(--color-text-secondary);
  font-size: 14px;
  margin: 0 0 4px;
}

.awaiting-hint {
  color: var(--color-text-muted);
  font-size: 13px;
  margin: 0 0 20px;
}

.pending-output {
  max-height: 300px;
  overflow-y: auto;
  margin-bottom: 20px;
  padding: 12px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-background-secondary);
  text-align: left;
}

.pending-json {
  margin: 0;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}

.awaiting-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  flex-wrap: wrap;
}

=======
>>>>>>> master
.completion-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-border);
}

.completion-bar > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.completion-bar span {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--color-primary-dark);
  font-size: 14px;
  font-weight: 600;
}

.completion-bar button,
.execution-reference {
  width: fit-content;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-text-muted);
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 11px;
  cursor: pointer;
}

<<<<<<< HEAD
.embedded .surface-title {
=======
.embedded .surface-intro h2 {
>>>>>>> master
  font-size: 20px;
}

.embedded .form-panel {
  padding: 18px;
}

@media (max-width: 600px) {
  .form-panel {
    padding: 18px 16px;
  }

  .completion-bar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
