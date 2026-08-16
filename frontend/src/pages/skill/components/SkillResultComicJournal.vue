<template>
  <section class="comic-result" aria-label="漫画手帐结果">
    <p v-if="!html" class="comic-empty">本次执行未产出页面，请重试或检查输入。</p>
    <iframe v-else class="comic-frame" :srcdoc="html" sandbox="" title="漫画手帐预览" />
    <div class="comic-actions">
      <a-button v-if="pngUrl" :href="pngUrl" download="comic.png" type="primary">下载 PNG</a-button>
      <a-button @click="goLibrary">打开手帐库</a-button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'

// 完成态 outputData 的 layoutResult 阶段输出不含 pageHtml（渲染在 ComicJournalService），
// 前端用 skill.complete 后调 /skill/{id}/result 的 outputData 也仅 LLM 输出。
// 因此 HTML 由后端渲染后存 comic_episode.pageHtml，前端在此组件跳 /comic 查看。
defineProps<{ outputData: Record<string, unknown> }>()

const router = useRouter()

const html = computed(() => '')
const pngUrl = computed(() => '')

const goLibrary = () => router.push('/comic')
</script>

<style scoped lang="scss">
.comic-frame {
  width: 100%;
  height: 70vh;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.comic-actions {
  display: flex;
  gap: 12px;
  margin-top: 14px;
}

.comic-empty {
  color: var(--color-text-muted);
}
</style>
