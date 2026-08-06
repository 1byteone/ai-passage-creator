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
    <slot />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import emptyGift from '@/assets/illustration/empty-gift.png'

const props = withDefaults(
  defineProps<{
    description?: string
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
  gap: 12px;
  padding: 40px 20px;
  text-align: center;

  .empty-image {
    width: 180px;
    height: 180px;
    object-fit: contain;
    opacity: 0.8;
    display: block;

    &[hidden] {
      display: none;
    }
  }

  .empty-description {
    color: var(--color-text-muted);
    font-size: 14px;
    margin: 0;
  }
}
</style>