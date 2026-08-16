<template>
  <section class="comic-library">
    <h1>我的漫画手帐</h1>
    <div v-if="loading">加载中…</div>
    <div v-else-if="books.length === 0" class="empty">
      还没有手帐，去技能中心用「漫画手帐」创建第一页吧。
    </div>
    <div v-else class="book-list">
      <article v-for="book in books" :key="book.id" class="book-card">
        <h2>{{ book.bookName }}</h2>
        <a-button size="small" @click="loadEpisodes(book.id!)">查看章节</a-button>
      </article>
    </div>
    <section v-if="episodes.length" class="episode-list">
      <h3>章节</h3>
      <div v-for="ep in episodes" :key="ep.id" class="episode-item">
        <span>{{ ep.title }}</span>
        <a-button size="small" @click="preview(ep)">预览</a-button>
        <a-button v-if="ep.pngUrl" size="small" :href="ep.pngUrl" download>下载</a-button>
      </div>
    </section>
    <iframe v-if="previewHtml" class="comic-frame" :srcdoc="previewHtml" sandbox="" title="预览" />
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import { getComicEpisode, getComicEpisodesByMonth, listComicBooks, listComicMonths } from '@/api/comicController'

const books = ref<API.ComicBookPo[]>([])
const episodes = ref<API.ComicEpisodePo[]>([])
const previewHtml = ref('')
const loading = ref(true)

const loadBooks = async () => {
  try {
    const res = await listComicBooks()
    if (res.data.code === 0) books.value = res.data.data ?? []
  } catch {
    message.error('手帐加载失败，请重试')
  } finally {
    loading.value = false
  }
}

// MVP：展示最新月册的章节；完整按档案→月册→章节两级浏览
const loadEpisodes = async (bookId: number) => {
  try {
    const res = await listComicMonths(bookId)
    const latest = res.data.data?.[0]
    if (latest) {
      episodes.value = await getComicEpisodesByMonth(bookId, latest.yearMonth!)
    }
  } catch {
    message.error('手帐加载失败，请重试')
  }
}

const preview = async (ep: API.ComicEpisodePo) => {
  try {
    const res = await getComicEpisode(ep.id!)
    previewHtml.value = res.data.data?.pageHtml ?? ''
  } catch {
    message.error('预览失败，请重试')
  }
}

onMounted(loadBooks)
</script>

<style scoped lang="scss">
.comic-library {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px 16px 48px;
}

.empty {
  color: var(--color-text-muted);
}

.book-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}

.book-card {
  padding: 16px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.book-card h2 {
  margin: 0 0 12px;
  font-size: 16px;
}

.episode-list {
  margin-top: 28px;
}

.episode-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--color-border);
}

.comic-frame {
  width: 100%;
  height: 70vh;
  margin-top: 24px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}
</style>
