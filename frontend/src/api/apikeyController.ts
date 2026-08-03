// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 创建 API Key POST /api-key */
export async function createApiKey(params: API.ApiKeyCreateRequest) {
  return request<API.BaseResponseApiKeyCreateVO>('/api-key', {
    method: 'POST',
    data: params,
  })
}

/** 查询 API Key 列表（脱敏） GET /api-key */
export async function listApiKeys(params?: {
  userId?: number
  pageNum?: number
  pageSize?: number
}) {
  return request<API.BaseResponsePageApiKeyVO>('/api-key', {
    method: 'GET',
    params,
  })
}

/** 吊销 API Key DELETE /api-key/{id} */
export async function revokeApiKey(id: number) {
  return request<API.BaseResponseBoolean>(`/api-key/${id}`, {
    method: 'DELETE',
  })
}
