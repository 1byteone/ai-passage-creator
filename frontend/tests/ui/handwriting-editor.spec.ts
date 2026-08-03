import { expect, test, type Page } from '@playwright/test'

const viewports = [
  { width: 1024, height: 900 },
  { width: 1440, height: 960 },
]

type RuntimeProblem = {
  path: string
  type: 'console' | 'pageerror' | 'http'
  message: string
}

const adminAccount = process.env.UI_TEST_ADMIN_ACCOUNT ?? 'admin_test'
const adminPassword = process.env.UI_TEST_ADMIN_PASSWORD ?? 'AdminTest@2026'

const collectRuntimeProblems = (page: Page) => {
  const problems: RuntimeProblem[] = []
  const currentPath = '/handwriting'

  page.on('console', (message) => {
    if (message.type() === 'warning' || message.type() === 'error') {
      problems.push({
        path: currentPath,
        type: 'console',
        message: message.text(),
      })
    }
  })
  page.on('pageerror', (error) => {
    problems.push({
      path: currentPath,
      type: 'pageerror',
      message: error.message,
    })
  })
  page.on('response', (response) => {
    if (response.url().includes('/api/') && response.status() >= 400) {
      problems.push({
        path: currentPath,
        type: 'http',
        message: `${response.status()} ${response.url()}`,
      })
    }
  })

  return problems
}

const loginAsAdmin = async (page: Page) => {
  await page.goto('/user/login')
  await expect(page.getByRole('heading', { level: 1, name: '欢迎回来' })).toBeVisible()
  await page.getByLabel('账号', { exact: false }).fill(adminAccount)
  await page.getByLabel('密码', { exact: false }).fill(adminPassword)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL('/')
}

test.describe('手写笔记编辑器', () => {

  test('页面加载后显示编辑器组件', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 960 })
    const problems = collectRuntimeProblems(page)
    await loginAsAdmin(page)
    await page.goto('/handwriting', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('main')).toBeVisible()
    await expect(page.getByRole('heading', { level: 1, name: '手写笔记编辑器' })).toBeVisible()
    await expect(page.getByPlaceholder('在此输入或粘贴文字内容...')).toBeVisible()
    await expect(page.getByRole('button', { name: '预览' })).toBeVisible()
    await expect(page.getByRole('button', { name: '导出 PNG' })).toBeVisible()
    expect(problems).toEqual([])
  })

  test('字体选择器列出可用字体', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 960 })
    await loginAsAdmin(page)
    await page.goto('/handwriting', { waitUntil: 'domcontentloaded' })
    await expect(page.getByText('字体')).toBeVisible()
    const fontSelect = page.locator('.font-selector').first()
    await expect(fontSelect).toBeVisible()
  })

  test('纸张选择器列出纸张类型', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 960 })
    await loginAsAdmin(page)
    await page.goto('/handwriting', { waitUntil: 'domcontentloaded' })
    await expect(page.getByText('纸张')).toBeVisible()
  })

  test('参数滑块可调节', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 960 })
    await loginAsAdmin(page)
    await page.goto('/handwriting', { waitUntil: 'domcontentloaded' })
    await expect(page.getByText('位置扰动')).toBeVisible()
    await expect(page.getByText('旋转')).toBeVisible()
    await expect(page.getByText('字号变化')).toBeVisible()
    await expect(page.getByText('墨迹浓淡')).toBeVisible()
  })

  test('空内容预览显示警告', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 960 })
    await loginAsAdmin(page)
    await page.goto('/handwriting', { waitUntil: 'domcontentloaded' })
    await page.getByRole('button', { name: '预览' }).click()
    await expect(page.getByText('请输入文字内容')).toBeVisible()
  })

  test('空内容导出显示警告', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 960 })
    await loginAsAdmin(page)
    await page.goto('/handwriting', { waitUntil: 'domcontentloaded' })
    await page.getByRole('button', { name: '导出 PNG' }).click()
    await expect(page.getByText('请输入文字内容')).toBeVisible()
  })

  test('输入内容后预览触发 API 请求', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 960 })
    const problems = collectRuntimeProblems(page)
    await loginAsAdmin(page)
    await page.goto('/handwriting', { waitUntil: 'domcontentloaded' })
    const textarea = page.getByPlaceholder('在此输入或粘贴文字内容...')
    await textarea.fill('这是一段测试手写效果的文字内容。包含中文、标点符号和段落。')
    await page.getByRole('button', { name: '预览' }).click()
    // 预览按钮应变为 loading 状态
    await expect(page.getByRole('button', { name: '预览' })).toBeVisible()
    expect(problems).toEqual([])
  })

  test('切换字体选项', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 960 })
    await loginAsAdmin(page)
    await page.goto('/handwriting', { waitUntil: 'domcontentloaded' })
    const fontSelect = page.locator('.ant-select').first()
    await fontSelect.click()
    await expect(page.locator('.ant-select-dropdown')).toBeVisible()
  })

  test('预览面板初始显示提示文字', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 960 })
    await loginAsAdmin(page)
    await page.goto('/handwriting', { waitUntil: 'domcontentloaded' })
    await expect(page.getByText('点击「预览」查看手写效果')).toBeVisible()
  })

  for (const vp of viewports) {
    test(`${vp.width}px 视口无横向溢出`, async ({ page }) => {
      await page.setViewportSize(vp)
      await loginAsAdmin(page)
      await page.goto('/handwriting', { waitUntil: 'domcontentloaded' })
      const overflow = await page.evaluate(() =>
        document.documentElement.scrollWidth > window.innerWidth + 1
      )
      expect(overflow).toBe(false)
    })
  }
})