import { expect, test } from '@playwright/test'

const adminAccount = process.env.UI_TEST_ADMIN_ACCOUNT ?? 'admin_test'
const adminPassword = process.env.UI_TEST_ADMIN_PASSWORD ?? 'AdminTest@2026'

// 回归：顶部导航"数据分析"应跳转到 /analytics（原 bug：菜单 key 指向旧页 /admin/statistics）
test('admin 点击顶部导航数据分析跳转到 /analytics', async ({ page }) => {
  await page.goto('/user/login')
  await expect(page.getByRole('heading', { level: 1, name: '欢迎回来' })).toBeVisible()
  await page.getByLabel('账号', { exact: false }).fill(adminAccount)
  await page.getByLabel('密码', { exact: false }).fill(adminPassword)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL('/')

  // 顶部导航点击"数据分析"（admin 可见，nav 内非下拉菜单）
  await page.locator('.nav-center').getByText('数据分析').click()
  await expect(page).toHaveURL(/analytics/)
})
