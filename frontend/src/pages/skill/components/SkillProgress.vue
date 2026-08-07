<template>
  <section class="progress-panel" aria-live="polite">
    <div class="progress-heading">
      <div>
        <p class="progress-kicker">执行进度</p>
        <h2>{{ statusText }}</h2>
      </div>
      <span class="phase-count">{{ completedCount }} / {{ phases.length }}</span>
    </div>

    <ol class="phase-list">
      <li
        v-for="(phase, index) in phases"
        :key="phase.name"
        :class="phaseState(index + 1)"
      >
        <span class="phase-marker" aria-hidden="true">
          <CheckOutlined v-if="index + 1 < phaseIndex" />
          <LoadingOutlined v-else-if="index + 1 === phaseIndex" spin />
          <span v-else>{{ index + 1 }}</span>
        </span>
        <span class="phase-label">{{ getPhaseLabel(phase.name) }}</span>
        <span class="phase-status">{{ phaseStatus(index + 1) }}</span>
      </li>
    </ol>

    <div v-if="streamedText" class="stream-preview">
      <div class="stream-header">
        <span>实时预览</span>
        <span>{{ streamedText.length }} 字符</span>
      </div>
      <pre>{{ streamedText }}</pre>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CheckOutlined, LoadingOutlined } from '@ant-design/icons-vue'
import { getPhaseLabel } from '@/config/skill'

const props = defineProps<{
  phases: API.SkillPhaseDefinition[]
  phaseIndex: number
  streamedText: string
  statusText: string
}>()

const completedCount = computed(() => Math.max(0, Math.min(props.phaseIndex - 1, props.phases.length)))

const phaseState = (index: number) => ({
  complete: index < props.phaseIndex,
  active: index === props.phaseIndex,
  pending: index > props.phaseIndex,
})

const phaseStatus = (index: number) => {
  if (index < props.phaseIndex) return '已完成'
  if (index === props.phaseIndex) return '执行中'
  return '等待中'
}
</script>

<style scoped>
.progress-panel {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--surface-panel);
}

.progress-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 22px 24px;
  border-bottom: 1px solid var(--color-border);
}

.progress-kicker {
  margin: 0 0 4px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.progress-heading h2 {
  margin: 0;
  color: var(--color-text);
  font-size: 18px;
  line-height: 1.4;
}

.phase-count {
  color: var(--color-text-secondary);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.phase-list {
  display: grid;
  margin: 0;
  padding: 0;
  list-style: none;
}

.phase-list li {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  min-height: 58px;
  padding: 10px 24px;
  border-bottom: 1px solid var(--color-border-light);
}

.phase-list li:last-child {
  border-bottom: 0;
}

.phase-marker {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid var(--color-border);
  border-radius: 50%;
  color: var(--color-text-muted);
  font-size: 12px;
}

.complete .phase-marker {
  border-color: var(--color-primary);
  background: var(--color-primary);
  color: white;
}

.active .phase-marker {
  border-color: var(--color-primary);
  color: var(--color-primary-dark);
  background: #f0fdf4;
}

.phase-label {
  color: var(--color-text);
  font-size: 14px;
  font-weight: 600;
}

.phase-status {
  color: var(--color-text-muted);
  font-size: 12px;
}

.active .phase-status {
  color: var(--color-primary-dark);
}

.stream-preview {
  contain: layout style;
  overflow-anchor: none;
  margin: 0 24px 24px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-background-secondary);
  overflow: hidden;
}

.stream-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border-bottom: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  font-size: 12px;
}

.stream-preview pre {
  max-height: 260px;
  margin: 0;
  padding: 16px;
  overflow: auto;
  color: var(--color-text);
  font: inherit;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 600px) {
  .progress-heading,
  .phase-list li {
    padding-left: 16px;
    padding-right: 16px;
  }

  .stream-preview {
    margin-left: 16px;
    margin-right: 16px;
  }
}
</style>
