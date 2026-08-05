# 插画角色子风格选择器 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创作页配图方式区域下方新增插画子风格选择器（4 张卡片缩略图：healing/cute/doodle/watercolor），用户选择后通过 API 透传至后端，持久化到 article 表，使色板切换和 AI 提示词差异化生效。

**Architecture:** 前端独立组件 `CharacterStyleSelector.vue` → ArticleCreatePage 集成 → API 扩展 → 后端 ArticleCreateRequest 加字段 → ArticleService 持久化 → Article 表加列（Flyway 迁移 + H2 同步）。

**Tech Stack:** Vue 3 / TypeScript strict / Ant Design Vue 4 / Java 21 / Spring Boot 3.5 / MyBatis-Flex / Flyway

## Global Constraints

- 组件：Vue 3 `<script setup lang="ts">`，TypeScript strict，零容忍 vue-tsc 错误
- 样式：SCSS scoped，Ant Design Vue 组件优先
- 后端：分层架构（Controller → Service → Mapper）
- 数据库：Flyway 迁移 + 同步 h2-schema.sql 测试
- 提交格式：Conventional Commits + `Co-Authored-By: Claude <noreply@anthropic.com>`

---

### Task 1: 后端 Article 加 characterStyle 字段 + 迁移

**Files:**
- Modify: `src/main/java/com/example/aipassagecreator/model/entity/Article.java`
- Create: `src/main/resources/db/migration/V{n}__add_article_character_style.sql`
- Modify: `src/main/resources/sql/h2-schema.sql`
- Modify: `src/main/java/com/example/aipassagecreator/model/dto/article/ArticleCreateRequest.java`
- Modify: `src/main/java/com/example/aipassagecreator/service/impl/ArticleServiceImpl.java`
- Modify: `src/main/java/com/example/aipassagecreator/service/ArticleService.java`（接口）
- Create: `src/main/resources/db/vendor/mysql/V{n}__add_article_character_style.sql`（如有 vendor 目录，或直接放在 db/migration 用可移植 SQL）

**Interfaces:**
- Consumes: `ArticleCreateRequest`（新字段）、`Article`（新字段）
- Produces: `ArticleService.createArticleTask(..., String characterStyle)` 重载

- [ ] **Step 1: Article 实体加字段**

在 `src/main/java/com/example/aipassagecreator/model/entity/Article.java` 新增：

```java
/** 插画子风格（healing/cute/doodle/watercolor），用于卡片渲染 */
private String characterStyle;
```

- [ ] **Step 2: Flyway 迁移**

创建 `src/main/resources/db/migration/V4__add_article_character_style.sql`：

```sql
ALTER TABLE article ADD COLUMN character_style VARCHAR(64) NULL COMMENT '插画子风格（healing/cute/doodle/watercolor）';
```

> 注：确认 V4 编号——检查 `src/main/resources/db/migration/` 目录已有文件编号，取下一个。

- [ ] **Step 3: 同步测试 schema**

在 `src/main/resources/sql/h2-schema.sql` 中 article 表定义加列：

```sql
`character_style` varchar(64) DEFAULT NULL COMMENT '插画子风格（healing/cute/doodle/watercolor）',
```

- [ ] **Step 4: ArticleCreateRequest 加字段**

```java
/** 插画子风格（healing/cute/doodle/watercolor），后端回退 HEALING */
private String characterStyle;
```

- [ ] **Step 5: ArticleService 接口 + 实现**

接口 `ArticleService.java`：
```java
String createArticleTask(String topic, String style, String methodology,
                         List<String> enabledImageMethods, String characterStyle, User loginUser);
```

> 保留无 `characterStyle` 的旧重载（或更新调用方）。建议统一使用带 characterStyle 的新方法。

实现 `ArticleServiceImpl.java`：
```java
article.setCharacterStyle(characterStyle);
```

- [ ] **Step 6: ArticleController 透传**

```java
String taskId = articleService.createArticleTaskWithQuotaCheck(
    request.getTopic(),
    request.getStyle(),
    methodology,
    request.getEnabledImageMethods(),
    request.getCharacterStyle(),  // 新增
    loginUser
);
```

- [ ] **Step 7: 测试确认**

Run: `mvn test -Dspring.profiles.active=test`
Expected: 全绿（含新字段兼容性）

- [ ] **Step 8: Commit**

```bash
git add src/main/java/.../Article.java src/main/resources/db/migration/V4__add_article_character_style.sql src/main/resources/sql/h2-schema.sql src/main/java/.../ArticleCreateRequest.java src/main/java/.../ArticleService.java src/main/java/.../ArticleServiceImpl.java src/main/java/.../ArticleController.java
git commit -m "feat(backend): article 加 character_style 字段 — 插画子风格持久化与透传

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: 创建 CharacterStyleSelector 组件

**Files:**
- Create: `frontend/src/pages/article/components/CharacterStyleSelector.vue`

**Interfaces:**
- Consumes: 无（纯展示组件）
- Produces: `{ value: string; disabled: boolean }` → `update:value`

- [ ] **Step 1: 创建组件文件**

创建 `frontend/src/pages/article/components/CharacterStyleSelector.vue`：

```vue
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
```

- [ ] **Step 2: type-check**

Run: `cd frontend && npm run type-check`
Expected: 零错误

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/article/components/CharacterStyleSelector.vue
git commit -m "feat(frontend): 插画子风格选择器组件 — 4 色板卡片缩略图 + 选中高亮

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: 集成到 ArticleCreatePage + 前端 API 扩展

**Files:**
- Modify: `frontend/src/pages/article/ArticleCreatePage.vue`
- Modify: `frontend/src/api/typings.d.ts`

**Interfaces:**
- Consumes: `CharacterStyleSelector` 组件（Task 2）
- Produces: `selectedCharacterStyle` ref → API 调用

- [ ] **Step 1: 引入组件**

在 `<script setup>` 顶部：

```typescript
import CharacterStyleSelector from './components/CharacterStyleSelector.vue'
```

- [ ] **Step 2: 新增状态**

```typescript
const selectedCharacterStyle = ref('')  // 选中的插画子风格（空字符串 = 未选）
```

- [ ] **Step 3: 模板插入**

在 `image-methods-section` 之后、`create-actions` 之前：

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

- [ ] **Step 4: API 调用传参**

`startCreate` 中 `createArticle` 调用增加 `characterStyle`：

```typescript
const res = await createArticle({
  topic: topic.value,
  style: selectedStyle.value || undefined,
  enabledImageMethods: selectedImageMethods.value.length > 0 ? selectedImageMethods.value : undefined,
  characterStyle: selectedCharacterStyle.value || undefined,
})
```

- [ ] **Step 5: resetCreate 重置**

```typescript
selectedCharacterStyle.value = ''
```

- [ ] **Step 6: 前端 API 类型扩展**

在 `frontend/src/api/typings.d.ts` 的 `ArticleCreateRequest` 中加：

```typescript
type ArticleCreateRequest = {
  topic?: string
  style?: string
  enabledImageMethods?: string[]
  characterStyle?: string   // 插画子风格（healing/cute/doodle/watercolor），后端回退 HEALING
}
```

- [ ] **Step 7: type-check**

Run: `cd frontend && npm run type-check`
Expected: 零错误

- [ ] **Step 8: Commit**

```bash
git add frontend/src/pages/article/ArticleCreatePage.vue frontend/src/api/typings.d.ts
git commit -m "feat(frontend): 创作页集成插画子风格选择器 — API 透传 + 重置

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: E2E 测试 + 全量验证

**Files:**
- Modify: `frontend/tests/ui/article-create-flow.spec.ts`

**Interfaces:**
- Consumes: 组件渲染结果（Task 2/3）

- [ ] **Step 1: 扩展现有完整流程测试**

在完整流程测试中，`startCreate` 前添加子风格选择断言：

```typescript
// 确认4张卡片渲染
await expect(page.locator('.style-card')).toHaveCount(4)

// 选中治愈风格 → 高亮
await page.locator('.style-card').first().click()
await expect(page.locator('.style-card').first()).toHaveClass(/style-selected/)

// 取消选中（再次点击）
await page.locator('.style-card').first().click()
await expect(page.locator('.style-card').first()).not.toHaveClass(/style-selected/)

// 选中后创作 → 参数应包含 characterStyle
await page.locator(':nth-match(.style-card, 2)').click()  // cute
// 后续断言 createArticle 请求包含 characterStyle='cute'
```

- [ ] **Step 2: 运行 E2E**

Run: `cd frontend && npx playwright test tests/ui/article-create-flow.spec.ts`
Expected: 全部通过

- [ ] **Step 3: 后端全量回归**

Run: `mvn test -Dspring.profiles.active=test`
Expected: 全绿

- [ ] **Step 4: 前端全量验证**

Run: `cd frontend && npm run type-check && npm run build`
Expected: 通过

- [ ] **Step 5: Commit**

```bash
git add frontend/tests/ui/article-create-flow.spec.ts
git commit -m "test(frontend): 插画子风格选择器 E2E — 4 卡片渲染 + 选中高亮 + 取消

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Self-Review

**1. Spec coverage:**
- 后端字段 + 迁移 + 透传 → Task 1 ✅
- 组件 CharacterStyleSelector.vue → Task 2 ✅
- 集成 ArticleCreatePage + API 类型 → Task 3 ✅
- E2E 断言 4 卡片 + 选中高亮 + 取消 → Task 4 ✅
- 验收标准（type-check/build/E2E/mvn test）→ Task 4 Steps 2-4 ✅

**2. Placeholder scan:** 无 TBD/TODO。Flyway V4 编号需确认已有迁移编号。

**3. Type consistency:** `selectedCharacterStyle`（string）与 `ArticleCreateRequest.characterStyle`（string?）一致。组件 `value` 与 `update:value` 类型匹配。