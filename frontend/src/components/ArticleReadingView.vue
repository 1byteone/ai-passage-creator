<template>
  <article class="reading-view" :aria-labelledby="titleId">
    <header class="article-header">
      <div v-if="showMeta" class="article-meta">
        <StatusBadge :status="article.status || 'COMPLETED'" />
        <time v-if="article.createTime" :datetime="article.createTime">
          创建于 {{ formatDate(article.createTime) }}
        </time>
      </div>
      <h1 :id="titleId">{{ article.mainTitle || article.topic || '未命名文章' }}</h1>
      <p v-if="article.subTitle">{{ article.subTitle }}</p>
    </header>

    <details v-if="article.outline?.length" class="outline">
      <summary>查看文章大纲（{{ article.outline.length }} 节）</summary>
      <ol>
        <li v-for="item in article.outline" :key="item.section">
          <strong>{{ item.title }}</strong>
          <ul v-if="item.points?.length">
            <li v-for="point in item.points" :key="point">{{ point }}</li>
          </ul>
        </li>
      </ol>
    </details>

    <div
      v-if="content"
      class="article-content"
      v-html="markdownToHtml(content)"
    ></div>
    <a-empty v-else description="这篇文章暂时没有可阅读的正文" class="content-empty" />

    <section v-if="!article.fullContent && article.images?.length" class="image-section">
      <h2>文章配图</h2>
      <div class="image-grid">
        <figure v-for="image in article.images" :key="image.position || image.url">
          <img :src="image.url" :alt="image.description || ''" loading="lazy" />
          <figcaption>
            <span>{{ image.description || image.keywords || '文章配图' }}</span>
            <small v-if="image.method">{{ image.method }}</small>
          </figcaption>
        </figure>
      </div>
    </section>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { markdownToHtml } from '@/utils/markdown'
import { formatDate } from '@/utils/date'

const props = withDefaults(
  defineProps<{
    article: Partial<API.ArticleVO>
    titleId?: string
    showMeta?: boolean
  }>(),
  { titleId: 'article-title', showMeta: true },
)

const content = computed(() => props.article.fullContent || props.article.content || '')
</script>

<style scoped>
.reading-view {
  width: min(760px, 100%);
  margin-inline: auto;
}

.article-header {
  padding: 18px 0 30px;
  border-bottom: 1px solid var(--border-default);
}

.article-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}

.article-meta time {
  color: var(--text-muted);
  font-size: 12px;
}

.article-header h1 {
  margin: 0;
  color: var(--text-strong);
  font-size: clamp(28px, 4vw, 42px);
  line-height: 1.2;
}

.article-header p {
  margin: 14px 0 0;
  color: var(--text-subtle);
  font-size: 17px;
  line-height: 1.7;
}

.outline {
  margin: 28px 0;
  padding: 16px 18px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--surface-muted);
}

.outline summary {
  color: var(--text-body);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.outline > ol {
  margin: 18px 0 0;
  padding-left: 24px;
  color: var(--text-subtle);
}

.outline > ol > li {
  margin-bottom: 14px;
  padding-left: 4px;
}

.outline ul {
  margin: 7px 0 0;
  padding-left: 18px;
  font-size: 13px;
  line-height: 1.7;
}

.article-content {
  padding-top: 28px;
  color: var(--text-body);
  font-size: 16px;
  line-height: 1.9;
  overflow-wrap: anywhere;
}

.article-content :deep(h2) {
  margin: 42px 0 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid var(--border-default);
  font-size: 24px;
  line-height: 1.35;
}

.article-content :deep(h3) {
  margin: 30px 0 12px;
  font-size: 19px;
}

.article-content :deep(p) {
  margin: 0 0 18px;
}

.article-content :deep(ul),
.article-content :deep(ol) {
  margin: 0 0 20px;
  padding-left: 1.6em;
}

.article-content :deep(blockquote) {
  margin: 24px 0;
  padding: 14px 18px;
  border-left: 3px solid var(--color-primary);
  background: var(--surface-brand-soft);
  color: var(--text-subtle);
}

.article-content :deep(pre) {
  max-width: 100%;
  padding: 18px;
  overflow: auto;
  border-radius: var(--radius-md);
  background: var(--color-secondary);
  color: var(--text-inverse);
  font-size: 13px;
}

.article-content :deep(code) {
  font-family: var(--font-mono);
}

.article-content :deep(img) {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 28px auto;
  border-radius: var(--radius-md);
}

.content-empty {
  padding: 72px 20px;
}

.image-section {
  margin-top: 48px;
  padding-top: 28px;
  border-top: 1px solid var(--border-default);
}

.image-section h2 {
  margin: 0 0 18px;
  font-size: 20px;
}

.image-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

figure {
  margin: 0;
  overflow: hidden;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
}

figure img {
  display: block;
  width: 100%;
  aspect-ratio: 16 / 10;
  object-fit: cover;
}

figcaption {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  color: var(--text-subtle);
  font-size: 12px;
}

figcaption small {
  color: var(--text-muted);
}

@media (max-width: 600px) {
  .article-header {
    padding-top: 4px;
  }

  .article-meta {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }

  .article-header h1 {
    font-size: 28px;
  }

  .article-header p {
    font-size: 15px;
  }

  .article-content {
    font-size: 15px;
    line-height: 1.85;
  }

  .article-content :deep(h2) {
    margin-top: 34px;
    font-size: 21px;
  }

  .image-grid {
    grid-template-columns: 1fr;
  }
}
</style>
