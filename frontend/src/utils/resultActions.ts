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
  if (!content) {
    message.warning('内容为空，无法下载')
    return
  }
  const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.style.display = 'none'
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  // 延迟释放，防止浏览器在下载启动前撤销 blob URL
  setTimeout(() => {
    document.body.removeChild(anchor)
    URL.revokeObjectURL(url)
  }, 100)
  message.success(successText)
}
