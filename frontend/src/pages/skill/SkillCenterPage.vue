<template>
  <main class="skill-center">
    <div class="center-shell">
      <header class="center-heading">
        <div>
          <p>AI 工具</p>
          <h1>AI 技能中心</h1>
          <span>选择一个明确任务，提交素材并带走结构化结果。</span>
        </div>
        <a-segmented v-model:value="category" :options="categoryOptions" />
      </header>

      <div v-if="loading" class="skill-grid" aria-label="技能加载中">
        <a-skeleton
          v-for="index in 3"
          :key="index"
          active
          :paragraph="{ rows: 3 }"
          class="skill-skeleton"
        />
      </div>

      <a-result
        v-else-if="errorMessage"
        status="warning"
        title="技能列表暂时不可用"
        :sub-title="errorMessage"
      >
        <template #extra>
          <a-button type="primary" @click="loadSkills">刷新</a-button>
        </template>
      </a-result>

      <div v-else-if="visibleSkills.length" class="skill-grid">
        <RouterLink
          v-for="skill in visibleSkills"
          :key="skill.name"
          :to="`/skill/${skill.name}`"
          class="skill-card"
        >
          <div :class="['skill-icon', getSkillUiConfig(skill.name).accent]">
            <component :is="iconFor(skill.name)" />
          </div>
          <div class="skill-copy">
            <span>{{ getSkillUiConfig(skill.name).categoryLabel }}</span>
            <h2>{{ getSkillUiConfig(skill.name).title }}</h2>
            <p>{{ skill.description || getSkillUiConfig(skill.name).description }}</p>
          </div>
          <div class="skill-meta">
            <span>{{ skill.phases || 1 }} 个阶段</span>
            <span class="card-open-icon" aria-hidden="true">
              <ArrowRightOutlined />
            </span>
          </div>
        </RouterLink>
      </div>

      <a-empty v-else description="当前没有可用技能">
        <a-button type="primary" @click="loadSkills">刷新</a-button>
      </a-empty>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, type Component } from 'vue'
import {
  ArrowRightOutlined,
  BulbOutlined,
  FileDoneOutlined,
  ShareAltOutlined,
} from '@ant-design/icons-vue'
import { listSkills } from '@/api/skillController'
import { getSkillUiConfig, PUBLIC_SKILL_ORDER } from '@/config/skill'

const skills = ref<API.SkillSummary[]>([])
const loading = ref(true)
const errorMessage = ref('')
const category = ref('all')
const categoryOptions = [
  { label: '全部', value: 'all' },
  { label: '写作', value: 'writing' },
]

const visibleSkills = computed(() => {
  const ordered = [...skills.value].sort(
    (left, right) =>
      PUBLIC_SKILL_ORDER.indexOf(left.name as (typeof PUBLIC_SKILL_ORDER)[number]) -
      PUBLIC_SKILL_ORDER.indexOf(right.name as (typeof PUBLIC_SKILL_ORDER)[number]),
  )
  return category.value === 'all'
    ? ordered
    : ordered.filter((skill) => skill.category === category.value)
})

const iconFor = (skillName: string): Component => {
  const icons: Record<string, Component> = {
    'topic-gen': BulbOutlined,
    proofreading: FileDoneOutlined,
    'article-to-x': ShareAltOutlined,
  }
  return icons[skillName] || BulbOutlined
}

const loadSkills = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await listSkills()
    if (response.data.code !== 0) {
      throw new Error(response.data.message || '请求失败')
    }
    skills.value = response.data.data || []
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(loadSkills)
</script>

<style scoped>
.skill-center {
  min-height: calc(100dvh - 64px);
  padding: 42px 20px 72px;
  background: var(--color-background-secondary);
}

.center-shell {
  max-width: 1120px;
  margin: 0 auto;
}

.center-heading {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 28px;
}

.center-heading p {
  margin: 0 0 5px;
  color: var(--color-primary-dark);
  font-size: 13px;
  font-weight: 600;
}

.center-heading h1 {
  margin: 0 0 8px;
  color: var(--color-text);
  font-size: 30px;
}

.center-heading span {
  color: var(--color-text-secondary);
  font-size: 14px;
}

.skill-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.skill-card,
.skill-skeleton {
  min-height: 260px;
  padding: 22px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: white;
}

.skill-card {
  display: flex;
  flex-direction: column;
  color: inherit;
  cursor: pointer;
  text-decoration: none;
  transition:
    border-color var(--transition-fast),
    box-shadow var(--transition-fast),
    transform var(--transition-fast);
}

.skill-card:hover,
.skill-card:focus-visible {
  border-color: #86efac;
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.08);
  outline: none;
  transform: translateY(-2px);
}

.skill-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  margin-bottom: 24px;
  border-radius: var(--radius-md);
  font-size: 20px;
}

.skill-icon.blue {
  background: #eff6ff;
  color: #1d4ed8;
}

.skill-icon.green {
  background: #f0fdf4;
  color: #15803d;
}

.skill-icon.amber {
  background: #fffbeb;
  color: #b45309;
}

.skill-copy {
  flex: 1;
}

.skill-copy span {
  color: var(--color-text-muted);
  font-size: 12px;
}

.skill-copy h2 {
  margin: 5px 0 10px;
  color: var(--color-text);
  font-size: 19px;
}

.skill-copy p {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.skill-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding-top: 20px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.card-open-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: var(--color-text-secondary);
  transition:
    color var(--transition-fast),
    background var(--transition-fast);
}

.skill-card:hover .card-open-icon,
.skill-card:focus-visible .card-open-icon {
  background: var(--color-background-tertiary);
  color: var(--color-primary-dark);
}

@media (max-width: 900px) {
  .skill-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 620px) {
  .skill-center {
    padding: 28px 16px 56px;
  }

  .center-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .center-heading h1 {
    font-size: 26px;
  }

  .skill-grid {
    grid-template-columns: 1fr;
  }
}
</style>
