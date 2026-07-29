<template>
  <a-drawer
    :open="open"
    :width="drawerWidth"
    placement="right"
    :destroy-on-close="false"
    :title="uiConfig.title"
    class="skill-launcher-drawer"
    @close="emit('update:open', false)"
  >
    <SkillExecuteSurface
      :skill-name="skillName"
      :initial-inputs="initialInputs"
      embedded
      @state-change="handleStateChange"
      @complete="(payload) => emit('complete', payload)"
      @topic-select="(option) => emit('topicSelect', option)"
    />
  </a-drawer>

  <button
    v-if="!open && executionState === 'EXECUTING'"
    type="button"
    class="running-bar"
    @click="emit('update:open', true)"
  >
    <LoadingOutlined spin />
    <span>{{ uiConfig.title }}仍在后台执行</span>
    <ArrowRightOutlined />
  </button>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
<<<<<<< HEAD
import { Drawer as ADrawer } from 'ant-design-vue'
=======
>>>>>>> master
import { ArrowRightOutlined, LoadingOutlined } from '@ant-design/icons-vue'
import { getSkillUiConfig } from '@/config/skill'
import SkillExecuteSurface from './SkillExecuteSurface.vue'

<<<<<<< HEAD
type ExecutionState = 'INPUT' | 'EXECUTING' | 'AWAITING_CONFIRMATION' | 'COMPLETED' | 'FAILED'
=======
type ExecutionState = 'INPUT' | 'EXECUTING' | 'COMPLETED' | 'FAILED'
>>>>>>> master

const props = withDefaults(
  defineProps<{
    open: boolean
    skillName: string
    initialInputs?: Record<string, unknown>
  }>(),
  {
    initialInputs: () => ({}),
  },
)

const emit = defineEmits<{
  'update:open': [value: boolean]
  complete: [payload: {
    skillName: string
    executionId: string
    inputs: Record<string, unknown>
    outputData: Record<string, unknown>
  }]
  topicSelect: [option: API.TopicOption]
}>()

const executionState = ref<ExecutionState>('INPUT')
const uiConfig = computed(() => getSkillUiConfig(props.skillName))
const drawerWidth = 'min(720px, 100vw)'

<<<<<<< HEAD
const handleStateChange = (nextState: ExecutionState, _executionId?: string) => {
=======
const handleStateChange = (nextState: ExecutionState) => {
>>>>>>> master
  executionState.value = nextState
}
</script>

<style scoped>
.running-bar {
  position: fixed;
  right: 20px;
  bottom: 20px;
  z-index: 90;
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 48px;
  padding: 0 16px;
  border: 1px solid #86efac;
  border-radius: var(--radius-md);
  background: white;
  color: var(--color-text);
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.16);
  cursor: pointer;
}

.running-bar .anticon:first-child {
  color: var(--color-primary);
}

@media (max-width: 600px) {
  .running-bar {
    right: 12px;
    bottom: 12px;
    left: 12px;
    justify-content: center;
  }
}
</style>
