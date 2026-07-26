# CLAUDE.md -- AI Passage Creator (灵犀写作)

## 项目概述

AI 驱动的内容创作平台（灵犀写作），基于 Spring Boot 3 + Spring AI Alibaba 构建。通过多智能体编排（StateGraph）自动完成选题输入、标题策划、大纲设计、正文撰写到多模态配图生成的全流程。支持 SSE 流式推送、Human-in-the-loop 三阶段工作流、Stripe 支付、AOP 日志跟踪。

---

## 技术栈

| 层级 | 技术 | 版本 |
|---|---|---|
| 语言 | Java 21 | 21 LTS |
| 框架 | Spring Boot | 3.5.13 |
| ORM | MyBatis-Flex | 1.11.1 |
| AI 框架 | Spring AI Alibaba Agent Framework | 1.1.0.0-RC2 |
| AI 模型 | DashScope (Tongyi Qianwen / 通义千问) | -- |
| 分布式会话 | Redis + Spring Session Data Redis | -- |
| 支付 | Stripe Java SDK | 31.2.0 |
| 对象存储 | Tencent COS | 5.6.228 |
| API 文档 | Knife4j | 4.4.0 |
| 工具库 | Hutool | 5.8.43 |
| JSON 处理 | Gson | 2.11.0 |
| 编译简化 | Lombok | 1.18.36 |
| HTML 解析 | Jsoup | 1.15.3 |
| HTTP 客户端 | OkHttp | 4.12.0 |

---

## 项目结构

```
ai-passage-creator/
├── pom.xml
├── sql/                          # 数据库初始化脚本（10个）
│   ├── init.sql                  # 基础库+user表
│   ├── core.sql                  # article表
│   ├── add_phase_fields.sql      # 阶段字段
│   ├── add_article_style.sql     # 风格字段
│   ├── add_quota_field.sql       # 配额字段
│   ├── add_vip_payment.sql       # VIP支付
│   ├── create_table_agent_log.sql# Agent日志
│   ├── alter_agent_log_table.sql # Agent日志扩展(model_used/token_usage)
│   ├── create_skill_execution_table.sql # Skill执行记录
│   └── update_article_table.sql  # 表修复
├── docs/                         # 项目复盘文档（8个）
│   ├── 01-多智能体编排与系统设计-项目复盘.md
│   ├── 02-异步与高并发处理-项目复盘.md
│   ├── 03-策略模式与扩展能力-项目复盘.md
│   ├── 04-AI生成与流式响应-项目复盘.md
│   ├── 05-AI内容创作平台-项目复盘.md
│   ├── ai-passage-creator-面试导向深度复盘.md
│   └── 编程导航 - 一站式程序员学习交流社区.pdf
├── PROJECT_ARCHITECTURE.md       # 技术架构笔记
└── src/main/java/com/example/aipassagecreator/
    ├── AiPassageCreatorApplication.java  # 启动类
    ├── agent/                             # 多智能体编排
    │   ├── ArticleAgentOrchestrator.java  # StateGraph编排器
    │   ├── agents/                        # 5个Agent实现
    │   │   ├── TitleGeneratorAgent.java
    │   │   ├── OutlineGeneratorAgent.java
    │   │   ├── ContentGeneratorAgent.java
    │   │   ├── ImageAnalyzerAgent.java
    │   │   └── ContentMergerAgent.java
    │   ├── config/AgentConfig.java        # 编排开关配置
    │   ├── context/StreamHandlerContext.java  # ThreadLocal上下文
    │   ├── parallel/ParallelImageGenerator.java # 并行图片生成
    │   └── tools/ImageGenerationTool.java  # Agent工具
    ├── annotation/
    │   └── AgentExecution.java            # 自定义注解
    ├── aop/
    │   ├── AgentExecutionAspect.java      # Agent日志AOP
    │   ├── AuthCheck.java                 # 权限注解
    │   └── AuthInterceptor.java           # 权限拦截器
    ├── common/
    │   ├── BaseResponse.java              # 统一响应
    │   ├── DeleteRequest.java
    │   ├── PageRequest.java               # 分页基类
    │   └── ResultUtils.java               # 响应工具
    ├── config/
    │   ├── AsyncConfig.java               # 异步线程池
    │   ├── CorsConfig.java                # 跨域配置
    │   ├── CosConfig.java                 # 腾讯云COS
    │   ├── EmojiPackConfig.java           # Bing表情包
    │   ├── IconifyConfig.java             # Iconify图标
    │   ├── IconifyService.java            # Iconify实现
    │   ├── MermaidConfig.java             # Mermaid CLI
    │   ├── PexelsConfig.java              # Pexels API
    │   ├── SessionRedisConfig.java        # Redis会话序列化
    │   ├── StripeConfig.java              # Stripe支付
    │   ├── SvgDiagramConfig.java          # SVG生成
    │   └── SwaggerConfig.java             # API文档
    ├── constant/
    │   ├── ArticleConstant.java           # 文章常量
    │   ├── PromptConstant.java            # LLM提示词
    │   └── UserConstant.java              # 用户常量
    ├── controller/
    │   ├── ArticleController.java         # 文章API
    │   ├── HealthController.java          # 健康检查
    │   ├── PaymentController.java         # 支付API
    │   ├── StatisticsController.java      # 统计API
    │   ├── StripeWebhookController.java   # Webhook
    │   └── UserController.java            # 用户API
    ├── enums/
    │   ├── ArticlePhaseEnum.java          # 阶段状态机
    │   ├── ArticleStatusEnum.java         # 状态枚举
    │   ├── ArticleStyleEnum.java          # 风格枚举
    │   ├── ImageMethodEnum.java           # 图片来源枚举
    │   ├── PaymentStatusEnum.java
    │   ├── ProductTypeEnum.java
    │   ├── SseMessageTypeEnum.java        # SSE消息类型
    │   └── UserRoleEnum.java
    ├── exception/
    │   ├── BusinessException.java         # 业务异常
    │   ├── ErrorCode.java                 # 错误码
    │   ├── GlobalExceptionHandler.java    # 全局异常处理
    │   └── ThrowUtils.java                # 断言工具
    ├── manager/
    │   └── SseEmitterManager.java         # SSE连接管理器
    ├── mapper/
    │   ├── AgentLogMapper.java
    │   ├── ArticleMapper.java
    │   ├── PaymentRecordMapper.java
    │   └── UserMapper.java
    ├── model/
    │   ├── dto/article/
    │   │   ├── ArticleAiModifyOutlineRequest.java
    │   │   ├── ArticleConfirmOutlineRequest.java
    │   │   ├── ArticleConfirmTitleRequest.java
    │   │   ├── ArticleCreateRequest.java
    │   │   ├── ArticleQueryRequest.java
    │   │   └── ArticleState.java           # 核心共享状态
    │   ├── dto/image/
    │   │   ├── ImageData.java              # 图片数据封装
    │   │   └── ImageRequest.java           # 图片请求
    │   ├── dto/user/
    │   │   └── ... (5个请求类)
    │   ├── po/
    │   │   ├── AgentLog.java
    │   │   ├── Article.java
    │   │   ├── PaymentRecord.java
    │   │   └── User.java
    │   └── vo/
    │       ├── AgentExecutionStats.java
    │       ├── ArticleVO.java
    │       ├── LoginUserVO.java
    │       └── StatisticsVO.java
    ├── service/
    │   ├── impl/
    │   │   ├── AgentLogServiceImpl.java
    │   │   ├── ArticleServiceImpl.java
    │   │   ├── PaymentServiceImpl.java
    │   │   ├── QuotaServiceImpl.java
    │   │   ├── StatisticsServiceImpl.java
    │   │   └── UserServiceImpl.java
    │   ├── AgentLogService.java
    │   ├── ArticleAgentService.java        # 原有Agent服务
    │   ├── ArticleAsyncService.java        # 异步调度
    │   ├── ArticleService.java
    │   ├── CosService.java                 # COS上传
    │   ├── EmojiPackService.java           # 表情包搜索
    │   ├── ImageSearchService.java         # 图片搜索接口
    │   ├── ImageServiceStrategy.java       # 策略选择器
    │   ├── MermaidService.java             # 流程图生成
    │   ├── PaymentService.java
    │   ├── PexelsService.java              # Pexels搜索
    │   ├── QuotaService.java
    │   ├── StatisticsService.java
    │   ├── SvgDiagramService.java          # SVG生成
    │   └── UserService.java
    ├── skill/                              # Skill 引擎（配置驱动的通用 LLM 工作流）
    │   ├── SkillController.java            # Skill API
    │   ├── SkillRegistry.java              # skill.yaml 扫描注册 + StateGraph 构建
    │   ├── SkillDefinition.java            # Skill 定义模型
    │   ├── PhaseDefinition.java            # 阶段定义
    │   ├── VariableDef.java                # 变量定义（含 uiType 供前端渲染）
    │   ├── SkillExecution.java             # 单次执行 POJO
    │   ├── SkillExecutionService.java      # @Async 调度 + 失败退配额
    │   ├── SkillExecutionChain.java        # 执行链
    │   ├── SkillNodeAction.java            # 通用 StateGraph 节点
    │   ├── SkillContext.java               # 运行时上下文（跨线程共享）
    │   ├── SkillSseEmitterManager.java     # SSE 管理（带事件缓冲重放）
    │   ├── SkillEventFactory.java          # 统一 JSON 事件构造
    │   ├── ModelRouter.java                # 模型路由 + 降级
    │   ├── ModelRouterConfig.java
    │   ├── PromptTemplateEngine.java       # Prompt 模板渲染
    │   ├── OutputParserRegistry.java       # 输出解析策略注册表
    │   ├── SkillOutputParser.java
    │   ├── config/AgnesModelConfig.java
    │   └── parsers/                        # json / markdown / topic-options
    └── utils/
        └── GsonUtils.java                  # Gson工具

src/main/resources/skills/                  # Skill 声明（新增能力无需写 Java）
├── topic-gen/{skill.yaml, prompts/}
├── proofreading/{skill.yaml, prompts/}
├── article-to-x/{skill.yaml, prompts/}
└── research/{skill.yaml, prompts/}         # multiRound，暂未公开
```

---

## 启动配置

### 服务器配置
- **端口**: 8567
- **Context-path**: `/api`
- **Session Cookie**: 30 天过期
- **跨域**: 允许所有 origin patterns

### 数据库
| 组件 | 连接 |
|---|---|
| MySQL | localhost:3306/ai_passage_creator |
| Redis | localhost:6379, DB 0, 无密码 |

### 外部服务
| 服务 | 用途 | 配置项 |
|---|---|---|
| DashScope | LLM 调用 | `spring.ai.dashscope.api-key` |
| Tencent COS | 图片存储 | `tencent.cos.*` (secret-id/secret-key/region/bucket) |
| Pexels | 图库检索 | `pexels.api-key` |
| Stripe | 支付 | `stripe.api-key` / `webhook-secret` (测试模式) |
| Mermaid | 流程图 | `mermaid.cli-command: mmdc` |

### 数据库表（5张）
- **user**: Snowflake ID, userAccount, userPassword (MD5+salt), userRole (user/vip/admin), quota, vipTime
- **article**: taskId (UUID), userId, topic, userDescription, style, enabledImageMethods(JSON), titleOptions(JSON), outline(JSON), content, fullContent, images(JSON), status, phase, errorMessage
- **payment_record**: stripeSessionId, amount(USD), currency, status (PENDING/SUCCEEDED/FAILED/REFUNDED), productType (VIP_PERMANENT)
- **agent_log**: taskId, agentName, durationMs, status (SUCCESS/FAILED/RUNNING), prompt, inputData(JSON), outputData(JSON)
- **skill_execution**: skillExecutionId, skillName, userId, status, phase, inputData(JSON), outputData(JSON), tokenUsage, modelUsed, durationMs, errorMessage

---

## 核心模块详解

### 1. 多智能体编排 (agent/ 包)

基于 Spring AI Alibaba 的 `StateGraph` 构建 DAG 工作流，支持特征开关切换 StateGraph vs 传统串行执行。

**5 个 Agent:**
| Agent | 功能 | 输入 | 输出 |
|---|---|---|---|
| TitleGeneratorAgent | 生成 3-5 个标题方案 | topic, style | titleOptions: List<TitleOption> |
| OutlineGeneratorAgent | 生成结构化大纲 | mainTitle, subTitle, userDescription, style | outline: OutlineResult |
| ContentGeneratorAgent | 生成 Markdown 正文 | mainTitle, subTitle, outline | content: String |
| ImageAnalyzerAgent | 分析配图需求 | mainTitle, content, enabledMethods | Agent4Result (contentWithPlaceholders + imageRequirements) |
| ContentMergerAgent | 图文合成 | contentWithPlaceholders, images | fullContent: String |

**三阶段执行：**
- Phase1: `title_generator` (单节点) -> 用户选择标题
- Phase2: `outline_generator` (单节点) -> 用户编辑确认
- Phase3: `content_generator` -> `image_analyzer` -> `parallel_image_generator` -> `content_merger` (4节点流水线)

**Feature Toggle:**
```
article.agent.orchestrator.enabled=true
```
- true: 使用 StateGraph 编排
- false: 使用 ArticleAgentService 传统串行

**关键状态键:** 所有键使用 `ReplaceStrategy`
- taskId, topic, style, userDescription
- mainTitle, subTitle, titleOptions, outline
- content, contentWithPlaceholders, imageRequirements, images, fullContent
- enabledImageMethods

**已知挑战：StateGraph 跨 ClassLoader 类型转换**
```java
// 方案：className 字符串比较 + Gson 深拷贝
if (v.getClass().getName().equals(ArticleState.OutlineResult.class.getName())) {
    return (ArticleState.OutlineResult) v;
}
// 或使用 Gson 深拷贝
ArticleState.OutlineResult outline = GsonUtils.fromJson(GsonUtils.toJson(v), OutlineResult.class);
```

### 2. 异步与并发

**线程池配置 (AsyncConfig.java):**
- corePoolSize: 5
- maxPoolSize: 10
- queueCapacity: 100
- 拒绝策略: CallerRunsPolicy（背压机制）
- 线程名前缀: `article-async-`

**异步调度链路:**
```
Controller.createArticle()
  -> ArticleService.createArticleTaskWithQuotaCheck()  // 配额检查 + 任务创建（事务）
  -> ArticleAsyncService.executePhase1() @Async       // 阶段1异步
  -> 用户确认标题
  -> ArticleAsyncService.executePhase2() @Async       // 阶段2异步
  -> 用户确认大纲
  -> ArticleAsyncService.executePhase3() @Async       // 阶段3异步
```

**两阶段任务追踪:**
- `status`: PENDING -> PROCESSING -> COMPLETED / FAILED
- `phase`: PENDING -> TITLE_GENERATING -> TITLE_SELECTING -> OUTLINE_GENERATING -> OUTLINE_EDITING -> CONTENT_GENERATING

**状态机校验 (ArticlePhaseEnum.canTransitionTo()):**
```
PENDING -> TITLE_GENERATING
TITLE_GENERATING -> TITLE_SELECTING
TITLE_SELECTING -> OUTLINE_GENERATING
OUTLINE_GENERATING -> OUTLINE_EDITING
OUTLINE_EDITING -> CONTENT_GENERATING
CONTENT_GENERATING (终态)
```

### 3. SSE 流式推送

**SseEmitterManager:**
- `ConcurrentHashMap<String, SseEmitter>` 管理连接
- 10 分钟超时，3 秒重连时间
- 自动注册 timeout/completion/error 回调清理

**SseMessageTypeEnum (消息类型):**
| 类型 | 触发时机 |
|---|---|
| AGENT1_COMPLETE | 标题方案生成完成 |
| TITLES_GENERATED | 标题方案已生成（带数据） |
| AGENT2_STREAMING | 大纲流式输出（带前缀: "AGENT2_STREAMING:内容"） |
| AGENT2_COMPLETE | 大纲生成完成 |
| AGENT3_STREAMING | 正文流式输出（带前缀） |
| AGENT3_COMPLETE | 正文生成完成 |
| AGENT4_COMPLETE | 配图需求分析完成 |
| IMAGE_COMPLETE | 单张配图完成（带数据） |
| AGENT5_COMPLETE | 配图生成完成 |
| MERGE_COMPLETE | 图文合成完成 |
| ALL_COMPLETE | 全部完成 |
| ERROR | 错误 |

**StreamHandlerContext (ThreadLocal 桥接):**
```java
// StateGraph Agent 中无法直接传递 Consumer（不可序列化），使用 ThreadLocal 桥接
StreamHandlerContext.set(streamHandler);  // Agent 外设置
Consumer<String> handler = StreamHandlerContext.get();  // Agent 内获取
StreamHandlerContext.clear();  // finally 清理
```

### 4. 策略模式 (图片)

**ImageSearchService 接口 (6+1 实现):**
- `PexelsService`: Pexels API 图库检索
- `EmojiPackService`: Bing 图片搜索 (Jsoup 解析 HTML)
- `MermaidService`: CLI mmdc 流程图生成
- `IconifyService`: Iconify API 图标搜索
- `SvgDiagramService`: LLM 生成 SVG 代码
- `NANO_BANANA` (内置): DashScope 图片生成
- `PICSUM`: 降级方案 (Picsum.photos)

**ImageServiceStrategy 策略选择器:**
- `@PostConstruct` 自动注册所有 ImageSearchService 实现到 EnumMap
- `getImageAndUpload()`: 统一流程：策略调用 -> COS 上传 -> 降级回退
- 按 imageSource 分组，不同组并行，同组串行（避免 API 限流）

**图片来源权限控制:**
- 普通用户: PEXELS, MERMAID, ICONIFY, EMOJI_PACK
- VIP/Admin: 额外支持 NANO_BANANA, SVG_DIAGRAM

**ImageData 统一数据封装:**
- 三种类型: BYTES, URL, DATA_URL
- 工厂方法: `fromUrl()`, `fromDataUrl()`, `fromBytes()`
- 支持 base64 data URL 解码

### 5. AOP 日志

**@AgentExecution 注解:**
- 标记智能体方法，含 value 和 description 属性

**AgentExecutionAspect:**
- `@Around("@annotation(agentExecution)")` 拦截
- 记录: startTime, params (taskId/mainTitle), durationMs, status (RUNNING/SUCCESS/FAILED), prompt
- 异步保存 AgentLog 到数据库

**AopContext.currentProxy() 方案:**
```java
// Spring AOP 代理失效：同类内部方法调用不走代理
// 方案1：获取代理对象
private ArticleAgentService getProxy() {
    return (ArticleAgentService) AopContext.currentProxy();
}
// 方案2：启动类配置
@EnableAspectJAutoProxy(exposeProxy = true)
```

### 6. Stripe 支付

**VIP 定价:** $199 永久（ProductTypeEnum.VIP_PERMANENT）

**支付流程：**
```
用户点击购买 -> 创建 Stripe Checkout Session -> 重定向 Stripe
-> 支付成功 -> Stripe Webhook -> handlePaymentSuccess()
-> 更新 payment_record 状态 + 升级用户角色为 vip
```

**幂等性方案:**
```java
// 状态检查：SUCCEEDED 状态的不再处理
if (PaymentStatusEnum.SUCCEEDED.getValue().equals(record.getStatus())) {
    log.warn("支付记录已处理");
    return;
}
```

**Webhook Session 提取 (3 级 fallback):**
1. `event.getDataObjectDeserializer().getObject()`（直接反序列化）
2. `deserializeUnsafe()`（API 版本不匹配时）
3. `Session.retrieve(sessionId)`（从 JSON 手动提取 ID 后查询）

**退款：**
```java
// 撤销 VIP 身份：角色改为 user，quota 重置为 5，vipTime 置空
```

### 7. 配额管理

**并发安全的配额扣减 (SQL CAS):**
```sql
UPDATE user SET quota = quota - 1 WHERE id = ? AND quota > 0
```
- 使用影响行数判断是否成功
- Admin/VIP 不消耗配额
- 配额检查和任务创建在同一个 `@Transactional` 中

**配额回滚:** 如果任务创建失败，事务回滚后配额自动恢复

### 8. LLM JSON 修复算法

**问题:** LLM 输出 JSON 可能因 Token 限制被截断

**tryFixJson() 算法:**
1. 检查是否以 `{` 开头
2. 统计未闭合的大括号 `{` 和方括号 `[`
3. 如果字符串未闭合（`inString`），先补全引号
4. 按序补全缺失的 `]` 和 `}`
5. 规则: 不修复已以 `}` 结尾的 JSON（可能是格式问题而非截断）

### 9. 图片并行生成

**ParallelImageGenerator:**
```java
// 按 imageSource 分组 -> 不同组并行 + 同组串行
Map<String, List<ImageRequirement>> groupedBySource = requirements.stream()
    .collect(Collectors.groupingBy(ImageRequirement::getImageSource));

// CompletableFuture 并行执行
List<CompletableFuture<Void>> futures = groupedBySource.entrySet().stream()
    .map(entry -> CompletableFuture.runAsync(() -> { ... }))
    .toList();

CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
```

- `CopyOnWriteArrayList` 保证线程安全
- 最终按 position 排序

### 10. 文章风格

**4 种风格 (ArticleStyleEnum):**
- `tech`: 科技风格，专业严谨、数据驱动
- `emotional`: 情感风格，温暖细腻、故事化
- `educational`: 教育风格，通俗易懂、循序渐进
- `humorous`: 轻松幽默，网络流行语、俏皮话

风格通过 Prompt 附加内容实现，在 AGENT1~AGENT3 的 Prompt 后拼接对应风格的描述文本。

---

## 设计模式目录

| 模式 | 应用位置 |
|---|---|
| 策略模式 | ImageSearchService 接口 + ImageServiceStrategy |
| 状态机 | ArticlePhaseEnum.canTransitionTo() |
| 模板方法 | ImageSearchService 默认方法 getImage()/getImageData() |
| 观察者模式 | SSE: SseEmitterManager + Consumer streamHandler |
| 代理模式 | AopContext.currentProxy() |
| ThreadLocal | StreamHandlerContext |
| 工厂方法 | ImageData.from*() |
| 责任链 | Phase1 -> 等待 -> Phase2 -> 等待 -> Phase3 |
| CAS | decrementQuota SQL: `UPDATE ... WHERE quota > 0` |
| 特征开关 | agentConfig.isOrchestratorEnabled() |

---

## 已知技术挑战与解决方案

| 问题 | 解决方案 |
|---|---|
| StateGraph 跨 ClassLoader 类型转换 | className 字符串比较 + Gson 深拷贝 |
| Spring AOP 内部方法调用失效 | AopContext.currentProxy() + @EnableAspectJAutoProxy(exposeProxy=true) |
| SSE Consumer 不可序列化无法传入 StateGraph | StreamHandlerContext ThreadLocal 桥接 |
| LLM JSON 截断 | tryFixJson() 括号补全算法 |
| 并发配额扣减 | SQL CAS: `UPDATE ... WHERE quota > 0` |
| Stripe Webhook 幂等性 | 状态检查 + 3 级 Session 提取 |
| SSE 心跳保持 | comment heartbeat + 3 秒重连 + 10 分钟超时自动清理 |

---

## API 路由

| 路径 | 方法 | 功能 |
|---|---|---|
| `/article/create` | POST | 创建文章任务（配图方式可选） |
| `/article/confirm-title` | POST | 确认标题 + 启动大纲生成 |
| `/article/confirm-outline` | POST | 确认大纲 + 启动正文配图生成 |
| `/article/ai-modify-outline` | POST | AI 修改大纲 (VIP 专属) |
| `/article/progress/{taskId}` | GET (SSE) | 获取生成进度推送 |
| `/article/{taskId}` | GET | 文章详情 |
| `/article/list` | POST | 分页查询文章列表 |
| `/article/delete` | POST | 删除文章（逻辑删除） |
| `/article/execution-logs/{taskId}` | GET | 获取 Agent 执行日志 |
| `/user/register` | POST | 注册 |
| `/user/login` | POST | 登录 |
| `/user/logout` | POST | 登出 |
| `/user/get/login` | GET | 获取当前登录用户 |
| `/payment/create-vip-session` | POST | 创建 VIP 支付会话 |
| `/payment/refund` | POST | 申请退款 |
| `/payment/records` | GET | 获取支付记录 |
| `/webhook/stripe` | POST | Stripe Webhook 回调 |
| `/statistics/overview` | GET | 系统统计概览 (Admin) |
| `/health/` | GET | 健康检查 |
| `/skill/list` | GET | 列出公开 Skill |
| `/skill/{name}/definition` | GET | 获取 Skill 定义（含 `variables[*].uiType` 供前端渲染动态表单） |
| `/skill/{name}/execute` | POST | 执行 Skill（扣 1 配额，admin/VIP 豁免） |
| `/skill/{executionId}/progress` | GET (SSE) | Skill 执行进度推送 |
| `/skill/{executionId}/result` | GET | 获取执行结果（从 DB 查询） |
| `/skill/{executionId}/confirm` | POST | 多轮交互确认（**当前为桩实现**） |
| `/skill/executions` | POST | 分页查询执行历史（非 Admin 仅见本人） |

> **SSE 端点的状态码语义**：`GlobalExceptionHandler` 对携带
> `Accept: text/event-stream` 的请求返回**真实 HTTP 状态码**（401/403/404 等），
> 普通 REST 请求仍维持 `HTTP 200 + body 内业务错误码`的既有契约。
> 原因：SSE 若返回 200 但 Content-Type 不匹配，浏览器 EventSource 会无限重连。

---

## Skill 引擎 (skill/ 包)

配置驱动的通用 LLM 工作流引擎，与文章写作链路并行的第二条主线。
Skill 以 `src/main/resources/skills/{name}/` 下的 `skill.yaml` + `prompts/*.md` 声明，
无需写 Java 代码即可新增能力。

### 已注册 Skill

| Skill | 阶段数 | multiRound | 是否公开 |
|---|---|---|---|
| `topic-gen` | 1 | false | ✅ |
| `proofreading` | 3 | false | ✅ |
| `article-to-x` | 1 | false | ✅ |
| `research` | 2 | **true** | ❌ 仅内部（未加入 `PUBLIC_SKILLS`） |

公开范围由 `SkillController.PUBLIC_SKILLS` 白名单控制。

### 核心组件

| 组件 | 职责 |
|---|---|
| `SkillRegistry` | 启动时扫描并注册 skill.yaml，构建 StateGraph |
| `SkillDefinition` / `PhaseDefinition` / `VariableDef` | YAML 映射的定义模型 |
| `SkillNodeAction` | 通用 StateGraph 节点：渲染 Prompt → 调 LLM → 解析输出 → 采集用量 |
| `SkillExecution` | 单次执行的 POJO，负责状态流转与持久化 |
| `SkillExecutionService` | `@Async("skillExecutor")` 异步调度 + 失败退配额 |
| `SkillSseEmitterManager` | SSE 连接管理，**带事件缓冲重放**（解决订阅竞态） |
| `SkillEventFactory` | 统一构造 JSON 格式 SSE 事件 |
| `SkillContext` | `ConcurrentHashMap` 运行时上下文（替代 ThreadLocal，支持跨线程） |
| `ModelRouter` | 模型路由（agnes / dashscope）+ 降级 |
| `OutputParserRegistry` | 输出解析策略（json / markdown / topic-options） |

### SSE 事件类型（全部为 JSON）

`skill.started` / `skill.phase_started` / `skill.progress` /
`skill.phase_complete` / `skill.complete` / `skill.error`

> 与文章链路的 `SseMessageTypeEnum`（纯文本前缀格式）**不同**，Skill 侧统一 JSON。

### 配额与计费

- 每次执行扣 **1** 配额，admin/VIP 豁免（复用 `QuotaService` 的 SQL CAS）
- **派发失败**与**运行时失败**均通过 `QuotaService.refundQuota()` 退还
- `skill_execution.token_usage` / `model_used` 记录真实用量与生效模型
- 流式响应仅末尾分片携带整次用量，故取 **max 而非累加**

### 线程池

`skillExecutor`：core 5 / max 15 / queue 200 / `CallerRunsPolicy`，与文章写作线程池隔离。

---

## 错误码

| 码值 | 枚举 | 含义 |
|---|---|---|
| 0 | SUCCESS | 成功 |
| 40000 | PARAMS_ERROR | 请求参数错误 |
| 40100 | NOT_LOGIN_ERROR | 未登录 |
| 40101 | NO_AUTH_ERROR | 无权限 |
| 40300 | FORBIDDEN_ERROR | 禁止访问 |
| 40400 | NOT_FOUND_ERROR | 请求数据不存在 |
| 50000 | SYSTEM_ERROR | 系统内部异常 |
| 50001 | OPERATION_ERROR | 操作失败 |

---

## 面试备战索引

使用 `/mentor` 命令启动以下面试题目：

| 主题领域 | 核心知识点 | /mentor 命令 |
|---|---|---|
| 多智能体编排 | StateGraph DAG, 5个Agent分工, 串行+并行混合, ReplaceStrategy | `/mentor multi-agent-orchestration` |
| 异步与并发 | @Async + ThreadPoolTaskExecutor, CallerRunsPolicy, 两阶段状态追踪 | `/mentor async-concurrency` |
| SSE 流式通信 | SseEmitterManager, StreamHandlerContext(ThreadLocal), 16种消息类型 | `/mentor sse-streaming` |
| 策略模式 | ImageSearchService 6实现, EnumMap注册, COS上传, Picsum降级 | `/mentor strategy-pattern` |
| AI 生成与 LLM | DashScope, Prompt模板, 流式调用, JSON修复(tryFixJson) | `/mentor ai-generation` |
| Stripe 支付 | Checkout Session, Webhook签名, 幂等性, 3级Session提取, 退款 | `/mentor stripe-payment` |
| AOP 日志 | @AgentExecution注解, @Around切面, AopContext.currentProxy() | `/mentor aop-logging` |
| 配额与并发安全 | SQL CAS扣减, 事务回滚, Admin/VIP豁免 | `/mentor quota-management` |
| 系统架构 | 4层架构, 3阶段Human-in-loop, 设计模式目录 | `/mentor system-architecture` |
| 图片并行生成 | CompletableFuture, CopyOnWriteArrayList, 按source分组 | `/mentor parallel-image` |