<template>
  <section class="social-result">
    <header class="result-heading">
      <div>
        <p>转写完成</p>
        <h2>{{ platformLabel(inputs.platform) }} 文案</h2>
      </div>
      <div class="result-actions">
        <a-button @click="copyResult"><template #icon><CopyOutlined /></template>复制文案</a-button>
        <a-button type="primary" @click="downloadResult">
          <template #icon><DownloadOutlined /></template>下载 Markdown
        </a-button>
      </div>
    </header>

    <div class="comparison-layout">
      <section class="result-pane">
        <div class="pane-heading">
          <h3>社交文案</h3>
          <span>{{ result.length }} 字符</span>
        </div>
        <article>{{ result }}</article>
      </section>

      <details class="source-pane" open>
        <summary>
          <span>原文摘要</span>
          <span>{{ original.length }} 字符</span>
        </summary>
        <article>{{ original }}</article>
      </details>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CopyOutlined, DownloadOutlined } from '@ant-design/icons-vue'
import { platformLabel } from '@/config/skill'
import { copyResultText, downloadResultText } from '@/utils/resultActions'

const props = defineProps<{
  inputs: Record<string, unknown>
  outputData: Record<string, unknown>
}>()

const result = computed(() => String(props.outputData.condensedContent || ''))
const original = computed(() => String(props.inputs.articleContent || ''))

const copyResult = () => copyResultText(result.value, '社交文案已复制')
const downloadResult = () => downloadResultText(result.value, 'social-copy.md')
</script>

<style scoped>
.social-result {
  display: grid;
  gap: 20px;
}

.result-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.result-heading p {
  margin: 0 0 4px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.result-heading h2 {
  margin: 0;
  color: var(--color-text);
  font-size: 22px;
}

.result-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.comparison-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(280px, 0.9fr);
  gap: 16px;
}

.result-pane,
.source-pane {
  min-width: 0;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--surface-panel);
  overflow: hidden;
}

.pane-heading,
.source-pane summary {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--color-border);
}

.pane-heading h3,
.source-pane summary span:first-child {
  margin: 0;
  color: var(--color-text);
  font-size: 14px;
  font-weight: 600;
}

.pane-heading span,
.source-pane summary span:last-child {
  color: var(--color-text-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.source-pane summary {
  cursor: pointer;
}

.result-pane article,
.source-pane article {
  max-height: 560px;
  padding: 22px;
  overflow: auto;
  color: var(--color-text);
  font-size: 15px;
  line-height: 1.85;
  white-space: pre-wrap;
  word-break: break-word;
}

.result-pane {
  order: 1;
}

.source-pane {
  order: 2;
}

@media (max-width: 760px) {
  .result-heading {
    flex-direction: column;
  }

  .comparison-layout {
    grid-template-columns: 1fr;
  }

  .result-pane {
    order: 1;
  }

  .source-pane {
    order: 2;
  }

  .result-pane article,
  .source-pane article {
    max-height: none;
    overflow: visible;
  }
}
</style>
