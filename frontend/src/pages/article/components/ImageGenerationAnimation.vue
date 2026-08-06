<template>
  <div class="image-gen-animation" role="status" aria-live="polite">
    <!-- 标题区 -->
    <div class="animation-header">
      <PictureOutlined class="header-icon" />
      <span class="header-title">{{ headerText }}</span>
      <span v-if="phase === 'generating'" class="header-count">
        {{ doneCount }}/{{ total }}
      </span>
      <CheckCircleOutlined v-if="completed" class="header-check" />
    </div>

    <!-- 卡片区 -->
    <div class="card-grid">
      <div
        v-for="(_, index) in total"
        :key="index"
        :class="['image-card', {
          'card-done': index < doneCount && imageUrls[index],
          'card-generating': index < doneCount && !imageUrls[index],
          'card-pending': index >= doneCount,
        }]"
      >
        <!-- 已完成：显示真实图片（渐进模糊） -->
        <img
          v-if="index < doneCount && imageUrls[index]"
          :src="imageUrls[index]"
          :class="['card-image', { loaded: loadedFlags[index] }]"
          @load="onImageLoad(index)"
          @error="onImageLoad(index)"
          alt="配图"
        />
        <!-- 待生成：灰色占位 -->
        <div v-else class="card-pending-content">
          <PictureOutlined class="card-pending-icon" />
          <span class="card-index">{{ index + 1 }}</span>
        </div>
      </div>
    </div>

    <!-- 进度条 -->
    <a-progress
      :percent="progressPercent"
      :status="completed ? 'success' : 'active'"
      :stroke-color="completed ? undefined : { from: '#22C55E', to: '#16A34A' }"
      class="animation-progress"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, reactive } from 'vue'
import { Progress as AProgress } from 'ant-design-vue'
import { CheckCircleOutlined, PictureOutlined } from '@ant-design/icons-vue'

const props = defineProps<{
  total: number
  doneCount: number
  imageUrls: string[]
  phase: 'analyzing' | 'generating' | 'done'
  completed: boolean
}>()

// 记录每张图片是否已加载完成（触发 blur→clear 过渡）
const loadedFlags = reactive<Record<number, boolean>>({})
const onImageLoad = (index: number) => { loadedFlags[index] = true }

// 阶段文案
const headerText = computed(() => {
  if (props.completed) return '全部配图生成完成'
  if (props.phase === 'analyzing') return '正在分析配图需求'
  if (props.phase === 'generating') return '正在生成配图'
  return '正在生成配图'
})

// 进度百分比（0-100）
const progressPercent = computed(() => {
  if (props.total <= 0) return 0
  return Math.round((props.doneCount / props.total) * 100)
})
</script>

<style scoped lang="scss">
.image-gen-animation {
  background: var(--color-background-secondary);
  border: 1px solid var(--border-subtle, var(--color-border-light));
  border-radius: var(--radius-lg);
  padding: 20px 24px;
  margin-top: 24px;

  .animation-header {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    margin-bottom: 16px;
    font-size: 15px;
    font-weight: 600;
    color: var(--color-text);

    .header-icon {
      color: var(--color-primary);
    }

    .header-count {
      color: var(--color-text-muted);
      font-variant-numeric: tabular-nums;
      font-weight: 500;
    }

    .header-check {
      color: var(--color-success);
      font-size: 16px;
    }
  }

  .card-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(72px, 1fr));
    gap: 12px;
    margin-bottom: 16px;
  }

  .image-card {
    aspect-ratio: 1;
    border-radius: var(--radius-md);
    overflow: hidden;
    display: flex;
    align-items: center;
    justify-content: center;

    &.card-pending {
      background: var(--surface-muted, var(--color-background-tertiary));
      animation: pulse-skeleton 1.6s ease-in-out infinite;
    }

    &.card-done {
      background: rgba(34, 197, 94, 0.1);
      animation: card-appear 0.4s ease-out;
    }

    &.card-generating {
      // 彩色渐变占位，模拟 BlurHash 效果
      background: linear-gradient(135deg, #dbeafe 0%, #bfdbfe 25%, #a5b4fc 50%, #c7d2fe 75%, #ddd6fe 100%);
      background-size: 200% 200%;
      animation: gradient-shift 3s ease infinite;
      // shimmer 光泽扫光伪元素
      position: relative;
      &::after {
        content: '';
        position: absolute;
        inset: 0;
        background: linear-gradient(
          105deg,
          transparent 30%,
          rgba(255, 255, 255, 0.3) 45%,
          rgba(255, 255, 255, 0.5) 50%,
          rgba(255, 255, 255, 0.3) 55%,
          transparent 70%
        );
        background-size: 200% 100%;
        animation: shimmer 2.5s ease-in-out infinite;
        pointer-events: none;
      }
    }
  }

  .card-image {
    width: 100%;
    height: 100%;
    object-fit: cover;
    position: relative;
    z-index: 1;
    // 多层过渡：blur + brightness + contrast + scale
    transition: filter 1.8s cubic-bezier(0.4, 0, 0.2, 1), opacity 1.8s cubic-bezier(0.4, 0, 0.2, 1), transform 1.8s cubic-bezier(0.4, 0, 0.2, 1);

    // 未加载时：强模糊 + 高亮度 + 低对比度
    filter: blur(40px) brightness(1.3) contrast(0.85);
    opacity: 0.6;
    transform: scale(1.15);

    &.loaded {
      filter: blur(0) brightness(1) contrast(1);
      opacity: 1;
      transform: scale(1);
    }
  }

  @keyframes shimmer {
    0% { background-position: 200% 0; }
    100% { background-position: -200% 0; }
  }

  @keyframes gradient-shift {
    0% { background-position: 0% 50%; }
    50% { background-position: 100% 50%; }
    100% { background-position: 0% 50%; }
  }

  @keyframes pulse-skeleton {
    0%, 100% { opacity: 1; }
    50% { opacity: 0.5; }
  }

  @keyframes card-appear {
    from { opacity: 0; transform: translateY(12px) scale(0.95); }
    to { opacity: 1; transform: translateY(0) scale(1); }
  }

  .card-pending-content {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 100%;
    position: relative;
  }

  .card-pending-icon {
    font-size: 22px;
    color: var(--color-text-muted);
  }

  .card-index {
    position: absolute;
    bottom: 6px;
    right: 8px;
    font-size: 11px;
    color: var(--color-text-muted);
    font-variant-numeric: tabular-nums;
  }

  .animation-progress {
    :deep(.ant-progress-inner) {
      background: var(--color-background-tertiary);
    }
  }
}

/* 移动端：卡片栅格更紧凑 */
@media (max-width: 768px) {
  .image-gen-animation {
    padding: 16px;
  }

  .card-grid {
    grid-template-columns: repeat(4, 1fr);
    gap: 8px;
  }
}
</style>
