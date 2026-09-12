import { expect, test, type Page } from '@playwright/test'

/**
 * dataviz 执行页的入口级验证。
 * 阶段标签与结果渲染器只在执行期出现，其逻辑由 Node 单测覆盖
 * （tests/dataviz-view.test.ts）与组件级断言（iframe sandbox、产物 URL）。
 */
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

async function mockSkillDefinition(page: Page) {
  await page.route('**/api/skill/data-visualization-report/definition', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 0,
        data: {
          name: 'data-visualization-report',
          description: '数据可视化与报告生成',
          category: 'dataviz',
          requiredRoles: ['user'],
          multiRound: true,
          variables: {
            rawData: {
              description: '数据内容（JSON 对象数组，或首行为表头的 CSV）',
              required: true,
              uiType: 'textarea',
              maxLength: 200000,
            },
            dataFormat: { description: '数据格式', uiType: 'select', defaultValue: 'csv' },
            goal: { description: '分析目标', uiType: 'textarea' },
            style: { description: '视觉风格', uiType: 'select', defaultValue: 'glance' },
          },
          phases: [
            { name: 'profile_dataset', requireConfirmation: false, phaseIndex: 1 },
            { name: 'recommend_charts', requireConfirmation: true, phaseIndex: 2 },
          ],
        },
      }),
    }),
  )
}

/** 历史执行列表：首次加载时为空，避免页面向后端取真实数据 */
async function mockExecutionHistory(page: Page) {
  await page.route('**/api/skill/execution/list*', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 0, data: { records: [], total: 0 } }),
    }),
  )
}

test.describe('数据可视化报告 skill 入口', () => {
  test.beforeEach(async ({ page }) => {
    await mockAuth(page)
    await mockSkillDefinition(page)
    await mockExecutionHistory(page)
  })

  test('技能执行页按定义渲染数据输入项', async ({ page }) => {
    await page.goto('/skill/data-visualization-report')

    // 输入项来自后端 skill 定义（required 的 rawData 必须出现）
    await expect(page.getByText('数据内容（JSON 对象数组，或首行为表头的 CSV）')).toBeVisible()
    await expect(page.getByText('视觉风格')).toBeVisible()
  })

  test('页面加载无未捕获的运行时错误', async ({ page }) => {
    const problems: string[] = []
    page.on('pageerror', (error) => problems.push(error.message))

    await page.goto('/skill/data-visualization-report')
    await expect(page.getByText('数据内容（JSON 对象数组，或首行为表头的 CSV）')).toBeVisible()

    expect(problems, `运行时错误: ${problems.join(' | ')}`).toEqual([])
  })
})
