/**
 * Markdown 工具函数
 */
import { marked } from 'marked'
import DOMPurify from 'dompurify'

/**
 * 将 Markdown 转换为安全的 HTML（已清洗 XSS）
 * @param markdown Markdown 内容
 */
export const markdownToHtml = (markdown: string): string => {
  return DOMPurify.sanitize(marked(markdown) as string)
}
