import { expect, test, type Page, type Request } from '@playwright/test'

const publicRoutes = ['/', '/user/login', '/user/register', '/skill']
const protectedRoutes = [
  '/create',
  '/skill/topic-gen',
  '/skill/not-a-real-skill',
  '/article/list',
  '/vip',
  '/admin/statistics',
  '/admin/userManage',
]

const viewports = [
  { width: 375, height: 812 },
  { width: 768, height: 900 },
  { width: 1024, height: 900 },
  { width: 1440, height: 960 },
]

const adminAccount = process.env.UI_TEST_ADMIN_ACCOUNT ?? 'admin_test'
const adminPassword = process.env.UI_TEST_ADMIN_PASSWORD ?? 'AdminTest@2026'

type RuntimeProblem = {
  path: string
  type: 'console' | 'pageerror' | 'http'
  message: string
}

type RouteContract = {
  heading: string | RegExp
  ready: (page: Page) => Promise<void>
}

const routeContracts: Record<string, RouteContract> = {
  '/': {
    heading: 'AI 爆款文章创作器',
    ready: async (page) => {
      await expect(page.getByRole('button', { name: '开始创作', exact: true })).toBeVisible()
    },
  },
  '/user/login': {
    heading: '欢迎回来',
    ready: async (page) => {
      await expect(page.getByRole('button', { name: '登录', exact: true })).toBeVisible()
    },
  },
  '/user/register': {
    heading: '创建账号',
    ready: async (page) => {
      await expect(page.getByRole('button', { name: '注册', exact: true })).toBeVisible()
    },
  },
  '/skill': {
    heading: 'AI 技能中心',
    ready: async (page) => {
      await expect(page.getByLabel('技能加载中')).toHaveCount(0)
      await expect(page.getByRole('link', { name: /选题生成/ })).toBeVisible()
    },
  },
  '/create': {
    heading: '创作新文章',
    ready: async (page) => {
      await expect(page.locator('#article-topic-input')).toBeVisible()
    },
  },
  '/skill/topic-gen': {
    heading: '选题生成',
    ready: async (page) => {
      await expect(page.getByRole('button', { name: '生成选题', exact: true })).toBeVisible()
    },
  },
  '/skill/not-a-real-skill': {
    heading: '这个技能暂不可用',
    ready: async (page) => {
      await expect(page.getByRole('button', { name: '重新加载', exact: true })).toBeVisible()
    },
  },
  '/article/list': {
    heading: '历史文章',
    ready: async (page) => {
      await expect(page.getByLabel('文章加载中')).toHaveCount(0)
      await expect(page.locator('.history-page .page-state[role="alert"]')).toHaveCount(0)
      await expect(page.getByRole('heading', { level: 2, name: '筛选文章' })).toBeVisible()
    },
  },
  '/vip': {
    heading: /^(管理员权限|永久会员权益|永久会员)$/,
    ready: async (page) => {
      await expect(page.getByRole('heading', { level: 2, name: /高级能力|会员状态|升级后/ })).toBeVisible()
    },
  },
  '/admin/statistics': {
    heading: '数据分析',
    ready: async (page) => {
      await expect(page.locator('.statistics-page .page-state[role="alert"]')).toHaveCount(0)
      await expect(page.locator('.metric-grid')).toHaveAttribute('aria-busy', 'false')
      await expect(page.getByRole('heading', { level: 2, name: '创作运行概览' })).toBeVisible()
    },
  },
  '/admin/userManage': {
    heading: '用户管理',
    ready: async (page) => {
      await expect(page.locator('.user-section .loading-state')).toHaveCount(0)
      await expect(page.locator('.user-section .result-state')).toHaveCount(0)
      await expect(page.getByRole('heading', { level: 2, name: /^(全部用户|筛选结果)$/ })).toBeVisible()
    },
  },
}

const collectRuntimeProblems = (page: Page) => {
  const problems: RuntimeProblem[] = []
  const pendingApiRequests = new Map<Request, string>()
  const lastApiActivityByPath = new Map<string, number>()
  let currentPath = '/'
  lastApiActivityByPath.set(currentPath, Date.now())

  const isApiRequest = (request: Request) => request.url().includes('/api/')
  const finishApiRequest = (request: Request) => {
    if (!isApiRequest(request)) return
    const originPath = pendingApiRequests.get(request) ?? currentPath
    pendingApiRequests.delete(request)
    lastApiActivityByPath.set(originPath, Date.now())
  }

  page.on('request', (request) => {
    if (!isApiRequest(request)) return
    pendingApiRequests.set(request, currentPath)
    lastApiActivityByPath.set(currentPath, Date.now())
  })
  page.on('requestfinished', finishApiRequest)
  page.on('requestfailed', finishApiRequest)

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
      const originPath = pendingApiRequests.get(response.request()) ?? currentPath
      problems.push({
        path: originPath,
        type: 'http',
        message: `${response.status()} ${response.url()}`,
      })
    }
  })

  return {
    problems,
    setCurrentPath: (path: string) => {
      currentPath = path
      if (!lastApiActivityByPath.has(path)) {
        lastApiActivityByPath.set(path, Date.now())
      }
    },
    waitForApiSettled: async () => {
      await expect
        .poll(
          () => ({
            pending: [...pendingApiRequests.values()].filter((path) => path === currentPath).length,
            quiet: Date.now() - (lastApiActivityByPath.get(currentPath) ?? 0) >= 150,
          }),
          {
            message: `${currentPath} 的 API 请求应完成且不再产生新请求`,
            timeout: 15_000,
          },
        )
        .toEqual({ pending: 0, quiet: true })
    },
  }
}

const loginAsAdmin = async (page: Page) => {
  await page.goto('/user/login')
  await expect(page.getByRole('heading', { level: 1, name: '欢迎回来' })).toBeVisible()
  await page.getByLabel('账号', { exact: false }).fill(adminAccount)
  await page.getByLabel('密码', { exact: false }).fill(adminPassword)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL('/')
}

const assertPageContract = async (
  page: Page,
  path: string,
  waitForApiSettled: () => Promise<void>,
) => {
  const contract = routeContracts[path]
  expect(contract, `${path} 必须声明页面就绪契约`).toBeDefined()

  await page.goto(path, { waitUntil: 'domcontentloaded' })
  await expect(page.locator('main')).toBeVisible()
  await expect(page.getByRole('heading', { level: 1, name: contract.heading })).toBeVisible()
  await contract.ready(page)
  await waitForApiSettled()

  expect(new URL(page.url()).pathname).toBe(path)
  await expect(page.locator('main')).toHaveCount(1)
  await expect(page.locator('main main')).toHaveCount(0)

  const audit = await page.evaluate(() => {
    const renderedControls = [
      ...document.querySelectorAll<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>(
        'input:not([type="hidden"]):not([aria-hidden="true"]), textarea:not([aria-hidden="true"]), select:not([aria-hidden="true"])',
      ),
    ].filter((element) => {
      const rect = element.getBoundingClientRect()
      return rect.width > 0 && rect.height > 0
    })

    const unlabeledControls = renderedControls
      .filter((element) => {
        const hasAriaName = Boolean(
          element.getAttribute('aria-label') || element.getAttribute('aria-labelledby'),
        )
        return !hasAriaName && !element.closest('label') && element.labels?.length === 0
      })
      .map((element) => ({
        tag: element.tagName.toLowerCase(),
        id: element.id,
        role: element.getAttribute('role'),
        placeholder: element.getAttribute('placeholder'),
      }))

    const ids = [...document.querySelectorAll<HTMLElement>('[id]')]
      .map((element) => element.id)
      .filter(Boolean)
    const duplicateIds = [...new Set(ids.filter((id, index) => ids.indexOf(id) !== index))]

    return {
      horizontalOverflow: document.documentElement.scrollWidth > window.innerWidth + 1,
      unlabeledControls,
      duplicateIds,
    }
  })

  expect(audit.horizontalOverflow, `${path} 不应出现横向溢出`).toBe(false)
  expect(audit.unlabeledControls, `${path} 的可见表单控件必须有标签`).toEqual([])
  expect(audit.duplicateIds, `${path} 不应包含重复 DOM id`).toEqual([])
}

for (const viewport of viewports) {
  test(`${viewport.width}px 核心路由满足 UI 交付契约`, async ({ page }) => {
    await page.setViewportSize(viewport)
    const runtime = collectRuntimeProblems(page)

    for (const path of publicRoutes) {
      runtime.setCurrentPath(path)
      await assertPageContract(page, path, runtime.waitForApiSettled)
    }

    runtime.setCurrentPath('/user/login')
    await loginAsAdmin(page)

    for (const path of protectedRoutes) {
      runtime.setCurrentPath(path)
      await assertPageContract(page, path, runtime.waitForApiSettled)
    }

    expect(runtime.problems, '核心路由不应产生控制台、页面或 HTTP 错误').toEqual([])
  })
}

test('登录主路径支持键盘导航并显示焦点', async ({ page }) => {
  await page.goto('/user/login')
  await expect(page.getByRole('heading', { level: 1, name: '欢迎回来' })).toBeVisible()

  const visited: string[] = []
  const required = new Set(['请输入账号', '请输入密码', '登录'])

  for (let index = 0; index < 16 && required.size > 0; index += 1) {
    await page.keyboard.press('Tab')
    const focus = await page.evaluate(() => {
      const element = document.activeElement as HTMLElement | null
      if (!element || element === document.body) return null

      const style = window.getComputedStyle(element)
      const text =
        element.getAttribute('aria-label') ||
        element.getAttribute('placeholder') ||
        element.textContent?.trim().replace(/\s+/g, ' ') ||
        element.tagName

      return {
        text,
        focusVisible: element.matches(':focus-visible'),
        outlineStyle: style.outlineStyle,
        outlineWidth: style.outlineWidth,
        boxShadow: style.boxShadow,
        hasVisibleIndicator:
          (style.outlineStyle !== 'none' && Number.parseFloat(style.outlineWidth) > 0) ||
          style.boxShadow !== 'none',
      }
    })

    expect(focus, `第 ${index + 1} 次 Tab 后应聚焦可交互元素`).not.toBeNull()
    if (!focus) continue

    visited.push(focus.text)
    expect(
      focus.hasVisibleIndicator,
      `${focus.text} 必须显示键盘焦点：${JSON.stringify(focus)}`,
    ).toBe(true)
    required.delete(focus.text)
  }

  expect([...required], `未通过键盘到达：${visited.join(' → ')}`).toEqual([])
})

test('减少动态效果偏好下不运行持续动画', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await page.goto('/')
  await expect(page.getByRole('heading', { level: 1, name: 'AI 爆款文章创作器' })).toBeVisible()

  const motionState = await page.evaluate(() => ({
    reducedMotion: window.matchMedia('(prefers-reduced-motion: reduce)').matches,
    persistentAnimations: document
      .getAnimations()
      .filter((animation) => animation.playState === 'running')
      .filter((animation) => animation.effect?.getComputedTiming().iterations === Number.POSITIVE_INFINITY)
      .length,
  }))

  expect(motionState.reducedMotion).toBe(true)
  expect(motionState.persistentAnimations).toBe(0)
})
