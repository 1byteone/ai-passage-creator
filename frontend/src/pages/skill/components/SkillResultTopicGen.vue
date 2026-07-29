<template>
  <section class="topic-result">
    <header class="result-heading">
      <div>
        <p>生成完成</p>
        <h2>{{ options.length }} 个可执行的选题方向</h2>
      </div>
      <div v-if="options.length" class="result-actions">
        <a-button @click="copyOptions">
          <template #icon><CopyOutlined /></template>
          复制全部
        </a-button>
        <a-button type="primary" @click="downloadOptions">
          <template #icon><DownloadOutlined /></template>
          下载 Markdown
        </a-button>
      </div>
    </header>

    <div v-if="options.length" class="topic-list">
      <article v-for="(option, index) in options" :key="`${option.title}-${index}`">
        <div class="topic-main">
          <span class="topic-index">{{ String(index + 1).padStart(2, '0') }}</span>
          <div>
            <div class="topic-meta">
              <span>{{ option.type || '内容选题' }}</span>
              <span v-if="option.workload">投入：{{ option.workload }}</span>
            </div>
            <h3>{{ option.title }}</h3>
          </div>
          <a-button type="primary" @click="useTopic(option)">
            <template #icon><ArrowRightOutlined /></template>
            使用此选题
          </a-button>
        </div>

        <div class="topic-detail">
          <div>
            <h4>建议大纲</h4>
            <ol>
              <li v-for="item in option.outline || []" :key="item">{{ item }}</li>
            </ol>
          </div>
          <div>
            <h4>优势</h4>
            <ul class="pros">
              <li v-for="item in option.pros || []" :key="item">{{ item }}</li>
            </ul>
          </div>
          <div>
            <h4>注意点</h4>
            <ul class="cons">
              <li v-for="item in option.cons || []" :key="item">{{ item }}</li>
            </ul>
          </div>
        </div>
      </article>
    </div>
    <a-empty v-else description="本次没有解析到有效选题，请调整方向后重试" />
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRightOutlined, CopyOutlined, DownloadOutlined } from '@ant-design/icons-vue'
import { copyResultText, downloadResultText } from '@/utils/resultActions'

const props = defineProps<{
  outputData: Record<string, unknown>
  embedded?: boolean
}>()

const emit = defineEmits<{
  select: [option: API.TopicOption]
}>()

const router = useRouter()
const options = computed<API.TopicOption[]>(() => {
  const value = props.outputData.topicOptions
  return Array.isArray(value) ? (value as API.TopicOption[]) : []
})

const formattedOptions = computed(() =>
  options.value
    .map((option, index) => {
      const outline = (option.outline || []).map((item) => `- ${item}`).join('\n')
      const pros = (option.pros || []).map((item) => `- ${item}`).join('\n')
      const cons = (option.cons || []).map((item) => `- ${item}`).join('\n')
      return [
        `## ${index + 1}. ${option.title}`,
        option.type ? `类型：${option.type}` : '',
        option.workload ? `投入：${option.workload}` : '',
        outline ? `### 建议大纲\n${outline}` : '',
        pros ? `### 优势\n${pros}` : '',
        cons ? `### 注意点\n${cons}` : '',
      ]
        .filter(Boolean)
        .join('\n\n')
    })
    .join('\n\n---\n\n'),
)

const copyOptions = () => copyResultText(formattedOptions.value, '全部选题已复制')
const downloadOptions = () => downloadResultText(formattedOptions.value, 'topic-options.md')

const useTopic = (option: API.TopicOption) => {
  emit('select', option)
  if (!props.embedded) {
    router.push({
      path: '/create',
      query: {
        topic: option.title,
      },
    })
  }
}
</script>

<style scoped>
.topic-result {
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

.topic-list {
  display: grid;
  border-top: 1px solid var(--color-border);
}

.topic-list article {
  padding: 24px 0;
  border-bottom: 1px solid var(--color-border);
}

.topic-main {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  align-items: start;
  gap: 16px;
}

.topic-index {
  color: var(--color-text-muted);
  font-size: 13px;
  font-variant-numeric: tabular-nums;
}

.topic-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 6px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.topic-main h3 {
  margin: 0;
  color: var(--color-text);
  font-size: 18px;
  line-height: 1.45;
}

.topic-detail {
  display: grid;
  grid-template-columns: 1.6fr 1fr 1fr;
  gap: 20px;
  margin: 18px 0 0 58px;
}

.topic-detail h4 {
  margin: 0 0 8px;
  color: var(--color-text);
  font-size: 13px;
}

.topic-detail ol,
.topic-detail ul {
  margin: 0;
  padding-left: 18px;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.65;
}

.pros li::marker {
  color: var(--color-primary);
}

.cons li::marker {
  color: #d97706;
}

@media (max-width: 760px) {
  .result-heading {
    flex-direction: column;
  }

  .topic-main {
    grid-template-columns: 32px minmax(0, 1fr);
  }

  .topic-main .ant-btn {
    grid-column: 2;
    justify-self: stretch;
  }

  .topic-detail {
    grid-template-columns: 1fr;
    margin-left: 48px;
  }
}
</style>
