<template>
  <form class="skill-form" novalidate @submit.prevent="handleSubmit">
    <div v-for="[fieldName, rawDefinition] in fields" :key="fieldName" class="field-group">
      <label class="field-label" :for="fieldName">
        {{ fieldDefinition(fieldName, rawDefinition).description || fieldName }}
        <span v-if="fieldDefinition(fieldName, rawDefinition).required" aria-hidden="true">*</span>
      </label>

      <a-textarea
        v-if="fieldDefinition(fieldName, rawDefinition).uiType === 'textarea'"
        :id="fieldName"
        :ref="(element: unknown) => setFieldRef(fieldName, element)"
        :value="stringValue(fieldName)"
        :placeholder="fieldDefinition(fieldName, rawDefinition).placeholder"
        :auto-size="{ minRows: fieldName === 'articleContent' ? 9 : 5, maxRows: 16 }"
        :status="errors[fieldName] ? 'error' : undefined"
        :disabled="loading"
        show-count
        @update:value="(value: string) => updateValue(fieldName, value)"
        @blur="validateField(fieldName, rawDefinition)"
      />

      <a-select
        v-else-if="fieldDefinition(fieldName, rawDefinition).uiType === 'select'"
        :id="fieldName"
        :ref="(element: unknown) => setFieldRef(fieldName, element)"
        :value="modelValue[fieldName]"
        :options="fieldDefinition(fieldName, rawDefinition).options"
        :placeholder="fieldDefinition(fieldName, rawDefinition).placeholder"
        :status="errors[fieldName] ? 'error' : undefined"
        :disabled="loading"
        class="field-control"
        @change="(value: unknown) => updateValue(fieldName, value)"
        @blur="validateField(fieldName, rawDefinition)"
      />

      <a-radio-group
        v-else-if="fieldDefinition(fieldName, rawDefinition).uiType === 'radio'"
        :value="modelValue[fieldName]"
        :disabled="loading"
        class="radio-control"
        @change="handleRadioChange(fieldName, $event)"
      >
        <a-radio
          v-for="option in fieldDefinition(fieldName, rawDefinition).options || []"
          :key="String(option.value)"
          :value="option.value"
        >
          {{ option.label }}
        </a-radio>
      </a-radio-group>

      <a-input
        v-else
        :id="fieldName"
        :ref="(element: unknown) => setFieldRef(fieldName, element)"
        :value="stringValue(fieldName)"
        :placeholder="fieldDefinition(fieldName, rawDefinition).placeholder"
        :status="errors[fieldName] ? 'error' : undefined"
        :disabled="loading"
        @update:value="(value: string) => updateValue(fieldName, value)"
        @blur="validateField(fieldName, rawDefinition)"
      />

      <div class="field-meta">
        <span v-if="errors[fieldName]" class="field-error" role="alert">
          {{ errors[fieldName] }}
        </span>
        <span v-else class="field-help">
          {{ helperText(fieldName, rawDefinition) }}
        </span>
        <span
          v-if="fieldDefinition(fieldName, rawDefinition).maxLength"
          class="character-count"
          :class="{ warning: exceedsRecommendedLength(fieldName, rawDefinition) }"
        >
          {{ stringValue(fieldName).length }} /
          {{ fieldDefinition(fieldName, rawDefinition).maxLength }} 建议
        </span>
      </div>
    </div>

    <a-button
      type="primary"
      html-type="submit"
      size="large"
      :loading="loading"
      class="execute-button"
    >
      <template #icon>
        <PlayCircleOutlined />
      </template>
      {{ actionLabel }}
    </a-button>
  </form>
</template>

<script setup lang="ts">
import { computed, reactive } from 'vue'
import { PlayCircleOutlined } from '@ant-design/icons-vue'
import { getFieldDefinition } from '@/config/skill'

const props = defineProps<{
  definition: API.SkillDefinition
  skillName: string
  modelValue: Record<string, unknown>
  actionLabel: string
  loading?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, unknown>]
  submit: []
}>()

const errors = reactive<Record<string, string>>({})
const fieldRefs = new Map<string, { focus?: () => void }>()

const fields = computed(() => Object.entries(props.definition.variables || {}))

const fieldDefinition = (fieldName: string, raw?: API.SkillVariableDef) =>
  getFieldDefinition(props.skillName, fieldName, raw)

const stringValue = (fieldName: string) => String(props.modelValue[fieldName] ?? '')

const setFieldRef = (fieldName: string, element: unknown) => {
  if (element && typeof element === 'object') {
    fieldRefs.set(fieldName, element as { focus?: () => void })
  }
}

const updateValue = (fieldName: string, value: unknown) => {
  emit('update:modelValue', {
    ...props.modelValue,
    [fieldName]: value,
  })
  if (errors[fieldName]) {
    delete errors[fieldName]
  }
}

const handleRadioChange = (fieldName: string, event: { target: { value: unknown } }) => {
  updateValue(fieldName, event.target.value)
}

const validateField = (fieldName: string, raw?: API.SkillVariableDef): boolean => {
  const definition = fieldDefinition(fieldName, raw)
  const value = props.modelValue[fieldName]
  if (definition.required && (value === undefined || value === null || String(value).trim() === '')) {
    errors[fieldName] = `请输入${definition.description || fieldName}`
    return false
  }
  delete errors[fieldName]
  return true
}

const helperText = (fieldName: string, raw?: API.SkillVariableDef) => {
  const definition = fieldDefinition(fieldName, raw)
  if (fieldName === 'articleContent') {
    return '支持直接粘贴长文，长度限制以服务端实际能力为准。'
  }
  if (!definition.required) {
    return '可选，不填写时使用推荐默认值。'
  }
  return ''
}

const exceedsRecommendedLength = (fieldName: string, raw?: API.SkillVariableDef) => {
  const maxLength = fieldDefinition(fieldName, raw).maxLength
  return Boolean(maxLength && stringValue(fieldName).length > maxLength)
}

const handleSubmit = () => {
  const firstInvalid = fields.value.find(
    ([fieldName, definition]) => !validateField(fieldName, definition),
  )
  if (firstInvalid) {
    fieldRefs.get(firstInvalid[0])?.focus?.()
    return
  }
  emit('submit')
}
</script>

<style scoped>
.skill-form {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.field-group {
  min-width: 0;
}

.field-label {
  display: inline-flex;
  gap: 4px;
  margin-bottom: 8px;
  color: var(--color-text);
  font-size: 14px;
  font-weight: 600;
}

.field-label span {
  color: var(--color-error);
}

.field-control {
  width: 100%;
}

.radio-control {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.field-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  min-height: 22px;
  padding-top: 6px;
  font-size: 12px;
}

.field-error {
  color: #b42318;
}

.field-help,
.character-count {
  color: var(--color-text-muted);
}

.character-count {
  margin-left: auto;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.character-count.warning {
  color: #b54708;
}

.execute-button {
  align-self: flex-start;
  min-width: 160px;
  height: 46px;
  font-weight: 600;
}

@media (max-width: 600px) {
  .field-meta {
    flex-direction: column;
    gap: 2px;
  }

  .character-count {
    margin-left: 0;
  }

  .execute-button {
    width: 100%;
  }
}
</style>
