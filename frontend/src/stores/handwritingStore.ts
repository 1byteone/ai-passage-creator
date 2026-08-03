import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  listFonts,
  listPapers,
  preview,
  exportPng,
  type HandwritingFont,
  type PaperType,
  type HandwritingRequest,
  type HandwritingParams,
} from '@/api/handwritingController'

export const useHandwritingStore = defineStore('handwriting', () => {
  const fonts = ref<HandwritingFont[]>([])
  const papers = ref<PaperType[]>([])
  const previewUrl = ref<string | null>(null)
  const previewing = ref(false)
  const exporting = ref(false)
  const params = ref<HandwritingParams>({
    positionJitter: 2.0,
    rotationJitter: 1.5,
    sizeJitter: 5.0,
    inkDensity: 0.85,
  })

  async function loadFonts() {
    const res = await listFonts()
    fonts.value = res.data ?? []
  }

  async function loadPapers() {
    const res = await listPapers()
    papers.value = res.data ?? []
  }

  async function doPreview(content: string, fontName: string, paperType: string) {
    previewing.value = true
    try {
      const req: HandwritingRequest = {
        content,
        fontName,
        paperType,
        params: params.value,
        paperImageUrl: null,
      }
      const res = await preview(req)
      previewUrl.value = res.data ?? null
      return res.data
    } finally {
      previewing.value = false
    }
  }

  async function doExport(content: string, fontName: string, paperType: string) {
    exporting.value = true
    try {
      const req: HandwritingRequest = {
        content,
        fontName,
        paperType,
        params: params.value,
        paperImageUrl: null,
      }
      const res = await exportPng(req)
      return res.data
    } finally {
      exporting.value = false
    }
  }

  function resetParams() {
    params.value = {
      positionJitter: 2.0,
      rotationJitter: 1.5,
      sizeJitter: 5.0,
      inkDensity: 0.85,
    }
  }

  return {
    fonts, papers, previewUrl, previewing, exporting, params,
    loadFonts, loadPapers, doPreview, doExport, resetParams,
  }
})
