import request from '@/request'

/** 上传图片 → COS URL */
export async function uploadImage(file: File): Promise<string> {
  const form = new FormData()
  form.append('file', file)
  const res = await request.post<API.BaseResponseString>('/file/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  if (res.data.code === 0 && res.data.data) return res.data.data
  throw new Error(res.data.message || '上传失败')
}

/** 我的手帐档案列表 */
export async function listComicBooks() {
  return request.get<API.BaseResponseListComicBookPo>('/comic/books')
}

/** 档案月册列表（按 year_month 倒序，[0] 为最新） */
export async function listComicMonths(bookId: number) {
  return request.get<API.BaseResponseListComicMonthlyVolumePo>(`/comic/books/${bookId}/months`)
}

/** 月册的章节列表 */
export async function getComicEpisodesByMonth(bookId: number, yearMonth: string) {
  const res = await request.get<API.BaseResponseListComicEpisodePo>(
    `/comic/books/${bookId}/months/${yearMonth}/episodes`,
  )
  return res.data.data ?? []
}

/** 章节详情（含 pageHtml/pngUrl） */
export async function getComicEpisode(episodeId: number) {
  return request.get<API.BaseResponseComicEpisodePo>(`/comic/episodes/${episodeId}`)
}
