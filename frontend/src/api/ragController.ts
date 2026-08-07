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

/** 查询文章创作时的 RAG 参考溯源 GET /rag/references/{taskId} */
export async function getRagReferences(taskId: string) {
  return request<API.BaseResponseListRagReference>(`/rag/references/${encodeURIComponent(taskId)}`, {
    method: 'GET',
  })
}