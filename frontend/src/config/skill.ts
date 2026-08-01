export type SkillIconName = 'ideas' | 'proofreading' | 'social' | 'seo' | 'translate'

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

export const PUBLIC_SKILL_ORDER = ['topic-gen', 'proofreading', 'article-to-x', 'research', 'seo-optimizer', 'content-translator', 'ai-detox', 'seeding-copy', 'rewrite-plagiarism', 'video-script', 'outline-expander', 'content-summarizer', 'headline-optimizer'] as const

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
  'seo-optimizer': {
    name: 'seo-optimizer',
    title: 'SEO 优化',
    shortTitle: 'SEO 优化',
    description: '对文章做 SEO 审计评分，优化标题、关键词密度与结构。',
    inputLabel: '待优化的文章与目标关键词',
    outputLabel: 'SEO 评分报告与优化后文章',
    actionLabel: '优化 SEO',
    icon: 'seo',
    categoryLabel: '写作',
    accent: 'green',
  },
  'content-translator': {
    name: 'content-translator',
    title: '多语言翻译',
    shortTitle: '翻译',
    description: '将文章翻译为英文、日文、韩文等 6 种语言，保留 Markdown 格式。',
    inputLabel: '待翻译的文章与目标语言',
    outputLabel: '翻译后的完整文章',
    actionLabel: '开始翻译',
    icon: 'translate',
    categoryLabel: '写作',
    accent: 'amber',
  },
  'ai-detox': {
    name: 'ai-detox',
    title: 'AI 检测率优化',
    shortTitle: '去 AI 味',
    description: '两阶段降低 AI 检测率：审计高风险模式 + 句法变异/词汇替换重写。',
    inputLabel: '待优化的文章与优化强度',
    outputLabel: 'AI 痕迹审计报告 + 自然化改写稿',
    actionLabel: '去 AI 味',
    icon: 'proofreading',
    categoryLabel: '写作',
    accent: 'green',
  },
  'seeding-copy': {
    name: 'seeding-copy',
    title: '内容种草文案',
    shortTitle: '种草文案',
    description: '生成小红书/抖音/微博风格的种草文案，含标题、正文、标签和互动引导。',
    inputLabel: '产品信息（卖点/场景/目标用户）',
    outputLabel: '平台适配的种草文案',
    actionLabel: '生成种草文案',
    icon: 'social',
    categoryLabel: '写作',
    accent: 'amber',
  },
  'rewrite-plagiarism': {
    name: 'rewrite-plagiarism',
    title: '改写降重',
    shortTitle: '降重',
    description: '句法重组+同义词替换+段落重构，降低查重率并保留原意。',
    inputLabel: '待降重文章与降重强度',
    outputLabel: '降重改写后的文章',
    actionLabel: '开始降重',
    icon: 'proofreading',
    categoryLabel: '写作',
    accent: 'green',
  },
  'video-script': {
    name: 'video-script',
    title: '视频脚本生成',
    shortTitle: '视频脚本',
    description: '生成抖音/快手/YouTube Shorts 风格短视频脚本，含分镜、台词、时长。',
    inputLabel: '视频主题、平台与风格',
    outputLabel: '分镜脚本 + 台词 + 制作建议',
    actionLabel: '生成脚本',
    icon: 'social',
    categoryLabel: '写作',
    accent: 'blue',
  },
  'outline-expander': {
    name: 'outline-expander',
    title: '大纲扩展',
    shortTitle: '扩展大纲',
    description: '将简要大纲扩展为详细章节，补充研究要点、案例建议与数据来源。',
    inputLabel: '简要大纲与文章主题',
    outputLabel: '含研究要点与案例的详细提纲',
    actionLabel: '扩展大纲',
    icon: 'ideas',
    categoryLabel: '写作',
    accent: 'green',
  },
  'content-summarizer': {
    name: 'content-summarizer',
    title: '摘要生成',
    shortTitle: '摘要',
    description: '将长文章提炼为简洁摘要，支持短/中/长三种长度，保留核心观点。',
    inputLabel: '待总结的文章与目标长度',
    outputLabel: '精炼摘要（100-600字）',
    actionLabel: '生成摘要',
    icon: 'proofreading',
    categoryLabel: '写作',
    accent: 'green',
  },
  'headline-optimizer': {
    name: 'headline-optimizer',
    title: '标题优化',
    shortTitle: '起标题',
    description: '生成多个标题变体用于 A/B 测试，含点击率预估和适用平台建议。',
    inputLabel: '文章内容或主题',
    outputLabel: '多版本标题方案 + 最佳推荐',
    actionLabel: '优化标题',
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
  seo_audit: 'SEO 审计评分',
  seo_rewrite: 'SEO 优化改写',
  translate: '多语言翻译',
  ai_audit: 'AI 痕迹审计',
  detox_rewrite: 'AI 痕迹优化改写',
  generate_copy: '生成种草文案',
  rewrite_plagiarism: '降重改写',
  generate_script: '生成视频脚本',
  expand_outline: '扩展大纲',
  summarize: '生成摘要',
  generate_headlines: '生成标题方案',
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
  'seo-optimizer': {
    articleContent: {
      description: '待优化的文章',
      required: true,
      uiType: 'textarea',
      placeholder: '粘贴需要做 SEO 优化的文章正文',
      maxLength: 20000,
    },
    primaryKeyword: {
      description: '主要目标关键词',
      uiType: 'input',
      placeholder: '例如：Spring AI 入门指南',
    },
  },
  'content-translator': {
    articleContent: {
      description: '待翻译的文章',
      required: true,
      uiType: 'textarea',
      placeholder: '粘贴需要翻译的文章',
      maxLength: 20000,
    },
    targetLang: {
      description: '目标语言',
      uiType: 'select',
      defaultValue: 'en',
      options: [
        { label: 'English (英文)', value: 'en' },
        { label: '日本語 (日文)', value: 'ja' },
        { label: '한국어 (韩文)', value: 'ko' },
        { label: 'Français (法文)', value: 'fr' },
        { label: 'Deutsch (德文)', value: 'de' },
        { label: 'Español (西班牙文)', value: 'es' },
      ],
    },
  },
  'ai-detox': {
    articleContent: {
      description: '待优化的文章',
      required: true,
      uiType: 'textarea',
      placeholder: '粘贴需要降低 AI 检测率的文章',
      maxLength: 20000,
    },
    intensity: {
      description: '优化强度',
      uiType: 'select',
      defaultValue: 'medium',
      options: [
        { label: '轻度（微调表达，保留原结构）', value: 'light' },
        { label: '中度（句法变化 + 词汇替换）', value: 'medium' },
        { label: '重度（深度重写，最大化自然度）', value: 'heavy' },
      ],
    },
  },
  'seeding-copy': {
    productInfo: {
      description: '产品/服务信息',
      required: true,
      uiType: 'textarea',
      placeholder: '描述产品/服务：名称、核心卖点、目标用户、使用场景...',
      maxLength: 5000,
    },
    platform: {
      description: '目标平台',
      uiType: 'select',
      defaultValue: 'xiaohongshu',
      options: [
        { label: '小红书', value: 'xiaohongshu' },
        { label: '抖音/短视频', value: 'douyin' },
        { label: '微博', value: 'weibo' },
        { label: '微信公众号', value: 'wechat' },
      ],
    },
    tone: {
      description: '文案风格',
      uiType: 'select',
      defaultValue: 'authentic',
      options: [
        { label: '真实体验（第一人称）', value: 'authentic' },
        { label: '专业种草（数据支撑）', value: 'professional' },
        { label: '有趣种草（轻松幽默）', value: 'humorous' },
        { label: '紧迫营销（限时优惠）', value: 'urgent' },
      ],
    },
  },
  'rewrite-plagiarism': {
    articleContent: {
      description: '待降重文章',
      required: true,
      uiType: 'textarea',
      placeholder: '粘贴需要降重改写的文章',
      maxLength: 20000,
    },
    intensity: {
      description: '降重强度',
      uiType: 'select',
      defaultValue: 'medium',
      options: [
        { label: '轻度（同义词+语序）', value: 'light' },
        { label: '中度（句法+段落重构）', value: 'medium' },
        { label: '重度（深度重写）', value: 'heavy' },
      ],
    },
  },
  'video-script': {
    topic: {
      description: '视频主题',
      required: true,
      uiType: 'input',
      placeholder: '例如：3 分钟学会 Spring Boot 部署',
    },
    platform: {
      description: '目标平台',
      uiType: 'select',
      defaultValue: 'douyin',
      options: [
        { label: '抖音', value: 'douyin' },
        { label: '快手', value: 'kuaishou' },
        { label: 'YouTube Shorts', value: 'youtube' },
        { label: '视频号', value: 'wechat' },
      ],
    },
    duration: {
      description: '视频时长',
      uiType: 'select',
      defaultValue: '60s',
      options: [
        { label: '30 秒', value: '30s' },
        { label: '60 秒', value: '60s' },
        { label: '90 秒', value: '90s' },
        { label: '3 分钟', value: '3min' },
      ],
    },
    style: {
      description: '视频风格',
      uiType: 'select',
      defaultValue: 'tutorial',
      options: [
        { label: '教程/干货', value: 'tutorial' },
        { label: '故事/叙事', value: 'story' },
        { label: '测评/开箱', value: 'review' },
        { label: '搞笑/娱乐', value: 'humor' },
      ],
    },
  },
  'outline-expander': {
    outline: {
      description: '简要大纲',
      required: true,
      uiType: 'textarea',
      placeholder: '每行一个要点，如：\n1. 引言\n2. 核心概念\n3. 实践方法',
      maxLength: 5000,
    },
    topic: {
      description: '文章主题（可选）',
      uiType: 'input',
      placeholder: '例如：Spring Boot 微服务架构',
    },
    depth: {
      description: '扩展深度',
      uiType: 'select',
      defaultValue: 'detailed',
      options: [
        { label: '标准（200-300字/章）', value: 'standard' },
        { label: '详细（500-800字/章）', value: 'detailed' },
        { label: '深度（1000+字/章）', value: 'deep' },
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
