<template>
  <section class="skill-center" aria-labelledby="skill-center-title">
    <div class="center-shell">
      <header class="center-heading">
        <div>
          <p>AI 工具</p>
          <h1 id="skill-center-title">AI 技能中心</h1>
          <span>选择一个明确任务，提交素材并带走结构化结果。</span>
        </div>
        <fieldset class="category-filter">
          <legend class="visually-hidden">筛选技能分类</legend>
          <label v-for="option in categoryOptions" :key="option.value">
            <input v-model="category" type="radio" name="skill-category" :value="option.value" />
            <span>{{ option.label }}</span>
          </label>
        </fieldset>
      </header>

      <div v-if="loading" class="skill-list" aria-busy="true" aria-label="技能加载中">
        <span class="visually-hidden" role="status">正在加载技能列表…</span>
        <div v-for="index in 3" :key="index" class="skill-skeleton" aria-hidden="true">
          <span class="skeleton-block skeleton-icon"></span>
          <span class="skeleton-lines">
            <span class="skeleton-line skeleton-line-title"></span>
            <span class="skeleton-line"></span>
            <span class="skeleton-line skeleton-line-short"></span>
          </span>
        </div>
      </div>

      <div v-else-if="errorMessage" class="skill-state" role="alert">
        <h2>技能列表暂时不可用</h2>
        <p>{{ errorMessage }}</p>
        <button type="button" class="retry-button" @click="loadSkills">刷新</button>
      </div>

      <div v-else-if="visibleSkills.length" class="skill-list">
        <RouterLink
          v-for="skill in visibleSkills"
          :key="skill.name"
          :to="`/skill/${skill.name}`"
          class="skill-card"
        >
          <div class="skill-identity">
            <div :class="['skill-icon', getSkillUiConfig(skill.name).accent]">
              <component :is="iconFor(skill.name)" />
            </div>
            <span>{{ getSkillUiConfig(skill.name).categoryLabel }}</span>
          </div>
          <div class="skill-copy">
            <h2>{{ getSkillUiConfig(skill.name).title }}</h2>
            <p>{{ getSkillUiConfig(skill.name).description }}</p>
          </div>
          <dl class="skill-contract">
            <div>
              <dt>你需要提供</dt>
              <dd>{{ getSkillUiConfig(skill.name).inputLabel }}</dd>
            </div>
            <div>
              <dt>你将得到</dt>
              <dd>{{ getSkillUiConfig(skill.name).outputLabel }}</dd>
            </div>
          </dl>
          <div class="skill-open">
            <span>{{ getSkillUiConfig(skill.name).actionLabel }}</span>
            <ArrowRightOutlined aria-hidden="true" />
          </div>
        </RouterLink>
      </div>

      <div v-else class="skill-state" role="status">
        <h2>当前没有可用技能</h2>
        <p>稍后刷新，或返回首页开始文章创作。</p>
        <button type="button" class="retry-button" @click="loadSkills">刷新</button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, type Component } from 'vue'
import {
  ArrowRightOutlined,
  BulbOutlined,
  FileDoneOutlined,
  ShareAltOutlined,
} from '@ant-design/icons-vue'
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
    const response = await fetch('/api/skill/list', {
      headers: { Accept: 'application/json' },
      credentials: 'include',
    })
    if (!response.ok) {
      throw new Error(`请求失败（HTTP ${response.status}）`)
    }
    const payload = (await response.json()) as API.BaseResponseSkillSummaryList
    if (payload.code !== 0) {
      throw new Error(payload.message || '请求失败')
    }
    skills.value = payload.data || []
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

.category-filter {
  display: inline-flex;
  margin: 0;
  padding: 3px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--color-background-tertiary);
}

.category-filter label {
  position: relative;
  cursor: pointer;
}

.category-filter input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
}

.category-filter label > span {
  display: inline-flex;
  min-width: 64px;
  min-height: 34px;
  align-items: center;
  justify-content: center;
  padding: 0 14px;
  border-radius: calc(var(--radius-md) - 2px);
  color: var(--color-text-secondary);
  font-size: 13px;
  font-weight: 600;
  transition:
    background var(--transition-fast),
    color var(--transition-fast),
    box-shadow var(--transition-fast);
}

.category-filter input:checked + span {
  background: var(--surface-panel);
  color: var(--color-primary-dark);
  box-shadow: var(--shadow-sm);
}

.category-filter input:focus-visible + span {
  outline: 3px solid rgba(34, 197, 94, 0.35);
  outline-offset: 2px;
}

.skill-list {
  display: grid;
  border-top: 1px solid var(--border-default);
}

.skill-card,
.skill-skeleton {
  padding: 24px 16px;
  border-bottom: 1px solid var(--border-default);
  background: var(--surface-panel);
}

.skill-skeleton {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr);
  align-items: center;
  gap: 18px;
  min-height: 98px;
}

.skeleton-block,
.skeleton-line {
  display: block;
  background: linear-gradient(
    90deg,
    var(--color-background-tertiary) 25%,
    var(--color-background-secondary) 50%,
    var(--color-background-tertiary) 75%
  );
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.4s ease-in-out infinite;
}

.skeleton-icon {
  width: 42px;
  height: 42px;
  border-radius: var(--radius-md);
}

.skeleton-lines {
  display: grid;
  gap: 9px;
}

.skeleton-line {
  width: 72%;
  height: 10px;
  border-radius: var(--radius-full);
}

.skeleton-line-title {
  width: 34%;
  height: 14px;
}

.skeleton-line-short {
  width: 52%;
}

.skill-state {
  display: grid;
  justify-items: center;
  padding: 72px 20px;
  border: 1px solid var(--border-default);
  background: var(--surface-panel);
  text-align: center;
}

.skill-state h2 {
  margin: 0 0 8px;
  color: var(--color-text);
  font-size: 18px;
}

.skill-state p {
  max-width: 520px;
  margin: 0 0 20px;
  color: var(--color-text-secondary);
  font-size: 14px;
}

.retry-button {
  min-width: 96px;
  min-height: 40px;
  padding: 0 18px;
  border: 0;
  border-radius: var(--radius-md);
  background: var(--gradient-primary);
  color: white;
  font: inherit;
  font-size: 14px;
  font-weight: 600;
}

@keyframes skeleton-shimmer {
  from {
    background-position: 200% 0;
  }

  to {
    background-position: -200% 0;
  }
}

.skill-card {
  display: grid;
  grid-template-columns: 112px minmax(180px, 0.8fr) minmax(320px, 1.25fr) 128px;
  align-items: center;
  gap: 24px;
  color: inherit;
  cursor: pointer;
  text-decoration: none;
  transition:
    border-color var(--transition-fast),
    background var(--transition-fast);
}

.skill-card:hover,
.skill-card:focus-visible {
  background: var(--surface-brand-soft);
  outline: none;
}

.skill-identity {
  display: flex;
  align-items: center;
  gap: 12px;
}

.skill-identity > span {
  color: var(--text-muted);
  font-size: 12px;
}

.skill-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
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

.skill-copy h2 {
  margin: 0 0 6px;
  color: var(--color-text);
  font-size: 19px;
}

.skill-copy p {
  margin: 0;
  color: var(--color-text-secondary);
  font-size: 14px;
  line-height: 1.7;
}

.skill-contract {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  margin: 0;
}

.skill-contract dt {
  margin-bottom: 4px;
  color: var(--text-muted);
  font-size: 11px;
}

.skill-contract dd {
  margin: 0;
  color: var(--text-body);
  font-size: 13px;
  line-height: 1.5;
}

.skill-open {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  color: var(--color-primary-dark);
  font-size: 13px;
  font-weight: 600;
}

@media (max-width: 900px) {
  .skill-skeleton {
    min-height: 170px;
  }

  .skill-card {
    grid-template-columns: 96px minmax(0, 1fr) 120px;
  }

  .skill-contract {
    grid-column: 2 / 4;
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

  .category-filter {
    width: 100%;
  }

  .category-filter label {
    flex: 1;
  }

  .category-filter label > span {
    width: 100%;
  }

  .skill-skeleton {
    min-height: 250px;
    padding: 22px 4px;
  }

  .skill-card {
    grid-template-columns: 1fr auto;
    gap: 14px 16px;
    padding: 22px 4px;
  }

  .skill-identity {
    grid-column: 1;
  }

  .skill-copy {
    grid-column: 1 / 3;
  }

  .skill-contract {
    grid-column: 1 / 3;
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .skill-open {
    grid-column: 2;
    grid-row: 1;
  }

  .skill-open span {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-block,
  .skeleton-line {
    animation: none;
  }
}
</style>
