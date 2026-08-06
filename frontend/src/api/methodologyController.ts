// @ts-ignore
/* eslint-disable */
import request from '@/request'

/** 获取方法论模板列表 GET /methodology/list */
export async function getMethodologyList() {
  return request<API.BaseResponseListMethodologyVO>('/methodology/list', {
    method: 'GET',
  })
}
