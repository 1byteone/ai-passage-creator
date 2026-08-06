# 插画风格全链路交付 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 打通插画风格全链路，使用户可见可用：恢复配图生成逻辑、子风格联动配图提示词、CardPage 增加插画风格选择。

**Architecture:** 后端取消注释恢复配图生成 → ArticleState 透传 characterStyle → IllustrationPromptBuilder 联动 → 前端 CardPage 增加选择器。

**Tech Stack:** Java 21 / Spring Boot 3.5 / Vue 3 / TypeScript strict

## Global Constraints

- 后端：分层架构，MyBatis-Flex
- 前端：Vue 3 Composition API `<script setup lang="ts">`，TypeScript strict
- 提交格式：Conventional Commits + `Co-Authored-By: Claude <noreply@anthropic.com>`

---

### Task 1: 恢复配图生成逻辑

**Files:**
- Modify: `src/main/java/.../service/ArticleAgentService.java`

**Interfaces:**
- Consumes: `ArticleState`（已有配图需求）
- Produces: `IMAGE_COMPLETE` SSE 事件

- [ ] **Step 1: 取消 agent5GenerateImages 中的注释**

找到 `agent5GenerateImages` 中被 `/* */` 注释的代码块（约第 320-360 行），取消注释。

**注意重复推送问题**：注释块前后都有 `imageCompleteMessage` 推送。取消注释后，保留注释块内的推送，删除注释块外重复的推送，确保每个配图只推送一次 `IMAGE_COMPLETE`。

当前结构（被注释时，外部有一个冗余推送）：
```java
// 外部冗余推送（注释状态下）
String imageCompleteMessage = SseMessageTypeEnum.IMAGE_COMPLETE.getStreamingPrefix()+GsonUtils.toJson(imageResult);
streamHandler.accept(imageCompleteMessage);

/* // 被注释的真正检索逻辑
String imageUrl = imageSearchService.searchImage(...);
...
// 内部也推送 IMAGE_COMPLETE
streamHandler.accept(imageCompleteMessage);
*/
```

恢复后：
```java
// 调用图片检索服务
String imageUrl = imageSearchService.searchImage(requirement.getKeywords());
...
// 推送单张配图成功
String imageCompleteMessage = ...;
streamHandler.accept(imageCompleteMessage);
```

- [ ] **Step 2: 验证**

Run: `mvn test -Dspring.profiles.active=test`
Expected: 全绿

- [ ] **Step 3: Commit**

```bash
git add src/main/java/.../service/ArticleAgentService.java
git commit -m "fix(agent): 恢复 agent5GenerateImages 配图生成逻辑 — 取消被注释的检索代码

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: 子风格联动配图提示词

**Files:**
- Modify: `src/main/java/.../service/ArticleAgentService.java`
- Modify: `src/main/java/.../model/po/Article.java`（如需要）
- Modify: `src/main/java/.../service/impl/ArticleServiceImpl.java`（如需要）

**Interfaces:**
- Consumes: `ArticleState.characterStyle`、`IllustrationPromptBuilder`
- Produces: 风格化配图提示词

- [ ] **Step 1: 确认 ArticleState 是否有 characterStyle**

检查 `ArticleState` 类是否有 `characterStyle` 字段。没有则新增。

- [ ] **Step 2: 在 agent5GenerateImages 中读取 characterStyle**

在恢复的配图检索逻辑前，读取 `characterStyle` 并用 `IllustrationPromptBuilder` 生成风格化提示词：

```java
// 从文章读取插画子风格，用于配图提示词风格化
String characterStyle = state.getCharacterStyle();
if (characterStyle != null && !characterStyle.isBlank()) {
    IllustrationCharacterStyle style = IllustrationCharacterStyle.from(characterStyle);
    String stylePrompt = illustrationPromptBuilder.build(requirement.getKeywords(), style);
    requirement.setKeywords(stylePrompt);
}
```

- [ ] **Step 3: 注入 IllustrationPromptBuilder**

检查 `ArticleAgentService` 是否已有 `IllustrationPromptBuilder` 注入。没有则构造器注入。

- [ ] **Step 4: 验证**

Run: `mvn test -Dspring.profiles.active=test`
Expected: 全绿

- [ ] **Step 5: Commit**

```bash
git add src/main/java/.../service/ArticleAgentService.java
git commit -m "feat(agent): agent 5GenerateImages 读 characterStyle — 配图提示词子风格化

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: CardPage 插画选择 + 卡片风格

**Files:**
- Modify: `frontend/src/pages/article/CardPage.vue`
- Modify: `frontend/src/api/typings.d.ts`（`CardGenerateRequest`）

**Interfaces:**
- Consumes: `CharacterStyleSelector` 组件（已有）
- Produces: `generateCards({ taskId, cardStyle, characterStyle })`

- [ ] **Step 1: 引入组件**

在 `CardPage.vue` 的 `<script setup>` 中：

```typescript
import CharacterStyleSelector from './components/CharacterStyleSelector.vue'
```

- [ ] **Step 2: 新增状态**

```typescript
const cardStyle = ref('')  // 卡片风格：空=默认，warm/minimal/free/illustration
const characterStyle = ref('')  // 插画子风格
```

- [ ] **Step 3: 模板插入**

在「生成卡片」按钮前插入卡片风格选择和子风格选择：

```vue
<!-- 卡片风格选择 -->
<div class="card-syle-section" style="margin-bottom: 16px;">
  <div class="section-header">
    <span class="section-title">卡片风格</span>
    <span class="section-tip">选择卡片的视觉风格<span>
  </div>
  <a-radio-group v-model:value="cardStyle">
    <a-radio value="">默认</a-radio>
    <a-radio value="warm">温暖</a-radio>
    <a-radio value="minimal">极简</a-radio>
    <a-radio value="free">自由</a-radio>
    <a-radio value="illustration">插画</a-radio>
  </a-radio-group>
</div>

<!-- 插画子风格选择 -->
<CharacterStyleSelector
  v-if="cardStyle === 'illustration'"
  v-model:value="characterStyle"
  :disabled="generating"
/>
```

- [ ] **Step 4: API 调用透传**

在 `generateCards` 调用中：

```typescript
const res = await generateCards({
  taskId: taskId.value,
  cardStyle: cardStyle.value || undefined,
  characterStyle: characterStyle.value || undefined,
})
```

- [ ] **Step 5: 前端类型扩展**

在 `frontend/src/api/typings.d.ts` 的 `CardGenerateRequest` 中：

```typescript
type CardGenerateRequest = {
  taskId?: string
  cardStyle?: string
  methodologyName?: string
  characterStyle?: string   // 新增
}
```

- [ ] **Step 6: type-check**

Run: `cd frontend && npm run type-check`
Expected: 零错误

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/article/CardPage.vue frontend/src/api/typings.d.ts
git commit -m "feat(frontend): CardPage 增加插画风格选择器 — cardStyle + characterStyle 透传

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: 全量回归

**Files:**
- 无新增文件，只运行测试

- [ ] **Step 1: 后端全量回归**

Run: `mvn test -Dspring.profiles.active=test`
Expected: 全绿

- [ ] **Step 2: 前端全量验证**

Run: `cd frontend && npm run type-check && npm run build`
Expected: 通过

- [ ] **Step 3: 前端 E2E**

Run: `cd frontend && npx playwright test tests/ui/article-create-flow.spec.ts`
Expected: 全通过

---

## Self-Review

1. Task 1 取消注释+修复重复推送 ✅
2. Task 2 ArticleState 加 characterStyle + PromptBuilder 注入 ✅
3. Task 3 CardPage 选择器 + 类型扩展 + 透传 ✅
4. Task 4 全量回归 ✅