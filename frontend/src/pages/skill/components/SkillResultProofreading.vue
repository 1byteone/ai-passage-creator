<template>
  <section class="proofreading-result">
    <header class="result-heading">
      <div>
        <p>审校完成</p>
        <h2>问题、修改与终稿</h2>
      </div>
      <div class="result-actions">
        <a-button @click="copyFinal">
          <template #icon><CopyOutlined /></template>
          复制终稿
        </a-button>
        <a-button type="primary" @click="downloadFinal">
          <template #icon><DownloadOutlined /></template>
          下载 Markdown
        </a-button>
      </div>
    </header>

    <div class="review-summary">
      <div v-if="score !== null" class="score-block">
        <span>内容评分</span>
        <strong>{{ score }}</strong>
      </div>
      <div class="summary-copy">
        <h3>总体评价</h3>
        <p>{{ review.summary || '审校流程已完成，请重点检查下方修改内容。' }}</p>
      </div>
    </div>

    <div class="issue-grid">
      <div v-for="group in issueGroups" :key="group.title" class="issue-group">
        <h3>{{ group.title }}</h3>
        <ul v-if="group.items.length">
          <li v-for="item in group.items" :key="item">{{ item }}</li>
        </ul>
        <p v-else class="empty-copy">未发现明显问题</p>
      </div>
    </div>

    <section class="diff-section">
      <div class="section-heading">
        <div>
          <p>修改检查</p>
          <h3>原文与语气调整稿</h3>
        </div>
        <div class="diff-legend" aria-label="差异图例">
          <span class="removed">删除</span>
          <span class="added">新增</span>
        </div>
      </div>
      <div class="diff-content">
        <span
          v-for="(segment, index) in diffSegments"
          :key="`${segment.type}-${index}`"
          :class="segment.type"
        >
          {{ segment.value }}
        </span>
      </div>
    </section>

    <section class="final-section">
      <div class="section-heading">
        <div>
          <p>最终稿</p>
          <h3>节奏润色后的可用版本</h3>
        </div>
      </div>
      <article class="final-content">{{ finalContent }}</article>
    </section>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CopyOutlined, DownloadOutlined } from '@ant-design/icons-vue'
import { diffText } from '@/utils/textDiff'
import { copyResultText, downloadResultText } from '@/utils/resultActions'

const props = defineProps<{
  inputs: Record<string, unknown>
  outputData: Record<string, unknown>
}>()

const review = computed<Record<string, unknown>>(() => {
  const value = props.outputData.reviewResult
  return value && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {}
})

const score = computed(() =>
  typeof review.value.overallScore === 'number' ? review.value.overallScore : null,
)
const originalContent = computed(() => String(props.inputs.articleContent || ''))
const polishedContent = computed(() =>
  String(props.outputData.polishedContent || props.outputData.finalContent || ''),
)
const finalContent = computed(() =>
  String(props.outputData.finalContent || props.outputData.polishedContent || ''),
)
const diffSegments = computed(() => diffText(originalContent.value, polishedContent.value))

const arrayValue = (key: string) => {
  const value = review.value[key]
  return Array.isArray(value) ? value.map(String).filter(Boolean) : []
}

const issueGroups = computed(() => [
  { title: '逻辑问题', items: arrayValue('logicIssues') },
  { title: '结构问题', items: arrayValue('structureIssues') },
  { title: '修改建议', items: arrayValue('suggestions') },
])

const copyFinal = () => copyResultText(finalContent.value, '终稿已复制')
const downloadFinal = () => downloadResultText(finalContent.value, 'proofread-article.md')
</script>

<style scoped>
.proofreading-result {
  display: grid;
  gap: 24px;
}

.result-heading,
.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.result-heading p,
.section-heading p {
  margin: 0 0 4px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.result-heading h2,
.section-heading h3 {
  margin: 0;
  color: var(--color-text);
}

.result-heading h2 {
  font-size: 22px;
}

.section-heading h3 {
  font-size: 17px;
}

.result-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.review-summary {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.score-block {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 20px;
  border-right: 1px solid var(--color-border);
  background: #f0fdf4;
  color: var(--color-primary-dark);
}

.score-block span {
  font-size: 12px;
}

.score-block strong {
  font-size: 30px;
  font-variant-numeric: tabular-nums;
}

.summary-copy {
  padding: 20px 24px;
}

.summary-copy h3,
.issue-group h3 {
  margin: 0 0 8px;
  color: var(--color-text);
  font-size: 14px;
}

.summary-copy p,
.empty-copy {
  margin: 0;
  color: var(--color-text-secondary);
  line-height: 1.7;
}

.issue-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.issue-group {
  padding: 18px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.issue-group ul {
  margin: 0;
  padding-left: 18px;
  color: var(--color-text-secondary);
}

.issue-group li {
  margin-bottom: 8px;
  line-height: 1.6;
}

.diff-section,
.final-section {
  display: grid;
  gap: 14px;
}

.diff-legend {
  display: flex;
  gap: 8px;
  font-size: 12px;
}

.diff-legend span {
  padding: 3px 8px;
  border-radius: var(--radius-sm);
}

.diff-legend .removed,
.diff-content .removed {
  background: #fff1f0;
  color: #a8071a;
}

.diff-legend .added,
.diff-content .added {
  background: #f0fdf4;
  color: #166534;
}

.diff-content,
.final-content {
  max-height: 520px;
  padding: 22px;
  overflow: auto;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text);
  font-size: 15px;
  line-height: 1.9;
  white-space: pre-wrap;
  word-break: break-word;
}

.diff-content .removed {
  text-decoration: line-through;
}

@media (max-width: 760px) {
  .result-heading,
  .section-heading {
    flex-direction: column;
  }

  .review-summary {
    grid-template-columns: 1fr;
  }

  .score-block {
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .issue-grid {
    grid-template-columns: 1fr;
  }

  .diff-content,
  .final-content {
    max-height: none;
    overflow: visible;
  }
}
</style>
