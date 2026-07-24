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
    enabledImageMethods?: string[]
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
    id?: number
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
    id?: number
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

  type SkillProgressEvent = {
    type:
      | 'skill.started'
      | 'skill.phase_started'
      | 'skill.progress'
      | 'skill.phase_complete'
      | 'skill.complete'
      | 'skill.error'
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
  }

  type SkillResultResponse = {
    skillExecutionId?: string
    skillName?: string
    status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'NOT_FOUND'
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
    pageNum?: number
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
    id?: number
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
  }

  type UserVO = {
    id?: number
    userAccount?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    createTime?: string
  }
}
