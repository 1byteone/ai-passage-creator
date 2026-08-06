# 创作页一键生成插画卡片 — 设计文档

> **日期**: 2026-08-06
> **分支**: dev
> **状态**: 待审批
> **前置依赖**: 插画全链路后端已就绪（CardService/CardController/IllustrationImageService）

---

## 1. 背景

用户已在创作页选择了插画子风格，但创作完成后只能看到文章正文，需要手动去 `/article/:taskId/cards` 页面选择插画风格并生成卡片——流程长、用户感知不到插画效果。

目标：在创作页完成态（`COMPLETED` 阶段）一键生成插画卡片，用户无需跳转。

---

## 2. 需求对齐

| 维度 | 决策 |
|---|---|
| 入口 | 创作页完成态（CompletedState 组件）增加「生成插画卡片」按钮 |
| 卡片风格 | 自动选择 `illustration` |
| 子风格 | 复用创作时选择的 `characterStyle`（若未选则默认 HEALING） |
| 触发方式 | 点击按钮 → 调用 `generateCards` API → 轮询完成 → 弹窗展示卡片预览 |
| 预览 | 在弹窗/模态框中展示生成的卡片 PNG，支持下载 |
| 正常卡片生成 | 原有的「查看详情」按钮行为不变，用户仍可去 CardPage 做精细调整 |

---

## 3. 技术方案

### 3.1 文件修改

| 文件 | 操作 |
|------|------|
| `frontend/src/pages/article/ArticleCreatePage.vue` | 完成态增加「生成插画卡片」按钮 |
| `frontend/src/pages/article/components/CompletedState.vue` | 增加插画卡片生成逻辑 + 预览弹窗 |
| `frontend/src/api/cardController.ts` | 确认 `generateCards` 和 `getCards` API 可用 |

### 3.2 数据流

```
用户选择子风格 → 创作完成（COMPLETED）
  → 用户点击「生成插画卡片」
  → 调用 generateCards({
       taskId,
       cardStyle: 'illustration',
       characterStyle: selectedCharacterStyle || undefined
     })
  → 后端 CardAsyncService 异步生成
  → 前端轮询 getCards() 直到完成
  → 弹出模态框展示卡片 PNG
  → 用户可下载 / 关闭弹窗
```

### 3.3 CompletedState 组件扩展

```vue
<!-- 插画卡片区域 -->
<div v-if="hasCharacterStyle" class="illustration-card-section">
  <div class="illustration-preview">
    <div class="preview-header">
      <PictureOutlined />
      <span>插画人物卡片</span>
    </div>
    <div v-if="illustrationCards.length > 0" class="card-gallery">
      <div v-for="card in illustrationCards" :key="card.pageNo" class="card-item">
        <img :src="card.imageUrl" :alt="`卡片第 ${card.pageNo} 页`" />
        <a-button size="small" @click="downloadImage(card.imageUrl, `card-${card.pageNo}`)">
          下载
        </a-button>
      </div>
    </div>
    <div v-else class="card-empty">
      <a-button type="primary" :loading="generatingIllustration" @click="generateIllustrationCard">
        <PictureOutlined />
        生成插画卡片
      </a-button>
    </div>
  </div>
</div>
```

### 3.4 边界处理

| 边界 | 处理 |
|---|---|
| 用户未选子风格 | `hasCharacterStyle` 为 false，不显示插画卡片区域 |
| 插画卡片生成中 | 按钮 loading 态，防止重复点击 |
| 生成完成 | 轮询到卡片后展示预览 |
| 生成失败 | 显示错误提示，不阻塞其他操作 |
| 重新创作 | 重置插画卡片状态 |

---

## 4. 测试策略

| 测试 | 方式 |
|---|---|
| 创作完成态显示插画卡片按钮 | 前端 E2E（Mock SSE 驱动到 COMPLETED） |
| 点击按钮调用 generateCards 带正确参数 | 前端 E2E |
| 轮询完成后展示卡片预览 | 前端 E2E |

---

## 5. 风险

| 风险 | 缓解 |
|---|---|
| CompletedState 是独立组件，需传更多 props | 加 `characterStyle` 和 `taskId` 两个 prop |
| 卡片生成是异步的，需要轮询 | 复用现有 pollCards 机制 |
| 用户可能快速点击「重新创作」 | 生成中的按钮 disabled，重新创作时重置 |