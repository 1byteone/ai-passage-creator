<template>
  <div class="brand-loader" role="status" aria-label="加载中">
    <div class="loader-ring">
      <img :src="heroPlants" alt="" aria-hidden="true" class="loader-illustration" />
    </div>
    <p v-if="text" class="loader-text">{{ text }}</p>
  </div>
</template>

<script setup lang="ts">
import heroPlants from '@/assets/illustration/hero-plants.png'

defineProps<{
  text?: string
}>()
</script>

<style scoped lang="scss">
.brand-loader {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 48px;
  text-align: center;
}

.loader-ring {
  position: relative;
  width: 120px;
  height: 120px;

  // 旋转光圈（品牌色）
  &::before {
    content: '';
    position: absolute;
    inset: -12px;
    border-radius: 50%;
    border: 3px solid transparent;
    border-top-color: var(--color-primary);
    animation: ring-spin 1.5s linear infinite;
  }

  .loader-illustration {
    width: 100%;
    height: 100%;
    object-fit: contain;
    animation: loader-float 2s ease-in-out infinite;
  }
}

@keyframes ring-spin {
  to { transform: rotate(360deg); }
}

@keyframes loader-float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-4px); }
}

.loader-text {
  color: var(--color-text-muted);
  font-size: 14px;
  margin: 0;
}
</style>