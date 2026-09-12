export type WorkflowMode = 'prototype' | 'audit'
export type PrototypeStatus = 'draft' | 'reviewing' | 'confirmed' | 'generated'
export type AuditStatus = 'unreviewed' | 'pass' | 'todo' | 'blocked'

export interface FlowItem {
  id: string
  group: string
  pageName: string
  state: string
  action: string
  next: string
  confirmed: boolean
  changed?: boolean
}

export interface AuditItem {
  id: string
  group: string
  pageName: string
  state: string
  reproduce: string
  expected: string
  actual: string
  feedback: string
  status: AuditStatus
  changed: boolean
  previousFeedback?: string
}

const KEY = 'vibecoding-workflow-state-v1'

const clean = (value: string) => value.trim().replace(/\s+/g, ' ')

export function analyzePrd(prd: string): FlowItem[] {
  const lines = prd.split(/\r?\n/).map(clean).filter(Boolean)
  const source = lines.length ? lines : ['待补充产品需求']
  const result: FlowItem[] = source.slice(0, 30).map((line, index) => ({
    id: `flow-${index + 1}`,
    group: index === 0 ? '进入产品' : index < 4 ? '核心流程' : '补充状态',
    pageName: line.replace(/^[#\d.、\-\s]+/, '').slice(0, 48) || `流程页面 ${index + 1}`,
    state: /加载|loading/i.test(line) ? '加载中' : /失败|异常|错误/i.test(line) ? '失败/异常' : /空|无数据/i.test(line) ? '无数据' : '默认状态',
    action: /登录|注册|提交|创建|保存|重试|申请|下一步/.test(line) ? line.slice(0, 56) : '待确认：用户可执行操作',
    next: index < source.length - 1 ? `进入「${source[index + 1].slice(0, 24)}」` : '待确认：下一页面或完成状态',
    confirmed: false,
  }))
  const keywords: Array<[RegExp, string, string]> = [
    [/权限|授权|申请/, '权限申请', '拒绝后进入异常恢复'],
    [/加载|loading/i, '加载中', '加载完成或失败'],
    [/空|无数据/, '无数据', '创建内容或返回上一步'],
    [/失败|异常|错误/, '失败/异常', '重试或返回安全页面'],
  ]
  keywords.forEach(([pattern, state, next]) => {
    if (pattern.test(prd) && !result.some((item) => item.state === state)) {
      result.push({ id: `flow-${result.length + 1}`, group: '异常与恢复', pageName: state, state, action: '待确认：处理该状态', next, confirmed: false })
    }
  })
  return result
}

export function toAuditItems(items: FlowItem[]): AuditItem[] {
  return items.map((item) => ({
    id: `audit-${item.id}`,
    group: item.group,
    pageName: item.pageName,
    state: item.state,
    reproduce: `进入「${item.pageName}」，执行：${item.action}`,
    expected: `页面呈现「${item.state}」，操作结果：${item.next}`,
    actual: '',
    feedback: '',
    status: 'unreviewed',
    changed: false,
  }))
}

const PENDING_TEXT = '待确认'
const PROTOTYPE_CONNECTOR = '<div class="connector" aria-hidden="true"><span class="connector-line"></span><span class="connector-arrow"></span></div>'

const PROTOTYPE_STYLE = `
body{margin:0;padding:32px;background:#f3f4f6;color:#111827;font:14px Arial,sans-serif}
.wrap{max-width:980px;margin:auto}
h1{font-size:24px;margin:0 0 8px}
.note{color:#6b7280;margin:0 0 6px}
.summary{color:#4b5563;margin:0 0 26px;font-size:13px}
.flow{display:flex;flex-direction:column;max-width:860px;margin:0 auto}
.group{margin:0}
.group-head{display:flex;align-items:center;flex-wrap:wrap;gap:10px;padding:10px 12px;border:2px solid #111827;background:#e5e7eb}
.group-head h2{margin:0;font-size:16px}
.group-badge{padding:4px 6px;border:1px solid #6b7280;background:#fff;font:700 12px/1 monospace}
.group-meta{margin-left:auto;font-size:12px;color:#4b5563}
.flow-stack{display:flex;flex-direction:column}
.connector{display:flex;flex-direction:column;align-items:center;padding:2px 0}
.connector-line{display:block;width:2px;height:22px;background:#9ca3af}
.connector-arrow{display:block;width:0;height:0;border-left:6px solid transparent;border-right:6px solid transparent;border-top:8px solid #9ca3af}
.screen{background:#fff;border:2px solid #6b7280;padding:18px 20px}
.screen.is-unconfirmed{border-style:dashed;background:#fafafa}
.screen-head{display:flex;align-items:flex-start;gap:12px;border-bottom:1px solid #d1d5db;padding-bottom:12px}
.step-badge{flex:0 0 auto;display:grid;place-items:center;min-width:34px;height:34px;background:#111827;color:#fff;font:700 13px/1 monospace}
.screen-title{min-width:0}
.screen-title h3{margin:0;font-size:16px;overflow-wrap:anywhere}
.screen-path{margin:4px 0 0;font-size:12px;color:#6b7280}
.state-badge{flex:0 0 auto;max-width:220px;margin-left:auto;padding:4px 8px;border:1px solid #9ca3af;background:#f3f4f6;font-size:12px}
.placeholder{height:110px;margin:18px 0;border:2px dashed #9ca3af;background:#e5e7eb;display:grid;place-items:center;color:#6b7280}
.screen-fields{margin:0;border-top:1px solid #e5e7eb}
.field{display:grid;grid-template-columns:132px minmax(0,1fr);gap:12px;padding:9px 0;border-bottom:1px solid #f3f4f6}
.field dt{color:#6b7280;font-size:12px}
.field dd{margin:0;line-height:1.55;overflow-wrap:anywhere}
.field.is-pending{background:#f9fafb}
.field.is-pending dt::before{content:"";display:inline-block;width:8px;height:8px;margin-right:6px;border:1px dashed #6b7280}
.field.is-pending dd{color:#4b5563;text-decoration:underline dashed #9ca3af;text-underline-offset:3px}
.pending-flag{margin:14px 0 0;padding:8px 10px;border:1px dashed #9ca3af;background:#f9fafb;color:#4b5563;font-size:12px}
.empty{margin:0;padding:20px;border:2px dashed #9ca3af;background:#fff;color:#6b7280}
@media (max-width:560px){body{padding:18px 14px}.screen-head{flex-wrap:wrap}.state-badge{margin-left:0;max-width:none}.field{grid-template-columns:1fr;gap:2px}}
`

interface PrototypeGroup {
  name: string
  entries: Array<{ item: FlowItem; step: number }>
}

// 只合并"连续同名"分组，绝不重排：数组顺序就是流程顺序（异常项由 analyzePrd 追加在末尾）。
function groupFlowItems(items: FlowItem[]): PrototypeGroup[] {
  const groups: PrototypeGroup[] = []
  items.forEach((item, index) => {
    const name = item.group || '未分组'
    const last = groups[groups.length - 1]
    if (last && last.name === name) last.entries.push({ item, step: index + 1 })
    else groups.push({ name, entries: [{ item, step: index + 1 }] })
  })
  return groups
}

function renderPrototypeField(label: string, value: string): string {
  const text = value || `${PENDING_TEXT}：未填写`
  const pending = text.includes(PENDING_TEXT)
  return `<div class="field${pending ? ' is-pending' : ''}"><dt>${escapeHtml(label)}</dt><dd>${escapeHtml(text)}</dd></div>`
}

function renderPrototypeScreen(item: FlowItem, step: number, ordinal: number, total: number): string {
  const heading = `<header class="screen-head"><span class="step-badge">${String(step).padStart(2, '0')}</span><div class="screen-title"><h3>${escapeHtml(item.pageName || `${PENDING_TEXT}页面名称`)}</h3><p class="screen-path">${escapeHtml(item.group || '未分组')} · 组内第 ${ordinal}/${total} 屏</p></div><span class="state-badge">当前状态：${escapeHtml(item.state || PENDING_TEXT)}</span></header>`
  const body = `<div class="placeholder">内容区域 / 图片占位</div><dl class="screen-fields">${renderPrototypeField('可执行操作', item.action)}${renderPrototypeField('下一页面 / 状态', item.next)}</dl>`
  const flag = item.confirmed ? '' : `<p class="pending-flag">${PENDING_TEXT}：该屏尚未勾选确认，流程可能调整。</p>`
  return `<article class="screen${item.confirmed ? '' : ' is-unconfirmed'}" data-step="${step}">${heading}${body}${flag}</article>`
}

function renderPrototypeGroup(group: PrototypeGroup, index: number): string {
  const screens = group.entries.map((entry, position) => renderPrototypeScreen(entry.item, entry.step, position + 1, group.entries.length))
  const first = String(group.entries[0].step).padStart(2, '0')
  const last = String(group.entries[group.entries.length - 1].step).padStart(2, '0')
  const range = first === last ? `步骤 ${first}` : `步骤 ${first}–${last}`
  const pending = group.entries.filter((entry) => !entry.item.confirmed).length
  const head = `<header class="group-head"><span class="group-badge">分组 ${String(index + 1).padStart(2, '0')}</span><h2>${escapeHtml(group.name)}</h2><span class="group-meta">${range} · ${group.entries.length} 屏${pending ? ` · ${pending} 屏待确认` : ''}</span></header>`
  return `<section class="group">${head}<div class="flow-stack">${screens.join(PROTOTYPE_CONNECTOR)}</div></section>`
}

export function buildPrototypeHtml(projectName: string, items: FlowItem[]): string {
  const title = projectName || '未命名产品'
  const groups = groupFlowItems(items)
  const pendingCount = items.filter((item) => !item.confirmed).length
  const flow = groups.length
    ? groups.map((group, index) => renderPrototypeGroup(group, index)).join(PROTOTYPE_CONNECTOR)
    : '<p class="empty">暂无流程项：请先分析 PRD 并确认流程后再生成原型。</p>'
  const summary = `共 ${items.length} 屏 / ${groups.length} 个分组，严格按流程顺序自上而下排列${pendingCount ? `；其中 ${pendingCount} 屏待确认` : ''}。`
  return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>${escapeHtml(title)} · 低保真流程</title><style>${PROTOTYPE_STYLE}</style></head><body><main class="wrap"><h1>${escapeHtml(title)} · 低保真流程原型</h1><p class="note">基于已确认流程草案生成；未明确部分保留“待确认”。</p><p class="summary">${summary}</p><section class="flow">${flow}</section></main></body></html>`
}

export function exportAuditMarkdown(projectName: string, round: number, items: AuditItem[]): string {
  const groups = [...new Set(items.map((item) => item.group))]
  const safe = (value: string) => redactSecrets(value)
  return `# ${safe(projectName || '未命名产品')} 页面全流程巡查\n\n- 轮次：${round}\n- 导出时间：${new Date().toISOString()}\n\n${groups.map((group) => `## ${safe(group)}\n\n${items.filter((item) => item.group === group).map((item) => `### ${safe(item.pageName)} · ${safe(item.state)}\n- 状态：${statusLabel(item.status)}${item.changed ? '（已改动）' : ''}\n- 复现：${safe(item.reproduce)}\n- 预期：${safe(item.expected)}\n- 实际：${safe(item.actual || '未填写')}\n- 反馈：${safe(item.feedback || '未填写')}\n`).join('\n')}`).join('\n')}`
}

export function redactSecrets(value: string): string {
  return value.replace(/((?:password|passwd|token|secret|api[_-]?key|cookie)\s*[:=]\s*)([^\s,;]+)/gi, '$1[REDACTED]')
}

export function saveWorkflowState(state: unknown) { localStorage.setItem(KEY, JSON.stringify(state)) }
export function loadWorkflowState<T>(): T | null {
  try { return JSON.parse(localStorage.getItem(KEY) || 'null') as T | null } catch { return null }
}
export function statusLabel(status: AuditStatus) { return ({ unreviewed: '未检查', pass: '可以', todo: '待改', blocked: '阻塞' })[status] }
function escapeHtml(value: string) { return value.replace(/[&<>\"]/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[char] || char) }
