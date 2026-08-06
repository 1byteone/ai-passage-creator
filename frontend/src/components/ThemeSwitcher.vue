<template>
  <a-dropdown :trigger="['click']">
    <a-button class="theme-toggle" :aria-label="`当前主题：${currentLabel}`">
      <BgColorsOutlined />
      <span class="theme-toggle-label">{{ currentLabel }}</span>
    </a-button>
    <template #overlay>
      <a-menu @click="handleSelect">
        <a-menu-item v-for="t in themeOptions" :key="t.id">
          <div class="theme-option">
            <span class="theme-swatch" :style="{ background: t.swatch }"></span>
            <span>{{ t.label }}</span>
            <CheckOutlined v-if="t.id === currentThemeId" class="theme-check" />
          </div>
        </a-menu-item>
      </a-menu>
    </template>
  </a-dropdown>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { BgColorsOutlined, CheckOutlined } from '@ant-design/icons-vue'
import {
  applyTheme,
  getCurrentTheme,
  getCurrentThemeLabel,
  getThemeOptions,
} from '@/composables/useTheme'

const themeOptions = getThemeOptions()

const currentThemeId = computed(() => getCurrentTheme())
const currentLabel = computed(() => getCurrentThemeLabel())

const handleSelect = ({ key }: { key: string }) => {
  applyTheme(key)
}
</script>

<style scoped lang="scss">
.theme-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  border-radius: var(--radius-md);
}

.theme-option {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 140px;
}

.theme-swatch {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  border: 1px solid rgba(0, 0, 0, 0.1);
  flex-shrink: 0;
}

.theme-check {
  color: var(--color-primary);
  margin-left: auto;
}
</style>