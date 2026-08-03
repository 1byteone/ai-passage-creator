// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 我的创作分析 GET /analytics/mine */
export async function getMyAnalytics() {
  return request<API.BaseResponseAnalyticsVO>('/analytics/mine', { method: 'GET' })
}

/** 全站内容分析 GET /analytics/content */
export async function getContentAnalytics() {
  return request<API.BaseResponseAnalyticsVO>('/analytics/content', { method: 'GET' })
}

/** 指定用户分析 GET /analytics/user */
export async function getUserAnalytics(userId: number) {
  return request<API.BaseResponseAnalyticsVO>('/analytics/user', {
    method: 'GET',
    params: { userId },
  })
}