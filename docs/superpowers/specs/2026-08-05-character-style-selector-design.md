# 插画角色子风格选择器 — 设计文档

> **日期**: 2026-08-05
> **分支**: dev
> **状态**: 已批准（头脑风暴对齐 4 项决策）
> **前置依赖**: 插画卡片风格后端全链路已完成（IllustrationCharacterStyle / PromptBuilder / ImageService / CardService 透传 / illustration.html 4 色板）

---

## 1. 背景与目标

插画卡片风格已实现 4 种子风格（healing/cute/doodle/watercolor），后端全链路就绪（CardTemplateEngine 注入 characterStyle，illustration.html 有 4 色板 CSS），但**前端没有子风格选择器**，用户无法选择风格，始终使用默认 HEALING。

目标：在创作页「配图方式」区域下方新增插画子风格选择器，用户通过卡片缩略图直观选择 4 种风格，选中后透传到后端，使色板切换和 AI 提示词差异化生效。

### 1.1 核心痛点

| 问题 | 现状 |
|---|---|
| 用户无法选择风格 | 4 种子风格后端就绪，但前端无入口，始终 HEALING |
| 色板切换死代码 | illustration.html 4 色板 CSS 已实现，但 characterStyle 从未变化 |
| AI 提示词未差异化 | PromptBuilder 的 STYLE_VISUAL 已按风格区分，但未被触发 |

---

## 2. 需求对齐（头脑风暴沉淀）

| 维度 | 决策 |
|---|---|
| 选择时机 | 创作前选择，与文章风格/配图方式并列 |
| 可见条件 | 始终显示（即使不选插画卡片风格也可选，后端回退 HEALING） |
| UI 形态 | 卡片缩略图（4 张，色板预览 + 图标 + 风格名） |
| 默认值 | 无默认值，不选则后端回退 HEALING |

---

## 3. 技术方案（方案 A：独立组件）

**决策**: 新建 `CharacterStyleSelector.vue` 独立组件，嵌入创作页配图方式下方。

**理由**:
- 卡片缩略图需要 CSS 色板 + 图标 + 选中态，封装成组件职责清晰
- ArticleCreatePage 已超载（2800+ 行），避免进一步膨胀
- 未来卡片渲染页可复用

**Trust Spectrum**: 🟡 业务逻辑（选择器状态）+ 🟢 样板代码（CSS 卡片），测试覆盖。

---

## 4. 组件设计（CharacterStyleSelector.vue）

### 4.1 Props & Emits

| Prop | 类型 | 默认 | 说明 |
|---|---|---|---|
| `value` | string | '' | 当前选中的子风格名，空字符串表示未选 |
| `disabled` | boolean | false | 创作中禁用 |

| Emit | 参数 | 说明 |
|---|---|---|
| `update:value` | `(style: string)` | 选中/反选风格 |

### 4.2 模板结构

```
CharacterStyleSelector
├── 标题区
│   ├── PictureOutlined 图标
│   ├── "插画角色风格" 标题
│   └── "选择文章的插画视觉风格（治愈/可爱/涂鸦/水彩）" 提示
└── 卡片区（v-for 遍历 4 种风格）
    ├── 色板预览块（3 色渐变条，从左到右：背景色→强调色→文字色）
    ├── 风格图标（HeartOutlined / SmileOutlined / EditOutlined / BgColorsOutlined）
    ├── 风格名称
    │   ├── 中文名（治愈 / 可爱 / 涂鸦 / 水彩）
    │   └── 英文名（Healing / Cute / Doodle / Watercolor）
    └── 选中高亮
        ├── 绿色边框 2px（border-color: var(--color-primary)）
        ├── 绿色背景淡色（rgba(34, 197, 94, 0.06)）
        └── 右上角 CheckCircleOutlined 角标（淡入动画）
```

### 4.3 4 子风格配置

| 枚举值 | 中文名 | 英文名 | 色板渐变（从左到右） | 图标 |
|---|---|---|---|---|
| `healing` | 治愈 | Healing | #F5E6D3 → #D4956A → #2D1810 | HeartOutlined |
| `cute` | 可爱 | Cute | #FFF8F0 → #E07B6B → #2D2D2D | SmileOutlined |
| `doodle` | 涂鸦 | Doodle | #FFF8F0 → #7BB89A → #2D1F14 | EditOutlined |
| `watercolor` | 水彩 | Watercolor | #F5F1E8 → #D14545 → #1A1A1A | BgColorsOutlined |

### 4.4 选中态行为

- 点击卡片 → `emit('update:value', style.value)`（选中该风格）
- 再次点击已选中的卡片 → `emit('update:value', '')`（取消选择）
- 选中时高亮：绿色边框 + 淡绿背景 + 勾选角标
- 禁用时：`pointer-events: none` + 半透明 0.6

---

## 5. ArticleCreatePage 集成

### 5.1 模板位置

在配图方式区域（`image-methods-section`）之后、`create-actions` 之前：

```vue
<!-- 插画角色风格选择 -->
<section class="character-style-section setting-panel">
  <div class="section-header">
    <div>
      <span class="section-title">插画角色风格</span>
      <span class="section-tip">选择文章的插画视觉风格，仅卡片风格为插画时生效</span>
    </div>
  </div>
  <CharacterStyleSelector
    v-model:value="selectedCharacterStyle"
    :disabled="isCreating"
  />
</section>
```

### 5.2 新增状态

```typescript
const selectedCharacterStyle = ref('')  // 选中的插画子风格（空字符串 = 未选）
```

### 5.3 API 调用

```typescript
const res = await createArticle({
  topic: topic.value,
  style: selectedStyle.value || undefined,
  enabledImageMethods: selectedImageMethods.value.length > 0 ? selectedImageMethods.value : undefined,
  characterStyle: selectedCharacterStyle.value || undefined,  // 新增
})
```

### 5.4 重置

```typescript
const resetCreate = () => {
  // ... 现有重置
  selectedCharacterStyle.value = ''
}
```

### 5.5 边界处理

| 边界 | 处理 |
|---|---|
| 未选子风格 | `characterStyle: undefined` 后端回退 HEALING |
| 创作中禁用 | `:disabled="isCreating"` 防止选中途切换 |
| 重置创作 | `resetCreate` 重置 `selectedCharacterStyle = ''` |
| 不合法值 | 后端 `IllustrationCharacterStyle.from()` 自动回退 HEALING（白名单） |

---

## 6. 后端扩展

### 6.1 ArticleCreateRequest 加字段

```java
/** 插画子风格（healing/cute/doodle/watercolor），后端回退 HEALING */
private String characterStyle;
```

### 6.2 透传链

`ArticleController.createTask` → `ArticleAgentService.createTask` → 后续卡片生成时传至 `CardService`。

> **注**：`CardService` 的全链路透传已在插画任务中完成（`CardController`、`CardAsyncService`、`CardService` 均已支持 `characterStyle` 参数），本任务只需在创作阶段加接收字段并传递。

---

## 7. 测试策略

### 7.1 测试方式

| 层级 | 测试内容 | 方式 |
|---|---|---|
| 组件渲染 | 4 张卡片渲染、色板/图标/名称、选中高亮、取消选中 | 前端 E2E |
| 状态流转 | 选→取消→选、创作中禁用 | E2E |
| API 透传 | createArticle 携带 characterStyle | 后端单元测试 |
| 后端回退 | 不传/非法值 → 默认 HEALING | 已有 `IllustrationCharacterStyleTest` 覆盖 |

### 7.2 验收标准

1. `npm run type-check` 零错误
2. `npm run build` 通过
3. 前端 E2E：创作页显示 4 张子风格卡片，选中高亮，createArticle 参数包含 characterStyle
4. 后端 `mvn test` 无回归
5. 创作页重置时选中状态清空

---

## 8. 目录结构

```
新增:
  frontend/src/pages/article/components/CharacterStyleSelector.vue     # 插画子风格选择组件

修改:
  frontend/src/pages/article/ArticleCreatePage.vue                     # 集成选择器 + selectedCharacterStyle
  frontend/src/api/typings.d.ts                                        # ArticleCreateRequest 加 characterStyle
  frontend/tests/ui/article-create-flow.spec.ts                        # E2E 断言选择与透传
  src/main/java/.../model/dto/ArticleCreateRequest.java                # 后端接收 characterStyle
  src/main/java/.../controller/ArticleController.java                  # 透传至异步服务
  src/main/java/.../service/ArticleAgentService.java                   # 接参并存储
```

---

## 9. 风险与缓解

| 风险 | 缓解 |
|---|---|
| 创作页进一步膨胀 | 独立组件，页面只加 2 行模板 + 1 行状态 + 1 行 API 参数 |
| 后端透传链未完整 | 已确认 `CardService` 全链路就绪，只需加接收字段 |
| 卡片缩略图移动端适配 | 移动端 2 列布局，色板条自适应 |
| 选中态与重置同步 | `resetCreate` 统一重置 `selectedCharacterStyle = ''` |