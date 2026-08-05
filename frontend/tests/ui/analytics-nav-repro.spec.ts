import { expect, test } from '@playwright/test'

const adminAccount = process.env.UI_TEST_ADMIN_ACCOUNT ?? 'admin_test'
const adminPassword = process.env.UI_TEST_ADMIN_PASSWORD ?? 'AdminTest@2026'

// 回归：用户下拉菜单点击"数据分析"应跳转到 /analytics（原 bug：菜单 key 未作为路由跳转）
test('admin 点击数据分析跳转到 /analytics', async ({ page }) => {
  await page.goto('/user/login')
  await expect(page.getByRole('heading', { level: 1, name: '欢迎回来' })).toBeVisible()
  await page.getByLabel('账号', { exact: false }).fill(adminAccount)
  await page.getByLabel('密码', { exact: false }).fill(adminPassword)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL('/')

  // 打开用户下拉菜单
  await page.locator('.user-info').click()
  await expect(page.locator('.ant-dropdown-menu')).toBeVisible()

  // 点击"数据分析"菜单项
  await page.getByText('数据分析').last().click()
  await expect(page).toHaveURL(/analytics/)
})
