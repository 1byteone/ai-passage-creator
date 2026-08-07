/**
 * SSE 工具函数
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */

export interface SSEMessage {
  type: string
  content?: string
  fullContent?: string
  imageRequirements?: unknown[]
  images?: API.ImageItem[]
  // 单张配图完成事件携带的图片数据（后端 ImageResult）
  image?: { position?: number; url?: string; method?: string; keywords?: string; sectionTitle?: string; description?: string }
  message?: string
  outline?: Array<{
    section: number
    title: string
    points: string[]
  }>
  titleOptions?: Array<{
    mainTitle: string
    subTitle: string
  }>
  // 质量门报告字段（QUALITY_CHECKED 事件）
  score?: number
  passed?: boolean
  detoxed?: boolean
  violations?: string[]
  viralScore?: number
  // RAG 参考注入事件（RAG_REFERENCE_FOUND）
  stage?: string
  count?: number
}

export interface SSEOptions {
  onMessage: (message: SSEMessage) => void
  onError?: (error: Event) => void
  onComplete?: () => void
}

export interface SkillSSEOptions {
  onMessage: (message: API.SkillProgressEvent) => void
  onFallback: () => void
  onParseError?: (error: unknown) => void
}

export interface SSEConnection {
  close: () => void
}

export interface SkillSSEConnection {
  close: () => void
}

/**
 * 建立 SSE 连接（带一次重连）
 */
export const connectSSE = (taskId: string, options: SSEOptions): SSEConnection => {
  const { onMessage, onError, onComplete } = options
  let source: EventSource | null = null
  let reconnectCount = 0
  let closed = false
  let terminal = false

  const build = () => {
    if (closed) return
    source = new EventSource(`/api/article/progress/${taskId}`, { withCredentials: true })

    source.onmessage = (event) => {
      let message: SSEMessage
      try {
        message = JSON.parse(event.data) as SSEMessage
      } catch (error) {
        console.error('SSE 消息解析失败:', error)
        return
      }

      try {
        onMessage(message)
      } catch (error) {
        console.error('SSE 消息处理失败:', error)
      }

      // 检查是否完成
      if (message.type === 'ALL_COMPLETE' || message.type === 'ERROR') {
        terminal = true
        source?.close()
        source = null
        onComplete?.()
      }
    }

    source.onerror = (error) => {
      source?.close()
      source = null
      if (closed || terminal) return

      if (reconnectCount < 1) {
        reconnectCount += 1
        console.warn('SSE 连接中断，正在重连...')
        build()
      } else {
        console.error('SSE 重连失败，连接关闭:', error)
        closed = true
        onError?.(error)
      }
    }
  }

  build()

  return {
    close: () => {
      closed = true
      source?.close()
      source = null
    },
  }
}

/**
 * 关闭 SSE 连接
 */
export const closeSSE = (connection: SSEConnection | SkillSSEConnection | null) => {
  if (connection) {
    connection.close()
  }
}

export const connectSkillSSE = (
  executionId: string,
  options: SkillSSEOptions,
): SkillSSEConnection => {
  return connectSkillLikeSSE(
    `/api/skill/${encodeURIComponent(executionId)}/progress`,
    (message) => message.type === 'skill.complete' || message.type === 'skill.error',
    options,
  )
}

/**
 * 链式编排 SSE — 与单 skill 完全一致的重连/关闭语义，仅 URL 与终态事件不同
 */
export const connectChainSSE = (
  chainId: string,
  options: SkillSSEOptions,
): SkillSSEConnection => {
  return connectSkillLikeSSE(
    `/api/skill/chain/${encodeURIComponent(chainId)}/progress`,
    (message) => message.type === 'chain.complete' || message.type === 'chain.error',
    options,
  )
}

/**
 * skill/chain SSE 通用连接（带一次重连）。
 * 单 skill 与链式复用同一套连接语义，仅 URL 与终态判定不同。
 */
const connectSkillLikeSSE = (
  url: string,
  isTerminal: (message: API.SkillProgressEvent) => boolean,
  options: SkillSSEOptions,
): SkillSSEConnection => {
  let source: EventSource | null = null
  let reconnectTimer: number | null = null
  let reconnectCount = 0
  let closed = false
  let terminal = false

  const close = () => {
    closed = true
    source?.close()
    source = null
    if (reconnectTimer !== null) {
      window.clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  const connect = () => {
    if (closed) return
    source = new EventSource(url, { withCredentials: true })

    source.onmessage = (event) => {
      // 连接成功收到消息后重置重连计数，为下一次断连做准备
      reconnectCount = 0
      try {
        const message = JSON.parse(event.data) as API.SkillProgressEvent
        options.onMessage(message)
        if (isTerminal(message)) {
          terminal = true
          close()
        }
      } catch (error) {
        options.onParseError?.(error)
      }
    }

    source.onerror = () => {
      source?.close()
      source = null
      if (closed || terminal) return

      if (reconnectCount < 1) {
        reconnectCount += 1
        reconnectTimer = window.setTimeout(connect, 1000 * 2 ** (reconnectCount - 1))
      } else {
        closed = true
        options.onFallback()
      }
    }
  }

  connect()
  return { close }
}
