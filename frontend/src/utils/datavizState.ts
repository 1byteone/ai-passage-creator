/** 报告产物地址：与后端 context-path /api 对齐，供 iframe 与下载按钮直连 */
export type ArtifactKind = 'html' | 'png'

export const buildArtifactUrl = (executionId: string, kind: ArtifactKind) =>
  `/api/dataviz/${encodeURIComponent(executionId)}/${kind}`

export interface ArtifactState {
  htmlReady: boolean
  pngReady: boolean
}

/** 轮询约 60 秒仍无 HTML 即判超时，避免无限转圈 */
export const MAX_POLL_ATTEMPTS = 40
export const POLL_INTERVAL_MS = 1500

export const shouldStopPolling = (state: ArtifactState) => state.htmlReady

export const isPollExhausted = (attempts: number) => attempts >= MAX_POLL_ATTEMPTS

/** 缺 executionId 时无法索引产物，直接给出可读原因 */
export const missingExecutionIdMessage = '缺少执行编号，无法索引报告产物'
