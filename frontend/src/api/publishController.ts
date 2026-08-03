// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 创建发布排期 POST /publish/schedule */
export async function schedulePublish(params: API.PublishScheduleRequest) {
  return request<API.BaseResponsePublishSchedule>('/publish/schedule', {
    method: 'POST',
    data: params,
  })
}

/** 取消发布排期 POST /publish/cancel/{scheduleId} */
export async function cancelPublish(scheduleId: number) {
  return request<API.BaseResponseBoolean>(`/publish/cancel/${scheduleId}`, {
    method: 'POST',
  })
}

/** 文章排期列表 GET /publish/article/{taskId} */
export async function getArticlePublish(taskId: string) {
  return request<API.BaseResponseListPublishSchedule>(
    `/publish/article/${encodeURIComponent(taskId)}`,
    { method: 'GET' },
  )
}