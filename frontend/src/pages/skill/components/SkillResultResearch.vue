<template>
  <section class="research-result">
    <header class="result-heading">
      <div>
        <p>调研完成</p>
        <h2>{{ brief.title || '结构化调研简报' }}</h2>
      </div>
      <div class="result-actions">
        <a-button @click="copyAll">
          <template #icon><CopyOutlined /></template>
          复制全部
        </a-button>
        <a-button type="primary" @click="downloadAll">
          <template #icon><DownloadOutlined /></template>
          下载 Markdown
        </a-button>
      </div>
    </header>

    <!-- 核心发现 -->
    <section v-if="keyFindings.length" class="section-block">
      <div class="section-heading">
        <h3>核心发现</h3>
        <span class="count-badge">{{ keyFindings.length }} 项</span>
      </div>
      <ul class="findings-list">
        <li v-for="(item, index) in keyFindings" :key="`finding-${index}`">
          <span class="finding-index">{{ index + 1 }}</span>
          <span>{{ item }}</span>
        </li>
      </ul>
    </section>

    <!-- 已确认事实 -->
    <section v-if="confirmedFacts.length" class="section-block">
      <div class="section-heading">
        <h3>已确认事实</h3>
        <span class="count-badge">{{ confirmedFacts.length }} 项</span>
      </div>
      <div class="facts-grid">
        <div
          v-for="(fact, index) in confirmedFacts"
          :key="`fact-${index}`"
          class="fact-card"
          :class="`confidence-${fact.confidence || 'medium'}`"
        >
          <div class="fact-header">
            <span class="confidence-tag">{{ confidenceLabel(fact.confidence) }}</span>
          </div>
          <p class="fact-text">{{ fact.fact }}</p>
        </div>
      </div>
    </section>

    <!-- 待确认问题 -->
    <section v-if="unresolvedQuestions.length" class="section-block">
      <div class="section-heading">
        <h3>待确认问题</h3>
        <span class="count-badge">{{ unresolvedQuestions.length }} 项</span>
      </div>
      <ul class="questions-list">
        <li v-for="(item, index) in unresolvedQuestions" :key="`question-${index}`">
          <QuestionCircleOutlined class="question-icon" />
          <span>{{ item }}</span>
        </li>
      </ul>
    </section>

    <!-- 写作建议 -->
    <section v-if="writingSuggestions.length" class="section-block">
      <div class="section-heading">
        <h3>写作建议</h3>
        <span class="count-badge">{{ writingSuggestions.length }} 项</span>
      </div>
      <ul class="suggestions-list">
        <li v-for="(item, index) in writingSuggestions" :key="`suggestion-${index}`">
          <BulbOutlined class="suggestion-icon" />
          <span>{{ item }}</span>
        </li>
      </ul>
    </section>

    <!-- 信息来源 -->
    <section v-if="sources.length" class="section-block">
      <div class="section-heading">
        <h3>信息来源</h3>
        <span class="count-badge">{{ sources.length }} 项</span>
      </div>
      <ol class="sources-list">
        <li v-for="(source, index) in sources" :key="`source-${index}`">
          <LinkOutlined class="source-icon" />
          <span>{{ source }}</span>
        </li>
      </ol>
    </section>

    <a-empty v-if="isEmpty" description="本次未解析到结构化调研数据，请调整主题后重试" />
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  CopyOutlined,
  DownloadOutlined,
  QuestionCircleOutlined,
  BulbOutlined,
  LinkOutlined,
} from '@ant-design/icons-vue'
import { copyResultText, downloadResultText } from '@/utils/resultActions'

const props = defineProps<{
  inputs: Record<string, unknown>
  outputData: Record<string, unknown>
}>()

interface ConfirmedFact {
  fact: string
  confidence?: 'high' | 'medium' | 'low'
}

interface ResearchBrief {
  title?: string
  keyFindings?: string[]
  confirmedFacts?: ConfirmedFact[]
  unresolvedQuestions?: string[]
  writingSuggestions?: string[]
  sources?: string[]
}

const brief = computed<ResearchBrief>(() => {
  const value = props.outputData.researchBrief
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    return value as ResearchBrief
  }
  return {}
})

const keyFindings = computed<string[]>(() => {
  const value = brief.value.keyFindings
  return Array.isArray(value) ? value.filter(Boolean) : []
})

const confirmedFacts = computed<ConfirmedFact[]>(() => {
  const value = brief.value.confirmedFacts
  if (!Array.isArray(value)) return []
  return value.filter((f: unknown): f is ConfirmedFact => {
    return f !== null && typeof f === 'object' && typeof (f as ConfirmedFact).fact === 'string'
  })
})

const unresolvedQuestions = computed<string[]>(() => {
  const value = brief.value.unresolvedQuestions
  return Array.isArray(value) ? value.filter(Boolean) : []
})

const writingSuggestions = computed<string[]>(() => {
  const value = brief.value.writingSuggestions
  return Array.isArray(value) ? value.filter(Boolean) : []
})

const sources = computed<string[]>(() => {
  const value = brief.value.sources
  return Array.isArray(value) ? value.filter(Boolean) : []
})

const isEmpty = computed(
  () =>
    !keyFindings.value.length &&
    !confirmedFacts.value.length &&
    !unresolvedQuestions.value.length &&
    !writingSuggestions.value.length &&
    !sources.value.length,
)

const confidenceLabel = (confidence?: string): string => {
  const labels: Record<string, string> = {
    high: '高可信度',
    medium: '中等可信度',
    low: '低可信度',
  }
  return labels[confidence || 'medium'] || '中等可信度'
}

const formattedMarkdown = computed(() => {
  const lines: string[] = ['# 结构化调研简报', '']

  if (brief.value.title) {
    lines.push(`> ${brief.value.title}`, '')
  }

  if (keyFindings.value.length) {
    lines.push('## 核心发现', '')
    keyFindings.value.forEach((item, i) => {
      lines.push(`${i + 1}. ${item}`)
    })
    lines.push('')
  }

  if (confirmedFacts.value.length) {
    lines.push('## 已确认事实', '')
    confirmedFacts.value.forEach((fact) => {
      const label = confidenceLabel(fact.confidence)
      lines.push(`- [${label}] ${fact.fact}`)
    })
    lines.push('')
  }

  if (unresolvedQuestions.value.length) {
    lines.push('## 待确认问题', '')
    unresolvedQuestions.value.forEach((item) => {
      lines.push(`- ${item}`)
    })
    lines.push('')
  }

  if (writingSuggestions.value.length) {
    lines.push('## 写作建议', '')
    writingSuggestions.value.forEach((item) => {
      lines.push(`- ${item}`)
    })
    lines.push('')
  }

  if (sources.value.length) {
    lines.push('## 信息来源', '')
    sources.value.forEach((source, i) => {
      lines.push(`${i + 1}. ${source}`)
    })
    lines.push('')
  }

  return lines.join('\n')
})

const copyAll = () => copyResultText(formattedMarkdown.value, '调研简报已复制')
const downloadAll = () => downloadResultText(formattedMarkdown.value, 'research-brief.md')
</script>

<style scoped>
.research-result {
  display: grid;
  gap: 24px;
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

.section-block {
  display: grid;
  gap: 14px;
}

.section-heading {
  display: flex;
  align-items: center;
  gap: 10px;
}

.section-heading h3 {
  margin: 0;
  color: var(--color-text);
  font-size: 17px;
}

.count-badge {
  padding: 2px 10px;
  border-radius: 20px;
  background: var(--color-background-secondary);
  color: var(--color-text-muted);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

/* 核心发现 */
.findings-list {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.findings-list li {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 18px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: white;
  color: var(--color-text);
  font-size: 14px;
  line-height: 1.65;
}

.finding-index {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--color-primary);
  color: white;
  font-size: 11px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

/* 已确认事实 */
.facts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
}

.fact-card {
  padding: 16px 18px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: white;
}

.fact-card.confidence-high {
  border-left: 3px solid var(--color-primary);
}

.fact-card.confidence-medium {
  border-left: 3px solid #d97706;
}

.fact-card.confidence-low {
  border-left: 3px solid #dc2626;
}

.fact-header {
  margin-bottom: 8px;
}

.confidence-tag {
  display: inline-block;
  padding: 1px 8px;
  border-radius: var(--radius-sm);
  font-size: 11px;
  font-weight: 500;
}

.fact-card.confidence-high .confidence-tag {
  background: #f0fdf4;
  color: var(--color-primary-dark);
}

.fact-card.confidence-medium .confidence-tag {
  background: #fffbeb;
  color: #92400e;
}

.fact-card.confidence-low .confidence-tag {
  background: #fef2f2;
  color: #991b1b;
}

.fact-text {
  margin: 0;
  color: var(--color-text);
  font-size: 14px;
  line-height: 1.65;
}

/* 待确认问题 */
.questions-list {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.questions-list li {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 16px;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-md);
  background: #fffbeb;
  color: var(--color-text-secondary);
  font-size: 14px;
  line-height: 1.6;
}

.question-icon {
  flex-shrink: 0;
  margin-top: 3px;
  color: #d97706;
}

/* 写作建议 */
.suggestions-list {
  display: grid;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.suggestions-list li {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: white;
  color: var(--color-text);
  font-size: 14px;
  line-height: 1.6;
}

.suggestion-icon {
  flex-shrink: 0;
  margin-top: 3px;
  color: var(--color-primary);
}

/* 信息来源 */
.sources-list {
  margin: 0;
  padding-left: 22px;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.8;
}

.sources-list li {
  padding: 4px 0;
}

.source-icon {
  margin-right: 6px;
  color: var(--color-text-muted);
  font-size: 12px;
}

@media (max-width: 760px) {
  .result-heading {
    flex-direction: column;
  }

  .facts-grid {
    grid-template-columns: 1fr;
  }
}
</style>