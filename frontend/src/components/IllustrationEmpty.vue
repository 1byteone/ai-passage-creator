<template>
  <div class="illustration-empty">
    <!-- 插画舞台：插画 + 装饰点圆环（undraw 多层次对象） -->
    <div class="illustration-stage">
      <span class="deco-dot deco-1" aria-hidden="true"></span>
      <span class="deco-ring deco-ring-1" aria-hidden="true"></span>
      <span class="deco-dot deco-2" aria-hidden="true"></span>
      <img
        v-show="!imageHidden"
        :src="image"
        :alt="description"
        class="empty-image"
        @error="onImageError"
      />
      <span class="deco-dot deco-3" aria-hidden="true"></span>
    </div>
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

  /* 浅绿径向渐变背景（品牌色融合，复用 --gradient-hero 浅绿基调） */
  background: radial-gradient(ellipse at center, rgba(34, 197, 94, 0.06) 0%, transparent 70%);
  border-radius: var(--radius-2xl);
}

/* 插画舞台：装饰点圆环错落，形成 undraw「多层次对象」层次感 */
.illustration-stage {
  position: relative;
  width: 220px;
  height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;

  .empty-image {
    width: 200px;
    height: 200px;
    object-fit: contain;
    opacity: 0.85;
    display: block;
    position: relative;
    z-index: 1;

    /* 呼吸动效（全局 prefers-reduced-motion 自动降级） */
    animation: empty-breathe 3s ease-in-out infinite;

    &[hidden] {
      display: none;
    }
  }
}

/* 装饰圆点（半透明品牌绿） */
.deco-dot {
  position: absolute;
  border-radius: 50%;
  background: rgba(34, 197, 94, 0.15);
  pointer-events: none;
  z-index: 0;

  &.deco-1 {
    width: 24px;
    height: 24px;
    top: 8px;
    left: 12px;
  }

  &.deco-2 {
    width: 12px;
    height: 12px;
    top: 44px;
    right: 4px;
  }

  &.deco-3 {
    width: 18px;
    height: 18px;
    bottom: 10px;
    left: 18px;
  }
}

/* 装饰圆环（更淡，增加层次） */
.deco-ring {
  position: absolute;
  border: 2px solid rgba(34, 197, 94, 0.12);
  border-radius: 50%;
  pointer-events: none;
  z-index: 0;

  &.deco-ring-1 {
    width: 48px;
    height: 48px;
    bottom: 4px;
    right: 10px;
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

@keyframes empty-breathe {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.03); }
}
</style>