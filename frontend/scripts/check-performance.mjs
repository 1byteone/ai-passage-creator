import { spawn } from 'node:child_process'
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createServer } from 'node:net'
import { basename, join } from 'node:path'
import { fileURLToPath } from 'node:url'

import { launch as launchChrome } from 'chrome-launcher'
import lighthouse from 'lighthouse'

const frontendDirectory = fileURLToPath(new URL('../', import.meta.url))
const distIndex = fileURLToPath(new URL('../dist/index.html', import.meta.url))
const viteCli = fileURLToPath(new URL('../node_modules/vite/bin/vite.js', import.meta.url))
const outputDirectory = fileURLToPath(new URL('../performance-results/', import.meta.url))

const routes = [
  {
    path: '/',
    label: '首页',
    slug: 'home',
    expectedH1: 'AI 爆款文章创作器',
    criticalSelector: '#home-topic-input',
    criticalText: '开始创作',
    requiredApis: [{ path: '/api/user/get/login', allowedStatuses: [200, 401, 403] }],
  },
  {
    path: '/user/login',
    label: '登录页',
    slug: 'login',
    expectedH1: '欢迎回来',
    criticalSelector: 'form[name="login"]',
    criticalText: '登录后继续创作或查看历史成果。',
    requiredApis: [{ path: '/api/user/get/login', allowedStatuses: [200, 401, 403] }],
  },
  {
    path: '/skill',
    label: 'Skill 中心',
    slug: 'skill',
    expectedH1: 'AI 技能中心',
    criticalSelector: '.center-heading',
    criticalText: '选择一个明确任务',
    requiredApis: [
      { path: '/api/user/get/login', allowedStatuses: [200, 401, 403] },
      { path: '/api/skill/list', allowedStatuses: [200] },
    ],
  },
]

const lighthouseOptions = {
  output: 'json',
  logLevel: 'error',
  onlyCategories: ['performance'],
  formFactor: 'mobile',
  screenEmulation: {
    mobile: true,
    width: 390,
    height: 844,
    deviceScaleFactor: 2,
    disabled: false,
  },
  throttlingMethod: 'simulate',
  throttling: {
    rttMs: 150,
    throughputKbps: 1.6 * 1024,
    requestLatencyMs: 150 * 3.75,
    downloadThroughputKbps: 1.6 * 1024 * 0.9,
    uploadThroughputKbps: 750 * 0.9,
    cpuSlowdownMultiplier: 4,
  },
}

const delay = (milliseconds) =>
  new Promise((resolve) => {
    setTimeout(resolve, milliseconds)
  })

const hasEnvironmentVariable = (name) =>
  Object.prototype.hasOwnProperty.call(process.env, name)

const environmentValue = (name) => (hasEnvironmentVariable(name) ? process.env[name] : undefined)

const parseCliArguments = () => {
  const supportedOptions = new Set(['mode', 'runs'])
  const parsed = new Map()

  for (const argument of process.argv.slice(2)) {
    const match = /^--([a-z-]+)=(.*)$/.exec(argument)
    if (!match || !supportedOptions.has(match[1])) {
      throw new Error(`不支持的性能脚本参数：${argument}`)
    }
    if (parsed.has(match[1])) {
      throw new Error(`性能脚本参数重复：--${match[1]}`)
    }
    parsed.set(match[1], match[2])
  }

  return parsed
}

const parseNonEmptyString = (value, label) => {
  if (typeof value !== 'string' || value.trim() === '') {
    throw new Error(`${label} 不能为空。`)
  }
  return value.trim()
}

const parseInteger = (value, label, minimum, maximum) => {
  const normalized = parseNonEmptyString(value, label)
  if (!/^\d+$/.test(normalized)) {
    throw new Error(`${label} 必须是整数。`)
  }

  const parsed = Number(normalized)
  if (!Number.isSafeInteger(parsed) || parsed < minimum || parsed > maximum) {
    throw new Error(`${label} 必须是 ${minimum} 到 ${maximum} 之间的整数。`)
  }
  return parsed
}

const parseFiniteNumber = (value, label, { minimum, maximum }) => {
  const normalized = parseNonEmptyString(value, label)
  const numberPattern = /^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:e[+-]?\d+)?$/i
  if (!numberPattern.test(normalized)) {
    throw new Error(`${label} 必须是有限数值。`)
  }

  const parsed = Number(normalized)
  if (!Number.isFinite(parsed) || parsed < minimum || parsed > maximum) {
    throw new Error(`${label} 必须在 ${minimum} 到 ${maximum} 之间。`)
  }
  return parsed
}

const parseBaseUrl = (value) => {
  const normalized = parseNonEmptyString(value, 'PERF_BASE_URL')
  let parsed
  try {
    parsed = new URL(normalized)
  } catch {
    throw new Error('PERF_BASE_URL 必须是有效的 HTTP(S) URL。')
  }

  if (!['http:', 'https:'].includes(parsed.protocol)) {
    throw new Error('PERF_BASE_URL 仅支持 HTTP 或 HTTPS。')
  }
  if (parsed.username || parsed.password || parsed.search || parsed.hash) {
    throw new Error('PERF_BASE_URL 不得包含凭据、查询参数或哈希。')
  }
  if (parsed.pathname !== '/' && parsed.pathname !== '') {
    throw new Error('PERF_BASE_URL 必须指向站点根路径。')
  }

  return parsed.origin
}

const assertSupportedNodeVersion = () => {
  const [major, minor, patch] = process.versions.node.split('.').map(Number)
  const supported = major > 22 || (major === 22 && (minor > 19 || (minor === 19 && patch >= 0)))
  if (!supported) {
    throw new Error(`性能门禁要求 Node.js >=22.19.0，当前版本为 ${process.version}。`)
  }
}

const createConfiguration = () => {
  assertSupportedNodeVersion()
  const cliArguments = parseCliArguments()
  const validateMode = (value, label) => {
    const parsed = parseNonEmptyString(value, label)
    if (!['smoke', 'release'].includes(parsed)) {
      throw new Error(`${label} 必须是 smoke 或 release。`)
    }
    return parsed
  }
  const environmentMode = hasEnvironmentVariable('PERF_MODE')
    ? validateMode(environmentValue('PERF_MODE'), 'PERF_MODE')
    : undefined
  const cliMode = cliArguments.has('mode')
    ? validateMode(cliArguments.get('mode'), '--mode')
    : undefined
  const mode = cliMode ?? environmentMode ?? 'smoke'

  const defaultRuns = mode === 'release' ? '3' : '1'
  const environmentRuns = hasEnvironmentVariable('PERF_RUNS')
    ? parseInteger(environmentValue('PERF_RUNS'), 'PERF_RUNS', 1, 5)
    : undefined
  const cliRuns = cliArguments.has('runs')
    ? parseInteger(cliArguments.get('runs'), '--runs', 1, 5)
    : undefined
  const runsPerRoute = cliRuns ?? environmentRuns ?? Number(defaultRuns)
  if (mode === 'release' && runsPerRoute < 3) {
    throw new Error('release 性能门禁每个路由至少需要 3 次正式采样。')
  }

  const previewHost = parseNonEmptyString(
    environmentValue('PERF_PREVIEW_HOST') ?? '127.0.0.1',
    'PERF_PREVIEW_HOST',
  )
  const configuredPreviewPort = hasEnvironmentVariable('PERF_PREVIEW_PORT')
    ? parseInteger(environmentValue('PERF_PREVIEW_PORT'), 'PERF_PREVIEW_PORT', 1, 65_535)
    : undefined
  const existingBaseUrl = hasEnvironmentVariable('PERF_BASE_URL')
    ? parseBaseUrl(environmentValue('PERF_BASE_URL'))
    : undefined

  const chromeOverride = hasEnvironmentVariable('PERF_CHROME_PATH')
    ? environmentValue('PERF_CHROME_PATH')
    : environmentValue('CHROME_PATH')
  const chromePath = chromeOverride !== undefined
    ? parseNonEmptyString(chromeOverride, 'Chrome 路径')
    : process.platform === 'win32'
      ? 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe'
      : undefined

  const budgets = {
    performanceScore: parseFiniteNumber(
      environmentValue('PERF_MIN_SCORE') ?? '0.85',
      'PERF_MIN_SCORE',
      { minimum: 0, maximum: 1 },
    ),
    firstContentfulPaint: parseFiniteNumber(
      environmentValue('PERF_MAX_FCP_MS') ?? '1800',
      'PERF_MAX_FCP_MS',
      { minimum: 0, maximum: 600_000 },
    ),
    largestContentfulPaint: parseFiniteNumber(
      environmentValue('PERF_MAX_LCP_MS') ?? '2500',
      'PERF_MAX_LCP_MS',
      { minimum: 0, maximum: 600_000 },
    ),
    cumulativeLayoutShift: parseFiniteNumber(
      environmentValue('PERF_MAX_CLS') ?? '0.1',
      'PERF_MAX_CLS',
      { minimum: 0, maximum: 1 },
    ),
    totalBlockingTime: parseFiniteNumber(
      environmentValue('PERF_MAX_TBT_MS') ?? '300',
      'PERF_MAX_TBT_MS',
      { minimum: 0, maximum: 600_000 },
    ),
  }

  return {
    mode,
    runsPerRoute,
    previewHost,
    configuredPreviewPort,
    existingBaseUrl,
    chromePath,
    budgets,
  }
}

const findAvailablePort = (host) =>
  new Promise((resolve, reject) => {
    const server = createServer()
    server.unref()
    server.once('error', reject)
    server.listen({ host, port: 0, exclusive: true }, () => {
      const address = server.address()
      if (!address || typeof address === 'string') {
        server.close()
        reject(new Error('无法确定动态生产预览端口。'))
        return
      }
      server.close((error) => {
        if (error) reject(error)
        else resolve(address.port)
      })
    })
  })

const extractHashedModuleEntries = (html, sourceLabel) => {
  const scriptTags = html.match(/<script\b[^>]*>/gi) ?? []
  const entries = scriptTags.flatMap((tag) => {
    const type = /\btype\s*=\s*(["'])module\1/i.exec(tag)
    const source = /\bsrc\s*=\s*(["'])(.*?)\1/i.exec(tag)
    if (!type || !source) return []

    const pathname = new URL(source[2], 'http://preview.invalid/').pathname
    const assetName = basename(pathname)
    return /-[a-z0-9_-]{8,}\.js$/i.test(assetName) ? [assetName] : []
  })

  const uniqueEntries = [...new Set(entries)].sort()
  if (uniqueEntries.length === 0) {
    throw new Error(`${sourceLabel} 未找到带哈希的 module 入口脚本。`)
  }
  return uniqueEntries
}

const assertMatchingPreviewEntry = (distHtml, previewHtml) => {
  const expectedEntries = extractHashedModuleEntries(distHtml, '当前 dist/index.html')
  const previewEntries = extractHashedModuleEntries(previewHtml, '生产预览 HTML')

  if (JSON.stringify(expectedEntries) !== JSON.stringify(previewEntries)) {
    throw new Error(
      `生产预览入口与当前 dist 不一致：期望 ${expectedEntries.join(', ')}，实际 ${previewEntries.join(', ')}。`,
    )
  }
  return expectedEntries
}

const createPreviewMonitor = (previewProcess) => {
  let stopRequested = false
  let termination
  let resolveTermination
  const terminationPromise = new Promise((resolve) => {
    resolveTermination = resolve
  })

  const recordTermination = (details) => {
    if (stopRequested || termination) return
    termination = details
    resolveTermination(details)
  }

  previewProcess.once('error', (error) => {
    recordTermination({ type: 'error', error })
  })
  previewProcess.once('exit', (code, signal) => {
    recordTermination({ type: 'exit', code, signal })
  })

  return {
    terminationPromise,
    getTermination: () => termination,
    requestStop: () => {
      stopRequested = true
    },
  }
}

const previewTerminationError = (termination) => {
  if (termination.type === 'error') {
    return new Error(`生产预览启动失败：${termination.error.message}`)
  }
  return new Error(
    `生产预览意外退出（code=${termination.code ?? 'null'}, signal=${termination.signal ?? 'null'}）。`,
  )
}

const runWhilePreviewAlive = async (operation, previewMonitor) => {
  if (!previewMonitor) return operation

  const existingTermination = previewMonitor.getTermination()
  if (existingTermination) throw previewTerminationError(existingTermination)

  const outcome = await Promise.race([
    Promise.resolve(operation).then(
      (value) => ({ type: 'operation-success', value }),
      (error) => ({ type: 'operation-error', error }),
    ),
    previewMonitor.terminationPromise.then((termination) => ({
      type: 'preview-termination',
      termination,
    })),
  ])

  if (outcome.type === 'preview-termination') {
    throw previewTerminationError(outcome.termination)
  }
  if (outcome.type === 'operation-error') throw outcome.error
  return outcome.value
}

const waitForServer = async (url, previewMonitor, timeoutMilliseconds = 120_000) => {
  const deadline = Date.now() + timeoutMilliseconds
  let lastError

  while (Date.now() < deadline) {
    const termination = previewMonitor?.getTermination()
    if (termination) throw previewTerminationError(termination)
    try {
      const response = await fetch(url, {
        cache: 'no-store',
        signal: AbortSignal.timeout(Math.min(5_000, Math.max(1, deadline - Date.now()))),
      })
      if (response.ok) return response.text()
      lastError = new Error(`HTTP ${response.status}`)
    } catch (error) {
      lastError = error
    }
    await delay(300)
  }

  throw new Error(
    `生产预览未在 ${timeoutMilliseconds / 1000} 秒内就绪：${lastError instanceof Error ? lastError.message : '未知错误'}`,
  )
}

const socketPayloadToText = async (payload) => {
  if (typeof payload === 'string') return payload
  if (payload instanceof ArrayBuffer) return Buffer.from(payload).toString('utf8')
  if (payload && typeof payload.text === 'function') return payload.text()
  return String(payload)
}

const connectCdp = async (webSocketDebuggerUrl) => {
  const socket = new WebSocket(webSocketDebuggerUrl)
  const pendingCommands = new Map()
  const eventSubscribers = new Set()
  let commandId = 0
  let closed = false

  const rejectPendingCommands = (error) => {
    for (const command of pendingCommands.values()) {
      clearTimeout(command.timeout)
      command.reject(error)
    }
    pendingCommands.clear()
  }

  socket.addEventListener('message', (event) => {
    void socketPayloadToText(event.data)
      .then((text) => JSON.parse(text))
      .then((message) => {
        if (message.id) {
          const command = pendingCommands.get(message.id)
          if (!command) return
          pendingCommands.delete(message.id)
          clearTimeout(command.timeout)
          if (message.error) {
            command.reject(new Error(`CDP ${message.error.code}: ${message.error.message}`))
          } else {
            command.resolve(message.result)
          }
          return
        }

        if (message.method) {
          for (const subscriber of eventSubscribers) {
            subscriber(message.method, message.params ?? {})
          }
        }
      })
      .catch((error) => rejectPendingCommands(error))
  })

  socket.addEventListener('close', () => {
    closed = true
    rejectPendingCommands(new Error('Chrome DevTools Protocol 连接已关闭。'))
  })

  await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error('连接 Chrome DevTools Protocol 超时。')), 10_000)
    socket.addEventListener(
      'open',
      () => {
        clearTimeout(timeout)
        resolve()
      },
      { once: true },
    )
    socket.addEventListener(
      'error',
      () => {
        clearTimeout(timeout)
        reject(new Error('无法连接 Chrome DevTools Protocol。'))
      },
      { once: true },
    )
  })

  const send = (method, params = {}, timeoutMilliseconds = 15_000) =>
    new Promise((resolve, reject) => {
      if (closed || socket.readyState !== WebSocket.OPEN) {
        reject(new Error(`CDP 连接不可用，无法执行 ${method}。`))
        return
      }

      commandId += 1
      const currentCommandId = commandId
      const timeout = setTimeout(() => {
        pendingCommands.delete(currentCommandId)
        reject(new Error(`CDP 命令 ${method} 超时。`))
      }, timeoutMilliseconds)
      pendingCommands.set(currentCommandId, { resolve, reject, timeout })
      socket.send(JSON.stringify({ id: currentCommandId, method, params }))
    })

  const subscribe = (subscriber) => {
    eventSubscribers.add(subscriber)
    return () => eventSubscribers.delete(subscriber)
  }

  const waitForEvent = (expectedMethod, timeoutMilliseconds = 30_000) =>
    new Promise((resolve, reject) => {
      const unsubscribe = subscribe((method, params) => {
        if (method !== expectedMethod) return
        clearTimeout(timeout)
        unsubscribe()
        resolve(params)
      })
      const timeout = setTimeout(() => {
        unsubscribe()
        reject(new Error(`等待 CDP 事件 ${expectedMethod} 超时。`))
      }, timeoutMilliseconds)
    })

  const close = () => {
    closed = true
    rejectPendingCommands(new Error('Chrome DevTools Protocol 连接已主动关闭。'))
    if (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING) {
      socket.close()
    }
  }

  return { send, subscribe, waitForEvent, close }
}

const apiPathFromUrl = (url) => {
  try {
    const pathname = new URL(url).pathname
    return pathname.startsWith('/api/') ? pathname : undefined
  } catch {
    return undefined
  }
}

const remoteObjectText = (remoteObject) => {
  if (remoteObject.value !== undefined) return String(remoteObject.value)
  if (remoteObject.unserializableValue !== undefined) return String(remoteObject.unserializableValue)
  return remoteObject.description ?? remoteObject.type ?? '未知控制台值'
}

const waitForCondition = async (predicate, timeoutMilliseconds, failureMessage) => {
  const deadline = Date.now() + timeoutMilliseconds
  while (Date.now() < deadline) {
    if (await predicate()) return
    await delay(200)
  }
  throw new Error(failureMessage)
}

const validateRouteWithCdp = async ({ chromePort, baseUrl, route }) => {
  const debuggerBaseUrl = `http://127.0.0.1:${chromePort}`
  const targetResponse = await fetch(`${debuggerBaseUrl}/json/new?${encodeURIComponent('about:blank')}`, {
    method: 'PUT',
    signal: AbortSignal.timeout(10_000),
  })
  if (!targetResponse.ok) {
    throw new Error(`${route.label} 无法创建预热校验页面：HTTP ${targetResponse.status}`)
  }

  const target = await targetResponse.json()
  const apiResponses = []
  const apiFailures = []
  const runtimeErrors = new Set()
  const requestUrls = new Map()
  let cdp
  let unsubscribe = () => undefined
  let pageState

  try {
    cdp = await connectCdp(target.webSocketDebuggerUrl)
    unsubscribe = cdp.subscribe((method, params) => {
      if (method === 'Network.requestWillBeSent') {
        requestUrls.set(params.requestId, params.request.url)
        return
      }

      if (method === 'Network.responseReceived') {
        const path = apiPathFromUrl(params.response.url)
        if (path) {
          apiResponses.push({
            path,
            status: params.response.status,
            statusText: params.response.statusText,
            mimeType: params.response.mimeType,
          })
        }
        return
      }

      if (method === 'Network.loadingFailed') {
        const url = requestUrls.get(params.requestId)
        const path = url ? apiPathFromUrl(url) : undefined
        if (path) apiFailures.push({ path, errorText: params.errorText })
        return
      }

      if (method === 'Runtime.exceptionThrown') {
        const details = params.exceptionDetails
        runtimeErrors.add(details.exception?.description ?? details.text ?? '未捕获的运行时异常')
        return
      }

      if (method === 'Runtime.consoleAPICalled' && ['error', 'assert'].includes(params.type)) {
        runtimeErrors.add(params.args.map(remoteObjectText).join(' '))
        return
      }

      if (
        method === 'Log.entryAdded' &&
        params.entry.level === 'error' &&
        params.entry.source === 'javascript'
      ) {
        runtimeErrors.add(params.entry.text)
      }
    })

    await Promise.all([
      cdp.send('Page.enable'),
      cdp.send('Runtime.enable'),
      cdp.send('Network.enable'),
      cdp.send('Log.enable'),
    ])

    const loadEvent = cdp.waitForEvent('Page.loadEventFired')
    const routeUrl = `${baseUrl}${route.path}`
    const navigation = await cdp.send('Page.navigate', { url: routeUrl })
    if (navigation.errorText) {
      throw new Error(`${route.label} 导航失败：${navigation.errorText}`)
    }
    await loadEvent

    await waitForCondition(
      async () => {
        const evaluation = await cdp.send('Runtime.evaluate', {
          expression: `(() => {
            const normalize = (value) => (value || '').replace(/\\s+/g, ' ').trim()
            return {
              pathname: location.pathname,
              h1: normalize(document.querySelector('h1')?.textContent),
              criticalPresent: Boolean(document.querySelector(${JSON.stringify(route.criticalSelector)})),
              criticalTextPresent: normalize(document.body?.innerText).includes(${JSON.stringify(route.criticalText)}),
              readyState: document.readyState,
            }
          })()`,
          returnByValue: true,
        })
        if (evaluation.exceptionDetails) {
          throw new Error(`${route.label} 页面状态读取失败。`)
        }
        pageState = evaluation.result.value
        return (
          pageState?.pathname === route.path &&
          pageState?.h1 === route.expectedH1 &&
          pageState?.criticalPresent &&
          pageState?.criticalTextPresent &&
          pageState?.readyState === 'complete'
        )
      },
      20_000,
      `${route.label} 未呈现预期 H1 或关键内容。`,
    )

    await waitForCondition(
      () =>
        route.requiredApis.every(({ path }) =>
          [...apiResponses, ...apiFailures].some((request) => request.path === path),
        ),
      15_000,
      `${route.label} 未观察到全部关键 API 请求。`,
    )
    await delay(300)

    const validationFailures = []
    for (const expectedApi of route.requiredApis) {
      const response = [...apiResponses].reverse().find(({ path }) => path === expectedApi.path)
      const requestFailure = apiFailures.find(({ path }) => path === expectedApi.path)
      if (requestFailure) {
        validationFailures.push(`${expectedApi.path} 网络失败：${requestFailure.errorText}`)
      } else if (!response || !expectedApi.allowedStatuses.includes(response.status)) {
        validationFailures.push(
          `${expectedApi.path} HTTP ${response?.status ?? '无响应'}（允许 ${expectedApi.allowedStatuses.join('/')}）`,
        )
      }
    }

    for (const response of apiResponses) {
      if (response.status >= 500) {
        validationFailures.push(`${response.path} HTTP ${response.status}`)
      }
    }
    for (const failure of apiFailures) {
      if (!validationFailures.some((message) => message.includes(failure.path))) {
        validationFailures.push(`${failure.path} 网络失败：${failure.errorText}`)
      }
    }
    if (runtimeErrors.size > 0) {
      validationFailures.push(`运行时错误：${[...runtimeErrors].join(' | ')}`)
    }

    if (validationFailures.length > 0) {
      throw new Error(`${route.label} 预热校验失败：${validationFailures.join('；')}`)
    }

    return {
      h1: pageState.h1,
      criticalSelector: route.criticalSelector,
      criticalText: route.criticalText,
      apiResponses: apiResponses.map(({ path, status, statusText, mimeType }) => ({
        path,
        status,
        statusText,
        mimeType,
      })),
      runtimeErrorCount: runtimeErrors.size,
    }
  } finally {
    unsubscribe()
    cdp?.close()
    await fetch(`${debuggerBaseUrl}/json/close/${target.id}`, {
      signal: AbortSignal.timeout(3_000),
    })
      .then((response) => {
        if (!response.ok) {
          console.warn(`${route.label} CDP 页面关闭返回 HTTP ${response.status}。`)
        }
      })
      .catch((error) => {
        console.warn(
          `${route.label} CDP 页面关闭失败：${error instanceof Error ? error.message : error}`,
        )
      })
  }
}

const median = (values) => {
  const sorted = [...values].sort((left, right) => left - right)
  const middle = Math.floor(sorted.length / 2)
  return sorted.length % 2 === 0
    ? (sorted[middle - 1] + sorted[middle]) / 2
    : sorted[middle]
}

const auditValue = (lhr, auditId) => {
  const value = lhr.audits[auditId]?.numericValue
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new Error(`Lighthouse 未返回 ${auditId} 数值。`)
  }
  return value
}

const findNodeDetail = (value) => {
  if (Array.isArray(value)) {
    for (const item of value) {
      const found = findNodeDetail(item)
      if (found) return found
    }
    return undefined
  }
  if (!value || typeof value !== 'object') return undefined

  if (value.type === 'node') {
    return {
      selector: value.selector,
      snippet: value.snippet,
      nodeLabel: value.nodeLabel,
      path: value.path,
      boundingRect: value.boundingRect,
    }
  }

  for (const nestedValue of Object.values(value)) {
    const found = findNodeDetail(nestedValue)
    if (found) return found
  }
  return undefined
}

const lcpElement = (lhr) => {
  for (const auditId of ['lcp-breakdown-insight', 'largest-contentful-paint-element']) {
    const details = lhr.audits[auditId]?.details
    const node = findNodeDetail(details)
    if (node) return { auditId, ...node }
  }
  return null
}

const collectMetrics = (lhr) => {
  const performanceScore = lhr.categories.performance?.score
  if (typeof performanceScore !== 'number' || !Number.isFinite(performanceScore)) {
    throw new Error('Lighthouse 未返回有效 Performance 分数。')
  }

  return {
    performanceScore,
    firstContentfulPaint: auditValue(lhr, 'first-contentful-paint'),
    largestContentfulPaint: auditValue(lhr, 'largest-contentful-paint'),
    cumulativeLayoutShift: auditValue(lhr, 'cumulative-layout-shift'),
    totalBlockingTime: auditValue(lhr, 'total-blocking-time'),
    speedIndex: auditValue(lhr, 'speed-index'),
    totalByteWeight: auditValue(lhr, 'total-byte-weight'),
    lcpElement: lcpElement(lhr),
  }
}

const aggregateSamples = (samples) => ({
  performanceScore: median(samples.map((sample) => sample.performanceScore)),
  firstContentfulPaint: median(samples.map((sample) => sample.firstContentfulPaint)),
  largestContentfulPaint: median(samples.map((sample) => sample.largestContentfulPaint)),
  cumulativeLayoutShift: median(samples.map((sample) => sample.cumulativeLayoutShift)),
  totalBlockingTime: median(samples.map((sample) => sample.totalBlockingTime)),
  speedIndex: median(samples.map((sample) => sample.speedIndex)),
  totalByteWeight: median(samples.map((sample) => sample.totalByteWeight)),
})

const representativeSampleIndex = (samples, aggregated) => {
  let closestIndex = 0
  let closestDistance = Number.POSITIVE_INFINITY
  samples.forEach((sample, index) => {
    const distance = Math.abs(sample.metrics.largestContentfulPaint - aggregated.largestContentfulPaint)
    if (distance < closestDistance) {
      closestDistance = distance
      closestIndex = index
    }
  })
  return closestIndex
}

const extractEnvironment = (lhr, chromeMetadata) => ({
  nodeVersion: process.version,
  lighthouseVersion: lhr.lighthouseVersion,
  chrome: {
    browser: chromeMetadata.Browser,
    protocolVersion: chromeMetadata['Protocol-Version'],
    userAgent: chromeMetadata['User-Agent'],
    v8Version: chromeMetadata['V8-Version'],
  },
  hostUserAgent: lhr.environment.hostUserAgent,
  networkUserAgent: lhr.environment.networkUserAgent,
  benchmarkIndex: lhr.environment.benchmarkIndex,
  formFactor: lhr.configSettings.formFactor,
  throttlingMethod: lhr.configSettings.throttlingMethod,
  throttling: lhr.configSettings.throttling,
  screenEmulation: lhr.configSettings.screenEmulation,
})

const formatMilliseconds = (value) => `${Math.round(value)} ms`
const formatBytes = (value) => `${(value / 1024).toFixed(1)} KiB`

const collectBudgetFailures = (routeResults, budgets) => {
  const failures = []
  for (const route of routeResults) {
    const metrics = route.median
    if (metrics.performanceScore < budgets.performanceScore) {
      failures.push(
        `${route.label} Performance ${Math.round(metrics.performanceScore * 100)} < ${Math.round(budgets.performanceScore * 100)}`,
      )
    }
    if (metrics.firstContentfulPaint > budgets.firstContentfulPaint) {
      failures.push(
        `${route.label} FCP ${formatMilliseconds(metrics.firstContentfulPaint)} > ${formatMilliseconds(budgets.firstContentfulPaint)}`,
      )
    }
    if (metrics.largestContentfulPaint > budgets.largestContentfulPaint) {
      failures.push(
        `${route.label} LCP ${formatMilliseconds(metrics.largestContentfulPaint)} > ${formatMilliseconds(budgets.largestContentfulPaint)}`,
      )
    }
    if (route.maximumCumulativeLayoutShift > budgets.cumulativeLayoutShift) {
      failures.push(
        `${route.label} CLS 最大值 ${route.maximumCumulativeLayoutShift.toFixed(3)} > ${budgets.cumulativeLayoutShift}`,
      )
    }
    if (metrics.totalBlockingTime > budgets.totalBlockingTime) {
      failures.push(
        `${route.label} TBT ${formatMilliseconds(metrics.totalBlockingTime)} > ${formatMilliseconds(budgets.totalBlockingTime)}`,
      )
    }
  }
  return failures
}

const writeReports = ({
  baseUrl,
  mode,
  runsPerRoute,
  budgets,
  entryAssets,
  routeResults,
  chromeMetadata,
}) => {
  mkdirSync(outputDirectory, { recursive: true })

  const reportRoutes = routeResults.map(({ representativeLhr, ...route }) => {
    const lhrFile = `latest-${route.slug}.lhr.json`
    writeFileSync(join(outputDirectory, lhrFile), `${JSON.stringify(representativeLhr, null, 2)}\n`, 'utf8')
    return {
      ...route,
      representative: {
        ...route.representative,
        lhrFile,
      },
    }
  })

  const failures = collectBudgetFailures(reportRoutes, budgets)
  const budgetEnforced = mode === 'release'
  const report = {
    generatedAt: new Date().toISOString(),
    baseUrl,
    mode,
    budgetEnforced,
    runsPerRoute,
    aggregation: {
      scoreFcpLcpTbt: 'median of raw values',
      cumulativeLayoutShiftGate: 'maximum raw value',
    },
    budgets,
    entryAssets,
    environment: reportRoutes[0]?.representative.environment ?? {
      nodeVersion: process.version,
      chrome: chromeMetadata,
    },
    note: 'Lighthouse 实验室环境不直接产出 INP；TBT 仅作为主线程阻塞的实验室代理，不能与真实用户 INP 等同。',
    routes: reportRoutes,
    budgetPassed: failures.length === 0,
    passed: !budgetEnforced || failures.length === 0,
    failures,
  }

  writeFileSync(join(outputDirectory, 'latest.json'), `${JSON.stringify(report, null, 2)}\n`, 'utf8')

  const markdownRows = reportRoutes.map(({ label, path, median: metrics, maximumCumulativeLayoutShift }) =>
    [
      label,
      `\`${path}\``,
      Math.round(metrics.performanceScore * 100),
      formatMilliseconds(metrics.firstContentfulPaint),
      formatMilliseconds(metrics.largestContentfulPaint),
      `${metrics.cumulativeLayoutShift.toFixed(3)} / ${maximumCumulativeLayoutShift.toFixed(3)}`,
      formatMilliseconds(metrics.totalBlockingTime),
      formatMilliseconds(metrics.speedIndex),
      formatBytes(metrics.totalByteWeight),
    ].join(' | '),
  )
  const environment = report.environment
  const markdown = `# 前端性能采样\n\n生成时间：${report.generatedAt}\n\n模式：${mode === 'release' ? 'release 稳定门禁' : 'smoke 冒烟采样（预算仅观察）'}。每个路由先预热并校验页面，再进行 ${runsPerRoute} 次 Lighthouse mobile simulated throttling 正式采样。\n\n环境：Node ${environment.nodeVersion}；Lighthouse ${environment.lighthouseVersion ?? '未知'}；${environment.chrome?.browser ?? 'Chrome 版本未知'}；${environment.throttlingMethod ?? 'simulate'} throttling。\n\n> Lighthouse 实验室环境不直接产出 INP；TBT 仅作为主线程阻塞的实验室代理，不能与真实用户 INP 等同。\n\n| 页面 | 路由 | Performance | FCP | LCP | CLS 中位数 / 最大值 | TBT | Speed Index | 传输体积 |\n| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n${markdownRows.map((row) => `| ${row} |`).join('\n')}\n\n预算结果：${failures.length === 0 ? '通过' : mode === 'release' ? '未通过' : '存在超限（smoke 不阻断）'}\n${failures.length === 0 ? '' : `\n${failures.map((failure) => `- ${failure}`).join('\n')}\n`}`
  writeFileSync(join(outputDirectory, 'latest.md'), markdown, 'utf8')

  return report
}

const run = async () => {
  const previewOutput = []
  let previewProcess
  let previewMonitor
  let chrome

  try {
    const configuration = createConfiguration()
    if (!existsSync(distIndex)) {
      throw new Error('未找到生产构建 dist/index.html，请先执行 npm run build。')
    }
    if (!configuration.existingBaseUrl && !existsSync(viteCli)) {
      throw new Error('未找到 Vite CLI，请先执行 npm install。')
    }
    if (configuration.chromePath && !existsSync(configuration.chromePath)) {
      throw new Error(`未找到 Chrome：${configuration.chromePath}`)
    }

    const previewPort = configuration.existingBaseUrl
      ? undefined
      : configuration.configuredPreviewPort ?? (await findAvailablePort(configuration.previewHost))
    const baseUrl = configuration.existingBaseUrl ?? `http://${configuration.previewHost}:${previewPort}`

    if (!configuration.existingBaseUrl) {
      previewProcess = spawn(
        process.execPath,
        [
          viteCli,
          'preview',
          '--host',
          configuration.previewHost,
          '--port',
          String(previewPort),
          '--strictPort',
        ],
        {
          cwd: frontendDirectory,
          env: process.env,
          stdio: ['ignore', 'pipe', 'pipe'],
          windowsHide: true,
        },
      )
      previewProcess.stdout.on('data', (chunk) => previewOutput.push(chunk.toString()))
      previewProcess.stderr.on('data', (chunk) => previewOutput.push(chunk.toString()))
      previewMonitor = createPreviewMonitor(previewProcess)
    }

    const previewHtml = await runWhilePreviewAlive(waitForServer(baseUrl, previewMonitor), previewMonitor)
    const distHtml = readFileSync(distIndex, 'utf8')
    const entryAssets = assertMatchingPreviewEntry(distHtml, previewHtml)

    chrome = await launchChrome({
      chromePath: configuration.chromePath,
      chromeFlags: [
        '--headless=new',
        '--no-first-run',
        '--no-default-browser-check',
        '--disable-dev-shm-usage',
      ],
    })
    const chromeMetadataResponse = await fetch(`http://127.0.0.1:${chrome.port}/json/version`, {
      signal: AbortSignal.timeout(10_000),
    })
    if (!chromeMetadataResponse.ok) {
      throw new Error(`无法读取 Chrome 版本：HTTP ${chromeMetadataResponse.status}`)
    }
    const chromeMetadata = await chromeMetadataResponse.json()

    const routeResults = []
    for (const route of routes) {
      process.stdout.write(`\n${route.label} ${route.path}\n`)
      const warmupValidation = await runWhilePreviewAlive(
        validateRouteWithCdp({ chromePort: chrome.port, baseUrl, route }),
        previewMonitor,
      )
      console.log(
        `  预热校验：H1、关键内容、${warmupValidation.apiResponses.length} 个 API 响应及运行时错误检查通过`,
      )

      const samples = []
      for (let runIndex = 0; runIndex < configuration.runsPerRoute; runIndex += 1) {
        const result = await runWhilePreviewAlive(
          lighthouse(`${baseUrl}${route.path}`, {
            ...lighthouseOptions,
            port: chrome.port,
          }),
          previewMonitor,
        )

        if (!result) {
          throw new Error(`${route.label} 第 ${runIndex + 1} 次 Lighthouse 未返回结果。`)
        }
        if (result.lhr.runtimeError) {
          throw new Error(
            `${route.label} 第 ${runIndex + 1} 次 Lighthouse 运行错误：${result.lhr.runtimeError.code} ${result.lhr.runtimeError.message}`,
          )
        }

        const expectedUrl = new URL(`${baseUrl}${route.path}`)
        const finalUrl = new URL(result.lhr.finalDisplayedUrl)
        if (finalUrl.origin !== expectedUrl.origin || finalUrl.pathname !== expectedUrl.pathname) {
          throw new Error(
            `${route.label} 被重定向到 ${finalUrl.origin}${finalUrl.pathname}，无法形成有效性能基线。`,
          )
        }

        const metrics = collectMetrics(result.lhr)
        samples.push({ metrics, lhr: result.lhr })
        console.log(
          `  第 ${runIndex + 1} 次：score ${Math.round(metrics.performanceScore * 100)} / LCP ${formatMilliseconds(metrics.largestContentfulPaint)} / CLS ${metrics.cumulativeLayoutShift.toFixed(3)} / TBT ${formatMilliseconds(metrics.totalBlockingTime)}`,
        )
      }

      const metricsSamples = samples.map(({ metrics }) => metrics)
      const aggregated = aggregateSamples(metricsSamples)
      const representativeIndex = representativeSampleIndex(samples, aggregated)
      const representative = samples[representativeIndex]
      routeResults.push({
        path: route.path,
        label: route.label,
        slug: route.slug,
        warmupValidation,
        samples: metricsSamples,
        median: aggregated,
        maximumCumulativeLayoutShift: Math.max(
          ...metricsSamples.map((sample) => sample.cumulativeLayoutShift),
        ),
        representative: {
          run: representativeIndex + 1,
          selection: 'closest raw LCP to route median LCP',
          lcpElement: representative.metrics.lcpElement,
          environment: extractEnvironment(representative.lhr, chromeMetadata),
        },
        representativeLhr: representative.lhr,
      })
    }

    const report = writeReports({
      baseUrl,
      mode: configuration.mode,
      runsPerRoute: configuration.runsPerRoute,
      budgets: configuration.budgets,
      entryAssets,
      routeResults,
      chromeMetadata,
    })

    console.log(
      `\nLighthouse ${configuration.mode === 'release' ? '发布性能门禁（原始值聚合）' : '性能冒烟采样（预算仅观察）'}`,
    )
    for (const route of report.routes) {
      const metrics = route.median
      console.log(
        `${route.label.padEnd(10)} score ${String(Math.round(metrics.performanceScore * 100)).padStart(3)} / FCP ${formatMilliseconds(metrics.firstContentfulPaint).padStart(8)} / LCP ${formatMilliseconds(metrics.largestContentfulPaint).padStart(8)} / CLS ${metrics.cumulativeLayoutShift.toFixed(3)} (max ${route.maximumCumulativeLayoutShift.toFixed(3)}) / TBT ${formatMilliseconds(metrics.totalBlockingTime).padStart(7)} / ${formatBytes(metrics.totalByteWeight)}`,
      )
    }

    if (report.failures.length > 0) {
      console.error(
        configuration.mode === 'release' ? '\n性能预算未通过：' : '\n性能预算观察到超限（smoke 不阻断）：',
      )
      report.failures.forEach((failure) => console.error(`- ${failure}`))
    }

    if (!report.passed) {
      process.exitCode = 1
    } else {
      console.log(
        `\n${configuration.mode === 'release' ? '性能预算通过' : '性能冒烟采样完成'}。报告已写入 performance-results/latest.{json,md}，并保存各路由代表性 LHR。`,
      )
    }
  } catch (error) {
    console.error(error instanceof Error ? error.stack ?? error.message : error)
    if (previewOutput.length > 0) {
      console.error('\n生产预览输出：')
      console.error(previewOutput.join('').trim())
    }
    process.exitCode = 1
  } finally {
    if (chrome) {
      try {
        await chrome.kill()
      } catch (error) {
        console.error(`关闭 Chrome 失败：${error instanceof Error ? error.message : error}`)
      }
    }
    if (previewProcess && previewProcess.exitCode === null && !previewProcess.killed) {
      previewMonitor?.requestStop()
      previewProcess.kill()
    }
  }
}

await run()
