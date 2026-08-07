import { createApp, defineAsyncComponent, type Component } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

// 主题字体（fontsource 本地包，离线可用；中文用系统 fallback）
import '@fontsource/inter/400.css'
import '@fontsource/inter/500.css'
import '@fontsource/inter/600.css'
import '@fontsource/inter/700.css'
import '@fontsource/archivo/600.css'
import '@fontsource/archivo/700.css'
import '@fontsource/jetbrains-mono/400.css'
import '@fontsource/jetbrains-mono/700.css'
import '@fontsource/source-serif-4/600.css'
import '@fontsource/source-serif-4/700.css'

import 'ant-design-vue/dist/reset.css'
import '@/styles/variables.css'
import '@/styles/themes/index.css'
import '@/styles/common.css'

import '@/access'
import { initTheme } from '@/composables/useTheme'

const app = createApp(App)
// 应用持久化主题（data-theme 需在组件渲染前设置，避免首帧闪烁）
initTheme()
const asyncAntComponents: Record<string, () => Promise<Component>> = {
  AAvatar: () => import('ant-design-vue/es/avatar').then((module) => module.default),
  AButton: () => import('ant-design-vue/es/button').then((module) => module.default),
  AConfigProvider: () => import('ant-design-vue/es/config-provider').then((module) => module.default),
  ADropdown: () => import('ant-design-vue/es/dropdown').then((module) => module.default),
  AEmpty: () => import('ant-design-vue/es/empty').then((module) => module.default),
  AInput: () => import('ant-design-vue/es/input').then((module) => module.default),
  AInputGroup: () => import('ant-design-vue/es/input').then((module) => module.default.Group),
  AInputPassword: () => import('ant-design-vue/es/input').then((module) => module.default.Password),
  AInputSearch: () => import('ant-design-vue/es/input').then((module) => module.default.Search),
  ATextarea: () => import('ant-design-vue/es/input').then((module) => module.default.TextArea),
  AMenu: () => import('ant-design-vue/es/menu').then((module) => module.default),
  AMenuDivider: () => import('ant-design-vue/es/menu').then((module) => module.default.Divider),
  AMenuItem: () => import('ant-design-vue/es/menu').then((module) => module.default.Item),
  AResult: () => import('ant-design-vue/es/result').then((module) => module.default),
  ASkeleton: () => import('ant-design-vue/es/skeleton').then((module) => module.default),
  ASpace: () => import('ant-design-vue/es/space').then((module) => module.default),
  ASpin: () => import('ant-design-vue/es/spin').then((module) => module.default),
}

app.use(createPinia())
app.use(router)
Object.entries(asyncAntComponents).forEach(([name, loader]) => {
  app.component(name, defineAsyncComponent(loader))
})

app.mount('#app')
