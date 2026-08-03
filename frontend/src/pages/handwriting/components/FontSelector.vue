<script setup lang="ts">
import type { HandwritingFont } from '@/api/handwritingController'

defineProps<{ fonts: HandwritingFont[]; modelValue: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()
</script>

<template>
  <div class="font-selector">
    <label style="margin-right:8px">字体</label>
    <a-select
      :value="modelValue"
      @change="(v: string) => emit('update:modelValue', v)"
      style="width: 180px"
    >
      <a-select-option v-for="f in fonts" :key="f.key" :value="f.key">
        {{ f.name }}
      </a-select-option>
    </a-select>
  </div>
</template>
