// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 提交审批 POST /approval/submit */
export async function submitForApproval(params: { taskId: string }) {
  return request<API.BaseResponseApprovalRecord>('/approval/submit', {
    method: 'POST',
    data: params,
  })
}

/** 审批通过 POST /approval/approve */
export async function approveArticle(params: { taskId: string; comment?: string }) {
  return request<API.BaseResponseApprovalRecord>('/approval/approve', {
    method: 'POST',
    data: params,
  })
}

/** 驳回审批 POST /approval/reject */
export async function rejectArticle(params: { taskId: string; comment?: string }) {
  return request<API.BaseResponseApprovalRecord>('/approval/reject', {
    method: 'POST',
    data: params,
  })
}

/** 查询审批历史 GET /approval/history/{taskId} */
export async function getApprovalHistory(taskId: string) {
  return request<API.BaseResponseListApprovalRecord>(
    `/approval/history/${encodeURIComponent(taskId)}`,
    { method: 'GET' },
  )
}
