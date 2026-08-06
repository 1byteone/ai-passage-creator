<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/loginUser'
import {
  RocketOutlined,
  FileTextOutlined,
  OrderedListOutlined,
  EditOutlined,
  PictureOutlined,
  ThunderboltOutlined,
  ClockCircleOutlined,
  RightOutlined,
  BulbOutlined,
  FileDoneOutlined,
  ShareAltOutlined
} from '@ant-design/icons-vue'
import { getSkillUiConfig } from '@/config/skill'
import heroWriting from '@/assets/illustration/hero-writing.png'
import heroPlants from '@/assets/illustration/hero-plants.png'
import BrandLoader from '@/components/BrandLoader.vue'

const router = useRouter()
const loginUserStore = useLoginUserStore()

// 输入框
const topic = ref('')
const previewTopic = computed(() => topic.value.trim() || '2026年AI如何改变职场')

// 最近文章
const recentArticles = ref<API.ArticleVO[]>([])
const loadingArticles = ref(false)
const extendedOverviewTrigger = ref<HTMLElement | null>(null)
const showExtendedOverview = ref(!window.matchMedia('(max-width: 480px)').matches)
let extendedOverviewObserver: IntersectionObserver | undefined

const goToCreate = () => {
  if (topic.value.trim()) {
    router.push({ path: '/create', query: { topic: topic.value } })
  } else {
    router.push('/create')
  }
}

const goToList = () => {
  router.push('/article/list')
}

const viewArticle = (article: API.ArticleVO) => {
  router.push(`/article/${article.taskId}`)
}

const quickSkills = [
  { name: 'topic-gen', icon: BulbOutlined },
  { name: 'proofreading', icon: FileDoneOutlined },
  { name: 'article-to-x', icon: ShareAltOutlined },
]

const workflowPreviewSteps = [
  {
    title: '标题方案',
    description: '根据选题生成多组可选择标题',
    status: '可选择',
  },
  {
    title: '大纲确认',
    description: '先确认结构，再进入正文生成',
    status: '可编辑',
  },
  {
    title: '正文与配图',
    description: '流式生成内容并匹配图片素材',
    status: '自动生成',
  },
]

const openSkill = (skillName: string) => {
  router.push(`/skill/${skillName}`)
}

const goToSkillCenter = () => {
  router.push('/skill')
}

// 加载最近文章
const loadRecentArticles = async () => {
  if (!loginUserStore.loginUser.id) return

  loadingArticles.value = true
  try {
    const { listArticle } = await import('@/api/articleController')
    const res = await listArticle({ pageNum: 1, pageSize: 6 })
    recentArticles.value = res.data.data?.records || []
  } catch (error) {
    console.error('加载文章失败:', error)
  } finally {
    loadingArticles.value = false
  }
}

// 格式化时间
const formatTime = (time: string | undefined) => {
  if (!time) return '--'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
    .format(new Date(time))
    .replace('/', '-')
}

const capabilityStages = [
  {
    icon: FileTextOutlined,
    title: '输入选题',
    description: '用一句话描述主题，系统保留原始意图并进入创作流程。',
    result: '主题上下文',
    signal: '可直接开始'
  },
  {
    icon: OrderedListOutlined,
    title: '确认标题和大纲',
    description: '先得到标题方案和章节结构，再决定是否继续生成正文。',
    result: '标题 + 大纲',
    signal: '可调整'
  },
  {
    icon: PictureOutlined,
    title: '生成正文与配图',
    description: '正文流式输出，配图跟随内容主题匹配，减少等待不确定感。',
    result: '正文 + 封面图',
    signal: '生成中可观察'
  },
  {
    icon: FileDoneOutlined,
    title: '继续编辑或导出',
    description: '成稿进入详情页，继续复制、导出、回看历史或再次处理。',
    result: '可复用成稿',
    signal: '任务闭环'
  }
]

const outputArtifacts = ['标题候选', '章节大纲', '正文段落', '封面配图', '历史记录']

watch(
  () => loginUserStore.loginUser.id,
  (userId) => {
    if (userId) void loadRecentArticles()
  },
  { immediate: true },
)

onMounted(() => {
  if (showExtendedOverview.value) return

  if (!('IntersectionObserver' in window)) {
    showExtendedOverview.value = true
    return
  }

  extendedOverviewObserver = new IntersectionObserver(([entry]) => {
    if (!entry?.isIntersecting) return
    showExtendedOverview.value = true
    extendedOverviewObserver?.disconnect()
  })

  if (extendedOverviewTrigger.value) {
    extendedOverviewObserver.observe(extendedOverviewTrigger.value)
  }
})

onBeforeUnmount(() => {
  extendedOverviewObserver?.disconnect()
})
</script>

<template>
  <div id="homePage">
    <!-- Hero Section -->
    <section class="hero-section">
      <div class="hero-bg"></div>
      <!-- undraw 插画装饰（绿色治愈系） -->
      <img
        :src="heroWriting"
        alt=""
        aria-hidden="true"
        class="hero-writing-decoration"
      />
      <img
        :src="heroPlants"
        alt=""
        aria-hidden="true"
        class="hero-plants-decoration"
      />
      <div class="container">
        <div class="hero-shell">
          <div class="hero-copy">
            <div class="hero-badge">
              <ThunderboltOutlined aria-hidden="true" />
              <span>AI 驱动的内容创作平台</span>
            </div>
            <h1 class="hero-title">AI 爆款文章创作器</h1>
            <p class="hero-subtitle">从选题进入标题、大纲、正文和配图流程，帮助内容创作者更稳定地完成一篇文章。</p>

            <!-- 核心输入框 -->
            <div class="input-wrapper">
              <label class="visually-hidden" for="home-topic-input">文章选题</label>
              <div class="topic-field">
                <EditOutlined class="input-icon" aria-hidden="true" />
                <input
                  id="home-topic-input"
                  v-model="topic"
                  type="text"
                  placeholder="输入文章选题，例如：AI如何改变职场"
                  aria-describedby="home-topic-help"
                  class="topic-input"
                  @keydown.enter="goToCreate"
                />
              </div>
              <button type="button" class="cta-btn" @click="goToCreate">
                <RocketOutlined aria-hidden="true" />
                开始创作
              </button>
            </div>

            <div class="hero-secondary-row">
              <button type="button" class="secondary-cta" @click="goToSkillCenter">
                查看工具箱
                <RightOutlined aria-hidden="true" />
              </button>
              <span id="home-topic-help" class="hero-tips">支持工作总结、心得体会、演讲稿、分析报告</span>
            </div>
          </div>

          <aside class="workflow-proof" aria-label="文章创作流程预览">
            <div class="proof-header">
              <div>
                <span>流程预览</span>
                <strong>从选题到成稿</strong>
              </div>
              <FileTextOutlined aria-hidden="true" />
            </div>

            <div class="proof-topic">
              <span>当前选题</span>
              <strong>{{ previewTopic }}</strong>
            </div>

            <div class="proof-steps">
              <div
                v-for="(step, index) in workflowPreviewSteps"
                :key="step.title"
                :class="['proof-step', { active: index === 0 }]"
              >
                <div class="proof-step-index">{{ index + 1 }}</div>
                <div class="proof-step-copy">
                  <strong>{{ step.title }}</strong>
                  <span>{{ step.description }}</span>
                </div>
                <span class="proof-step-status">{{ step.status }}</span>
              </div>
            </div>
          </aside>
        </div>
      </div>
    </section>

    <!-- Recent Articles Section -->
    <div
      v-if="loginUserStore.loginUser.id && (recentArticles.length > 0 || loadingArticles)"
      class="articles-section continue-section"
    >
      <div class="container">
        <div class="section-header-row">
          <div>
            <h2 class="section-title-sm">继续最近创作</h2>
            <p class="section-subtitle-sm">回到未完成任务，或查看最近生成的文章</p>
          </div>
          <button type="button" class="view-all-btn" @click="goToList">
            查看全部
            <RightOutlined aria-hidden="true" />
          </button>
        </div>

        <div class="recent-content" :aria-busy="loadingArticles">
          <BrandLoader
            v-if="loadingArticles && !recentArticles.length"
            text="正在加载最近创作…"
            class="recent-loading"
          />
          <div v-else-if="recentArticles.length" class="articles-grid">
            <div
              v-for="article in recentArticles"
              :key="article.id"
              class="article-card"
              @click="viewArticle(article)"
            >
              <div class="article-cover">
                <img
                  v-if="article.coverImage"
                  :src="article.coverImage"
                  :alt="article.mainTitle"
                />
                <div v-else class="cover-placeholder">
                  <FileTextOutlined aria-hidden="true" />
                </div>
              </div>
              <div class="article-info">
                <h4 class="article-title">{{ article.mainTitle || article.topic }}</h4>
                <div class="article-meta">
                  <span class="article-time">
                    <ClockCircleOutlined aria-hidden="true" />
                    {{ formatTime(article.createTime) }}
                  </span>
                  <span :class="['article-status', `status-${article.status?.toLowerCase()}`]">
                    {{ article.status === 'COMPLETED' ? '已完成' : article.status === 'PROCESSING' ? '生成中' : '等待中' }}
                  </span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <section class="skill-strip-section">
      <div class="container skill-strip-container">
        <div class="skill-strip-heading">
          <div>
            <span>AI 工具箱</span>
            <h2>从当前任务直接开始</h2>
          </div>
          <button type="button" class="section-link-btn" @click="router.push('/skill')">
            查看全部
            <RightOutlined aria-hidden="true" />
          </button>
        </div>
        <div class="skill-strip">
          <button
            v-for="skill in quickSkills"
            :key="skill.name"
            type="button"
            @click="openSkill(skill.name)"
          >
            <span :class="['quick-skill-icon', getSkillUiConfig(skill.name).accent]">
              <component :is="skill.icon" aria-hidden="true" />
            </span>
            <span class="quick-skill-copy">
              <strong>{{ getSkillUiConfig(skill.name).title }}</strong>
              <small>{{ getSkillUiConfig(skill.name).description }}</small>
            </span>
            <RightOutlined class="quick-skill-arrow" aria-hidden="true" />
          </button>
        </div>
      </div>
    </section>

    <div
      v-if="!showExtendedOverview"
      ref="extendedOverviewTrigger"
      class="extended-overview-trigger"
      aria-hidden="true"
    ></div>

    <section v-if="showExtendedOverview" class="capability-section">
      <div class="container">
        <div class="capability-header">
          <span>核心能力</span>
          <h2>把一篇文章拆成可确认的任务链</h2>
          <p>每一步都对应真实产物，减少从空白选题到完整成稿之间的跳跃感。</p>
        </div>
        <div class="capability-map">
          <div class="capability-steps">
            <article
              v-for="(stage, index) in capabilityStages"
              :key="stage.title"
              class="capability-step"
              tabindex="0"
            >
              <div class="capability-step-index">{{ index + 1 }}</div>
              <div class="capability-step-icon">
                <component :is="stage.icon" aria-hidden="true" />
              </div>
              <div class="capability-step-copy">
                <h3>{{ stage.title }}</h3>
                <p>{{ stage.description }}</p>
              </div>
              <div class="capability-step-result">
                <strong>{{ stage.result }}</strong>
                <span>{{ stage.signal }}</span>
              </div>
            </article>
          </div>

          <aside class="capability-output" aria-label="成稿产物预览">
            <div class="capability-output-header">
              <span>当前任务</span>
              <strong>{{ previewTopic }}</strong>
            </div>
            <div class="output-artifacts">
              <span
                v-for="artifact in outputArtifacts"
                :key="artifact"
              >
                {{ artifact }}
              </span>
            </div>
            <div class="output-sample">
              <div class="sample-line wide"></div>
              <div class="sample-line"></div>
              <div class="sample-line short"></div>
            </div>
            <button
              type="button"
              class="capability-action"
              @click="goToCreate"
            >
              <RocketOutlined aria-hidden="true" />
              <span>按这个选题开始</span>
            </button>
          </aside>
        </div>
      </div>
    </section>

    <div v-if="showExtendedOverview" class="features-section">
      <div class="container">
        <div class="section-header">
          <div class="section-badge">工作流边界</div>
          <h2 class="section-title">从输入到复用，路径保持清楚</h2>
          <p class="section-subtitle">标题、大纲、正文、配图和历史记录都围绕同一个创作任务展开</p>
        </div>
        <div class="workflow-summary">
          <div
            v-for="(stage, index) in capabilityStages"
            :key="index"
            class="workflow-summary-item"
          >
            <span>{{ stage.result }}</span>
            <strong>{{ stage.signal }}</strong>
          </div>
        </div>
      </div>
    </div>

  </div>
</template>

<style scoped>
#homePage {
  width: 100%;
  margin: 0;
  padding: 0;
  min-height: 100vh;
  background: var(--color-background);
}

/* Hero Section */
.hero-section {
  position: relative;
  padding: 72px 20px 56px;
  overflow: hidden;
}

.hero-bg {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: var(--gradient-hero);
  z-index: 0;
}

/* undraw 插画装饰（绿色治愈系） */
.hero-writing-decoration {
  position: absolute;
  right: 24px;
  bottom: 20px;
  width: 340px;
  height: 260px;
  object-fit: contain;
  opacity: 0.9;
  pointer-events: none;
  z-index: 1;

  /* 玻璃拟态容器：浅绿渐变圆角托底 + 微模糊（容器融色） */
  padding: 20px;
  background: rgba(255, 255, 255, 0.4);
  backdrop-filter: blur(4px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  border-radius: var(--radius-2xl);

  /* 漂浮动效 */
  animation: hero-float 6s ease-in-out infinite;
}

@keyframes hero-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-8px); }
}

.hero-plants-decoration {
  position: absolute;
  left: -30px;
  bottom: -40px;
  width: 240px;
  height: 240px;
  object-fit: contain;
  opacity: 0.7;
  pointer-events: none;
  z-index: 1;
  transform-origin: bottom center;

  /* 摇摆动效 */
  animation: hero-sway 8s ease-in-out infinite;
}

@keyframes hero-sway {
  0%, 100% { transform: rotate(-2deg); }
  50% { transform: rotate(2deg); }
}

.container {
  position: relative;
  z-index: 1;
  max-width: 1120px;
  margin: 0 auto;
}

.extended-overview-trigger {
  width: 100%;
  height: 1px;
}

/*
 * Keep the first render focused on the hero. These sections sit below the
 * fold, so layout/paint containment avoids paying their full rendering cost
 * during the initial interaction window while preserving their scroll space.
 */
.articles-section,
.skill-strip-section,
.capability-section,
.features-section {
  content-visibility: auto;
  contain-intrinsic-size: auto 720px;
}

.hero-shell {
  display: grid;
  grid-template-columns: minmax(0, 1.02fr) minmax(360px, 0.72fr);
  gap: 48px;
  align-items: center;
}

.hero-copy {
  min-width: 0;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 8px 16px;
  background: rgba(34, 197, 94, 0.1);
  border: 1px solid rgba(34, 197, 94, 0.2);
  border-radius: var(--radius-full);
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 24px;
  color: var(--color-primary-dark);
}

.hero-title {
  font-size: 52px;
  font-weight: 700;
  margin: 0 0 16px;
  letter-spacing: 0;
  line-height: 1.1;
  color: var(--color-text);
  background: linear-gradient(135deg, var(--color-primary-dark) 0%, var(--color-primary) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.hero-subtitle {
  max-width: 620px;
  font-size: 18px;
  line-height: 1.75;
  margin: 0 0 28px;
  color: var(--color-text-secondary);
  font-weight: 400;
}

/* 核心输入框 */
.input-wrapper {
  display: flex;
  gap: 12px;
  width: 100%;
  max-width: 680px;
  margin: 0 0 16px;
  padding: 8px;
  background: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  border: 1px solid var(--color-border);
}

.topic-field {
  display: flex;
  flex: 1;
  align-items: center;
  min-width: 0;
}

.topic-input {
  flex: 1;
  height: 52px;
  min-width: 0;
  border: 0;
  outline: 0;
  font-size: 16px;
  padding: 8px 16px;
  background: transparent;
  text-overflow: ellipsis;
}

.topic-input::placeholder {
  color: var(--color-text-muted);
}

.topic-field:focus-within {
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-focus);
}

.input-icon {
  color: var(--color-text-muted);
  font-size: 18px;
}

.cta-btn {
  height: 52px;
  padding: 0 32px;
  font-size: 16px;
  font-weight: 600;
  border-radius: var(--radius-lg);
  background: var(--gradient-primary);
  border: none;
  color: white;
  box-shadow: var(--shadow-green);
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
  transition: opacity var(--transition-normal) !important;
}

.cta-btn:hover,
.cta-btn:focus-visible,
.cta-btn:active {
  background: var(--gradient-primary);
  border: none;
  color: white;
  box-shadow: var(--shadow-green);
  opacity: 0.92;
}

.hero-secondary-row {
  display: flex;
  align-items: center;
  gap: 18px;
  flex-wrap: wrap;
}

.secondary-cta,
.section-link-btn,
.view-all-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.secondary-cta {
  height: 36px;
  padding: 0;
  color: var(--color-primary-dark);
  font-weight: 600;
}

.secondary-cta:hover,
.secondary-cta:focus-visible,
.section-link-btn:hover,
.section-link-btn:focus-visible {
  color: var(--color-primary);
}

.section-link-btn {
  padding: 6px 0;
  color: var(--color-primary-dark);
  font-weight: 600;
}

.hero-tips {
  display: block;
  max-width: 100%;
  font-size: 14px;
  color: var(--color-text-muted);
  margin: 0;
  overflow-wrap: anywhere;
}

.workflow-proof {
  min-width: 0;
  padding: 22px;
  background: var(--surface-panel, white);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
}

.proof-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--color-border-light);
}

.proof-header span,
.proof-topic span {
  display: block;
  color: var(--color-text-muted);
  font-size: 12px;
  line-height: 1.4;
}

.proof-header strong {
  display: block;
  margin-top: 4px;
  color: var(--color-text);
  font-size: 20px;
  line-height: 1.35;
}

.proof-header .anticon {
  flex: none;
  width: 40px;
  height: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary-dark);
  background: rgba(34, 197, 94, 0.1);
  border-radius: var(--radius-md);
  font-size: 18px;
}

.proof-topic {
  margin: 18px 0;
  padding: 14px;
  background: var(--color-background-secondary);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
}

.proof-topic strong {
  display: block;
  margin-top: 6px;
  color: var(--color-text);
  font-size: 15px;
  line-height: 1.5;
}

.proof-steps {
  display: grid;
  gap: 10px;
}

.proof-step {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  min-height: 72px;
  padding: 12px;
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
  background: white;
}

.proof-step.active {
  border-color: rgba(34, 197, 94, 0.32);
  background: rgba(34, 197, 94, 0.04);
}

.proof-step-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: var(--color-primary-dark);
  background: rgba(34, 197, 94, 0.12);
  font-size: 13px;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.proof-step-copy {
  min-width: 0;
}

.proof-step-copy strong,
.proof-step-copy span {
  display: block;
}

.proof-step-copy strong {
  margin-bottom: 4px;
  color: var(--color-text);
  font-size: 14px;
}

.proof-step-copy span {
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.proof-step-status {
  flex: none;
  color: var(--color-primary-dark);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

/* Features Section */
.skill-strip-section {
  padding: 0 20px 56px;
  background: var(--color-background);
}

.skill-strip-container {
  max-width: 1100px;
}

.skill-strip-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
}

.skill-strip-heading span {
  color: var(--color-primary-dark);
  font-size: 12px;
  font-weight: 600;
}

.skill-strip-heading h2 {
  margin: 4px 0 0;
  color: var(--color-text);
  font-size: 22px;
}

.skill-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: white;
  overflow: hidden;
}

.skill-strip button {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 20px;
  align-items: center;
  gap: 12px;
  min-height: 112px;
  padding: 18px;
  border: 0;
  border-right: 1px solid var(--color-border);
  background: white;
  text-align: left;
  cursor: pointer;
  transition: background var(--transition-fast);
}

.skill-strip button:last-child {
  border-right: 0;
}

.skill-strip button:hover,
.skill-strip button:focus-visible {
  background: var(--color-background-secondary);
  outline: none;
}

.quick-skill-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: var(--radius-md);
  font-size: 19px;
}

.quick-skill-icon.blue {
  background: #eff6ff;
  color: #1d4ed8;
}

.quick-skill-icon.green {
  background: #f0fdf4;
  color: #15803d;
}

.quick-skill-icon.amber {
  background: #fffbeb;
  color: #b45309;
}

.quick-skill-copy {
  min-width: 0;
}

.quick-skill-copy strong,
.quick-skill-copy small {
  display: block;
}

.quick-skill-copy strong {
  margin-bottom: 5px;
  color: var(--color-text);
  font-size: 15px;
}

.quick-skill-copy small {
  display: -webkit-box;
  overflow: hidden;
  color: var(--color-text-secondary);
  font-size: 12px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.quick-skill-arrow {
  color: var(--color-text-muted);
}

.capability-section {
  padding: 76px 20px;
  background: var(--color-background-secondary);
}

.capability-section .container,
.features-section .container {
  max-width: 1100px;
}

.capability-header {
  max-width: 680px;
  margin-bottom: 34px;
}

.capability-header span {
  color: var(--color-primary-dark);
  font-size: 13px;
  font-weight: 700;
}

.capability-header h2 {
  margin: 8px 0 10px;
  color: var(--color-text);
  font-size: 32px;
  line-height: 1.25;
  letter-spacing: 0;
}

.capability-header p {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 16px;
  line-height: 1.7;
}

.capability-map {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 24px;
  align-items: stretch;
}

.capability-steps {
  display: grid;
  gap: 12px;
}

.capability-step {
  display: grid;
  grid-template-columns: 34px 44px minmax(0, 1fr) minmax(116px, auto);
  align-items: center;
  gap: 14px;
  min-height: 104px;
  padding: 18px;
  background: white;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  transition:
    border-color var(--transition-fast),
    box-shadow var(--transition-fast),
    transform var(--transition-fast);
}

.capability-step:hover,
.capability-step:focus-visible {
  border-color: rgba(34, 197, 94, 0.36);
  box-shadow: var(--shadow-sm);
  outline: none;
  transform: translateY(-1px);
}

.capability-step-index {
  color: var(--color-primary-dark);
  font-size: 13px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
}

.capability-step-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  color: var(--color-primary-dark);
  background: rgba(34, 197, 94, 0.1);
  border-radius: var(--radius-md);
  font-size: 19px;
}

.capability-step-copy {
  min-width: 0;
}

.capability-step-copy h3 {
  margin: 0 0 6px;
  color: var(--color-text);
  font-size: 16px;
  line-height: 1.35;
}

.capability-step-copy p {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.capability-step-result {
  display: grid;
  gap: 5px;
  justify-items: end;
  min-width: 110px;
}

.capability-step-result strong {
  color: var(--color-text);
  font-size: 13px;
  white-space: nowrap;
}

.capability-step-result span {
  color: var(--color-primary-dark);
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.capability-output {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
  padding: 22px;
  background: white;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.capability-output-header {
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-border-light);
}

.capability-output-header span,
.capability-output-header strong {
  display: block;
}

.capability-output-header span {
  margin-bottom: 6px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.capability-output-header strong {
  color: var(--color-text);
  font-size: 18px;
  line-height: 1.45;
}

.output-artifacts {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.output-artifacts span {
  padding: 6px 10px;
  color: var(--color-primary-dark);
  background: rgba(34, 197, 94, 0.08);
  border: 1px solid rgba(34, 197, 94, 0.14);
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 700;
}

.output-sample {
  display: grid;
  gap: 10px;
  padding: 16px;
  background: var(--color-background-secondary);
  border: 1px solid var(--color-border-light);
  border-radius: var(--radius-md);
}

.sample-line {
  height: 10px;
  width: 76%;
  background: var(--color-border);
  border-radius: var(--radius-full);
}

.sample-line.wide {
  width: 100%;
}

.sample-line.short {
  width: 52%;
}

.capability-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 44px;
  width: 100%;
  color: white;
  background: var(--color-primary);
  border: 1px solid var(--color-primary);
  border-radius: var(--radius-md);
  font-weight: 700;
  cursor: pointer;
  transition:
    background var(--transition-fast),
    transform var(--transition-fast);
}

.capability-action:hover,
.capability-action:focus-visible {
  background: var(--color-primary-dark);
  outline: none;
}

.capability-action:active {
  transform: translateY(1px);
}

.features-section {
  padding: 56px 20px 80px;
  background: var(--color-background);
}

.section-header {
  text-align: center;
  margin-bottom: 28px;
}

.section-badge {
  display: inline-block;
  padding: 6px 14px;
  background: rgba(34, 197, 94, 0.1);
  border-radius: var(--radius-full);
  font-size: 13px;
  font-weight: 600;
  color: var(--color-primary-dark);
  margin-bottom: 16px;
}

.section-title {
  font-size: 32px;
  font-weight: 700;
  margin: 0 0 12px;
  color: var(--color-text);
  letter-spacing: 0;
}

.section-subtitle {
  font-size: 16px;
  color: var(--color-text-secondary);
  margin: 0;
}

.workflow-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  overflow: hidden;
  background: white;
}

.workflow-summary-item {
  display: grid;
  gap: 8px;
  min-height: 92px;
  padding: 18px;
  border-right: 1px solid var(--color-border);
}

.workflow-summary-item:last-child {
  border-right: 0;
}

.workflow-summary-item span {
  color: var(--color-text-muted);
  font-size: 12px;
}

.workflow-summary-item strong {
  color: var(--color-text);
  font-size: 15px;
  line-height: 1.4;
}

/* Articles Section */
.articles-section {
  padding: 60px 20px 80px;
  background: var(--color-background);
}

.continue-section {
  padding: 40px 20px 36px;
  border-top: 1px solid var(--color-border-light);
}

.articles-section .container {
  max-width: 1100px;
}

.section-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
}

.section-title-sm {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 4px;
  color: var(--color-text);
}

.section-subtitle-sm {
  font-size: 14px;
  color: var(--color-text-secondary);
  margin: 0;
}

.view-all-btn {
  color: var(--color-primary);
  font-weight: 500;
  padding: 0;
}

.recent-loading {
  min-height: 120px;
  margin: 0;
  display: grid;
  place-items: center;
  color: var(--color-text-muted);
  font-size: 14px;
}

.view-all-btn:hover {
  color: var(--color-primary-dark);
}

.articles-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.article-card {
  background: white;
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  overflow: hidden;
  transition: all var(--transition-normal);
  cursor: pointer;
}

.article-card:hover {
  border-color: var(--color-primary-light);
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}

.article-cover {
  height: 140px;
  background: var(--color-background-tertiary);
  overflow: hidden;
}

.article-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  color: var(--color-text-muted);
}

.article-info {
  padding: 16px;
}

.article-title {
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 12px;
  color: var(--color-text);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.article-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.article-time {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--color-text-muted);
}

.article-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  font-weight: 500;
}

.article-status.status-completed {
  background: rgba(34, 197, 94, 0.1);
  color: var(--color-primary-dark);
}

.article-status.status-processing {
  background: rgba(59, 130, 246, 0.1);
  color: #2563EB;
}

.article-status.status-pending {
  background: var(--color-background-tertiary);
  color: var(--color-text-muted);
}

/* Responsive */
@media (max-width: 992px) {
  .hero-shell {
    grid-template-columns: 1fr;
    gap: 28px;
  }

  .workflow-proof {
    max-width: 680px;
  }

  .skill-strip {
    grid-template-columns: 1fr;
  }

  .skill-strip button {
    min-height: 92px;
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .skill-strip button:last-child {
    border-bottom: 0;
  }

  .capability-map {
    grid-template-columns: 1fr;
  }

  .workflow-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .workflow-summary-item:nth-child(2n) {
    border-right: 0;
  }

  .workflow-summary-item:nth-child(-n + 2) {
    border-bottom: 1px solid var(--color-border);
  }

  .articles-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .hero-section {
    padding: 48px 16px 44px;
  }

  /* 移动端：隐藏主插画，缩小副插画 */
  .hero-writing-decoration {
    display: none;
  }

  .hero-plants-decoration {
    width: 140px;
    height: 140px;
    left: -30px;
    bottom: -40px;
  }

  .hero-title {
    font-size: 36px;
  }

  .hero-subtitle {
    font-size: 16px;
  }

  .input-wrapper {
    flex-direction: column;
    padding: 12px;
    box-sizing: border-box;
  }

  .cta-btn {
    width: 100%;
    justify-content: center;
  }

  .capability-section {
    padding: 56px 16px;
  }

  .capability-header h2 {
    font-size: 24px;
  }

  .capability-step {
    grid-template-columns: 32px minmax(0, 1fr);
    align-items: flex-start;
  }

  .capability-step-index {
    padding-top: 12px;
  }

  .capability-step-icon {
    display: none;
  }

  .capability-step-result {
    grid-column: 2;
    justify-items: start;
    min-width: 0;
  }

  .workflow-summary {
    grid-template-columns: 1fr;
  }

  .workflow-summary-item,
  .workflow-summary-item:nth-child(2n),
  .workflow-summary-item:nth-child(-n + 2) {
    border-right: 0;
    border-bottom: 1px solid var(--color-border);
  }

  .workflow-summary-item:last-child {
    border-bottom: 0;
  }

  .articles-grid {
    grid-template-columns: 1fr;
  }

  .section-title {
    font-size: 24px;
  }

  .section-header-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .skill-strip-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .workflow-proof {
    padding: 18px;
  }

  .proof-topic strong,
  .capability-output-header strong {
    overflow-wrap: anywhere;
  }

  .proof-step {
    grid-template-columns: 32px minmax(0, 1fr);
  }

  .proof-step-status {
    grid-column: 2;
  }
}

@media (max-width: 480px) {
  .hero-section {
    padding: 44px 12px 40px;
  }

  /* The compact hero keeps the primary topic action above the fold. */
  .workflow-proof {
    display: none;
  }

  .hero-title {
    font-size: 34px;
    line-height: 1.18;
  }

  .hero-subtitle {
    line-height: 1.7;
  }

  .input-wrapper,
  .workflow-proof,
  .capability-output,
  .skill-strip,
  .workflow-summary {
    max-width: 100%;
  }

  .topic-input {
    padding: 8px 10px;
  }

  .hero-secondary-row {
    gap: 10px;
  }

  .capability-step {
    padding: 16px 14px;
  }
}
</style>
