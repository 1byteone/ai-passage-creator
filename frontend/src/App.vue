<script setup lang="ts">
import { computed } from 'vue'
import BasicLayout from '@/layouts/BasicLayout.vue'
import { ANTD_THEME_TOKENS, themeId } from '@/composables/useTheme'

// antd ConfigProvider 主题跟随当前主题（让按钮/菜单/卡片等 antd 组件同步变色）
const antdTheme = computed(() => ({
  token: ANTD_THEME_TOKENS[themeId.value] ?? ANTD_THEME_TOKENS.default,
}))
</script>

<template>
  <a-config-provider :theme="antdTheme">
    <BasicLayout />
  </a-config-provider>
</template>
