# 文章创作主题样式系统 — 设计文档

> **日期**: 2026-08-06
> **分支**: dev
> **状态**: 已批准（头脑风暴对齐 6 项决策 + 3 轮审计修复）
> **前置依赖**: 现有绿色设计 token 系统（variables.css）+ 全局组件

---

## 1. 背景与目标

用户希望为一套可切换的**主题样式系统**，在文章创作、阅读、卡片渲染中提供不同视觉风格。用户可选择「瑞士甲板」「点阵终端」「简约点阵」等设计主义风格，适配不同创作场景。

### 1.1 设计风格研究结论（anysearch 调研）

| 风格 | 核心特征 |
|------|---------|
| **瑞士甲板**（International Typographic） | 左对齐右侧不齐、非对称、无衬线、模块化网格、8px 基线、黑/白/红高对比、超大排版、拒绝装饰 |
| **点阵终端**（Dot Matrix / Terminal） | 深色底、青霓虹光、等宽字体、点阵网格背景、赛博朋克 |
| **简约点阵**（Clean Minimal + Dot） | 暖白底、蓝强调、衬线标题、淡点阵背景纹理、长文阅读 |

---

## 2. 需求对齐（头脑风暴沉淀）

| 维度 | 决策 |
|---|---|
| 阶段划分 | 阶段 1：前端主题系统（CSS 变量 + 选择器 + localStorage） |
| 主题风格 | 默认治愈系 + 瑞士甲板 + 点阵终端 + 简约点阵 |
| 应用范围 | 全站主题（首页/文章/卡片/全局组件） |
| 切换机制 | `data-theme` 属性 + CSS 变量覆盖 |
| 持久化 | localStorage 保存用户选择 |
| 点阵特效 | 点阵/网格背景用 CSS gradient 实现 |

---

## 3. 3 轮审计结果

### 第 1 轮：主题机制
| 发现 | 严重度 | 处理 |
|------|--------|------|
| main.ts 需在 variables 后 common 前引入主题 | ✅ | themes/index.css 加在 common 前 |
| body 背景用 var(--surface-canvas) | ✅ | 主题覆盖变量即可 |
| GlobalHeader 有 .header-right 空间 | ✅ | ThemeSwitcher 放这里 |
| data-theme 未被占用 | ✅ | 安全使用 |

### 第 2 轮：点阵背景与硬编码
| 发现 | 严重度 | 处理 |
|------|--------|------|
| ArticleReadingView 无硬编码颜色 | ✅ | 变量可覆盖 |
| 5 处硬编码品牌色 | 🟡 Minor | 主题覆盖大部分，渐进优化 |
| 无 background-attachment 滥用 | ✅ | 点阵背景安全 |
| localStorage 无 SSR | ✅ | SPA 时序安全 |

### 第 3 轮：交互与组件
| 发现 | 严重度 | 处理 |
|------|--------|------|
| GlobalHeader 已有 a-dropdown 模式 | ✅ | ThemeSwitcher 复用 |
| antd portal 渲染到 body 下 | ✅ | CSS 变量在 :root 全局生效 |

---

## 4. 技术方案（方案 A：CSS 变量主题系统）

**决策**: 基于 `data-theme` 属性 + CSS 变量覆盖实现全站主题切换。

**理由**:
- 零新依赖，基于现有 variables.css 扩展
- 切换毫秒级生效，无后端交互
- 全局 :root 变量覆盖所有组件（含 antd portal）
- 后续扩展只需新增一个 CSS 文件

---

## 5. 文件结构

```
frontend/src/styles/
├── variables.css          # 基础 token（现有）
├── themes/
│   ├── index.css          # 主题入口：点阵背景 + 组件适配
│   ├── swiss.css          # 瑞士甲板变量
│   ├── dotmatrix.css      # 点阵终端变量
│   └── clean.css          # 简约点阵变量
└── common.css             # 全局组件样式（现有）

frontend/src/components/
└── ThemeSwitcher.vue      # 主题选择器

frontend/src/composables/
└── useTheme.ts            # 主题持久化 + 应用

修改:
  frontend/src/main.ts                          # 引入 themes/index.css
  frontend/src/components/GlobalHeader.vue      # 顶部加入 ThemeSwitcher
  frontend/tests/ui/theme-switcher.spec.ts      # E2E
```

---

## 6. 4 个主题变量规范

### 6.1 瑞士甲板（swiss.css）

| Token | 值 | 说明 |
|-------|-----|------|
| --surface-canvas | #F5F5F3 | 暖白纸 |
| --text-strong | #111111 | 近黑 |
| --color-primary | #E63946 | 瑞士红强调 |
| --font-heading | Archivo/Inter | 无衬线几何 |
| --font-body | Inter | 无衬线 |
| --radius-* | 0 | 直角 |
| --shadow-* | none | 无阴影 |
| --gradient-* | none | 无渐变 |
| --measure | 62ch | 严谨行长 |
| --letter-spacing-heading | -0.02em | 紧排 |

### 6.2 点阵终端（dotmatrix.css）

| Token | 值 | 说明 |
|-------|-----|------|
| --surface-canvas | #0A0E14 | 深炭黑 |
| --color-primary | #00F0FF | 青霓虹 |
| --font-heading | JetBrains Mono | 等宽 |
| --font-body | JetBrains Mono | 等宽终端 |
| --shadow-md | 0 0 12px rgba(0,240,255,0.15) | 霓虹阴影 |
| --dot-bg-color | rgba(0,240,255,0.05) | 点阵色 |
| --grid-line-color | rgba(0,240,255,0.08) | 网格线 |

### 6.3 简约点阵（clean.css）

| Token | 值 | 说明 |
|-------|-----|------|
| --surface-canvas | #F9F9F6 | 暖白纸 |
| --color-primary | #2563EB | Medium 蓝 |
| --font-heading | Source Serif 4 | 衬线标题 |
| --font-body | Inter | 无衬线正文 |
| --font-size-body | 17px | 阅读字号 |
| --measure | 68ch | 宽行长 |
| --dot-bg-color | rgba(37,99,235,0.04) | 淡点阵 |

---

## 7. 点阵网格背景实现

```css
/* 点阵终端：明显点阵 + 网格线 */
[data-theme="dotmatrix"] body {
  background-image:
    radial-gradient(circle, var(--dot-bg-color) 1px, transparent 1px),
    linear-gradient(var(--grid-line-color) 1px, transparent 1px),
    linear-gradient(90deg, var(--grid-line-color) 1px, transparent 1px);
  background-size: 24px 24px, 24px 24px, 24px 24px;
  background-attachment: fixed;
}

/* 简约点阵：极淡点阵 */
[data-theme="clean"] body {
  background-image:
    radial-gradient(circle, var(--dot-bg-color) 1px, transparent 1px);
  background-size: 32px 32px;
  background-attachment: fixed;
}
```

---

## 8. ThemeSwitcher 组件

```vue
<a-dropdown :trigger="['click']">
  <a-button class="theme-toggle" :aria-label="`当前主题：${currentLabel}`">
    <BgColorsOutlined />
    <span class="theme-toggle-label">{{ currentLabel }}</span>
  </a-button>
  <template #overlay>
    <a-menu @click="handleSelect">
      <a-menu-item v-for="t in themes" :key="t.id">
        <span class="theme-swatch" :style="{ background: t.swatch }"></span>
        <span>{{ t.label }}</span>
        <CheckOutlined v-if="t.id === currentTheme" />
      </a-menu-item>
    </a-menu>
  </template>
</a-dropdown>
```

---

## 9. useTheme composable

```typescript
const THEMES = [
  { id: 'default', label: '治愈系', swatch: '#22C55E' },
  { id: 'swiss', label: '瑞士甲板', swatch: '#E63946' },
  { id: 'dotmatrix', label: '点阵终端', swatch: '#00F0FF' },
  { id: 'clean', label: '简约点阵', swatch: '#2563EB' },
]

const applyTheme = (themeId: string) => {
  document.documentElement.dataset.theme = themeId
  localStorage.setItem('app-theme', themeId)
}

const getInitialTheme = (): string => {
  return localStorage.getItem('app-theme') || 'default'
}
```

---

## 10. 组件级适配

```css
/* 瑞士甲板：按钮直角+粗边+大写 */
[data-theme="swiss"] .ant-btn-primary {
  border-radius: 0 !important;
  font-weight: 700 !important;
  text-transform: uppercase !important;
  letter-spacing: 0.05em !important;
  border: 2px solid var(--swiss-accent) !important;
  box-shadow: none !important;
}

/* 点阵终端：卡片霓虹边框发光 */
[data-theme="dotmatrix"] .ant-card,
[data-theme="dotmatrix"] .ant-modal-content {
  box-shadow: 0 0 16px rgba(0, 240, 255, 0.1) !important;
  border-color: var(--border-strong) !important;
}
```

---

## 11. 测试策略

| 测试 | 方式 |
|---|---|
| 主题切换应用 data-theme | E2E |
| localStorage 持久化 | E2E |
| 4 主题全部可选 | E2E |
| 文章阅读层按主题渲染 | E2E |
| type-check + build | 零错误 |

---

## 12. 风险与缓解

| 风险 | 缓解 |
|---|---|
| !important 覆盖失效 | 主题只覆盖变量，组件适配必要时 !important |
| 硬编码颜色漏网 | 关键页面覆盖，渐进优化 |
| 点阵背景性能 | 纯 CSS gradient，低透明度 |
| antd portal 主题 | 变量在 :root 全局生效 |