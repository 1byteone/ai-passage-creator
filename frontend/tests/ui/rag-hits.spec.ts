import { expect, test, type Page } from '@playwright/test'

/** Mock 登录态 */
async function mockAuth(page: Page) {
  await page.route('**/api/user/get/login', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 0,
        data: { id: 1, userName: '测试用户', userRole: 'admin', quota: 5 },
      }),
    }),
  )
}

/**
 * Mock 推荐选题：创作页挂载即请求 /api/topic/recommend。本地若正运行后端，
 * 无 session 时返回 code=40100，会触发 request.ts 响应拦截器整页跳登录 ——
 * 必须 mock 才能让创作页测试与后端状态解耦（参考 article-create-flow 全流程同款问题）。
 */
async function mockRecommendedTopics(page: Page) {
  await page.route('**/api/topic/recommend*', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: { items: [], hasAi: false } }),
    }),
  )
}

/** Mock RAG 检索：返回自身 + 2 篇相关文章（验证详情页排除自身） */
async function mockRagSearch(page: Page, selfRefId: string) {
  await page.route('**/api/rag/search', (route) => {
    const body: API.RagHit[] = [
      { refId: selfRefId, title: '这是自身文章', content: '自身摘要', score: 0.95, type: 'article' },
      { refId: 'ref-1', title: 'AI 重塑职场', content: '历史文章摘要', score: 0.9, type: 'article' },
      { refId: 'ref-2', title: '远程办公指南', content: '另一篇摘要', score: 0.7, type: 'article' },
    ]
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: body }),
    })
  })
}

const collectRuntimeProblems = (page: Page) => {
  const problems: string[] = []
  page.on('pageerror', (error) => problems.push(`pageerror: ${error.message}`))
  page.on('response', (response) => {
    if (response.url().includes('/api/') && response.status() >= 400) {
      problems.push(`http ${response.status()} ${response.url()}`)
    }
  })
  return problems
}

test.describe('RAG 相关文章 UI', () => {
  test('创作页 — 输入选题显示历史参考，点击套用选题', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 960 })
    const problems = collectRuntimeProblems(page)
    await mockAuth(page)
    await mockRecommendedTopics(page)
    await mockRagSearch(page, '')

    await page.goto('/create', { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('heading', { level: 1, name: '创作新文章' })).toBeVisible()

    // 未输入时显示提示
    await expect(page.getByText('输入选题后，将展示相关历史文章')).toBeVisible()

    // 输入选题 → 防抖(500ms)后出现参考卡片
    await page.locator('#article-topic-input').fill('AI 如何改变职场')
    await expect(page.getByRole('button', { name: /AI 重塑职场/ })).toBeVisible({ timeout: 3_000 })

    // 点击套用选题 → 选题框填入
    await page.getByRole('button', { name: /AI 重塑职场/ }).click()
    await expect(page.locator('#article-topic-input')).toHaveValue('AI 重塑职场')

    expect(problems, '创作页 RAG 不应产生页面或 HTTP 错误').toEqual([])
  })

  test('详情页 — 加载显示相关文章（排除自身），点击跳转', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 960 })
    const problems = collectRuntimeProblems(page)
    await mockAuth(page)
    await mockRagSearch(page, 'task-detail-1')

    // mock 文章详情
    await page.route('**/api/article/task-detail-1', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          data: {
            taskId: 'task-detail-1',
            mainTitle: 'AI 重塑职场',
            subTitle: '2026 年全景展望',
            fullContent: '## 第一章\n正文内容',
            status: 'COMPLETED',
            userId: 1,
          },
        }),
      }),
    )
    // mock 执行日志（空）
    await page.route('**/api/article/execution-logs/**', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 0, data: null }),
      }),
    )

    await page.goto('/article/task-detail-1', { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('heading', { level: 1, name: 'AI 重塑职场' })).toBeVisible()

    // 相关文章区块出现，且排除了自身（'这是自身文章' 不显示）
    await expect(page.getByRole('heading', { level: 2, name: '相关文章' })).toBeVisible()
    await expect(page.getByRole('button', { name: /远程办公指南/ })).toBeVisible({ timeout: 3_000 })
    await expect(page.getByText('这是自身文章')).not.toBeVisible()

    // 点击卡片 → 跳转到该文章详情
    await page.getByRole('button', { name: /远程办公指南/ }).click()
    await page.waitForURL('**/article/ref-2')

    expect(problems, '详情页 RAG 不应产生页面或 HTTP 错误').toEqual([])
  })
})
