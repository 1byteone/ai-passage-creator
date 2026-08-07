<template>
  <div class="input-state">
    <div class="input-card">
      <div class="input-header">
        <h1 class="input-title">创作新文章</h1>
        <p class="input-subtitle">输入选题，AI 帮你生成爆款文章</p>
      </div>

      <div class="input-area">
        <a-textarea
          :value="topic"
          @update:value="$emit('update:topic', $event)"
          placeholder="请输入您想创作的文章选题，例如：2026年AI如何改变职场"
          :rows="6"
          :maxlength="MAX_TOPIC_LENGTH"
          show-count
          class="topic-textarea"
        />
        <a-button
          type="primary"
          size="large"
          :loading="loading"
          :disabled="!topic.trim()"
          @click="$emit('start')"
          class="create-btn"
        >
          <template #icon>
            <RocketOutlined />
          </template>
          开始创作
        </a-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { RocketOutlined } from '@ant-design/icons-vue'
import { MAX_TOPIC_LENGTH } from '@/constants/article'

defineProps<{
  topic: string
  loading?: boolean
}>()

defineEmits<{
  (e: 'update:topic', value: string): void
  (e: 'start'): void
}>()
</script>

<style scoped lang="scss">
.input-state {
  max-width: 700px;
  margin: 0 auto;
  padding-top: 60px;
}

.input-card {
  background: var(--color-background-secondary);
  border-radius: var(--radius-xl);
  padding: 40px;
}

.input-header {
  text-align: center;
  margin-bottom: 32px;
}

.input-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 8px;
  color: var(--color-text);
}

.input-subtitle {
  font-size: 15px;
  color: var(--color-text-secondary);
  margin: 0;
}

.input-area {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.topic-textarea {
  font-size: 15px;
  border-radius: var(--radius-lg);
  padding: 16px;
  background: var(--surface-panel);

  &:focus {
    border-color: var(--color-primary);
    box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.1);
  }
}

</style>
