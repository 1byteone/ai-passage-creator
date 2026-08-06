<template>
  <div id="cardPage">
    <!-- 文章信息头 -->
    <header class="page-heading">
      <div>
        <nav class="breadcrumb">
          <RouterLink to="/article/list">文章列表</RouterLink>
          <span>/</span>
          <RouterLink :to="`/article/${taskId}`">{{ articleTitle || taskId }}</RouterLink>
          <span>/</span>
          <span class="current">卡片管理</span>
        </nav>
        <span class="page-kicker">卡片渲染</span>
        <h1>{{ articleTitle || '卡片管理' }}</h1>
        <p>为已完成的文章生成高质量卡片图片，支持预览和下载。</p>
      </div>
      <div class="heading-actions">
        <a-button :loading="previewing" @click="doPreview">
          <template #icon><EyeOutlined /></template>
          预览（免费，前 2 页）
        </a-button>
        <a-popconfirm
          title="确定生成全部卡片？"
          ok-text="生成"
          cancel-text="取消"
          @confirm="doGenerate"
        >
          <a-button type="primary" :loading="generating || pollingCards">
            <template #icon><ThunderboltOutlined /></template>
            生成全部卡片
          </a-button>
        </a-popconfirm>
      </div>
    </header>

    <!-- 卡片风格与插画子风格选择 -->
    <div class="card-style-bar">
      <div class="style-select-row">
        <span class="style-label">卡片风格</span>
        <a-radio-group v-model:value="cardStyle" size="small">
          <a-radio value="">默认</a-radio>
          <a-radio value="warm">温暖</a-radio>
          <a-radio value="minimal">极简</a-radio>
          <a-radio value="free">自由</a-radio>
          <a-radio value="handwriting">手写</a-radio>
          <a-radio value="illustration">插画</a-radio>
        </a-radio-group>
      </div>
      <div v-if="cardStyle === 'illustration'" class="style-select-row">
        <span class="style-label">子风格</span>
        <a-radio-group v-model:value="characterStyle" size="small">
          <a-radio value="healing">治愈</a-radio>
          <a-radio value="cute">可爱</a-radio>
          <a-radio value="doodle">涂鸦</a-radio>
          <a-radio value="watercolor">水彩</a-radio>
        </a-radio-group>
      </div>
    </div>

    <!-- 预览结果 -->
    <div v-if="previewUrls.length" class="preview-section">
      <div class="section-heading">
        <h2>预览结果</h2>
        <span class="badge">{{ previewUrls.length }} 页</span>
      </div>
      <div class="card-gallery">
        <div v-for="(url, i) in previewUrls" :key="`preview-${i}`" class="card-item">
          <div class="card-label">
            <span>{{ i === 0 ? '封面' : `第 ${i + 1} 页` }}</span>
            <span class="card-dim">1080 × 1920</span>
          </div>
          <div class="card-image-wrap">
            <img :src="url" :alt="`预览第 ${i + 1} 页`" loading="lazy" />
          </div>
          <div class="card-item-actions">
            <a-button size="small" @click="downloadImage(url, `preview-${i + 1}`)">
              <template #icon><DownloadOutlined /></template>
              下载
            </a-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 操作反馈 -->
    <div v-if="operationNotice" class="page-feedback" aria-live="polite">
      <a-alert
        :type="operationNotice.type"
        show-icon
        closable
        :message="operationNotice.message"
        :description="operationNotice.description"
        @close="operationNotice = null"
      />
    </div>

    <!-- 已生成卡片 -->
    <section class="cards-section" aria-labelledby="cards-title">
      <div class="section-heading">
        <div>
          <span class="section-label">已生成卡片</span>
          <h2 id="cards-title">{{ cardsTitle }}</h2>
        </div>
        <div class="heading-right">
          <span v-if="cards.length" class="page-range">{{ cardsSummary }}</span>
          <a-button size="small" :loading="loading" @click="refreshCards">
            <template #icon><ReloadOutlined /></template>
          </a-button>
        </div>
      </div>

      <div v-if="initialLoading" class="loading-state">
        <a-skeleton active :paragraph="{ rows: 4 }" />
      </div>

      <a-result
        v-else-if="loadError && !cards.length"
        class="result-state"
        status="error"
        title="暂时无法加载卡片"
        :sub-title="loadError"
      >
        <template #extra>
          <a-button type="primary" :loading="loading" @click="refreshCards">重新加载</a-button>
        </template>
      </a-result>

      <a-alert
        v-if="loadError && cards.length"
        class="page-feedback"
        type="warning"
        show-icon
        closable
        message="刷新失败，当前仍显示上一次数据"
        :description="loadError"
        @close="loadError = ''"
      />

      <IllustrationEmpty
        v-else-if="!cards.length"
        class="empty-state"
        description="还没有生成卡片"
      >
        <span class="empty-hint">点击「预览」免费查看效果，或「生成全部卡片」开始渲染</span>
      </IllustrationEmpty>

      <template v-else>
        <div class="card-gallery">
          <div v-for="card in cards" :key="card.id ?? card.pageNo" class="card-item">
            <div class="card-label">
              <span>{{ card.pageType === 'COVER' ? '封面' : `第 ${card.pageNo} 页` }}</span>
              <span class="card-dim">{{ card.width }} × {{ card.height }}</span>
              <a-tag
                v-if="card.status === 'FAILED'"
                color="error"
                class="card-status-tag"
              >
                失败
              </a-tag>
            </div>
            <div v-if="card.status === 'FAILED'" class="card-failed">
              <p>{{ card.errorMessage || '渲染失败' }}</p>
            </div>
            <div v-else-if="card.status !== 'COMPLETED'" class="card-running">
              <a-spin size="small" />
              <span>渲染中…</span>
            </div>
            <div v-else-if="card.imageUrl" class="card-image-wrap">
              <img :src="card.imageUrl" :alt="`卡片第 ${card.pageNo} 页`" loading="lazy" />
            </div>
            <div v-else class="card-running">
              <span>暂无图片</span>
            </div>
            <div class="card-item-actions">
              <a-button
                v-if="card.imageUrl"
                size="small"
                @click="downloadImage(card.imageUrl!, `card-${card.pageNo}`)"
              >
                <template #icon><DownloadOutlined /></template>
                下载
              </a-button>
              <span v-if="card.renderMs" class="render-time">{{ card.renderMs }}ms</span>
            </div>
          </div>
        </div>
      </template>
    </section>
  </div>
</template>

<script lang="ts" setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  Alert as AAlert,
  Empty as AEmpty,
  Popconfirm as APopconfirm,
  Result as AResult,
  Skeleton as ASkeleton,
  Spin as ASpin,
  Tag as ATag,
} from 'ant-design-vue'
import {
  DownloadOutlined,
  EyeOutlined,
  ReloadOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons-vue'
import { previewCards, generateCards, getCards } from '@/api/cardController'
import { getArticle } from '@/api/articleController'
import type { OperationNotice } from '@/types/operationNotice'
import IllustrationEmpty from '@/components/IllustrationEmpty.vue'

const route = useRoute()

// ── 文章信息 ──

const taskId = computed(() => route.params.taskId as string)
const articleTitle = ref('')

const loadArticle = async () => {
  if (!taskId.value) return
  try {
    const res = await getArticle({ taskId: taskId.value })
    if (res.data.code === 0 && res.data.data) {
      articleTitle.value = res.data.data.mainTitle || res.data.data.topic || ''
    }
  } catch {
    // 静默失败
  }
}

// ── 预览 ──

const previewing = ref(false)
const previewUrls = ref<string[]>([])

const doPreview = async () => {
  if (!taskId.value || previewing.value) return
  previewing.value = true
  previewUrls.value = []
  try {
    const res = await previewCards({ taskId: taskId.value })
    if (res.data.code === 0) {
      if (res.data.data && res.data.data.length) {
        previewUrls.value = res.data.data
      } else {
        const { default: message } = await import('ant-design-vue/es/message')
        message.info('预览暂无可生成页面，请确认文章内容不为空')
      }
    } else {
      throw new Error(res.data.message || '预览失败')
    }
  } catch (e) {
    const { default: message } = await import('ant-design-vue/es/message')
    message.error(e instanceof Error ? e.message : '预览失败，请稍后重试')
  } finally {
    previewing.value = false
  }
}

// ── 生成 ──

const generating = ref(false)
const cardStyle = ref('')
const characterStyle = ref('')

const operationNotice = ref<OperationNotice | null>(null)

const doGenerate = async () => {
  if (!taskId.value || generating.value) return
  generating.value = true
  operationNotice.value = null
  try {
    const res = await generateCards({
      taskId: taskId.value,
      cardStyle: cardStyle.value || undefined,
      characterStyle: characterStyle.value || undefined,
    })
    if (res.data.code === 0 && res.data.data) {
      operationNotice.value = {
        type: 'success',
        message: '卡片生成已启动',
        description: '生成完成后将自动显示在下方，请稍等片刻。',
      }
      // 轮询等待生成完成
      await pollCards()
    } else {
      throw new Error(res.data.message || '生成失败')
    }
  } catch (e) {
    operationNotice.value = {
      type: 'error',
      message: '生成失败',
      description: e instanceof Error ? e.message : '网络或服务暂时不可用，请稍后重试。',
    }
    generating.value = false
  }
}

// ── 轮询 ──

let pollTimer: ReturnType<typeof setTimeout> | null = null
const pollingCards = ref(false)
let unmounted = false

const pollCards = async () => {
  if (pollingCards.value) return
  pollingCards.value = true

  let attempts = 0
  const maxAttempts = 30 // 轮询上限，约 60s 后自动停止

  const poll = async () => {
    if (unmounted) return
    if (attempts >= maxAttempts) {
      pollingCards.value = false
      generating.value = false
      operationNotice.value = {
        type: 'success',
        message: '生成耗时较长',
        description: '卡片仍在后台生成中，稍后点击刷新按钮查看。',
      }
      return
    }
    attempts++

    try {
      await fetchCards()
      if (unmounted) return
      // 如果全部卡片状态都是终端状态 (COMPLETED/FAILED)，停止轮询
      if (cards.value.length > 0 && cards.value.every((c) => c.status === 'COMPLETED' || c.status === 'FAILED')) {
        pollingCards.value = false
        generating.value = false
        return
      }
    } catch {
      // 轮询失败继续
    }

    pollTimer = setTimeout(poll, 2000)
  }

  pollTimer = setTimeout(poll, 2000)
}

// ── 卡片列表 ──

const cards = ref<API.CardPage[]>([])
const loading = ref(false)
const loadedOnce = ref(false)
const loadError = ref('')

let fetchSeq = 0

const fetchCards = async () => {
  if (!taskId.value) return
  loading.value = true
  loadError.value = ''
  const seq = ++fetchSeq

  try {
    const res = await getCards(taskId.value)
    if (seq !== fetchSeq) return

    if (res.data.code !== 0) {
      // 404 可能表示还没有生成
      if (res.data.code === 40400) {
        cards.value = []
        return
      }
      throw new Error(res.data.message || '加载失败')
    }

    cards.value = res.data.data ?? []
  } catch (error) {
    if (seq !== fetchSeq) return
    console.error('加载卡片列表失败:', error)
    loadError.value =
      error instanceof Error ? error.message : '网络或服务暂时不可用，请稍后重试。'
  } finally {
    loading.value = false
    loadedOnce.value = true
  }
}

const refreshCards = () => {
  operationNotice.value = null
  void fetchCards()
}

// ── 计算属性 ──

const initialLoading = computed(() => loading.value && !loadedOnce.value)

const cardsTitle = computed(() => {
  if (!cards.value.length) return '暂无卡片'
  const completed = cards.value.filter((c) => c.status === 'COMPLETED').length
  return `${completed}/${cards.value.length} 张已完成`
})

const cardsSummary = computed(() => {
  if (!cards.value.length) return ''
  const completed = cards.value.filter((c) => c.status === 'COMPLETED').length
  const failed = cards.value.filter((c) => c.status === 'FAILED').length
  const parts: string[] = []
  if (completed) parts.push(`${completed} 成功`)
  if (failed) parts.push(`${failed} 失败`)
  return parts.join(' / ')
})

// ── 下载 ──

const downloadImage = (url: string, filename: string) => {
  const a = document.createElement('a')
  a.style.display = 'none'
  a.href = url
  a.download = `${filename}.png`
  document.body.appendChild(a)
  a.click()
  setTimeout(() => {
    document.body.removeChild(a)
  }, 100)
}

// ── 生命周期 ──

onMounted(() => {
  void loadArticle()
  void fetchCards()
})

watch(taskId, () => {
  // 切换文章时停止当前轮询并清理状态
  if (pollTimer !== null) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
  pollingCards.value = false
  generating.value = false
  cards.value = []
  previewUrls.value = []
  loadError.value = ''
  void loadArticle()
  void fetchCards()
})

onBeforeUnmount(() => {
  unmounted = true
  if (pollTimer !== null) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
})
</script>

<style scoped lang="scss">
#cardPage {
  min-height: calc(100vh - 64px);
  padding: 40px 24px 72px;
  background: var(--surface-page);
  color: var(--text-body);
}

.page-heading,
.page-feedback,
.preview-section,
.cards-section {
  width: min(100%, 1120px);
  margin-right: auto;
  margin-left: auto;
}

// ── 页头 ──


.breadcrumb {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
  color: var(--text-muted);
  font-size: 12px;

  a {
    color: var(--text-subtle);
    text-decoration: none;

    &:hover {
      color: var(--color-primary);
    }
  }

  .current {
    color: var(--text-strong);
    font-weight: 600;
  }
}





.badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 100px;
  background: var(--surface-muted);
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
}

// ── 区块 ──

.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 0 18px;
  margin-bottom: 20px;
  border-bottom: 1px solid var(--border-default);

  h2 {
    margin: 0;
    color: var(--text-strong);
    font-family: var(--font-heading);
    font-size: 20px;
    font-weight: 650;
  }
}

.heading-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-range {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 12px;
}

.preview-section,
.cards-section {
  margin-bottom: 32px;
  padding: 24px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-lg);
  background: var(--surface-panel);
}

.page-feedback {
  margin-bottom: 24px;
}

.loading-state,
.empty-state {
  min-height: 200px;
  padding: 40px 0;
}

.empty-hint {
  display: block;
  margin-top: 8px;
  color: var(--text-muted);
  font-size: 12px;
}

// ── 卡片画廊 ──

.card-gallery {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 24px;
}

.card-item {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--surface-page);
  transition: box-shadow var(--transition-fast);

  &:hover {
    box-shadow: var(--shadow-md);
  }
}

.card-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--border-subtle);
  color: var(--text-strong);
  font-size: 13px;
  font-weight: 600;
}

.card-status-tag {
  margin-left: auto;
}

.card-dim {
  color: var(--text-muted);
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 400;
}

.card-image-wrap {
  position: relative;
  aspect-ratio: 9 / 16;
  overflow: hidden;
  background: var(--surface-muted);

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
}

.card-failed {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  padding: 24px;
  background: var(--surface-muted);
  color: var(--state-error-text);
  font-size: 12px;
  text-align: center;
}

.card-running {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 200px;
  padding: 24px;
  background: var(--surface-muted);
  color: var(--text-muted);
  font-size: 13px;
}

.card-item-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-top: 1px solid var(--border-subtle);
}

.render-time {
  color: var(--text-disabled);
  font-family: var(--font-mono);
  font-size: 11px;
}

// ── 响应式 ──

@media (max-width: 768px) {
  #cardPage {
    padding: 28px 16px 56px;
  }

  .page-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 18px;
  }

  .page-heading h1 {
    font-size: 30px;
  }

  .heading-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .heading-actions :deep(.ant-btn) {
    flex: 1;
    min-width: 140px;
  }

  .card-gallery {
    grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
    gap: 16px;
  }
}

@media (prefers-reduced-motion: reduce) {
  #cardPage :deep(*) {
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
