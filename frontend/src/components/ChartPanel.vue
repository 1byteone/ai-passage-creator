<template>
  <section class="chart-panel" :aria-labelledby="titleId">
    <header>
      <div>
        <p class="chart-range">{{ range }}</p>
        <h2 :id="titleId">{{ title }}</h2>
        <p class="chart-summary">{{ summary }}</p>
      </div>
      <slot name="action" />
    </header>

    <div v-if="loading" class="panel-state" aria-live="polite">
      <a-skeleton active :paragraph="{ rows: 5 }" />
    </div>
    <a-result
      v-else-if="error"
      status="warning"
      title="图表数据暂时不可用"
      :sub-title="error"
      class="panel-state"
    >
      <template #extra><a-button @click="emit('retry')">重新加载</a-button></template>
    </a-result>
    <a-empty v-else-if="empty" description="当前时间范围内暂无数据" class="panel-state" />
    <div v-else class="panel-content">
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  titleId: string
  range: string
  summary: string
  loading?: boolean
  error?: string
  empty?: boolean
}>()

const emit = defineEmits<{ retry: [] }>()
</script>

<style scoped>
.chart-panel {
  min-width: 0;
  padding: 24px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--surface-panel);
}

header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
}

.chart-range {
  margin: 0 0 5px;
  color: var(--text-muted);
  font-size: 12px;
}

h2 {
  margin: 0;
  font-size: 18px;
}

.chart-summary {
  max-width: 560px;
  margin: 8px 0 0;
  color: var(--text-subtle);
  font-size: 13px;
  line-height: 1.6;
}

.panel-state {
  min-height: 300px;
  display: grid;
  place-items: center;
}

.panel-content {
  min-width: 0;
}

@media (max-width: 600px) {
  .chart-panel {
    padding: 20px 16px;
  }

  header {
    flex-direction: column;
  }

  .panel-state {
    min-height: 220px;
  }
}
</style>
