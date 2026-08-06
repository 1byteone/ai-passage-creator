<template>
  <div class="illustration-empty">
    <img
      v-show="!imageHidden"
      :src="image"
      :alt="description"
      class="empty-image"
      @error="onImageError"
    />
    <p class="empty-description">{{ description }}</p>
    <p v-if="hint" class="empty-hint">{{ hint }}</p>
    <slot />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import emptyGift from '@/assets/illustration/empty-gift.png'

withDefaults(
  defineProps<{
    description?: string
    hint?: string
    image?: string
  }>(),
  {
    description: '暂无数据',
    image: emptyGift,
  },
)

// 图片加载失败时隐藏插画（退化为纯文字空状态）
const imageHidden = ref(false)
const onImageError = () => {
  imageHidden.value = true
}
</script>

<style scoped lang="scss">
.illustration-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 48px 24px;
  text-align: center;

  // 浅绿径向渐变背景（品牌色融合）
  background: radial-gradient(ellipse at center, rgba(34, 197, 94, 0.06) 0%, transparent 70%);
  border-radius: var(--radius-2xl);

  .empty-image {
    width: 200px;
    height: 200px;
    object-fit: contain;
    opacity: 0.85;
    display: block;

    // 呼吸动效
    animation: empty-breathe 3s ease-in-out infinite;

    &[hidden] {
      display: none;
    }
  }

  .empty-description {
    color: var(--color-text-secondary);
    font-size: 15px;
    font-weight: 500;
    margin: 0;
  }

  .empty-hint {
    color: var(--color-text-muted);
    font-size: 13px;
    margin: 0;
  }
}

@keyframes empty-breathe {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.03); }
}
</style>