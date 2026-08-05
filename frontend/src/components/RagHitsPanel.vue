<template>
  <div class="rag-hits-panel" aria-label="相关文章">
    <a-skeleton v-if="loading" active :paragraph="{ rows: 2 }" />
    <div v-else-if="noInput" class="rag-hits-hint">
      <BulbOutlined />
      <span>输入选题后，将展示相关历史文章</span>
    </div>
    <a-empty v-else-if="hits.length === 0" :description="emptyText" class="rag-hits-empty" />
    <ul v-else class="rag-hits-list">
      <li v-for="hit in hits" :key="hit.refId">
        <button type="button" class="rag-hit-card" @click="emit('select', hit)">
          <strong class="rag-hit-title">{{ hit.title || '未命名文章' }}</strong>
          <span class="rag-hit-tag">相关</span>
        </button>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { BulbOutlined } from '@ant-design/icons-vue'

withDefaults(
  defineProps<{
    hits: API.RagHit[]
    loading?: boolean
    noInput?: boolean
    emptyText?: string
  }>(),
  { loading: false, noInput: false, emptyText: '暂无相关历史，换个选题试试' },
)

const emit = defineEmits<{ (e: 'select', hit: API.RagHit): void }>()
</script>

<style scoped>
.rag-hits-panel { min-height: 40px; }
.rag-hits-hint {
  display: flex; align-items: center; gap: 8px;
  padding: 12px; border-radius: var(--radius-md);
  background: var(--color-background-secondary);
  color: var(--color-text-muted); font-size: 12px; line-height: 1.5;
}
.rag-hits-empty { margin: 0; }
.rag-hits-list { margin: 0; padding: 0; list-style: none; display: grid; gap: 8px; }
.rag-hit-card {
  display: flex; align-items: center; justify-content: space-between; gap: 10px;
  width: 100%; padding: 10px 12px; border: 1px solid var(--color-border);
  border-radius: var(--radius-md); background: var(--color-background-secondary);
  text-align: left; cursor: pointer; transition: all var(--transition-fast);
}
.rag-hit-card:hover { border-color: var(--color-primary); background: rgba(34, 197, 94, 0.05); }
.rag-hit-title {
  font-size: 13px; color: var(--color-text); line-height: 1.5;
  overflow: hidden; text-overflow: ellipsis;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;
}
.rag-hit-tag {
  flex: none; padding: 1px 8px; border-radius: var(--radius-full);
  background: rgba(34, 197, 94, 0.1); color: var(--color-primary-dark); font-size: 11px;
}
</style>
