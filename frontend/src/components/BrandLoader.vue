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

  /* 双色渐变光圈（品牌绿 → 浅绿），mask 细环 */
  &::before {
    content: '';
    position: absolute;
    inset: -12px;
    border-radius: 50%;
    background: conic-gradient(
      transparent 0%,
      var(--color-primary) 25%,
      var(--color-primary-light) 50%,
      transparent 75%
    );
    animation: ring-spin 1.5s linear infinite;
    mask: radial-gradient(farthest-side, transparent calc(100% - 3px), #000 calc(100% - 2px));
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