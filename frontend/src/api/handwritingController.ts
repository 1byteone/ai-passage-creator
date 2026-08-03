// @ts-ignore
/* eslint-disable */
import request from '@/request'

export interface HandwritingFont {
  name: string
  key: string
  previewText: string
}

export interface PaperType {
  key: string
  name: string
}

export interface HandwritingParams {
  positionJitter: number
  rotationJitter: number
  sizeJitter: number
  inkDensity: number
}

export interface HandwritingRequest {
  content: string
  fontName: string
  paperType: string
  params: HandwritingParams | null
  paperImageUrl: string | null
}

export interface HandwritingExportVO {
  taskId: string
  progressUrl: string
}

/** 获取可用手写字体列表 GET /api/handwriting/fonts */
export async function listFonts() {
  return request<any>('/handwriting/fonts', {
    method: 'GET',
  })
}

/** 获取纸张类型列表 GET /api/handwriting/papers */
export async function listPapers() {
  return request<any>('/handwriting/papers', {
    method: 'GET',
  })
}

/** 手写效果预览 POST /api/handwriting/preview */
export async function preview(data: HandwritingRequest) {
  return request<any>('/handwriting/preview', {
    method: 'POST',
    data,
  })
}

/** PNG 导出 POST /api/handwriting/export/png */
export async function exportPng(data: HandwritingRequest) {
  return request<any>('/handwriting/export/png', {
    method: 'POST',
    data,
  })
}

/** PDF 导出 POST /api/handwriting/export/pdf */
export async function exportPdf(data: HandwritingRequest) {
  return request<any>('/handwriting/export/pdf', {
    method: 'POST',
    data,
  })
}
