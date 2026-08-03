// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 卡片预览（前 2 页，不扣配额） POST /article/cards/preview */
export async function previewCards(params: API.CardGenerateRequest) {
  return request<API.BaseResponseListString>('/article/cards/preview', {
    method: 'POST',
    data: params,
  })
}

/** 卡片生成（异步，扣配额） POST /article/cards/generate */
export async function generateCards(params: API.CardGenerateRequest) {
  return request<API.BaseResponseCardGenerateResponse>('/article/cards/generate', {
    method: 'POST',
    data: params,
  })
}

/** 查询已生成卡片列表 GET /article/cards/{taskId} */
export async function getCards(taskId: string) {
  return request<API.BaseResponseListCardPage>(`/article/cards/${encodeURIComponent(taskId)}`, {
    method: 'GET',
  })
}
