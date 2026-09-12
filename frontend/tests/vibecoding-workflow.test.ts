import assert from 'node:assert/strict'
import test from 'node:test'
import { analyzePrd, buildPrototypeHtml, type FlowItem } from '../src/services/vibecodingWorkflow.ts'

const flowItem = (overrides: Partial<FlowItem>): FlowItem => ({
  id: 'flow-1',
  group: '核心流程',
  pageName: '页面',
  state: '默认状态',
  action: '点击继续',
  next: '进入下一页',
  confirmed: true,
  ...overrides,
})

test('prototype html stacks screens strictly in flow order', () => {
  const html = buildPrototypeHtml('订单', [
    flowItem({ pageName: '登录' }),
    flowItem({ pageName: '首页' }),
    flowItem({ pageName: '提交成功' }),
  ])

  const steps = ['1', '2', '3'].map((step) => html.indexOf(`data-step="${step}"`))
  assert.ok(steps.every((position) => position > -1))
  assert.ok(steps[0] < steps[1] && steps[1] < steps[2])
  assert.ok(html.indexOf('<h3>登录</h3>') < html.indexOf('<h3>首页</h3>'))
  assert.ok(html.indexOf('<h3>首页</h3>') < html.indexOf('<h3>提交成功</h3>'))
  // 单文件 + 无外部依赖 + 无脚本
  assert.ok(html.startsWith('<!doctype html>'))
  assert.ok(html.includes('<style>'))
  assert.ok(!html.includes('auto-fit'))
  assert.ok(!html.includes('<script'))
  assert.ok(!html.includes('<link'))
  assert.ok(!/https?:\/\//.test(html))
})

test('prototype html renders one section per consecutive group without reordering', () => {
  const html = buildPrototypeHtml('订单', [
    flowItem({ group: '进入产品', pageName: '登录' }),
    flowItem({ group: '核心流程', pageName: '首页' }),
    flowItem({ group: '核心流程', pageName: '创建订单' }),
    flowItem({ group: '异常与恢复', pageName: '支付失败' }),
  ])

  assert.equal((html.match(/<section class="group">/g) ?? []).length, 3)
  assert.equal((html.match(/<article class="screen/g) ?? []).length, 4)
  const badges = ['分组 01', '分组 02', '分组 03'].map((label) => html.indexOf(`>${label}<`))
  assert.ok(badges.every((position) => position > -1))
  assert.ok(badges[0] < badges[1] && badges[1] < badges[2])
  assert.ok(html.indexOf('进入产品') < html.indexOf('核心流程'))
  assert.ok(html.indexOf('核心流程') < html.indexOf('异常与恢复'))
  assert.ok(html.includes('组内第 1/2 屏'))
  assert.ok(html.includes('步骤 02–03'))
  // 非连续分组不得被合并/重排
  const split = buildPrototypeHtml('订单', [
    flowItem({ group: 'A', pageName: '页面一' }),
    flowItem({ group: 'B', pageName: '页面二' }),
    flowItem({ group: 'A', pageName: '页面三' }),
  ])
  assert.equal((split.match(/<section class="group">/g) ?? []).length, 3)
  assert.ok(split.indexOf('<h3>页面一</h3>') < split.indexOf('<h3>页面二</h3>'))
  assert.ok(split.indexOf('<h3>页面二</h3>') < split.indexOf('<h3>页面三</h3>'))
})

test('prototype html flags unconfirmed screens and 待确认 fields', () => {
  const html = buildPrototypeHtml('订单', [
    flowItem({ pageName: '登录' }),
    flowItem({ pageName: '创建订单', confirmed: false }),
  ])

  // 只数 class 属性，避免把 CSS 选择器 `.screen.is-unconfirmed` 也算进来
  assert.equal((html.match(/class="screen is-unconfirmed"/g) ?? []).length, 1)
  assert.equal((html.match(/class="pending-flag"/g) ?? []).length, 1)
  assert.equal((html.match(/class="field is-pending"/g) ?? []).length, 0)
  // 标记必须落在第 2 屏（未确认那屏）的开标签上：位于第 1 屏之后、其 data-step 之前
  const unconfirmedAt = html.indexOf('class="screen is-unconfirmed"')
  assert.ok(unconfirmedAt > html.indexOf('data-step="1"'))
  assert.ok(unconfirmedAt < html.indexOf('data-step="2"'))
  assert.ok(html.indexOf('待确认：该屏尚未勾选确认') > html.indexOf('data-step="2"'))

  // 字段值本身含"待确认"文案（analyzePrd 对缺失信息即如此标记）时应落到 is-pending
  const pendingFields = buildPrototypeHtml('订单', [flowItem({ action: '待确认：用户可执行操作' })])
  assert.equal((pendingFields.match(/class="field is-pending"/g) ?? []).length, 1)
})

test('prototype html escapes every dynamic text node', () => {
  const html = buildPrototypeHtml('</title><script>alert(1)</script>', [
    flowItem({ group: 'A&B', pageName: '<img src=x onerror=alert(1)>', action: '点击 "提交"', next: '进入下一页' }),
  ])

  assert.ok(!html.includes('<img'))
  assert.ok(!html.includes('<script'))
  assert.ok(html.includes('&lt;img src=x onerror=alert(1)&gt;'))
  assert.ok(html.includes('&lt;script&gt;'))
  assert.ok(html.includes('A&amp;B'))
  assert.ok(html.includes('&quot;提交&quot;'))
})

test('prototype html renders an empty state without flow items', () => {
  const html = buildPrototypeHtml('', [])
  assert.ok(html.includes('未命名产品'))
  assert.ok(html.includes('class="empty"'))
  assert.ok(html.includes('共 0 屏 / 0 个分组'))
})

test('analyzePrd output keeps every screen in analysis order', () => {
  const items = analyzePrd('登录\n首页\n创建内容\n失败后重试')
  const html = buildPrototypeHtml('我的产品', items)

  assert.equal((html.match(/<article class="screen/g) ?? []).length, items.length)
  const positions = items.map((item) => html.indexOf(`<h3>${item.pageName}</h3>`))
  assert.ok(positions.every((position) => position > -1))
  assert.deepEqual(positions, [...positions].sort((left, right) => left - right))
})
