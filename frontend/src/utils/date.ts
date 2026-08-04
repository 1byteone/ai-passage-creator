/**
 * 日期工具函数
 */
import dayjs from 'dayjs'

/**
 * 格式化日期
 * @param date 日期字符串或时间戳
 * @param format 格式化模板，默认 'YYYY-MM-DD HH:mm'
 */
export const formatDate = (date: string | number, format = 'YYYY-MM-DD HH:mm'): string => {
  return dayjs(date).format(format)
}

/**
 * 格式化日期（带空值/非法值兜底）
 *
 * 统一各页面本地 formatTime 的重复实现：
 * 值为空或非法时返回 fallback（默认 '—'），否则按 format 格式化。
 * @param value 日期字符串或时间戳，可空
 * @param format 格式化模板，默认 'YYYY-MM-DD HH:mm'
 * @param fallback 空值/非法值兜底文案，默认 '—'
 */
export const formatDateTime = (
  value?: string | number | null,
  format = 'YYYY-MM-DD HH:mm',
  fallback = '—',
): string => {
  if (!value || !dayjs(value).isValid()) return fallback
  return dayjs(value).format(format)
}

/**
 * 格式化日期（短格式）
 * @param date 日期字符串或时间戳
 */
export const formatDateShort = (date: string | number): string => {
  return formatDate(date, 'MM-DD HH:mm')
}

/**
 * 格式化日期（完整格式，含秒）
 * @param date 日期字符串或时间戳
 */
export const formatDateFull = (date: string | number): string => {
  return formatDate(date, 'YYYY-MM-DD HH:mm:ss')
}

/**
 * 格式化耗时（毫秒）
 * 统一展示：<1s 显示 ms，<60s 显示秒，否则显示分+秒；0/null 显示 '—'
 */
export const formatDuration = (ms?: number): string => {
  if (ms == null || ms === 0) return '—'
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  return `${Math.floor(ms / 60000)}m ${Math.round((ms % 60000) / 1000)}s`
}
