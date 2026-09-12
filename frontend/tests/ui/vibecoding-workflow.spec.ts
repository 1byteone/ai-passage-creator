import { expect, test } from '@playwright/test'

test.describe('Vibecoding 工作台', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/vibecoding')
    await page.evaluate(() => localStorage.clear())
    await page.reload()
  })

  test('先确认流程，再生成可下载的单文件原型', async ({ page }) => {
    await expect(page.getByRole('heading', { level: 1, name: 'Vibecoding 工作台' })).toBeVisible()
    await page.getByPlaceholder('粘贴 PRD、页面清单或流程说明...').fill('登录\n首页\n创建内容\n提交成功\n失败后重试')
    await page.getByRole('button', { name: '分析流程草案' }).click()
    await expect(page.getByText('流程草案已生成，请逐项确认')).toBeVisible()
    const generate = page.getByRole('button', { name: '生成低保真原型' })
    await expect(generate).toBeDisabled()
    const confirmations = page.locator('.flow-checkbox')
    for (let index = 0; index < await confirmations.count(); index += 1) {
      await confirmations.nth(index).click({ force: true })
    }
    await expect(generate).toBeEnabled()
    await generate.click()
    await expect(page.getByTitle('低保真原型预览')).toBeVisible()
    await expect(page.getByRole('button', { name: '下载 HTML' }).first()).toBeVisible()
  })

  test('巡查反馈自动保存，回归轮次保留上一轮原话并支持导出', async ({ page }) => {
    await page.locator('.ant-segmented-item').filter({ hasText: '页面全流程巡查' }).click()
    await page.getByPlaceholder('输入或粘贴流程说明，用于生成走查清单').fill('登录\n首页\n失败后重试')
    await page.getByRole('button', { name: '生成走查清单' }).click()
    const card = page.locator('.audit-card').first()
    await card.getByRole('button', { name: /待\s*改/ }).click()
    await card.getByPlaceholder('记录实际结果、问题证据或验收意见').fill('password=should-not-export')
    await page.getByRole('button', { name: '新建回归轮次' }).click()
    await expect(page.getByText('上一轮反馈：password=should-not-export')).toBeVisible()
    await expect(page.getByText('回归验证 · 已改动').first()).toBeVisible()
    await expect(page.getByRole('button', { name: '导出 Markdown' })).toBeVisible()
    await expect(page.getByRole('button', { name: '导出 JSON' })).toBeVisible()
    await page.waitForTimeout(500)
    await page.reload()
    await expect(page.getByText('上一轮反馈：password=should-not-export')).toBeVisible()
  })

  test('核心宽度无横向溢出', async ({ page }) => {
    for (const width of [375, 768, 1024, 1440]) {
      await page.setViewportSize({ width, height: 900 })
      await page.reload()
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true)
    }
  })
})
