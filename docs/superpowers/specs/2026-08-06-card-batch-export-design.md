# 卡片批量导出 — 一键打包全部卡片 PNG 为 zip

日期: 2026-08-06
状态: 已对齐

## Context

用户已完成文章卡片生成（CardPage），但每张卡片需单独点击下载（`downloadImage`）。竞品"内容资产库"支持一键导出全部素材。本功能：**卡片管理页加"全部导出"按钮，把该文章所有已完成卡片 PNG 打包为 zip 下载**。

现状：卡片 `imageUrl` 是 COS 预签名 URL（`generatePresignedUrl`），现有 `downloadImage` 用 `<a href>` 浏览器导航式下载（不受 CORS 限制）。zip 打包需 `fetch` 读二进制（受 CORS 限制）——COS 未配置 CORS 时 fetch 可能失败，需失败降级。

## 方案

### 1. 安装依赖

```bash
npm install jszip file-saver
```
- `jszip` 3.10.1：内存 zip 打包
- `file-saver` 2.0.5：触发浏览器下载

### 2. CardPage 加"全部导出"按钮

在 `heading-actions` 的"生成全部卡片"后加：
```html
<a-button
  :loading="exporting"
  :disabled="!hasCompletedCards"
  @click="exportAllCards"
>
  <template #icon><DownloadOutlined /></template>
  全部导出
</a-button>
```

### 3. exportAllCards 方法

```typescript
const exporting = ref(false)

const exportAllCards = async () => {
  const completed = cards.value.filter((c) => c.status === 'COMPLETED' && c.imageUrl)
  if (!completed.length) {
    message.info('没有已完成的卡片可导出')
    return
  }
  exporting.value = true
  try {
    const JSZip = (await import('jszip')).default
    const { saveAs } = await import('file-saver')
    const zip = new JSZip()
    const folder = zip.folder('cards')!
    let exported = 0
    for (const card of completed) {
      try {
        const resp = await fetch(card.imageUrl!)
        if (!resp.ok) continue
        const blob = await resp.blob()
        const name = card.pageType === 'COVER' ? 'cover' : `card-${card.pageNo}`
        folder.file(`${name}.png`, blob)
        exported++
      } catch {
        // COS 未开 CORS 时 fetch 失败，跳过该卡
        console.warn(`卡片 ${card.pageNo} 下载失败，跳过`)
      }
    }
    if (exported === 0) {
      message.error('导出失败：无法下载卡片图片（可能需要配置存储 CORS）')
      return
    }
    const blob = await zip.generateAsync({ type: 'blob' })
    saveAs(blob, `${articleTitle.value || 'cards'}-卡片.zip`)
    message.success(`已导出 ${exported}/${completed.length} 张卡片`)
  } finally {
    exporting.value = false
  }
}
```

### 4. 依赖懒加载

`jszip`/`file-saver` 用 `await import()` 动态引入，避免影响首屏 bundle 体积。

## 文件清单

| 文件 | 操作 |
|------|------|
| `frontend/package.json` | 加 jszip + file-saver |
| `frontend/src/pages/article/CardPage.vue` | 加"全部导出"按钮 + exportAllCards 方法 |

## 不做的事

- 不做后端改动（卡片数据已有）
- 不打包配图/正文（用户确认仅卡片 PNG）
- 不做 COS CORS 配置（属于部署配置，代码层降级处理）

## 验证

```bash
cd frontend && npm run type-check && npm run build
```
- 卡片管理页有已完成卡片 → 点"全部导出" → 下载 zip，含所有卡片 PNG
- 无卡片 → 按钮禁用 / 提示无卡片
- fetch 失败（CORS）→ 提示"导出失败"而非崩溃

## 已知限制

- COS 预签名 URL 若未配置 CORS，`fetch` 会失败 → 全部导出不可用（单张 `<a>` 下载仍可用）。这属于部署配置项，代码已降级处理。
