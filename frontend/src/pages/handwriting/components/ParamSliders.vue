<script setup lang="ts">
import { reactive, watch } from 'vue'
import type { HandwritingParams } from '@/api/handwritingController'

const props = defineProps<{ modelValue: HandwritingParams }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: HandwritingParams): void }>()

const local = reactive<HandwritingParams>({ ...props.modelValue })

watch(() => props.modelValue, (v) => {
  Object.assign(local, v)
}, { deep: true })

function emitUpdate() {
  emit('update:modelValue', { ...local })
}
</script>

<template>
  <div class="param-sliders" style="display:flex;gap:16px;align-items:center;flex-wrap:wrap">
    <div>
      <span style="font-size:12px;color:#666">位置扰动</span>
      <a-slider
        :min="0" :max="10" :step="0.5"
        v-model:value="local.positionJitter"
        @change="emitUpdate"
        style="width:120px"
      />
    </div>
    <div>
      <span style="font-size:12px;color:#666">旋转</span>
      <a-slider
        :min="0" :max="5" :step="0.5"
        v-model:value="local.rotationJitter"
        @change="emitUpdate"
        style="width:120px"
      />
    </div>
    <div>
      <span style="font-size:12px;color:#666">字号变化</span>
      <a-slider
        :min="0" :max="20" :step="1"
        v-model:value="local.sizeJitter"
        @change="emitUpdate"
        style="width:120px"
      />
    </div>
    <div>
      <span style="font-size:12px;color:#666">墨迹浓淡</span>
      <a-slider
        :min="0" :max="1" :step="0.05"
        v-model:value="local.inkDensity"
        @change="emitUpdate"
        style="width:120px"
      />
    </div>
  </div>
</template>
