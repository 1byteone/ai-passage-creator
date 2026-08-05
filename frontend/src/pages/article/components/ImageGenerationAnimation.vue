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
          'card-done': index < doneCount,
          'card-pending': index >= doneCount,
        }]"
      >
        <div v-if="index < doneCount" class="card-done-content">
          <PictureOutlined class="card-done-icon" />
          <CheckCircleOutlined class="card-check-badge" />
        </div>
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
import { computed } from 'vue'
import { Progress as AProgress } from 'ant-design-vue'
import { CheckCircleOutlined, PictureOutlined } from '@ant-design/icons-vue'

const props = defineProps<{
  total: number
  doneCount: number
  phase: 'analyzing' | 'generating' | 'done'
  completed: boolean
}>()

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
  }

  .card-pending-content,
  .card-done-content {
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

  .card-done-icon {
    font-size: 26px;
    color: var(--color-primary);
  }

  .card-check-badge {
    position: absolute;
    top: 4px;
    right: 4px;
    font-size: 14px;
    color: var(--color-success);
  }

  .animation-progress {
    :deep(.ant-progress-inner) {
      background: var(--color-background-tertiary);
    }
  }
}

/* 骨架脉动 */
@keyframes pulse-skeleton {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

/* 卡片浮现 */
@keyframes card-appear {
  from {
    opacity: 0;
    transform: translateY(12px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
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
