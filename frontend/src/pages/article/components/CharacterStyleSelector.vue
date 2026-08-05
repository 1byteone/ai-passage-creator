<template>
  <div class="character-style-selector" role="radiogroup" aria-label="插画角色风格">
    <div class="style-grid">
      <div
        v-for="style in styles"
        :key="style.value"
        :class="['style-card', { 'style-selected': value === style.value, 'style-disabled': disabled }]"
        role="radio"
        :aria-checked="value === style.value"
        :tabindex="disabled ? -1 : 0"
        @click="handleSelect(style.value)"
        @keydown.enter="handleSelect(style.value)"
        @keydown.space.prevent="handleSelect(style.value)"
      >
        <!-- 色板预览条 -->
        <div class="palette-bar">
          <div
            v-for="(color, i) in style.colors"
            :key="i"
            class="palette-swatch"
            :style="{ background: color }"
          />
        </div>
        <!-- 图标 + 名称 -->
        <div class="style-card-body">
          <component :is="style.icon" class="style-icon" />
          <div class="style-labels">
            <span class="style-label">{{ style.label }}</span>
            <span class="style-en">{{ style.en }}</span>
          </div>
        </div>
        <!-- 选中角标 -->
        <CheckCircleFilled v-if="value === style.value" class="selected-badge" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { CheckCircleFilled } from '@ant-design/icons-vue'
import {
  HeartOutlined,
  SmileOutlined,
  EditOutlined,
  BgColorsOutlined,
} from '@ant-design/icons-vue'
import type { Component } from 'vue'

const props = defineProps<{
  value: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:value', val: string): void
}>()

interface StyleItem {
  value: string
  label: string
  en: string
  icon: Component
  colors: string[]
}

const styles: StyleItem[] = [
  { value: 'healing', label: '治愈', en: 'Healing', icon: HeartOutlined, colors: ['#F5E6D3', '#D4956A', '#2D1810'] },
  { value: 'cute', label: '可爱', en: 'Cute', icon: SmileOutlined, colors: ['#FFF8F0', '#E07B6B', '#2D2D2D'] },
  { value: 'doodle', label: '涂鸦', en: 'Doodle', icon: EditOutlined, colors: ['#FFF8F0', '#7BB89A', '#2D1F14'] },
  { value: 'watercolor', label: '水彩', en: 'Watercolor', icon: BgColorsOutlined, colors: ['#F5F1E8', '#D14545', '#1A1A1A'] },
]

const handleSelect = (val: string) => {
  if (props.disabled) return
  // 点击已选中的 → 取消选择；否则选中
  emit('update:value', props.value === val ? '' : val)
}
</script>

<style scoped lang="scss">
.character-style-selector {
  width: 100%;
}

.style-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.style-card {
  position: relative;
  border: 2px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  cursor: pointer;
  transition: all var(--transition-fast);
  background: white;

  &:hover:not(.style-disabled) {
    border-color: var(--color-primary);
    box-shadow: 0 2px 8px rgba(34, 197, 94, 0.12);
    transform: translateY(-2px);
  }

  &.style-selected {
    border-color: var(--color-primary);
    background: rgba(34, 197, 94, 0.04);
  }

  &.style-disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

.palette-bar {
  display: flex;
  height: 8px;
  overflow: hidden;

  .palette-swatch {
    flex: 1;
  }
}

.style-card-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 12px;
}

.style-icon {
  font-size: 24px;
  color: var(--color-text-secondary);
}

.style-labels {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.style-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  line-height: 1.3;
}

.style-en {
  font-size: 11px;
  color: var(--color-text-muted);
  text-transform: capitalize;
  line-height: 1.2;
}

.selected-badge {
  position: absolute;
  top: 4px;
  right: 4px;
  font-size: 16px;
  color: var(--color-primary);
  animation: badge-appear 0.3s ease-out;
}

@keyframes badge-appear {
  from { opacity: 0; transform: scale(0.5); }
  to { opacity: 1; transform: scale(1); }
}

/* 移动端：2 列 */
@media (max-width: 992px) {
  .style-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>