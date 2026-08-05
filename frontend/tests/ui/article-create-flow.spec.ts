import { expect, test, type Page } from '@playwright/test'
import http from 'node:http'
import type { AddressInfo } from 'node:net'

/**
 * 创作模块全流程 E2E 测试 — 使用 Mock SSE 服务端驱动阶段流转。
 *
 * <p>前端通过 EventSource 连接 /api/article/progress/{taskId}，Playwright 用
 * {@code route.continue({ url })} 把请求转发到本地 Mock SSE 服务端（跨域被拦截层屏蔽，
 * 页面视角始终是同一 origin）。Mock 服务端支持 {@code push()} 按测试节奏推送事件，
 * 从而精确驱动 TITLE_SELECTING → OUTLINE_EDITING → CONTENT_GENERATING → COMPLETED。</p>
 */

interface MockSseServer {
  port: number
  push: (event: Record<string, unknown>) => void
  waitForConnection: () => Promise<void>
  close: () => Promise<void>
}

/** 启动一个可 push 事件的 Mock SSE 服务端 */
function startMockSseServer(): Promise<MockSseServer> {
  return new Promise((resolve) => {
    const clients = new Set<http.ServerResponse>()
    const buffer: Array<Record<string, unknown>> = []
    const connectionWaiters: Array<() => void> = []

    const writeEvent = (res: http.ServerResponse, event: Record<string, unknown>) => {
      res.write(`data: ${JSON.stringify(event)}\n\n`)
    }

    const server = http.createServer((req, res) => {
      res.writeHead(200, {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        Connection: 'keep-alive',
      })
      res.write(': connected\n\n')
      clients.add(res)
      // 先 flush 缓冲，再通知等待者，避免事件送达与等待解析间的竞态
      while (buffer.length) {
        writeEvent(res, buffer.shift()!)
      }
      connectionWaiters.splice(0).forEach((w) => w())
      req.on('close', () => {
        clients.delete(res)
      })
    })

    server.listen(0, '127.0.0.1', () => {
      const { port } = server.address() as AddressInfo
      resolve({
        port,
        push: (event) => {
          if (clients.size === 0) {
            buffer.push(event)
            return
          }
          for (const client of clients) {
            writeEvent(client, event)
          }
        },
        waitForConnection: () =>
          new Promise<void>((resolveWait) => {
            if (clients.size > 0) {
              resolveWait()
              return
            }
            connectionWaiters.push(resolveWait)
          }),
        close: async () => {
          // 先强制断开所有 SSE 连接，否则 server.close() 会因连接未关闭而阻塞
          for (const client of clients) {
            client.end()
            client.destroy()
          }
          clients.clear()
          await new Promise<void>((res) => server.close(() => res()))
        },
      })
    })
  })
}

/** Mock 登录态（原生 fetch 走 page.route） */
async function mockAuth(
  page: Page,
  overrides?: Partial<{ id: number; userName: string; userRole: string; quota: number }>,
) {
  const user = {
    id: 1,
    userName: '测试用户',
    userRole: 'admin',
    quota: 5,
    ...overrides,
  }
  await page.route('**/api/user/get/login', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: user }),
    }),
  )
  // 挂载时自动加载推荐选题，必须 mock，否则真实请求落到后端返回 40100 触发 request.ts 跳登录页
  await page.route('**/api/topic/recommend*', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: { items: [], hasAi: false } }),
    }),
  )
  // 选题输入触发 RAG 历史参考检索，同样需要 mock，否则后端未登录返回 40100 触发跳转
  await page.route('**/api/rag/search*', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: [] }),
    }),
  )
}

/** Mock 创作 API（创建任务/确认标题/确认大纲） */
async function mockArticleApis(page: Page, taskId = 'task-e2e-001') {
  await page.route('**/api/article/create', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: taskId }),
    }),
  )
  await page.route('**/api/article/confirm-title', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 0 }),
    }),
  )
  await page.route('**/api/article/confirm-outline', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 0 }),
    }),
  )
}

/** 把 SSE 请求转发到本地 Mock 服务端 */
async function routeSseToMock(page: Page, sse: MockSseServer, taskId = 'task-e2e-001') {
  await page.route('**/api/article/progress/**', (route) =>
    route.continue({
      url: `http://127.0.0.1:${sse.port}/api/article/progress/${taskId}`,
    }),
  )
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

const openCreatePage = async (page: Page) => {
  await page.goto('/create', { waitUntil: 'domcontentloaded' })
  await expect(page.getByRole('heading', { level: 1, name: '创作新文章' })).toBeVisible()
}

test.describe('创作模块全流程', () => {
  test('完整流程 — 选题输入到创作完成', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 960 })
    const problems = collectRuntimeProblems(page)
    await mockAuth(page)
    await mockArticleApis(page)
    const sse = await startMockSseServer()
    await routeSseToMock(page, sse)

    try {
      await openCreatePage(page)

      // 1. 输入选题 → 开始创作按钮启用
      const topicInput = page.locator('#article-topic-input')
      const createBtn = page.getByRole('button', { name: /开始创作/ })
      await expect(createBtn).toBeDisabled()
      await topicInput.fill('2026年AI如何改变职场')
      await expect(createBtn).toBeEnabled()

      // 2. 点击开始创作，等待 SSE 连接建立
      await createBtn.click()
      await sse.waitForConnection()

      // 3. 推送标题方案 → 标题选择阶段
      sse.push({ type: 'AGENT1_COMPLETE' })
      sse.push({
        type: 'TITLES_GENERATED',
        titleOptions: [
          { mainTitle: 'AI 重塑职场', subTitle: '2026 年全景展望' },
          { mainTitle: '程序员新战场', subTitle: '大模型时代生存指南' },
        ],
      })
      await expect(page.getByRole('heading', { name: '选择标题方案' })).toBeVisible()
      await expect(page.getByText('AI 重塑职场')).toBeVisible()

      // 4. 确认标题 → 推送大纲 → 大纲编辑阶段
      await page.getByRole('button', { name: /确认并生成大纲/ }).click()
      sse.push({ type: 'AGENT2_STREAMING', content: '{"sections":[{"section":1,"title":"AI冲击就业"}' })
      sse.push({
        type: 'OUTLINE_GENERATED',
        outline: [
          { section: 1, title: 'AI 冲击就业', points: ['岗位结构变化', '新技能需求'] },
          { section: 2, title: '如何应对', points: ['终身学习', '跨界融合'] },
        ],
      })
      await expect(page.getByRole('button', { name: /确认并生成正文/ })).toBeVisible()
      // 大纲章节标题渲染为输入框值
      await expect(page.getByRole('textbox', { name: '章节标题' }).first()).toHaveValue('AI 冲击就业')

      // 5. 确认大纲 → 推送正文流式 → 完成
      await page.getByRole('button', { name: /确认并生成正文/ }).click()
      sse.push({ type: 'AGENT3_STREAMING', content: '## 第一章\nAI 正在改变职场的每个角落。' })
      sse.push({ type: 'AGENT3_COMPLETE' })

      // 5.1 正文完成 → 配图动画组件出现（分析态）
      await expect(page.getByText('正在分析配图需求')).toBeVisible()

      sse.push({ type: 'AGENT4_COMPLETE', imageRequirements: [{ position: 1 }, { position: 2 }, { position: 3 }] })
      // 5.2 配图分析完成 → 生成态文案 + 3 张卡片
      await expect(page.getByText('正在生成配图')).toBeVisible()
      await expect(page.locator('.image-card')).toHaveCount(3)

      sse.push({ type: 'IMAGE_COMPLETE', image: { position: 1, url: 'https://img.example.com/1.png' } })
      // 5.3 首张完成 → 1 张已完成卡（勾选角标出现）
      await expect(page.locator('.image-card.card-done')).toHaveCount(1)
      await expect(page.locator('.image-card.card-pending')).toHaveCount(2)

      sse.push({ type: 'IMAGE_COMPLETE', image: { position: 2, url: 'https://img.example.com/2.png' } })
      sse.push({ type: 'IMAGE_COMPLETE', image: { position: 3, url: 'https://img.example.com/3.png' } })
      sse.push({ type: 'AGENT5_COMPLETE', images: [
        { position: 1, url: 'https://img.example.com/1.png' },
        { position: 2, url: 'https://img.example.com/2.png' },
        { position: 3, url: 'https://img.example.com/3.png' },
      ] })

      // 5.4 全部完成 → 成功态文案
      await expect(page.getByText('全部配图生成完成')).toBeVisible()

      sse.push({ type: 'MERGE_COMPLETE', fullContent: '## 第一章\nAI 正在改变职场的每个角落。' })
      sse.push({ type: 'QUALITY_CHECKED', score: 92, passed: true, detoxed: false, violations: [] })
      sse.push({ type: 'ALL_COMPLETE' })

      // 6. 断言完成状态 + 质量报告
      await expect(page.getByText('文章创作完成！')).toBeVisible()
      await expect(page.getByText('创作质量检测')).toBeVisible()
      await expect(page.getByText('92 分', { exact: true })).toBeVisible()

      expect(problems, '完整创作流程不应产生页面或 HTTP 错误').toEqual([])
    } finally {
      await sse.close()
    }
  })

  test('空标题方案 — 显示空状态而非死端', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 960 })
    await mockAuth(page)
    await mockArticleApis(page)
    const sse = await startMockSseServer()
    await routeSseToMock(page, sse)

    try {
      await openCreatePage(page)
      await page.locator('#article-topic-input').fill('测试选题')
      await page.getByRole('button', { name: /开始创作/ }).click()
      await sse.waitForConnection()

      // 推送空标题列表 → 应显示空状态而非卡死
      sse.push({ type: 'AGENT1_COMPLETE' })
      sse.push({ type: 'TITLES_GENERATED', titleOptions: [] })

      await expect(page.getByText('暂无标题方案')).toBeVisible()
      await expect(page.getByRole('button', { name: /确认并生成大纲/ })).toBeDisabled({
        timeout: 8_000,
      })
    } finally {
      await sse.close()
    }
  })

  test('配额为 0 — 按钮禁用并显示配额警告', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 960 })
    await mockAuth(page, { userRole: 'user', quota: 0 })
    await openCreatePage(page)

    await page.locator('#article-topic-input').fill('测试选题')
    const createBtn = page.getByRole('button', { name: /开始创作/ })
    await expect(createBtn).toBeDisabled()
    await expect(page.getByText('配额已用完，无法创建文章')).toBeVisible()
  })

  test('SSE 连接失败 — 显示错误提示并可重试', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 960 })
    await mockAuth(page)
    await mockArticleApis(page)
    // SSE 请求直接返回 500，触发一次重连后进入 onError
    await page.route('**/api/article/progress/**', (route) =>
      route.fulfill({
        status: 500,
        contentType: 'text/event-stream',
        body: 'mock server error',
      }),
    )

    await openCreatePage(page)
    await page.locator('#article-topic-input').fill('测试选题')
    await page.getByRole('button', { name: /开始创作/ }).click()

    // 连接失败后应弹出全局错误提示
    await expect(page.getByText('连接失败,请重试')).toBeVisible()
  })
})
