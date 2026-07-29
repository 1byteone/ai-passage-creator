import { message } from 'ant-design-vue'

export const copyResultText = async (content: string, successText = '结果已复制') => {
  try {
    await navigator.clipboard.writeText(content)
    message.success(successText)
  } catch {
    message.error('复制失败，请手动选择文本')
  }
}

export const downloadResultText = (
  content: string,
  filename: string,
  successText = '文件已开始下载',
) => {
  const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
  message.success(successText)
}
