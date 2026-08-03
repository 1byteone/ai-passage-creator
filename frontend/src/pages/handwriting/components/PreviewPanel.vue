<script setup lang="ts">
defineProps<{ previewUrl: string | null; previewing: boolean }>()
</script>

<template>
  <div class="preview-panel-inner" style="width:100%;height:100%;min-height:400px;display:flex;align-items:center;justify-content:center">
    <a-spin v-if="previewing" tip="渲染中..." />
    <img v-else-if="previewUrl" :src="previewUrl" style="max-width:100%;max-height:100%" alt="手写预览" />
    <span v-else style="color:#999">点击「预览」查看手写效果</span>
  </div>
</template>
