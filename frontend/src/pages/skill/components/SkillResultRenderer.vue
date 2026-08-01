<template>
  <SkillResultProofreading
    v-if="skillName === 'proofreading'"
    :inputs="inputs"
    :output-data="outputData"
  />
  <SkillResultTopicGen
    v-else-if="skillName === 'topic-gen'"
    :output-data="outputData"
    :embedded="embedded"
    @select="(option) => emit('selectTopic', option)"
  />
  <SkillResultArticleToX
    v-else-if="skillName === 'article-to-x'"
    :inputs="inputs"
    :output-data="outputData"
  />
  <SkillResultResearch
    v-else-if="skillName === 'research'"
    :inputs="inputs"
    :output-data="outputData"
  />
  <SkillResultDefault v-else :output-data="outputData" />
</template>

<script setup lang="ts">
import SkillResultArticleToX from './SkillResultArticleToX.vue'
import SkillResultDefault from './SkillResultDefault.vue'
import SkillResultProofreading from './SkillResultProofreading.vue'
import SkillResultResearch from './SkillResultResearch.vue'
import SkillResultTopicGen from './SkillResultTopicGen.vue'

defineProps<{
  skillName: string
  inputs: Record<string, unknown>
  outputData: Record<string, unknown>
  embedded?: boolean
}>()

const emit = defineEmits<{
  selectTopic: [option: API.TopicOption]
}>()
</script>
