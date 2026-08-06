<template>
  <div class="completed-state">
    <div class="success-header">
      <CheckCircleFilled class="success-icon" />
      <span>文章创作完成！</span>
      <a-button class="proofread-button" @click="launcherOpen = true">
        <FileDoneOutlined />
        文章审校
      </a-button>
      <a-button
        v-if="characterStyle"
        type="primary"
        class="illustration-button"
        :loading="generating"
        @click="generateIllustrationCard"
      >
        <PictureOutlined />
        生成插画卡片
      </a-button>
    </div>

    <!-- 插画卡片预览弹窗 -->
    <a-modal
      v-model:open="previewOpen"
      title="插画卡片预览"
      :footer="null"
      width="min(95vw, 600px)"
      :destroy-on-close="true"
    >
      <div v-if="cards.length > 0" class="illustration-gallery">
        <div v-for="(card, i) in cards" :key="card.pageNo ?? i" class="illustration-card-item">
          <div class="card-label">
            <span>{{ card.pageType === 'COVER' ? '封面' : `第 ${card.pageNo} 页` }}</span>
          </div>
          <img
            v-if="card.imageUrl"
            :src="card.imageUrl"
            :alt="`卡片第 ${card.pageNo} 页`"
            class="card-image"
          />
          <div v-else class="card-placeholder">渲染中…</div>
          <a-button
            v-if="card.imageUrl"
            size="small"
            class="card-download-btn"
            @click="downloadImage(card.imageUrl!, `illustration-card-${card.pageNo ?? i}`)"
          >
            <DownloadOutlined />
            下载
          </a-button>
        </div>
      </div>
      <div v-else class="illustration-empty">
        <a-spin v-if="generating" />
        <span v-else>暂无卡片，请先点击「生成插画卡片」</span>
      </div>
    </a-modal>

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
import { CheckCircleFilled, FileDoneOutlined, PictureOutlined, DownloadOutlined } from '@ant-design/icons-vue'
import SkillLauncher from '@/pages/skill/components/SkillLauncher.vue'
import ArticleReadingView from '@/components/ArticleReadingView.vue'
import { generateCards, getCards } from '@/api/cardController'

const props = defineProps<{
  article: Partial<API.ArticleVO>
  taskId?: string
  characterStyle?: string
}>()

const article = props.article
const launcherOpen = ref(false)
const generating = ref(false)
const previewOpen = ref(false)
const cards = ref<API.CardPage[]>([])

const generateIllustrationCard = async () => {
  if (!props.taskId || generating.value) return
  generating.value = true
  try {
    await generateCards({
      taskId: props.taskId,
      cardStyle: 'illustration',
      characterStyle: props.characterStyle || undefined,
    })

    // 轮询等待生成完成（最多 60s）
    for (let i = 0; i < 30; i++) {
      await new Promise(r => setTimeout(r, 2000))
      const res = await getCards(props.taskId)
      if (res.data.code === 0 && res.data.data) {
        const allDone = res.data.data.every(c => c.status === 'COMPLETED' || c.status === 'FAILED')
        if (allDone) {
          cards.value = res.data.data
          previewOpen.value = true
          generating.value = false
          return
        }
      }
    }
    // 超时
    const { default: message } = await import('ant-design-vue/es/message')
    message.warning('卡片生成较慢，请稍后前往卡片管理页面查看')
  } catch {
    const { default: message } = await import('ant-design-vue/es/message')
    message.error('卡片生成失败')
  } finally {
    generating.value = false
  }
}

const downloadImage = (url: string, filename: string) => {
  const a = document.createElement('a')
  a.style.display = 'none'
  a.href = url
  a.download = filename.replace(/[\\/:*?"<>|]/g, '_')
  document.body.appendChild(a)
  a.click()
  setTimeout(() => {
    document.body.removeChild(a)
  }, 100)
}
</script>

<style scoped lang="scss">
.completed-state {
  max-width: 100%;
}

.success-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
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
  border-color: rgba(255, 255, 255, 0.7);
  background: white;
  color: var(--color-primary-dark);
  font-weight: 600;
}

.illustration-button {
  margin-left: auto;
  background: white;
  color: var(--color-primary-dark);
  border: 1px solid rgba(255, 255, 255, 0.7);
  font-weight: 600;

  &:hover {
    background: rgba(255, 255, 255, 0.9) !important;
    border-color: white !important;
  }
}

.illustration-gallery {
  display: flex;
  flex-direction: column;
  gap: 16px;
  align-items: center;
}

.illustration-card-item {
  width: 100%;
  max-width: 400px;
  text-align: center;

  .card-label {
    font-size: 13px;
    font-weight: 600;
    color: var(--color-text-secondary);
    margin-bottom: 8px;
  }

  .card-image {
    width: 100%;
    border-radius: var(--radius-lg);
    box-shadow: var(--shadow-md);
    border: 1px solid var(--color-border);
  }

  .card-placeholder {
    padding: 80px 0;
    color: var(--color-text-muted);
    background: var(--color-background-secondary);
    border-radius: var(--radius-lg);
  }

  .card-download-btn {
    margin-top: 8px;
  }
}

.illustration-empty {
  text-align: center;
  padding: 40px 0;
  color: var(--color-text-muted);
}

@media (max-width: 600px) {
  .success-header {
    width: 100%;
    border-radius: var(--radius-md);
  }

  .illustration-button {
    margin-left: 0;
    width: 100%;
  }

  .proofread-button {
    width: 100%;
  }
}
</style>