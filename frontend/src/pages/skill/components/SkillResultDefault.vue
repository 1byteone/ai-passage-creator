<template>
  <section class="default-result">
    <header>
      <div>
        <p>执行完成</p>
        <h2>结构化结果</h2>
      </div>
      <a-button @click="copyResult">
        <template #icon><CopyOutlined /></template>
        复制
      </a-button>
    </header>
    <pre>{{ formattedResult }}</pre>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { message } from 'ant-design-vue'
import { CopyOutlined } from '@ant-design/icons-vue'

const props = defineProps<{
  outputData: Record<string, unknown>
}>()

const formattedResult = computed(() => {
  const values = Object.values(props.outputData)
  if (values.length === 1 && typeof values[0] === 'string') {
    return values[0]
  }
  return JSON.stringify(props.outputData, null, 2)
})

const copyResult = async () => {
  try {
    await navigator.clipboard.writeText(formattedResult.value)
    message.success('结果已复制')
  } catch {
    message.error('复制失败，请手动选择文本')
  }
}
</script>

<style scoped>
.default-result {
  display: grid;
  gap: 16px;
}

header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

header p {
  margin: 0 0 4px;
  color: var(--color-text-muted);
  font-size: 12px;
}

header h2 {
  margin: 0;
  color: var(--color-text);
  font-size: 22px;
}

pre {
  max-height: 600px;
  margin: 0;
  padding: 22px;
  overflow: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-background-secondary);
  color: var(--color-text);
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', monospace;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 760px) {
  pre {
    max-height: none;
    overflow: visible;
  }
}
</style>
