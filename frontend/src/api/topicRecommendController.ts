// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 获取推荐选题（热门/历史/AI 混合） GET /topic/recommend */
export async function getRecommendedTopics(
  params: { refresh?: boolean },
  options?: { [key: string]: any }
) {
  return request<API.BaseResponseTopicRecommendVO>('/topic/recommend', {
    method: 'GET',
    params: { ...params },
    ...(options || {}),
  })
}
