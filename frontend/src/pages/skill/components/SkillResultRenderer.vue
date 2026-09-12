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
  <SkillResultComicJournal v-else-if="skillName === 'comic-journal'" :output-data="outputData" />
  <SkillResultDataViz
    v-else-if="skillName === 'data-visualization-report'"
    :execution-id="executionId"
  />
  <SkillResultDefault v-else :output-data="outputData" />
</template>

<script setup lang="ts">
import SkillResultArticleToX from './SkillResultArticleToX.vue'
import SkillResultComicJournal from './SkillResultComicJournal.vue'
import SkillResultDataViz from './SkillResultDataViz.vue'
import SkillResultDefault from './SkillResultDefault.vue'
import SkillResultProofreading from './SkillResultProofreading.vue'
import SkillResultResearch from './SkillResultResearch.vue'
import SkillResultTopicGen from './SkillResultTopicGen.vue'

defineProps<{
  skillName: string
  inputs: Record<string, unknown>
  outputData: Record<string, unknown>
  /** 产物类 skill（如 dataviz）需要它去索引后端落盘的报告 */
  executionId?: string
  embedded?: boolean
}>()

const emit = defineEmits<{
  selectTopic: [option: API.TopicOption]
}>()
</script>
