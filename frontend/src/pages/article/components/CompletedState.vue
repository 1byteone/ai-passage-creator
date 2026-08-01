<template>
  <div class="completed-state">
    <div class="success-header">
      <CheckCircleFilled class="success-icon" />
      <span>文章创作完成！</span>
      <a-button class="proofread-button" @click="launcherOpen = true">
        <FileDoneOutlined />
        文章审校
      </a-button>
    </div>

    <ArticleReadingView :article="article" title-id="completed-article-title" :show-meta="false" />
    <SkillLauncher
      v-model:open="launcherOpen"
      skill-name="proofreading"
      :initial-inputs="{ articleContent: article.fullContent || article.content || '' }"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { CheckCircleFilled, FileDoneOutlined } from '@ant-design/icons-vue'
import SkillLauncher from '@/pages/skill/components/SkillLauncher.vue'
import ArticleReadingView from '@/components/ArticleReadingView.vue'

const props = defineProps<{
  article: Partial<API.ArticleVO>
}>()

const article = props.article
const launcherOpen = ref(false)
</script>

<style scoped lang="scss">
.completed-state {
  max-width: 100%;
}

.success-header {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: var(--gradient-primary);
  border-radius: var(--radius-full);
  margin-bottom: 24px;
  color: white;
  font-size: 14px;
  font-weight: 600;

  .success-icon {
    font-size: 16px;
  }
}

.proofread-button {
  margin-left: 8px;
  border-color: rgba(255, 255, 255, 0.7);
  background: white;
  color: var(--color-primary-dark);
  font-weight: 600;
}

@media (max-width: 600px) {
  .success-header {
    width: 100%;
    flex-wrap: wrap;
    border-radius: var(--radius-md);
  }

  .proofread-button {
    width: 100%;
    margin: 4px 0 0;
  }
}

</style>
