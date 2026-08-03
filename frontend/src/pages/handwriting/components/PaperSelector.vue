<script setup lang="ts">
import type { PaperType } from '@/api/handwritingController'

defineProps<{ papers: PaperType[]; modelValue: string }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()
</script>

<template>
  <div class="paper-selector">
    <label style="margin-right:8px">纸张</label>
    <a-select
      :value="modelValue"
      @change="(v: string) => emit('update:modelValue', v)"
      style="width: 140px"
    >
      <a-select-option v-for="p in papers" :key="p.key" :value="p.key">
        {{ p.name }}
      </a-select-option>
    </a-select>
  </div>
</template>
