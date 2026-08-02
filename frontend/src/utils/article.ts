/**
 * 文章相关工具函数
 */
import { STATUS_TEXT_MAP, STATUS_TAG_COLOR_MAP, STATUS_COLOR_MAP } from '@/constants/article'

/**
 * 获取状态文本
 * @param status 状态值
 */
export const getStatusText = (status: string): string => {
  return STATUS_TEXT_MAP[status] || status
}

/**
 * 获取状态标签颜色（用于 Ant Design Tag）
 * @param status 状态值
 */
export const getStatusTagColor = (status: string): string => {
  return STATUS_TAG_COLOR_MAP[status] || 'default'
}

/**
 * 获取状态颜色（用于自定义样式）
 * @param status 状态值
 */
export const getStatusColor = (status: string): string => {
  return STATUS_COLOR_MAP[status] || '#999'
}

/**
 * 导出文章为 Markdown 文件
 * @param title 文章标题
 * @param subTitle 副标题
 * @param content 正文内容
 * @param fullContent 完整图文内容（可选）
 * @param outline 大纲（可选）
 * @param images 配图列表（可选）
 */
export interface ExportArticleOptions {
  title: string
  subTitle?: string
  content?: string
  fullContent?: string
  outline?: Array<{ section: number; title: string }>
  images?: Array<{ description: string; url: string }>
}

export const exportAsMarkdown = (options: ExportArticleOptions): void => {
  const { title, subTitle, content, fullContent, outline, images } = options

  let markdown = `# ${title}\n\n`
  if (subTitle) {
    markdown += `> ${subTitle}\n\n`
  }

  // 优先使用完整图文
  if (fullContent) {
    markdown += fullContent
  } else {
    if (outline && outline.length > 0) {
      markdown += `## 目录\n\n`
      outline.forEach((item) => {
        markdown += `${item.section}. ${item.title}\n`
      })
      markdown += `\n---\n\n`
    }

    markdown += content || ''

    if (images && images.length > 0) {
      markdown += `\n\n## 配图\n\n`
      images.forEach((image) => {
        markdown += `![${image.description}](${image.url})\n\n`
      })
    }
  }

  const blob = new Blob([markdown], { type: 'text/markdown' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.style.display = 'none'
  a.href = url
  // 清理文件名中的非法字符
  const safeTitle = (title || '文章').replace(/[\\/:*?"<>|]/g, '_')
  a.download = `${safeTitle}.md`
  document.body.appendChild(a)
  a.click()
  // 延迟释放，防止浏览器在下载启动前撤销 blob URL
  setTimeout(() => {
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }, 100)
}
