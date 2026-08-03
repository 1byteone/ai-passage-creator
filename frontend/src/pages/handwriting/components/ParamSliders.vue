<script setup lang="ts">
import type { HandwritingParams } from '@/api/handwritingController'

const props = defineProps<{ modelValue: HandwritingParams }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: HandwritingParams): void }>()

function emitUpdate() {
  emit('update:modelValue', { ...props.modelValue })
}
</script>

<template>
  <div class="param-sliders" style="display:flex;gap:16px;align-items:center;flex-wrap:wrap">
    <div>
      <span style="font-size:12px;color:#666">位置扰动</span>
      <a-slider
        :min="0" :max="10" :step="0.5"
        :value="modelValue.positionJitter"
        @change="(v: number) => { modelValue.positionJitter = v; emitUpdate() }"
        style="width:120px"
      />
    </div>
    <div>
      <span style="font-size:12px;color:#666">旋转</span>
      <a-slider
        :min="0" :max="5" :step="0.5"
        :value="modelValue.rotationJitter"
        @change="(v: number) => { modelValue.rotationJitter = v; emitUpdate() }"
        style="width:120px"
      />
    </div>
    <div>
      <span style="font-size:12px;color:#666">字号变化</span>
      <a-slider
        :min="0" :max="20" :step="1"
        :value="modelValue.sizeJitter"
        @change="(v: number) => { modelValue.sizeJitter = v; emitUpdate() }"
        style="width:120px"
      />
    </div>
    <div>
      <span style="font-size:12px;color:#666">墨迹浓淡</span>
      <a-slider
        :min="0" :max="1" :step="0.05"
        :value="modelValue.inkDensity"
        @change="(v: number) => { modelValue.inkDensity = v; emitUpdate() }"
        style="width:120px"
      />
    </div>
  </div>
</template>
