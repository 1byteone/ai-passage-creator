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

/** 知识库文档列表（admin）GET /rag/documents */
export async function listRagDocuments(params: { pageNum?: number; pageSize?: number; keyword?: string }) {
  return request<API.BaseResponsePageRagDocument>('/rag/documents', { method: 'GET', params })
}

/** 删除知识库文档（admin）DELETE /rag/document/{id} */
export async function deleteRagDocument(id: number) {
  return request<API.BaseResponseBoolean>(`/rag/document/${id}`, { method: 'DELETE' })
}

/** 上传知识库文档（admin）POST /rag/document */
export async function uploadRagDocument(data: { title: string; source: string; text: string }) {
  return request<API.BaseResponseBoolean>('/rag/document', { method: 'POST', data })
}

/** 审核手工文档 POST /rag/document/{id}/approve */
export async function approveRagDocument(id: number) {
  return request<API.BaseResponseBoolean>(`/rag/document/${id}/approve`, { method: 'POST' })
}

/** 同步当前项目 Git 文档 POST /rag/knowledge/sync */
export async function syncRagKnowledge() {
  return request<API.BaseResponseRagSyncJob>('/rag/knowledge/sync', { method: 'POST' })
}

/** 查询当前项目 Git 文档同步任务 GET /rag/knowledge/sync/{id} */
export async function getRagSyncJob(id: number) {
  return request<API.BaseResponseRagSyncJob>(`/rag/knowledge/sync/${id}`, { method: 'GET' })
}

/** 当前项目研发知识库只读混合检索 POST /rag/knowledge/search */
export async function searchRagKnowledge(params: { query: string; topK?: number }) {
  return request<API.BaseResponseListKnowledgeHit>('/rag/knowledge/search', { method: 'POST', data: params })
}
