# undraw 插画风主题样式 — 设计文档

> **日期**: 2026-08-06
> **分支**: dev
> **状态**: 已批准（头脑风暴对齐 4 项决策 + 3 轮审计修复）
> **前置依赖**: 现有绿色设计 token 系统 + undraw 素材（4 子风格 × 3）

---

## 1. 背景与目标

项目当前是纯绿色系 UI，首页 Hero 纯文字、空状态用 Ant Design 默认 `a-empty`（无图）、登录品牌区无视觉。用户希望引入 **undraw 插画风主题**，用极简扁平插画增强产品视觉，形成"绿色治愈系"品牌调性。

### 1.1 undraw 风格研究结论

| 维度 | undraw 风格特征 |
|------|----------------|
| 视觉 | 极简扁平矢量插画、细微阴影、多层次对象（multi-level objects） |
| 色彩 | 单强调色技巧（single-accent-color）— 品牌色贯穿所有插画 |
| 应用 | 空状态、Hero 区、加载态、背景装饰 |
| 许可 | 免费可商用、免署名（open-source style） |

---

## 2. 需求对齐（头脑风暴沉淀）

| 维度 | 决策 |
|---|---|
| 应用范围 | 核心页优先（首页 Hero + 空状态 + 登录品牌区） |
| 视觉取向 | 绿色治愈系（保持品牌色，插画柔和浅色背景 + 绿强调） |
| 素材来源 | 复用现有 12 张 undraw 素材 |
| 落地方式 | 静态图 + CSS（复制到前端，CSS 匹配绿色系） |

---

## 3. 3 轮审计结果

### 第 1 轮：素材
| 发现 | 严重度 | 处理 |
|------|--------|------|
| PNG 尺寸大（1920px），无放大模糊 | ✅ 无风险 | — |
| `cute-1` 纵向（1176×1760） | 🟡 Minor | 空状态用 `object-fit: contain` |
| 素材是位图非 SVG，CSS tint 效果有限 | 🟡 Minor | 用背景色 + 低透明度，不强依赖 tint |

### 第 2 轮：前端引用
| 发现 | 严重度 | 处理 |
|------|--------|------|
| `@` 别名已配置 | ✅ 无风险 | 直接用 `@/assets/illustration/` |
| assets 目录已存在 | ✅ 无风险 | 新增 illustration 子目录 |
| **CSS 伪元素 `content: url('@/...')` 别名不生效** | 🟠 Important | 改用 `<img>` 标签渲染（Vite 处理 import），禁用伪元素 url() |

### 第 3 轮：空状态与响应式
| 发现 | 严重度 | 处理 |
|------|--------|------|
| `a-empty` 在 10+ 处使用 | 🟡 Minor | 只改核心 3 页（List/Card/Approval） |
| Hero 区已 `overflow: hidden` | ✅ 无风险 | 插画装饰不会溢出 |
| 移动端断点完备 | ✅ 无风险 | 插画在 <768px 缩小 |

**关键修复**：CSS 伪元素 `content: url('@/assets/...')` 中 `@` 别名不被 Vite 处理。改用 `<img>` 标签（Vite 自动处理 import）。

---

## 4. 技术方案（方案 A：静态图 + CSS 主题化）

**决策**: 复制现有 undraw 素材到前端 assets，用 `<img>` 标签 + CSS 实现绿色治愈系主题。

**理由**:
- 复用现有素材，零成本
- 改动集中（首页 + 空状态 + 登录品牌区），视觉冲击大
- 符合 Simplicity First，不做过度组件封装
- undraw 核心是"单强调色贯穿"，CSS + 绿色背景即可实现

---

## 5. 素材复用与文件结构

### 5.1 素材选择

| 用途 | 素材 | 原意 | 理由 |
|------|------|------|------|
| 首页 Hero 主插画 | doodle/doodle-1（Essay Writing） | 执笔写作 | 契合"AI 创作平台" |
| 首页 Hero 副插画 | healing/healing-1（Plants） | 绿植治愈 | 绿色治愈系 |
| 空状态 | cute/cute-1（Mobile Gift） | 礼物 | Q 版友好 |
| 登录品牌区 | healing/healing-2（Sweet Home） | 居家治愈 | 暖色欢迎 |

### 5.2 文件结构

```
frontend/src/assets/illustration/
├── hero-writing.png        # doodle/doodle-1 → Hero 主插画
├── hero-plants.png         # healing/healing-1 → Hero 副插画
├── empty-gift.png          # cute/cute-1 → 空状态
├── auth-home.png           # healing/healing-2 → 登录品牌区
└── LICENSES.md             # 许可记录（引用后端现有）
```

### 5.3 许可记录

前端新增 `LICENSES.md`，记录 4 张素材来源与授权（unDraw 免费可商用、免署名）。

---

## 6. 首页 Hero 插画设计

### 6.1 布局

hero-writing.png 作为右侧流程预览卡片背景装饰（低透明度浮层），hero-plants.png 作为 Hero 左下角装饰。

### 6.2 实现（<img> 标签 + 绝对定位）

```vue
<!-- Hero 区 -->
<section class="hero-section">
  <!-- 主插画：右侧流程预览卡片背景 -->
  <img
    :src="heroWriting"
    alt=""
    aria-hidden="true"
    class="hero-writing-decoration"
  />
  <!-- 副插画：左下角装饰 -->
  <img
    :src="heroPlants"
    alt=""
    aria-hidden="true"
    class="hero-plants-decoration"
  />
  <!-- 原有内容 -->
  ...
</section>
```

```scss
.hero-writing-decoration {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 320px;
  height: 240px;
  object-fit: contain;
  opacity: 0.15;
  pointer-events: none;
  z-index: 0;
}

.hero-plants-decoration {
  position: absolute;
  left: -40px;
  bottom: -60px;
  width: 260px;
  height: 260px;
  object-fit: contain;
  opacity: 0.6;
  pointer-events: none;
  z-index: 0;
}
```

### 6.3 移动端

- `< 768px` 隐藏 hero-writing 装饰
- hero-plants 缩小到 140px

---

## 7. 空状态组件（IllustrationEmpty）

### 7.1 组件设计

```vue
<template>
  <div class="illustration-empty">
    <img :src="image" :alt="description" class="empty-image" />
    <p class="empty-description">{{ description }}</p>
    <slot />
  </div>
</template>

<script setup lang="ts">
import emptyGift from '@/assets/illustration/empty-gift.png'

withDefaults(defineProps<{
  description?: string
  image?: string
}>(), {
  description: '暂无数据',
  image: emptyGift,
})
</script>

<style scoped lang="scss">
.illustration-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 40px 20px;
  text-align: center;

  .empty-image {
    width: 180px;
    height: 180px;
    object-fit: contain;
    opacity: 0.8;
  }

  .empty-description {
    color: var(--color-text-muted);
    font-size: 14px;
  }
}
</style>
```

### 7.2 替换位置（核心 3 页）

- `ArticleListPage.vue` — "还没有生成文章"
- `CardPage.vue` — "还没有生成卡片"
- `ApprovalPage.vue` — "暂无待审批内容"

---

## 8. 登录品牌区插画

### 8.1 实现

在 `AuthBrandSection.vue` 品牌文案下方加插画：

```vue
<img :src="authHome" alt="欢迎回来" class="auth-brand-illustration" />
```

```scss
.auth-brand-illustration {
  width: 100%;
  max-width: 320px;
  margin: 24px auto 0;
  object-fit: contain;
  opacity: 0.9;
}
```

---

## 9. 测试策略

| 测试 | 方式 |
|---|---|
| 首页 Hero 插画装饰出现 | 前端 E2E |
| 空状态插画组件 | 前端 E2E |
| type-check + build | 零错误 |
| 移动端插画适配 | 视觉检查 |

---

## 10. 文件清单

```
新增:
  frontend/src/assets/illustration/           # 4 张复制素材 + LICENSES.md
  frontend/src/components/IllustrationEmpty.vue

修改:
  frontend/src/pages/HomePage.vue             # Hero 插画装饰
  frontend/src/components/AuthBrandSection.vue # 登录品牌区插画
  frontend/src/pages/article/ArticleListPage.vue  # 空状态替换
  frontend/src/pages/article/CardPage.vue     # 空状态替换
  frontend/src/pages/approval/ApprovalPage.vue # 空状态替换
  frontend/tests/ui/core-routes.spec.ts       # E2E 断言
```

---

## 11. 风险与缓解

| 风险 | 缓解 |
|---|---|
| CSS 伪元素 url() 别名不生效 | 改用 `<img>` 标签（Vite 处理 import） |
| PNG 位图放大模糊 | 素材已 1920px 大，无风险 |
| 纵向图（cute-1）显示异常 | `object-fit: contain` |
| 图片加载失败 | `onerror` 隐藏插画 |
| 移动端遮挡内容 | 插画绝对定位 + overflow hidden + 响应式缩小 |
---

## 12. 细节升级（2026-08-06 追加）

头脑风暴对齐 4 项细节决策，基于专业 UI/UX 视角实现：

| 维度 | 决策 | 实现 |
|------|------|------|
| 动效程度 | 轻量动效 | Hero 浮动/摇摆 + 空状态呼吸 + 登录微浮 |
| 品牌色融合 | 容器融色 | 玻璃拟态 + 浅绿渐变托底，不动插画本体 |
| 空状态升级 | 情感化引导 | 插画 200px + 渐变背景 + 呼吸 + hint 引导 |
| 全局插画位 | 登录融合 + 全局加载 | 登录玻璃容器微浮 + 新增 BrandLoader |

### 12.1 新增组件 BrandLoader
- 旋转光圈（品牌绿）+ hero-plants 插画浮动 + 可选文字
- 替换创作页标题生成、首页最近创作的 loading

### 12.2 修复暗坑 #14
HomePage/AuthBrandSection 的 `<style scoped>` 无 `lang="scss"`，用 `//` 注释导致 vite build 失败 → 改用 `/* */` 注释。
