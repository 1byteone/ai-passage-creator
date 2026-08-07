<template>
  <section class="skill-chain-page" aria-labelledby="chain-title">
    <div class="chain-shell">
      <button class="back-link" type="button" @click="router.push('/skill')">
        <ArrowLeftOutlined aria-hidden="true" />
        返回技能中心
      </button>

      <header class="chain-heading">
        <p>AI 工具</p>
        <h1 id="chain-title">Skill 链式编排</h1>
        <span>串联多个技能，前一个的输出自动作为后一个的输入。</span>
      </header>

      <!-- 技能选择 -->
      <section v-if="!running && !completed" class="chain-setup">
        <div class="setup-card">
          <h2>1. 选择执行顺序</h2>
          <p class="setup-hint">按从上到下的顺序执行，至少选择 2 个技能。</p>
          <div class="skill-picker">
            <label
              v-for="skill in availableSkills"
              :key="skill.name"
              class="pick-item"
              :class="{ picked: selected.includes(skill.name) }"
            >
              <input
                v-model="selected"
                type="checkbox"
                :value="skill.name"
                :disabled="running"
              />
              <span>{{ getSkillUiConfig(skill.name).title }}</span>
              <small>{{ getSkillUiConfig(skill.name).description }}</small>
            </label>
          </div>
          <p v-if="selected.length < 2" class="pick-warn">请至少选择 2 个技能</p>
        </div>

        <div class="setup-card">
          <h2>2. 初始输入</h2>
          <p class="setup-hint">这些输入会传给第一个技能；各技能所需字段在后续步骤补充。</p>
          <a-textarea
            v-model="initialInputText"
            placeholder='JSON 格式，如 {"articleContent":"文章内容","platform":"twitter"}'
            :rows="5"
            :disabled="running"
          />
          <p v-if="inputError" class="pick-warn">{{ inputError }}</p>
        </div>

        <div class="setup-card chain-actions">
          <a-button
            type="primary"
            :loading="running"
            :disabled="selected.length < 2"
            @click="startChain"
          >
            <template #icon><PlayCircleOutlined /></template>
            开始链式执行
          </a-button>
        </div>
      </section>

      <!-- 执行中 -->
      <section v-if="running" class="chain-running">
        <a-spin />
        <p class="running-text">正在执行 {{ currentStep }} / {{ selected.length }} ...</p>
        <div v-for="(skillName, index) in selected" :key="skillName" class="chain-step">
          <span :class="['step-dot', index < currentStepIndex ? 'done' : index === currentStepIndex ? 'active' : '']" />
          <span class="step-name">{{ getSkillUiConfig(skillName).title }}</span>
          <span v-if="index < currentStepIndex" class="step-status done">✓</span>
          <span v-else-if="index === currentStepIndex" class="step-status running">…</span>
        </div>
      </section>

      <!-- 完成 -->
      <section v-if="completed" class="chain-result">
        <a-alert type="success" show-icon message="链式执行完成" />
        <div v-for="(skillName, index) in selected" :key="skillName" class="result-block">
          <div class="result-heading">
            <h2>{{ index + 1 }}. {{ getSkillUiConfig(skillName).title }}</h2>
          </div>
          <pre class="result-pre">{{ formatOutput(chainOutputs[skillName]) }}</pre>
        </div>
        <div class="chain-actions">
          <a-button @click="reset">重新编排</a-button>
        </div>
      </section>

      <!-- 失败 -->
      <a-alert v-if="error" type="error" show-icon :message="error" class="chain-error">
        <template #action>
          <a-button size="small" danger @click="reset">重试</a-button>
        </template>
      </a-alert>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeftOutlined, PlayCircleOutlined } from '@ant-design/icons-vue'
import { getSkillUiConfig } from '@/config/skill'
import { connectChainSSE, type SkillSSEConnection } from '@/utils/sse'

const router = useRouter()
const skills = ref<API.SkillSummary[]>([])
const selected = ref<string[]>([])
const initialInputText = ref('')
const inputError = ref('')
const running = ref(false)
const completed = ref(false)
const error = ref('')
const currentStepIndex = ref(0)
const chainOutputs = ref<Record<string, unknown>>({})

let sseConnection: SkillSSEConnection | null = null
let unmounted = false

const availableSkills = computed(() => skills.value)

const currentStep = computed(() => (currentStepIndex.value >= selected.value.length
  ? selected.value.length
  : currentStepIndex.value + 1))

const loadSkills = async () => {
  try {
    const response = await fetch('/api/skill/list', {
      headers: { Accept: 'application/json' },
      credentials: 'include',
    })
    const payload = (await response.json()) as API.BaseResponseSkillSummaryList
    if (payload.code !== 0) throw new Error(payload.message || '加载失败')
    skills.value = payload.data || []
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载技能列表失败'
  }
}

const startChain = async () => {
  error.value = ''
  inputError.value = ''

  let inputs: Record<string, unknown> = {}
  if (initialInputText.value.trim()) {
    try {
      inputs = JSON.parse(initialInputText.value)
    } catch {
      inputError.value = '初始输入不是合法 JSON'
      return
    }
  }

  running.value = true
  currentStepIndex.value = 0
  try {
    const response = await fetch('/api/skill/chain/execute', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ skillNames: selected.value, inputs }),
    })
    const payload = await response.json()
    if (payload.code !== 0) {
      throw new Error(payload.message || '链式执行失败')
    }
    const chainId = payload.data?.chainId as string | undefined
    if (!chainId) throw new Error('未能获取链式执行 ID')
    if (unmounted) return
    openChainSSE(chainId)
  } catch (e) {
    if (unmounted) return
    error.value = e instanceof Error ? e.message : '链式执行失败'
    running.value = false
  }
}

const openChainSSE = (chainId: string) => {
  sseConnection?.close()
  sseConnection = connectChainSSE(chainId, {
    onMessage: handleChainEvent,
    onFallback: () => {
      error.value = '链式执行进度连接中断，请重新发起'
      running.value = false
    },
    onParseError: () => {
      error.value = '收到无法识别的链式进度消息'
      running.value = false
    },
  })
}

const handleChainEvent = (event: API.SkillProgressEvent) => {
  if (event.type === 'chain.complete') {
    chainOutputs.value = (event.outputData as Record<string, unknown>) || {}
    currentStepIndex.value = selected.value.length
    running.value = false
    completed.value = true
    closeConnection()
    return
  }
  if (event.type === 'chain.error') {
    const failed = event.failedSkill
    error.value = failed
      ? `链式执行失败，失败环节：${getSkillUiConfig(failed).title}`
      : '链式执行失败'
    currentStepIndex.value = failed ? Math.max(0, selected.value.indexOf(failed)) : 0
    running.value = false
    closeConnection()
    return
  }
  if (event.skillName !== 'chain') {
    // 各 skill 自身终态：推进到已完成步骤
    if (event.type === 'skill.complete') {
      const idx = selected.value.indexOf(event.skillName)
      if (idx >= 0) currentStepIndex.value = Math.max(currentStepIndex.value, idx + 1)
    }
    return
  }
  // chain 级进度：phase=当前执行技能名，phaseIndex=1-based
  if (event.phase && selected.value.includes(event.phase)) {
    currentStepIndex.value = (event.phaseIndex ?? 1) - 1
  }
}

const closeConnection = () => {
  sseConnection?.close()
  sseConnection = null
}

const reset = () => {
  closeConnection()
  completed.value = false
  running.value = false
  error.value = ''
  chainOutputs.value = {}
  currentStepIndex.value = 0
}

const formatOutput = (value: unknown): string => {
  if (value === null || value === undefined) return '（无输出）'
  if (typeof value === 'string') return value
  return JSON.stringify(value, null, 2)
}

onMounted(loadSkills)
onBeforeUnmount(() => {
  unmounted = true
  closeConnection()
})
</script>

<style scoped>
.skill-chain-page {
  min-height: calc(100dvh - 64px);
  padding: 30px 20px 72px;
  background: var(--color-background-secondary);
}

.chain-shell {
  max-width: 880px;
  margin: 0 auto;
  display: grid;
  gap: 22px;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 40px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
  justify-self: start;
}

.chain-heading p {
  margin: 0 0 4px;
  color: var(--color-primary-dark);
  font-size: 12px;
  font-weight: 600;
}

.chain-heading h1 {
  margin: 0;
  color: var(--color-text);
  font-size: 24px;
}

.chain-heading span {
  color: var(--color-text-secondary);
  font-size: 14px;
}

.setup-card {
  padding: 22px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--surface-panel);
  display: grid;
  gap: 12px;
}

.setup-card h2 {
  margin: 0;
  font-size: 16px;
  color: var(--color-text);
}

.setup-hint {
  margin: 0;
  color: var(--color-text-muted);
  font-size: 13px;
}

.skill-picker {
  display: grid;
  gap: 8px;
}

.pick-item {
  display: grid;
  grid-template-columns: auto 1fr;
  grid-template-rows: auto auto;
  column-gap: 10px;
  align-items: start;
  padding: 12px 14px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: border-color 0.2s;
}

.pick-item input {
  grid-row: 1 / 3;
  margin-top: 2px;
}

.pick-item span {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

.pick-item small {
  font-size: 12px;
  color: var(--color-text-muted);
}

.pick-item.picked {
  border-color: var(--color-primary);
  background: #fff8f0;
}

.pick-warn {
  margin: 0;
  color: #cf1322;
  font-size: 13px;
}

.chain-actions {
  justify-content: flex-end;
}

.chain-running {
  padding: 30px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--surface-panel);
  display: grid;
  justify-items: center;
  gap: 14px;
}

.running-text {
  margin: 0;
  color: var(--color-text-secondary);
}

.chain-step {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 0;
  width: 100%;
}

.step-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--color-border);
}

.step-dot.done {
  background: var(--color-primary);
}

.step-dot.active {
  background: var(--color-primary-dark);
  box-shadow: 0 0 0 3px rgba(250, 140, 22, 0.2);
}

.step-name {
  flex: 1;
  font-size: 14px;
  color: var(--color-text);
}

.step-status.done {
  color: var(--color-primary);
}

.step-status.running {
  color: var(--color-text-muted);
}

.chain-result {
  display: grid;
  gap: 14px;
}

.result-block {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--surface-panel);
  overflow: hidden;
}

.result-heading {
  padding: 12px 18px;
  border-bottom: 1px solid var(--color-border);
}

.result-heading h2 {
  margin: 0;
  font-size: 15px;
  color: var(--color-text);
}

.result-pre {
  margin: 0;
  padding: 16px 18px;
  max-height: 320px;
  overflow: auto;
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.chain-error {
  margin-top: 8px;
}

@media (max-width: 600px) {
  .skill-chain-page {
    padding: 22px 14px 56px;
  }
}
</style>
