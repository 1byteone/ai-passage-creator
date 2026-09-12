// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 报告产物就绪状态 GET /dataviz/{executionId}/artifact */
export async function getDataVizArtifact(executionId: string) {
  return request<API.BaseResponseDataVizArtifactVO>(
    `/dataviz/${encodeURIComponent(executionId)}/artifact`,
    { method: 'GET' },
  )
}
