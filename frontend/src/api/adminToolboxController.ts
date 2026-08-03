// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 查询外部依赖熔断状态 GET /breaker/status */
export async function getBreakerStatus() {
  return request<API.BaseResponseBreakerStatus>('/breaker/status', { method: 'GET' })
}

/** 发送测试 Webhook POST /webhook/test */
export async function sendTestWebhook(params: { url: string }) {
  return request<API.BaseResponseBoolean>('/webhook/test', {
    method: 'POST',
    data: params,
  })
}
