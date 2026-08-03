<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useHandwritingStore } from '@/stores/handwritingStore'
import FontSelector from './components/FontSelector.vue'
import PaperSelector from './components/PaperSelector.vue'
import ParamSliders from './components/ParamSliders.vue'
import PreviewPanel from './components/PreviewPanel.vue'
import { message } from 'ant-design-vue'

const store = useHandwritingStore()
const content = ref('')
const selectedFont = ref('shoushu')
const selectedPaper = ref('line')

onMounted(async () => {
  await Promise.all([store.loadFonts(), store.loadPapers()])
})

async function handlePreview() {
  if (!content.value.trim()) {
    message.warning('请输入文字内容')
    return
  }
  try {
    await store.doPreview(content.value, selectedFont.value, selectedPaper.value)
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '未知错误'
    message.error('预览失败: ' + msg)
  }
}

async function handleExport() {
  if (!content.value.trim()) {
    message.warning('请输入文字内容')
    return
  }
  try {
    const result = await store.doExport(content.value, selectedFont.value, selectedPaper.value)
    if (result) {
      message.success('导出任务已创建，请等待渲染完成')
    }
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '未知错误'
    message.error('导出失败: ' + msg)
  }
}
</script>

<template>
  <div class="handwriting-editor">
    <h1 style="margin-bottom: 24px">手写笔记编辑器</h1>

    <div class="config-bar">
      <FontSelector v-model="selectedFont" :fonts="store.fonts" />
      <PaperSelector v-model="selectedPaper" :papers="store.papers" />
      <ParamSliders v-model="store.params" />
    </div>

    <div class="editor-body">
      <div class="text-panel">
        <textarea
          v-model="content"
          placeholder="在此输入或粘贴文字内容..."
          rows="20"
        />
      </div>
      <div class="preview-panel">
        <PreviewPanel
          :preview-url="store.previewUrl"
          :previewing="store.previewing"
        />
      </div>
    </div>

    <div class="action-bar">
      <a-button type="default" @click="handlePreview" :loading="store.previewing">
        预览
      </a-button>
      <a-button type="primary" @click="handleExport" :loading="store.exporting">
        导出 PNG
      </a-button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.handwriting-editor {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
}
.config-bar {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
  align-items: center;
  flex-wrap: wrap;
}
.editor-body {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  min-height: 500px;
}
.text-panel textarea {
  width: 100%;
  height: 100%;
  min-height: 400px;
  padding: 16px;
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  font-size: 16px;
  line-height: 1.8;
  resize: vertical;
}
.preview-panel {
  border: 1px solid #d9d9d9;
  border-radius: 8px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}
.action-bar {
  display: flex;
  gap: 12px;
  margin-top: 24px;
  justify-content: flex-end;
}
</style>
