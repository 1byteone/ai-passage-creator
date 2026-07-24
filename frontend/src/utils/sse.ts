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

export interface SkillSSEConnection {
  close: () => void
}

/**
 * 建立 SSE 连接
 */
export const connectSSE = (taskId: string, options: SSEOptions): EventSource => {
  const { onMessage, onError, onComplete } = options

  const eventSource = new EventSource(`/api/article/progress/${taskId}`)

  eventSource.onmessage = (event) => {
    try {
      const message: SSEMessage = JSON.parse(event.data)
      onMessage(message)
      
      // 检查是否完成
      if (message.type === 'ALL_COMPLETE' || message.type === 'ERROR') {
        eventSource.close()
        onComplete?.()
      }
    } catch (error) {
      console.error('SSE 消息解析失败:', error)
    }
  }

  eventSource.onerror = (error) => {
    console.error('SSE 连接错误:', error)
    onError?.(error)
    eventSource.close()
  }

  return eventSource
}

/**
 * 关闭 SSE 连接
 */
export const closeSSE = (eventSource: EventSource | null) => {
  if (eventSource) {
    eventSource.close()
  }
}

export const connectSkillSSE = (
  executionId: string,
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
    source = new EventSource(`/api/skill/${encodeURIComponent(executionId)}/progress`)

    source.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data) as API.SkillProgressEvent
        options.onMessage(message)
        if (message.type === 'skill.complete' || message.type === 'skill.error') {
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
