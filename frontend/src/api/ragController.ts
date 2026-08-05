// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 向量语义检索 POST /rag/search */
export async function searchRag(params: { query: string; type?: string; topK?: number }) {
  return request<API.BaseResponseListRagHit>('/rag/search', {
    method: 'POST',
    data: params,
  })
}