# 插画风格全链路交付 — 设计文档

> **日期**: 2026-08-06
> **分支**: dev
> **状态**: 待审批
> **前置依赖**: 插画卡片风格后端 + 子风格选择器已完成

---

## 1. 背景

当前两个功能虽已开发但用户不可见：
1. **配图生成**：`agent5GenerateImages` 中的配图检索逻辑被注释，创作流程不生成任何配图，`IMAGE_COMPLETE` 不触发，配图动画无图可展示
2. **插画卡片风格**：后端全链路就绪，但 CardPage 入口不传 `cardStyle=illustration` 和 `characterStyle`，用户无法选择插画风格

目标：打通全链路，使用户可见、可用。

---

## 2. 交付范围

| 交付 | 说明 |
|---|---|
| 恢复配图生成 | 取消 `agent5GenerateImages` 注释，恢复图片检索逻辑 |
| 子风格联动配图提示词 | 从 Article 读取 `characterStyle`，传给 `IllustrationPromptBuilder` 生成风格化提示词 |
| CardPage 插画选择 | CardPage 增加卡片风格选择器 + 子风格选择器，透传至后端 |

---

## 3. Task 1：恢复配图生成逻辑

### 3.1 文件
- `src/main/java/.../service/ArticleAgentService.java`

### 3.2 实现

找到 `agent5GenerateImages` 中被注释的代码块（`/* */`），将其取消注释。

当前状态（被注释）：
```java
/* //调用图片检索服务
String imageUrl = imageSearchService.searchImage(requirement.getKeywords());
// 降级策略...
*/
```

恢复后：
```java
// 调用图片检索服务
String imageUrl = imageSearchService.searchImage(requirement.getKeywords());
// 降级策略（同上）
```

注意：恢复后需要确保 `imageSearchService` 的注入和 `IMAGE_COMPLETE` 推送逻辑正确。当前代码在注释块内外都有 `imageCompleteMessage` 推送——取消注释后需确保不重复推送。

### 3.3 验证
- 创作流程应能触发 `IMAGE_COMPLETE` 事件
- 前端配图动画应显示图片生成进度（卡片浮现）
- 文章详情应包含配图

---

## 4. Task 2：子风格联动配图提示词

### 4.1 文件
- `src/main/java/.../service/ArticleAgentService.java`
- `src/main/java/.../service/impl/ArticleServiceImpl.java`

### 4.2 实现

在 `agent5GenerateImages` 中，恢复的配图检索逻辑前，读取 Article 的 `characterStyle`：

```java
// 从 Article 读取插画子风格
String characterStyle = state.getCharacterStyle();
// 如果用户选择了子风格，用风格化提示词生成配图
if (characterStyle != null && !characterStyle.isBlank()) {
    IllustrationCharacterStyle style = IllustrationCharacterStyle.from(characterStyle);
    String stylePrompt = illustrationPromptBuilder.build(requirement.getKeywords(), style);
    requirement.setKeywords(stylePrompt);  // 覆盖原关键词为风格化提示词
}
```

> 注：`ArticleState` 需要透传 `characterStyle`。检查 `ArticleState` 是否已有 `characterStyle` 字段，没有则加。

### 4.3 验证
- 选择「治愈」风格 → 配图提示词含暖色/温馨描述
- 选择「可爱」风格 → 配图提示词含可爱/圆润描述
- 不选子风格 → 默认 HEALING，不影响

---

## 5. Task 3：CardPage 插画选择 + 卡片风格

### 5.1 文件
- `frontend/src/pages/article/CardPage.vue`
- `frontend/src/api/typings.d.ts`（`CardGenerateRequest` 类型）
- `frontend/src/pages/article/components/CharacterStyleSelector.vue`

### 5.2 CardPage 增加卡片风格选择

在 CardPage 的「生成卡片」按钮前增加卡片风格选择器：

```vue
<!-- 卡片风格选择 -->
<div class="card-style-section">
  <a-radio-group v-model:value="cardStyle">
    <a-radio value="">默认</a-radio>
    <a-radio value="warm">温暖</a-radio>
    <a-radio value="minimal">极简</a-radio>
    <a-radio value="free">自由</a-ratio>
    <a-radio value="illustration">插画</a-radio>
  </a-radio-group>
</div>

<!-- 插画子风格选择（仅当 cardStyle=illustration 时显示） -->
<CharacterStyleSelector
  v-if="cardStyle === 'illustration'"
  v-model:value="characterStyle"
  :disabled="generating"
/>
```

### 5.3 调用透传

```typescript
const res = await generateCards({
  taskId: taskId.value,
  cardStyle: cardStyle.value || undefined,
  characterStyle: characterStyle.value || undefined,
})
```

### 5.4 前端类型扩展

```typescript
type CardGenerateRequest = {
  taskId?: string
  cardStyle?: string
  methodologyName?: string
  characterStyle?: string   // 插画子风格
}
```

### 5.5 验证
- CardPage 显示卡片风格选择器
- 选择「插画」→ 显示子风格选择器
- 选择子风格 + 生成卡片 → 后端接收 `cardStyle=illustration` + `characterStyle=xxx`
- 不选 → 默认行为不变

---

## 6. 测试策略

| Task | 测试内容 | 验证方式 |
|---|---|---|
| T1 | 配图生成恢复 → IMAGE_COMPLETE 事件触发 | 创作全流程 E2E（Mock SSE 模拟） |
| T2 | 子风格提示词联动 → 提示词含风格描述 | 后端单元测试 |
| T3 | CardPage 风格选择 + 子风格透传 | E2E 断言 |
| 全量 | 无回归 | `mvn test` + `npm run build` |

---

## 7. 风险

| 风险 | 缓解 |
|---|---|
| 配图生成逻辑注释中有重复推送 | 检查取消注释后不重复推送 IMAGE_COMPLETE |
| ArticleState 无 characterStyle 字段 | 加字段并透传 |
| CardPage 已有复杂状态 | 只加 2 个模板块 + 1 个 API 参数，改动小 |