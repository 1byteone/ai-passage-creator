<template>
  <section class="skill-execute-page" aria-label="AI 技能执行工作台">
    <div class="execute-shell">
      <button class="back-link" type="button" @click="router.push('/skill')">
        <ArrowLeftOutlined aria-hidden="true" />
        返回技能中心
      </button>
      <SkillExecuteSurface
        ref="surfaceRef"
        :key="skillName"
        :skill-name="skillName"
        :restore-execution-id="restoreExecutionId"
        @execution-change="syncExecutionId"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { Modal } from 'ant-design-vue'
import { ArrowLeftOutlined } from '@ant-design/icons-vue'
import SkillExecuteSurface from './components/SkillExecuteSurface.vue'

const route = useRoute()
const router = useRouter()
const surfaceRef = ref<InstanceType<typeof SkillExecuteSurface> | null>(null)

const skillName = computed(() => String(route.params.skillName || ''))
const restoreExecutionId = computed(() => String(route.query.executionId || ''))

const syncExecutionId = (executionId: string) => {
  const query = {
    ...route.query,
  }
  if (executionId) {
    query.executionId = executionId
  } else {
    delete query.executionId
  }
  router.replace({
    query,
  })
}

onBeforeRouteLeave(() => {
  if (!surfaceRef.value?.isExecuting) return true
  return new Promise<boolean>((resolve) => {
    Modal.confirm({
      title: '任务仍在执行',
      content: '离开后任务会继续在后台运行，可通过当前执行链接恢复。',
      okText: '继续离开',
      cancelText: '留在页面',
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    })
  })
})
</script>

<style scoped>
.skill-execute-page {
  min-height: calc(100dvh - 64px);
  padding: 30px 20px 72px;
  background: var(--color-background-secondary);
}

.execute-shell {
  max-width: 1040px;
  margin: 0 auto;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 40px;
  margin-bottom: 18px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-text-secondary);
  cursor: pointer;
}

.back-link:hover,
.back-link:focus-visible {
  color: var(--color-primary-dark);
}

@media (max-width: 600px) {
  .skill-execute-page {
    padding: 22px 14px 56px;
  }
}
</style>
