declare namespace API {
  type BaseResponseSkillDefinition = {
    code?: number
    data?: SkillDefinition
    message?: string
  }

  type BaseResponseSkillExecuteResponse = {
    code?: number
    data?: SkillExecuteResponse
    message?: string
  }

  type BaseResponseSkillResultResponse = {
    code?: number
    data?: SkillResultResponse
    message?: string
  }

  type BaseResponseSkillSummaryList = {
    code?: number
    data?: SkillSummary[]
    message?: string
  }

  type AgentExecutionStats = {
    taskId?: string
    totalDurationMs?: number
    agentCount?: number
    agentDurations?: Record<string, unknown>
    overallStatus?: string
    logs?: AgentLog[]
  }

  type AgentLog = {
    id?: number
    taskId?: string
    agentName?: string
    startTime?: string
    endTime?: string
    durationMs?: number
    status?: string
    errorMessage?: string
    prompt?: string
    inputData?: string
    outputData?: string
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type ArticleAiModifyOutlineRequest = {
    taskId?: string
    modifySuggestion?: string
  }

  type ArticleConfirmOutlineRequest = {
    taskId?: string
    outline?: OutlineSection[]
  }

  type ArticleConfirmTitleRequest = {
    taskId?: string
    selectedMainTitle?: string
    selectedSubTitle?: string
    userDescription?: string
  }

  type ArticleCreateRequest = {
    topic?: string
    style?: string
    methodology?: string   // 方法论文档（default/douyin/xiaohongshu/wechat），后端回退 default
    enabledImageMethods?: string[]
    characterStyle?: string   // 插画子风格（healing/cute/doodle/watercolor），后端回退 HEALING
  }

  type ArticleQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    userId?: number
    status?: string
  }

  type ArticleVO = {
    id?: number
    taskId?: string
    userId?: number
    topic?: string
    userDescription?: string
    mainTitle?: string
    subTitle?: string
    titleOptions?: TitleOption[]
    outline?: OutlineItem[]
    content?: string
    fullContent?: string
    coverImage?: string
    images?: ImageItem[]
    status?: string
    phase?: string
    errorMessage?: string
    createTime?: string
    completedTime?: string
  }

  type BaseResponseAgentExecutionStats = {
    code?: number
    data?: AgentExecutionStats
    message?: string
  }

  type BaseResponseArticleVO = {
    code?: number
    data?: ArticleVO
    message?: string
  }

  type BaseResponseBoolean = {
    code?: number
    data?: boolean
    message?: string
  }

  type BaseResponseListOutlineSection = {
    code?: number
    data?: OutlineSection[]
    message?: string
  }

  type BaseResponseListPaymentRecord = {
    code?: number
    data?: PaymentRecord[]
    message?: string
  }

  type BaseResponseLoginUserVO = {
    code?: number
    data?: LoginUserVO
    message?: string
  }

  type BaseResponseLong = {
    code?: number
    data?: number
    message?: string
  }

  type BaseResponsePageArticleVO = {
    code?: number
    data?: PageArticleVO
    message?: string
  }

  type BaseResponsePageUserVO = {
    code?: number
    data?: PageUserVO
    message?: string
  }

  type BaseResponseStatisticsVO = {
    code?: number
    data?: StatisticsVO
    message?: string
  }

  type BaseResponseString = {
    code?: number
    data?: string
    message?: string
  }

  type BaseResponseUser = {
    code?: number
    data?: User
    message?: string
  }

  type BaseResponseUserVO = {
    code?: number
    data?: UserVO
    message?: string
  }

  type BaseResponseVoid = {
    code?: number
    data?: Record<string, unknown>
    message?: string
  }

  type DeleteRequest = {
    id?: number | string
  }

  type getArticleParams = {
    taskId: string
  }

  type getExecutionLogsParams = {
    taskId: string
  }

  type getProgressParams = {
    taskId: string
  }

  type getUserByIdParams = {
    id: number
  }

  type getUserVOByIdParams = {
    id: number
  }

  type ImageItem = {
    position?: number
    url?: string
    method?: string
    keywords?: string
    sectionTitle?: string
    description?: string
  }

  type LoginUserVO = {
    id?: string
    userAccount?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    quota?: number
    vipTime?: string
    createTime?: string
    updateTime?: string
  }

  type OutlineItem = {
    section?: number
    title?: string
    points?: string[]
  }

  type OutlineSection = {
    section?: number
    title?: string
    points?: string[]
  }

  type PageArticleVO = {
    records?: ArticleVO[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type PageUserVO = {
    records?: UserVO[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type PaymentRecord = {
    id?: number
    userId?: number
    stripeSessionId?: string
    stripePaymentIntentId?: string
    amount?: number
    currency?: string
    status?: string
    productType?: string
    description?: string
    refundTime?: string
    refundReason?: string
    createTime?: string
    updateTime?: string
  }

  type refundParams = {
    reason?: string
  }

  type SseEmitter = {
    timeout?: number
  }

  type StatisticsVO = {
    todayCount?: number
    weekCount?: number
    monthCount?: number
    totalCount?: number
    successRate?: number
    avgDurationMs?: number
    activeUserCount?: number
    totalUserCount?: number
    vipUserCount?: number
    quotaUsed?: number
  }

  type SkillDefinition = {
    name: string
    description?: string
    category?: string
    requiredRoles?: string[]
    multiRound?: boolean
    variables?: Record<string, SkillVariableDef>
    phases?: SkillPhaseDefinition[]
  }

  type SkillExecuteRequest = {
    inputs: Record<string, unknown>
  }

  type SkillExecuteResponse = {
    skillExecutionId: string
    skillName: string
    status: string
    totalPhases: number
    progressUrl: string
  }

  type SkillPhaseDefinition = {
    name: string
    promptFile?: string
    model?: string
    streaming?: boolean
    outputParser?: string
    outputKey: string
    requireConfirmation?: boolean
  }

  type SkillConfirmAction = 'approve' | 'modify' | 'retry'

  type SkillProgressEvent = {
    type:
      | 'skill.started'
      | 'skill.phase_started'
      | 'skill.progress'
      | 'skill.phase_complete'
      | 'skill.awaiting_confirmation'
      | 'skill.complete'
      | 'skill.error'
      | 'chain.complete'
      | 'chain.error'
    skillExecutionId: string
    skillName: string
    timestamp?: number
    status?: string
    phase?: string
    phaseIndex?: number
    totalPhases?: number
    data?: string
    outputData?: unknown
    errorMessage?: string
    /** 仅 chain.error：失败的 skill 名（前端定位失败环节） */
    failedSkill?: string
    /** 仅 skill.awaiting_confirmation：后端支持的确认动作 */
    supportedActions?: SkillConfirmAction[]
    /** 仅 skill.awaiting_confirmation：待用户审阅的上一阶段产出 */
    pendingOutput?: unknown
  }

  type SkillConfirmRequest = {
    action: SkillConfirmAction
    /** modify 动作携带的修改数据，JSON 对象字符串 */
    modifiedData?: string
  }

  type SkillResultResponse = {
    skillExecutionId?: string
    skillName?: string
    status: 'PENDING' | 'RUNNING' | 'AWAITING_CONFIRMATION' | 'SUCCESS' | 'FAILED' | 'NOT_FOUND'
    phase?: string
    durationMs?: number
    errorMessage?: string
    inputData?: Record<string, unknown>
    outputData?: Record<string, unknown>
  }

  type SkillSummary = {
    name: string
    description?: string
    category?: string
    phases?: number
    multiRound?: boolean
  }

  type SkillVariableDef = {
    name?: string
    description?: string
    required?: boolean
    source?: string
    phaseRef?: string
    uiType?: 'input' | 'textarea' | 'select' | 'radio'
    options?: SkillVariableOption[]
    defaultValue?: unknown
    placeholder?: string
    maxLength?: number
  }

  type SkillVariableOption = {
    label: string
    value: unknown
  }

  type TopicOption = {
    title: string
    type?: string
    workload?: string
    outline?: string[]
    pros?: string[]
    cons?: string[]
  }

  type TitleOption = {
    mainTitle?: string
    subTitle?: string
  }

  type User = {
    id?: number
    userAccount?: string
    userPassword?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    quota?: number
    vipTime?: string
    editTime?: string
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type UserAddRequest = {
    userName?: string
    userAccount?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
  }

  type UserLoginRequest = {
    userAccount?: string
    userPassword?: string
  }

  type UserQueryRequest = {
    current?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: number
    userName?: string
    userAccount?: string
    userProfile?: string
    userRole?: string
  }

  type UserRegisterRequest = {
    userAccount?: string
    userPassword?: string
    checkPassword?: string
  }

  type UserUpdateRequest = {
    id?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
  }

  type UserVO = {
    id?: string
    userAccount?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    createTime?: string
  }

  // ── Skill Execution History ──

  type SkillExecutionVO = {
    skillExecutionId?: string
    skillName?: string
    status?: string
    phase?: string
    tokenUsage?: number
    modelUsed?: string
    durationMs?: number
    errorMessage?: string
    createTime?: string
  }

  type SkillExecutionQueryRequest = {
    skillName?: string
    status?: string
    current?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
  }

  type PageSkillExecutionVO = {
    records?: SkillExecutionVO[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type BaseResponsePageSkillExecutionVO = {
    code?: number
    data?: PageSkillExecutionVO
    message?: string
  }

  // ── Approval 审批 ──

  type ApprovalRecord = {
    id?: number
    articleTaskId?: string
    versionNo?: number
    status?: string
    submittedBy?: number
    reviewerId?: number
    comment?: string
    submitTime?: string
    reviewTime?: string
  }

  type BaseResponseApprovalRecord = {
    code?: number
    data?: ApprovalRecord
    message?: string
  }

  type BaseResponseListApprovalRecord = {
    code?: number
    data?: ApprovalRecord[]
    message?: string
  }

  // ── Publish 发布排期 ──

  type PublishSchedule = {
    id?: number
    articleTaskId?: string
    publishAt?: string
    status?: string
    platform?: string
    adapterOutput?: string
    contentTitle?: string
    methodologyName?: string
    publishedAt?: string
    createdBy?: number
    createTime?: string
  }

  type PublishScheduleRequest = {
    taskId: string
    publishAt: string
    platform?: string
    methodologyName?: string
  }

  type BaseResponsePublishSchedule = {
    code?: number
    data?: PublishSchedule
    message?: string
  }

  type BaseResponseListPublishSchedule = {
    code?: number
    data?: PublishSchedule[]
    message?: string
  }

  // ── Analytics 分析 ──

  type AnalyticsVO = {
    totalArticles?: number
    styleDistribution?: Record<string, number>
    imageMethodDistribution?: Record<string, number>
    qualityTrend?: number[]
    avgQualityScore?: number
    skillUsageTop?: Record<string, number>
    modelUsage?: Record<string, number>
    dailyActiveUsers?: Record<string, number>
    quotaConsumed?: number
    successRate?: number
    totalTokenUsage?: number
  }

  type BaseResponseAnalyticsVO = {
    code?: number
    data?: AnalyticsVO
    message?: string
  }

  // ── Admin 工具箱 ──

  type BreakerStatusItem = {
    status?: string
    failures?: number
  }

  type BreakerStatus = Record<string, BreakerStatusItem>

  type BaseResponseBreakerStatus = {
    code?: number
    data?: BreakerStatus
    message?: string
  }

  // ── RAG 向量检索 ──

  type RagHit = {
    refId?: string
    title?: string
    content?: string
    score?: number
    type?: string
  }

  type BaseResponseListRagHit = {
    code?: number
    data?: RagHit[]
    message?: string
  }

  // ── Workspace 协作空间 ──

  type Workspace = {
    id?: number
    name?: string
    description?: string
    ownerId?: number
    memberCount?: number
    status?: string
    createTime?: string
    updateTime?: string
  }

  type WorkspaceMember = {
    id?: number
    workspaceId?: number
    userId?: number
    role?: string
    joinedAt?: string
    userName?: string
    userAvatar?: string
  }

  type WorkspaceCreateRequest = {
    name: string
    description?: string
  }

  type WorkspaceUpdateRequest = {
    name?: string
    description?: string
  }

  type BaseResponseWorkspace = {
    code?: number
    data?: Workspace
    message?: string
  }

  type BaseResponseListWorkspace = {
    code?: number
    data?: Workspace[]
    message?: string
  }

  type BaseResponseListWorkspaceMember = {
    code?: number
    data?: WorkspaceMember[]
    message?: string
  }

  // ── Card 卡片 ──

  type CardGenerateRequest = {
    taskId?: string
    cardStyle?: string
    methodologyName?: string
    characterStyle?: string
  }

  type CardPage = {
    id?: number
    taskId?: string
    pageNo?: number
    pageType?: string
    style?: string
    imageUrl?: string
    imageKey?: string
    width?: number
    height?: number
    bytes?: number
    status?: string
    complianceReport?: string
    errorMessage?: string
    renderMs?: number
    createTime?: string
  }

  type CardGenerateResponse = {
    taskId?: string
    progressUrl?: string
  }

  type BaseResponseListString = {
    code?: number
    data?: string[]
    message?: string
  }

  type BaseResponseListCardPage = {
    code?: number
    data?: CardPage[]
    message?: string
  }

  type BaseResponseCardGenerateResponse = {
    code?: number
    data?: CardGenerateResponse
    message?: string
  }

  // ── API Key ──

  type ApiKeyVO = {
    id?: number
    userId?: number
    name?: string
    apiKeyPrefix?: string
    lastUsedAt?: string
    expiresAt?: string
    createTime?: string
  }

  type ApiKeyCreateVO = {
    id?: number
    name?: string
    apiKey?: string
    apiKeyPrefix?: string
    expiresAt?: string
  }

  type ApiKeyCreateRequest = {
    userId?: number
    name?: string
    expiresAt?: string
  }

  type PageApiKeyVO = {
    records?: ApiKeyVO[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type BaseResponseApiKeyCreateVO = {
    code?: number
    data?: ApiKeyCreateVO
    message?: string
  }

  type BaseResponsePageApiKeyVO = {
    code?: number
    data?: PageApiKeyVO
    message?: string
  }

  type BaseResponseTopicRecommendVO = {
    code?: number
    data?: TopicRecommendVO
    message?: string
  }

  type TopicRecommendVO = {
    items?: TopicRecommendItem[]
    hasAi?: boolean
  }

  type TopicRecommendItem = {
    text?: string
    source?: string
  }
}
