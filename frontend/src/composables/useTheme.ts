import { ref } from 'vue'

/**
 * 主题系统 composable — 管理 data-theme 应用 + localStorage 持久化。
 * SPA 纯客户端，无 SSR，localStorage 时序安全。
 */

export interface ThemeOption {
  id: string
  label: string
  swatch: string
}

const STORAGE_KEY = 'app-theme'

export const THEME_OPTIONS: ThemeOption[] = [
  { id: 'default', label: '治愈系', swatch: '#22C55E' },
  { id: 'swiss', label: '瑞士甲板', swatch: '#E63946' },
  { id: 'dotmatrix', label: '点阵终端', swatch: '#00F0FF' },
  { id: 'clean', label: '简约点阵', swatch: '#2563EB' },
]

const currentTheme = ref<string>(getInitialTheme())

/** 读取 localStorage 初始主题，无则 default */
function getInitialTheme(): string {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved && THEME_OPTIONS.some((t) => t.id === saved)) {
    return saved
  }
  return 'default'
}

/** 应用主题到 html 根节点 + 持久化 */
export function applyTheme(themeId: string): void {
  if (!THEME_OPTIONS.some((t) => t.id === themeId)) {
    themeId = 'default'
  }
  document.documentElement.dataset.theme = themeId
  localStorage.setItem(STORAGE_KEY, themeId)
  currentTheme.value = themeId
}

/** 初始化主题（应用模块加载时调用一次） */
export function initTheme(): void {
  applyTheme(currentTheme.value)
}

/** 获取当前主题 id */
export function getCurrentTheme(): string {
  return currentTheme.value
}

/** 获取当前主题 label */
export function getCurrentThemeLabel(): string {
  return THEME_OPTIONS.find((t) => t.id === currentTheme.value)?.label ?? '治愈系'
}

/** 获取所有主题选项 */
export function getThemeOptions(): ThemeOption[] {
  return THEME_OPTIONS
}
