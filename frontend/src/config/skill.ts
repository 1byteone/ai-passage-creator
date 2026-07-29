export type SkillIconName = 'ideas' | 'proofreading' | 'social'

export interface SkillUiConfig {
  name: string
  title: string
  shortTitle: string
  description: string
  inputLabel: string
  outputLabel: string
  actionLabel: string
  icon: SkillIconName
  categoryLabel: string
  accent: 'blue' | 'green' | 'amber'
}

export const PUBLIC_SKILL_ORDER = ['topic-gen', 'proofreading', 'article-to-x', 'research'] as const

export const SKILL_UI_CONFIG: Record<string, SkillUiConfig> = {
  'topic-gen': {
    name: 'topic-gen',
    title: '选题生成',
    shortTitle: '找选题',
    description: '从一个方向拆出可比较的标题、文章结构与投入评估。',
    inputLabel: '一个内容方向或目标受众',
    outputLabel: '多组选题、标题和建议大纲',
    actionLabel: '生成选题',
    icon: 'ideas',
    categoryLabel: '写作',
    accent: 'blue',
  },
  proofreading: {
    name: 'proofreading',
    title: '文章审校',
    shortTitle: '审校文章',
    description: '检查内容与逻辑，降低生硬的 AI 表达，并输出可直接使用的终稿。',
    inputLabel: '一篇需要检查的文章',
    outputLabel: '问题清单、修改对比和终稿',
    actionLabel: '开始审校',
    icon: 'proofreading',
    categoryLabel: '写作',
    accent: 'green',
  },
  'article-to-x': {
    name: 'article-to-x',
    title: '社交转写',
    shortTitle: '转社交文案',
    description: '把长文浓缩为适合微博、小红书或 X 的短内容。',
    inputLabel: '一篇需要浓缩的长文',
    outputLabel: '适配目标平台的短文案',
    actionLabel: '转为社交文案',
    icon: 'social',
    categoryLabel: '写作',
    accent: 'amber',
  },
  research: {
    name: 'research',
    title: '结构化调研',
    shortTitle: '调研',
    description: '围绕主题进行多轮搜索，整理关键发现与结构化简报。',
    inputLabel: '调研主题与关键问题',
    outputLabel: '调研发现与写作建议',
    actionLabel: '开始调研',
    icon: 'ideas',
    categoryLabel: '写作',
    accent: 'blue',
  },
}

export const PHASE_LABELS: Record<string, string> = {
  generate_topics: '生成选题方案',
  content_review: '检查内容与逻辑',
  ai_tone_fix: '调整表达语气',
  rhythm_polish: '润色节奏与终稿',
  condense: '提炼社交文案',
  search: '资料检索',
  summary: '生成调研简报',
}

const FALLBACK_FIELDS: Record<string, Record<string, API.SkillVariableDef>> = {
  'topic-gen': {
    direction: {
      description: '选题方向',
      required: true,
      uiType: 'textarea',
      placeholder: '描述行业、目标受众、内容目标或你正在关注的问题',
      maxLength: 1000,
    },
    style: {
      description: '文章风格',
      uiType: 'select',
      defaultValue: '',
      options: [
        { label: '自动匹配', value: '' },
        { label: '科技', value: 'tech' },
        { label: '情感', value: 'emotional' },
        { label: '教育', value: 'educational' },
        { label: '轻松幽默', value: 'humorous' },
      ],
    },
  },
  proofreading: {
    articleContent: {
      description: '文章正文',
      required: true,
      uiType: 'textarea',
      placeholder: '粘贴需要审校的文章正文',
      maxLength: 20000,
    },
    style: {
      description: '文章风格',
      uiType: 'select',
      defaultValue: '',
      options: [
        { label: '自动匹配', value: '' },
        { label: '科技', value: 'tech' },
        { label: '情感', value: 'emotional' },
        { label: '教育', value: 'educational' },
        { label: '轻松幽默', value: 'humorous' },
      ],
    },
  },
  'article-to-x': {
    articleContent: {
      description: '原始长文',
      required: true,
      uiType: 'textarea',
      placeholder: '粘贴需要转写的长文',
      maxLength: 20000,
    },
    platform: {
      description: '目标平台',
      uiType: 'select',
      defaultValue: 'weibo',
      options: [
        { label: '微博', value: 'weibo' },
        { label: '小红书', value: 'xiaohongshu' },
        { label: 'X / Twitter', value: 'twitter' },
      ],
    },
    style: {
      description: '开头风格',
      uiType: 'select',
      defaultValue: 'value-proposition',
      options: [
        { label: '价值主张型', value: 'value-proposition' },
        { label: '金句型', value: 'quote' },
        { label: '数据型', value: 'data' },
      ],
    },
  },
}

export const getSkillUiConfig = (skillName: string): SkillUiConfig => {
  return (
    SKILL_UI_CONFIG[skillName] || {
      name: skillName,
      title: skillName,
      shortTitle: skillName,
      description: '通用 AI 技能',
      inputLabel: '任务所需素材',
      outputLabel: '结构化处理结果',
      actionLabel: '开始执行',
      icon: 'ideas',
      categoryLabel: '其他',
      accent: 'green',
    }
  )
}

export const getFieldDefinition = (
  skillName: string,
  fieldName: string,
  backendDefinition?: API.SkillVariableDef,
): API.SkillVariableDef => {
  const fallback = FALLBACK_FIELDS[skillName]?.[fieldName] || {
    description: fieldName,
    uiType: 'input' as const,
  }
  return {
    ...fallback,
    ...(backendDefinition || {}),
    options: backendDefinition?.options || fallback.options,
  }
}

export const getPhaseLabel = (phaseName: string): string => {
  if (PHASE_LABELS[phaseName]) {
    return PHASE_LABELS[phaseName]
  }
  return phaseName
    .split('_')
    .filter(Boolean)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

export const platformLabel = (value: unknown): string => {
  const labels: Record<string, string> = {
    weibo: '微博',
    xiaohongshu: '小红书',
    twitter: 'X / Twitter',
  }
  return labels[String(value || '')] || '社交平台'
}
