/**
 * 操作反馈通知（页面级 Alert 提示）
 *
 * 统一各页面本地重复的 OperationNotice 接口。
 * description 为可选，兼容部分页面不传详情文案的用法。
 */
export interface OperationNotice {
  type: 'success' | 'error'
  message: string
  description?: string
}
