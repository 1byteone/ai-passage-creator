import { expect, test } from '@playwright/test'

/**
 * 主题切换系统 E2E 测试
 * 验证：data-theme 应用、localStorage 持久化、4 主题切换、全局生效
 */

test.describe('主题切换系统', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/', { waitUntil: 'domcontentloaded' })
    // 首次导航后清空 localStorage 并刷新，确保初始 default
    await page.evaluate(() => localStorage.removeItem('app-theme'))
    await page.reload({ waitUntil: 'domcontentloaded' })
  })

  test('默认主题为 default', async ({ page }) => {
    const theme = await page.evaluate(() => document.documentElement.dataset.theme)
    expect(theme).toBe('default')
  })

  test('切换瑞士甲板主题 → data-theme 变为 swiss', async ({ page }) => {
    // 点击主题切换器
    await page.getByRole('button', { name: /当前主题/ }).click()
    // 选择瑞士甲板
    await page.getByRole('menuitem', { name: '瑞士甲板' }).click()

    const theme = await page.evaluate(() => document.documentElement.dataset.theme)
    expect(theme).toBe('swiss')
  })

  test('切换点阵终端主题 → data-theme 变为 dotmatrix', async ({ page }) => {
    await page.getByRole('button', { name: /当前主题/ }).click()
    await page.getByRole('menuitem', { name: '点阵终端' }).click()

    const theme = await page.evaluate(() => document.documentElement.dataset.theme)
    expect(theme).toBe('dotmatrix')
  })

  test('切换简约点阵主题 → data-theme 变为 clean', async ({ page }) => {
    await page.getByRole('button', { name: /当前主题/ }).click()
    await page.getByRole('menuitem', { name: '简约点阵' }).click()

    const theme = await page.evaluate(() => document.documentElement.dataset.theme)
    expect(theme).toBe('clean')
  })

  test('localStorage 持久化 — 刷新后主题保持', async ({ page }) => {
    // 切到瑞士甲板
    await page.getByRole('button', { name: /当前主题/ }).click()
    await page.getByRole('menuitem', { name: '瑞士甲板' }).click()

    // 刷新页面
    await page.reload({ waitUntil: 'domcontentloaded' })

    const theme = await page.evaluate(() => document.documentElement.dataset.theme)
    expect(theme).toBe('swiss')
  })

  test('4 个主题全部可选项可见', async ({ page }) => {
    await page.getByRole('button', { name: /当前主题/ }).click()
    const menu = page.getByRole('menu')
    await expect(menu.getByText('治愈系')).toBeVisible()
    await expect(menu.getByText('瑞士甲板')).toBeVisible()
    await expect(menu.getByText('点阵终端')).toBeVisible()
    await expect(menu.getByText('简约点阵')).toBeVisible()
  })
})
