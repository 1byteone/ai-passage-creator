# 灵犀写作 - 项目面试题库

> 本题库基于 AI Passage Creator (灵犀写作) 项目实际代码整理，覆盖架构设计、技术实现、疑难问题、项目经历模拟四个方向，共 50+ 题。
> 建议结合 `.claude/skills/mentor.md` 交互式陪练一起使用。

---

## 一、架构设计类

### Q1. 为什么选择 StateGraph 多智能体编排而非单一 Prompt 生成全部内容？

**难度:** ⭐⭐⭐  **方向:** 架构

**问题:** 你们的系统采用了多智能体编排（StateGraph）方案，为什么不是用一个大的 Prompt 让 AI 一次性生成标题、大纲、正文和配图？多智能体方案带来了哪些好处和成本？

**参考答案:**
单一 Prompt 模式下，Prompt 会非常长且复杂，LLM 在长上下文中注意力分散，输出质量下降。多智能体的优势：
- **任务解耦**：每个 Agent 专注单一子任务（标题/大纲/正文/图像），Prompt 精简，输出质量高
- **灵活编排**：支持串行（前序依赖）和并行（图片生成按源并行）混合
- **可观测**：每个 Agent 独立日志，故障定位快
- **Human-in-loop**：阶段间插入用户确认点，体验好

成本是增加了系统复杂度（StateGraph 配置、状态传递、跨 Agent 上下文维护）。

**追问:** StateGraph 中你们是如何处理 Agent 间数据传递的？
**提示点:** OverAllState + ReplaceStrategy + ArticleState 内部类
**评分要点:** 能讲清楚"为什么要拆"和"拆后的代价"，而不是只罗列好处

---

### Q2. 如何实现 AI 自动生成和用户交互的无缝衔接？

**难度:** ⭐⭐  **方向:** 架构

**问题:** 描述一下从用户输入主题到最终看到文章的完整流程，特别是 AI 自动生成和用户手动操作是如何交替进行的？

**参考答案:**
三阶段人机协作流程：
1. **Phase 1 - 标题生成**: 用户输入主题 → AI 生成 3-5 个标题方案 → SSE 推送 → 用户选择/自定义
2. **Phase 2 - 大纲生成**: AI 生成结构化大纲 → SSE 流式推送 → 用户编辑/确认（拖拽、行内编辑、AI 辅助）
3. **Phase 3 - 内容生成**: AI 生成正文 → 分析配图需求 → 并行生成多源配图 → 图文合成

关键：ArticlePhaseEnum 状态机严格约束阶段流转，每个阶段由用户操作触发下一阶段。

**追问:** 如果用户长时间不操作，会影响后续流程吗？
**提示点:** 异步方法执行完即释放线程，状态持久化到 DB
**评分要点:** 强调"阶段分离 + 状态持久化 + 用户触发推进"

---

### Q3. 系统整体架构如何分层？

**难度:** ⭐⭐  **方向:** 架构

**问题:** 从架构角度描述这个系统的分层设计，以及每层的核心职责。

**参考答案:**
系统分四层：
1. **Controller 层**: 处理 HTTP/SSE 连接（ArticleController、PaymentController、UserController、StripeWebhookController）
2. **Service 层**: 业务逻辑（ArticleServiceImpl 业务编排+配额+状态管理、ArticleAsyncService 异步调度+SSE 分发、PaymentServiceImpl 支付+幂等、QuotaServiceImpl CAS 扣减）
3. **Agent 层**: AI 推理（Orchestrator StateGraph 引擎 → Individual Agents NodeAction → Tools @Tool）
4. **横切关注点**: AOP（@AgentExecution 日志、@AuthCheck 权限）、Strategy（ImageServiceStrategy 配图）

**追问:** Agent 层和 Service 层的边界在哪？为什么不合并？
**提示点:** 关注点分离 — AI 推理 vs 业务逻辑
**评分要点:** 能清晰说出每层职责，强调横切关注点的独立性

---

### Q4. 为什么选择 SSE 而不是 WebSocket？

**难度:** ⭐⭐  **方向:** 架构

**问题:** 实时推送数据有很多方案，为什么选了 SSE？什么场景下 WebSocket 更合适？

**参考答案:**
本项目是服务端单向推送（AI 生成内容 → 前端展示），没有双向通信需求。SSE 优势：
- 浏览器原生 EventSource API，无需额外客户端库
- 基于 HTTP 协议，兼容性好，Vite/Nginx 代理无需特殊配置
- 自动重连机制
- 实现轻量

WebSocket 适合需要双向实时通信的场景（如聊天、协作编辑）。

**追问:** SSE 连接断开后怎么处理？
**提示点:** EventSource 原生自动重连 + 后端 onTimeout 回调清理
**评分要点:** 明确"单向 vs 双向"是选型核心依据

---

### Q5. 异步任务体系的设计思路是什么？

**难度:** ⭐⭐⭐  **方向:** 架构

**问题:** 文章生成涉及多次 AI 调用（耗时 30s+），如何避免接口阻塞？异步架构的具体实现？

**参考答案:**
ThreadPoolTaskExecutor 自定义线程池（core=5, max=10, queue=100, CallerRunsPolicy），@Async("articleExecutor") 异步执行。三阶段分别异步调度：
- Controller 收到请求后立即返回 taskId
- @Async 方法在线程池中执行 AI 调用
- 进度通过 SSE 实时推送
- CallerRunsPolicy 作为背压机制，队列满时由调用线程执行

**追问:** 为什么 core=5？为什么用 CallerRunsPolicy 而不是 AbortPolicy？
**提示点:** AI 调用是 IO 密集型 + 外部 API 并发限制
**评分要点:** 能讲清线程池参数的"为什么"，特别是背压机制

---

### Q6. 为什么需要 status + phase 双重状态字段？

**难度:** ⭐⭐  **方向:** 架构

**问题:** article 表设计了 status 和 phase 两个状态字段，为什么不是一个字段？

**参考答案:**
- **status**（ArticleStatusEnum）: PENDING → PROCESSING → COMPLETED/FAILED，粗粒度，适合查询分组和列表展示
- **phase**（ArticlePhaseEnum）: PENDING → TITLE_GENERATING → TITLE_SELECTING → ... → CONTENT_GENERATING，细粒度，支持阶段流转验证

双重状态分离了"生命周期"和"工作流阶段"两个关注点，便于独立演进和查询优化。

**追问:** phase 状态机是如何校验合法流转的？
**提示点:** ArticlePhaseEnum.canTransitionTo()
**评分要点:** 强调"关注点分离"的设计理念

---

### Q7. 系统的扩展性体现在哪些方面？

**难度:** ⭐⭐⭐  **方向:** 架构

**问题:** 如果要接入一个新的图片来源（如百度图片），代码需要改哪些地方？

**参考答案:**
只需新增一个类实现 ImageSearchService 接口并注册为 Spring Bean，其他代码无需改动。扩展性设计：
- **策略模式**: 新增图源只需实现接口，@PostConstruct 自动注册
- **StateGraph 编排**: 增减 Agent 节点只需修改图定义
- **异步线程池**: 参数可配置
- **阶段状态机**: 可在枚举中插入新阶段
- **Feature Toggle**: StateGraph 和顺序执行可切换

**追问:** 如果新增一个 Agent 节点呢？
**提示点:** 实现 NodeAction + 注册到 StateGraph
**评分要点:** 能举出多个扩展点，体现"开闭原则"

---

### Q8. 系统如何应对高并发场景？

**难度:** ⭐⭐⭐  **方向:** 架构

**问题:** 假设 1000 个用户同时创建文章任务，系统会出什么问题？

**参考答案:**
关键瓶颈分析：
- **配额扣减**: SQL CAS（UPDATE ... WHERE quota > 0）保证原子性，不会超扣
- **线程池**: 核心 5 线程处理任务入队（queue=100），超出的触发 CallerRunsPolicy 背压
- **AI 调用**: 外部 API 限制并发，需异步排队，超时控制
- **数据库**: 文章创建是单条 insert，压力不大
- **SSE 连接**: SseEmitter 存储在本地内存，多实例需改为 Redis Pub/Sub

主要瓶颈在 AI 外部 API 限流和线程池容量。

**追问:** 如果要支持多实例部署，SSE 怎么改造？
**提示点:** Redis Pub/Sub 广播 + Stick Session 或网关层路由
**评分要点:** 能识别瓶颈并给出改进方向

---

### Q9. 为什么选择 MyBatis-Flex？

**难度:** ⭐⭐  **方向:** 架构

**问题:** ORM 框架是怎么选型的？

**参考答案:**
MyBatis-Flex 优势：
- 无侵入设计，不修改 MyBatis 行为
- Lambda 查询类型安全
- 性能优于 MyBatis-Plus（无拦截器链损耗）
- 代码生成能力强
- 与 Spring Boot 3 兼容性好

本项目表结构简单（4 张表），不需要 JPA 的级联、懒加载等复杂特性。

**追问:** MyBatis-Flex 和 MyBatis-Plus 的核心区别？
**提示点:** 查询实现方式（Lambda vs Wrapper）
**评分要点:** 有真实的对比思考，而不是盲选

---

### Q10. 用户会话为什么存储在 Redis？

**难度:** ⭐⭐  **方向:** 架构

**问题:** 为什么不用传统 Tomcat Session 而用 Redis？

**参考答案:**
传统 Tomcat Session 存储在本地内存，多实例部署时需要 Session 黏滞或同步复制。Spring Session Data Redis 将 Session 存储在 Redis 中，实现多实例共享。配置简单（仅 store-type=redis + timeout），代码零入侵。JSON 序列化避免 JDK 序列化的跨版本兼容问题。

**追问:** Redis 挂了会怎样？
**提示点:** Session 失效 → 用户需重新登录，不影响业务数据
**评分要点:** 理解分布式 Session 的必要性

---

### Q11. 系统的"关注点分离"体现在哪？

**难度:** ⭐⭐⭐  **方向:** 架构

**问题:** 这个项目在关注点分离方面有哪些体现？

**参考答案:**
- **Agent 层 vs Service 层分离**: Agent 专注 AI Prompt 调用，Service 专注业务逻辑
- **图像源策略分离**: 每种图源自成 Service，通过统一接口交互
- **AOP 横切分离**: 日志、权限作为横切关注点
- **异步线程池隔离**: AI 任务线程池与 Web 请求线程池分离
- **SSE 管理分离**: SseEmitterManager 独立管理连接生命周期
- **前端组件化**: ArticleCreatePage 主控 + 子组件

**追问:** 这些分离带来了什么好处？
**提示点:** 可测试性、可维护性、可扩展性
**评分要点:** 能从多个层面举例，体现系统性思维

---

### Q12. 如果让用户自定义 Agent 执行顺序，架构怎么改？

**难度:** ⭐⭐⭐  **方向:** 架构

**问题:** 假如将来支持"工作流画布"功能，用户拖拽配置 Agent 顺序，现有架构支撑得了吗？

**参考答案:**
核心思路是将"代码固化的编排逻辑"提取为"元数据驱动"：
- StateGraph 定义配置化：从 Java 代码改为 JSON/DB 定义（Node + Edge 列表）
- Agent 可配置化：节点通过 className/BeanName 动态加载
- 从 DB 读取图定义动态编译 Graph
- 前端提供可视化编排界面
- 状态字段需扩展支持动态 Phase

**追问:** 动态编译 Graph 有什么风险？
**提示点:** 循环依赖、类型安全、性能
**评分要点:** 体现"配置化 vs 硬编码"的架构演进思维

---

### Q13. 人机协作三阶段流程的优缺点？

**难度:** ⭐⭐  **方向:** 架构

**问题:** 三阶段流程（生成→确认→生成）有什么优缺点？

**参考答案:**
优点：
- 用户参与感强，可干预生成方向
- 每阶段输出质量可控（标题不满意可重新选）
- 容错性好，错误早发现早纠正

缺点：
- 用户操作步骤多，转化率可能受影响
- 阶段间等待，整体耗时增加
- 状态管理复杂（需持久化中间状态）

**追问:** 如何平衡自动化和用户控制？
**提示点:** 提供"全自动模式"开关 + 默认值
**评分要点:** 能辩证看待，不一边倒

---

### Q14. 如果优化系统架构，你会怎么改？

**难度:** ⭐⭐⭐  **方向:** 架构

**问题:** 以你现在的视角看，这个架构最大的不足是什么？

**参考答案:**
- **SSE 连接本地存储**: 改用 Redis Pub/Sub 广播，支持多实例
- **无消息队列**: 引入 RabbitMQ/RocketMQ 削峰
- **无 AI 结果缓存**: 引入语义相似度匹配缓存
- **Phase 状态硬编码**: 配置化定义流程
- **无监控告警**: 接入 Prometheus + Grafana

**追问:** 优先级怎么排？
**提示点:** 按瓶颈严重程度 + 实现成本
**评分要点:** 有优先级判断，而不是罗列所有方案

---

### Q15. 多智能体编排和传统工作流引擎（如 Activiti）的区别？

**难度:** ⭐⭐⭐  **方向:** 架构

**问题:** StateGraph 和传统 BPM 工作流引擎有什么区别？

**参考答案:**
- **定位不同**: 传统工作流引擎面向业务流程审批（人驱动），StateGraph 面向 AI 任务编排（Agent 驱动）
- **节点类型**: 传统是人工节点/系统节点，StateGraph 是 AI Agent 节点
- **状态传递**: 传统是表单数据，StateGraph 是结构化 AI 输出
- **执行模式**: 传统偏串行审批，StateGraph 支持并行 AI 调用
- **复杂度**: 传统引擎重（持久化、回退、会签），StateGraph 轻（DAG 执行）

**追问:** StateGraph 适合什么场景？
**提示点:** AI 任务编排、多步推理、工具调用
**评分要点:** 理解两者的应用场景差异

---

## 二、技术实现类

### Q16. StateGraph 的 State Key 为什么用 ReplaceStrategy？

**难度:** ⭐⭐  **方向:** 技术

**问题:** 为什么 StateGraph 中所有 Key 都用 ReplaceStrategy？

**参考答案:**
每个 Agent 输出的是独立的 State Key（titleOptions、outline、content 等），不是同一个 Key 的不同字段，所以新值替换旧值即可，不需要 MergeStrategy 合并。如果多个 Agent 修改同一 Key 的不同字段，才需要 MergeStrategy。

**追问:** MergeStrategy 适合什么场景？
**提示点:** 多 Agent 并发修改同一对象的多个字段
**评分要点:** 理解策略选择的依据

---

### Q17. StreamHandlerContext 是怎么工作的？

**难度:** ⭐⭐  **方向:** 技术

**问题:** StreamHandlerContext 这个类的作用和实现原理？

**参考答案:**
StreamHandlerContext 是 ThreadLocal<Consumer<String>> 的封装。作用是在 StateGraph Agent 中传递 SSE 流式处理器。因为 StateGraph 会对状态做序列化/反序列化，而 Consumer Lambda 不可序列化，所以通过 ThreadLocal 侧通道传递。使用时在 Graph.execute() 前 set，Agent 内部 get 使用，finally 中 clear。

**追问:** 为什么不用方法参数传递？
**提示点:** NodeAction 接口签名固定 + 不可序列化
**评分要点:** 理解"序列化限制"这个根本原因

---

### Q18. @AgentExecution AOP 如何记录日志？

**难度:** ⭐⭐  **方向:** 技术

**问题:** 怎么记录每个 Agent 的执行日志？数据结构和流程？

**参考答案:**
@Around 环绕通知：
1. 执行前: 记录 startTime，收集参数（taskId、agentName），创建 RUNNING 状态的 AgentLog
2. 执行后: 记录 endTime，计算 durationMs，状态设为 SUCCESS，记录 outputData 摘要
3. 异常时: 状态设为 FAILED，记录 errorMessage，重新抛出
4. finally: 异步保存 AgentLog 到数据库

数据表 agent_log: taskId, agentName, startTime, endTime, durationMs, status, errorMessage, prompt, inputData(JSON), outputData(JSON)

**追问:** 为什么用异步保存日志？
**提示点:** 不阻塞主流程 + 日志可接受少量丢失
**评分要点:** 理解 AOP 环绕通知的执行时机

---

### Q19. 为什么用 AopContext.currentProxy()？

**难度:** ⭐⭐⭐  **方向:** 技术

**问题:** ArticleAgentService 中为什么有 AopContext.currentProxy()？不这样做会怎样？

**参考答案:**
Spring AOP 基于代理模式，只有通过代理对象调用的方法才会触发切面。当方法 A 内部直接调用方法 B（this.B()）时，this 是原始对象不是代理对象，所以 B 上的 @AgentExecution 注解不会生效。AopContext.currentProxy() 获取当前 AOP 代理对象，通过代理调用 B 使切面生效。需要 @EnableAspectJAutoProxy(exposeProxy=true) 开启。

**追问:** 还有其他解决方案吗？
**提示点:** 注入自身 Bean / 拆分到不同类
**评分要点:** 理解 AOP 代理失效的根本原因

---

### Q20. tryFixJson() 怎么修复截断 JSON？

**难度:** ⭐⭐⭐  **方向:** 技术

**问题:** LLM 输出 JSON 可能被截断，怎么修复？算法通用吗？

**参考答案:**
统计未闭合的 { 和 [ 数量。扫描字符串：
- 遇到 { +1，遇到 } -1（忽略引号内的）
- 遇到 [ +1，遇到 ] -1（忽略引号内的）
- 如果扫描中途在字符串中截断（inString=true），先补全结束引号
- 最终 count > 0 表示缺失对应数量的关闭符，按序补全

这个算法对标准 JSON 截断修复有效，但不是万能的（嵌套复杂字符串等场景可能失败）。

**追问:** 为什么不直接用宽松解析？
**提示点:** 宽松解析仍需基本结构完整
**评分要点:** 理解算法的局限性

---

### Q21. ImageServiceStrategy 如何自动注册策略？

**难度:** ⭐⭐  **方向:** 技术

**问题:** 新增一个图片来源类后，ImageServiceStrategy 会自动识别吗？原理？

**参考答案:**
是的。@PostConstruct 方法遍历 ApplicationContext，获取所有 ImageSearchService 类型 Bean，调用每个 Bean 的 getMethod() 获取枚举标识，注册到 EnumMap<ImageMethodEnum, ImageSearchService> 中。新增实现只需实现接口、注册为 Spring @Service，自动被扫描注册。

**追问:** 如果两个实现返回相同的 getMethod() 会怎样？
**提示点:** 后注册的覆盖前者 / 启动报错
**评分要点:** 理解 Spring 依赖注入的机制

---

### Q22. SseEmitterManager 如何管理连接？

**难度:** ⭐⭐  **方向:** 技术

**问题:** 多个用户同时建立 SSE 连接，怎么管理？

**参考答案:**
ConcurrentHashMap<String, SseEmitter> 以 taskId 为 Key 存储。createEmitter(taskId) 创建 SseEmitter（10 分钟超时），注册 onCompletion/onTimeout/onError 回调自动从 Map 移除。sendHeartbeat(taskId) 发送 comment 类型消息保持连接。complete(taskId) 手动结束并清理。线程安全的 ConcurrentHashMap 支持并发操作。

**追问:** 为什么用 taskId 而不是 userId 作为 Key？
**提示点:** 一个用户可能同时创建多个任务
**评分要点:** 理解连接标识的选择

---

### Q23. ParallelImageGenerator 的并行策略？

**难度:** ⭐⭐⭐  **方向:** 技术

**问题:** 多张图片是怎么并行生成的？不同源和同源有什么区别？

**参考答案:**
按 imageSource 分组。不同组的图片通过 CompletableFuture.runAsync 并行执行。同组内的图片串行执行（避免 API 限流）。结果统一收集到 CopyOnWriteArrayList（线程安全），生成完成后按 position 排序。

**追问:** 为什么同组要串行？
**提示点:** 第三方 API 有 QPS 限制
**评分要点:** 理解"分组并行"的设计权衡

---

### Q24. @Async 在 Spring 中如何工作？

**难度:** ⭐⭐  **方向:** 技术

**问题:** @Async 为什么能异步执行？同类内部方法调用 @Async 会生效吗？

**参考答案:**
@EnableAsync 启用后，Spring 为 @Async 标注的方法创建 AOP 代理，将方法调用提交到指定的 TaskExecutor 线程池执行，调用方立即返回。同类内部方法调用 @Async 不会生效（同 AOP 问题），需要通过 AopContext.currentProxy() 或注入自身 Bean 调用。

**追问:** @Async 默认用哪个线程池？
**提示点:** SimpleAsyncTaskExecutor（每次新建线程，不推荐）
**评分要点:** 理解代理机制 + 自定义线程池的必要性

---

### Q25. Stripe Webhook 安全性？

**难度:** ⭐⭐  **方向:** 技术

**问题:** Stripe Webhook 被恶意调用会怎样？怎么保证安全？

**参考答案:**
两重保障：
- **签名验证**: Stripe SDK 的 Webhook.constructEvent(payload, sigHeader, secret) 验证请求确实来自 Stripe
- **幂等性**: 处理支付成功前检查记录 status，已经是 SUCCEEDED 的直接跳过

Webhook 端点不校验用户登录状态，但签名验证即可保证安全。

**追问:** 签名验证的原理是什么？
**提示点:** HMAC + timestamp 防重放
**评分要点:** 理解签名 + 幂等双层防护

---

### Q26. 配额扣减 SQL 为什么是原子性的？

**难度:** ⭐⭐⭐  **方向:** 技术

**问题:** 并发场景下配额扣减怎么解决超扣？UPDATE quota = quota - 1 WHERE quota > 0 为什么安全？

**参考答案:**
单条 UPDATE SQL 是数据库原子操作。quota > 0 条件确保不会扣成负数。返回的 affectedRows（1 成功 / 0 失败）判断是否扣减成功。整个过程不需要锁或事务传输。

如果不用 CAS：先用 SELECT 查询配额，判断 > 0 再 UPDATE，两个操作之间有间隙，并发时多个线程读到 quota=1 都执行 UPDATE，导致 quota 变成负数（竞态条件）。

**追问:** 为什么不用 synchronized？
**提示点:** 单实例有效，分布式失效 + 性能差
**评分要点:** 理解 CAS vs 锁的差异

---

### Q27. 前端 SSE 连接如何建立和处理？

**难度:** ⭐⭐  **方向:** 技术

**问题:** 前端怎么建立 SSE 连接？怎么处理不同类型的消息？

**参考答案:**
sse.ts 的 connectSSE(taskId, options) 创建 new EventSource(/api/article/progress/${taskId})。消息解析为 JSON，type 字段驱动 ArticleCreatePage 的 currentPhase 状态机。switch(msg.type) 处理：
- TITLES_GENERATED → 展示标题选择
- AGENT2_STREAMING → 流式渲染大纲
- AGENT3_STREAMING → 流式渲染内容
- IMAGE_COMPLETE → 更新图片进度
- ALL_COMPLETE → 展示最终文章
- ERROR → 错误处理

**追问:** EventSource 怎么处理断线重连？
**提示点:** 原生 readyState + 自动重连
**评分要点:** 理解 SSE 消息驱动状态机的模式

---

### Q28. ArticleCreatePage 多阶段状态机设计？

**难度:** ⭐⭐  **方向:** 技术

**问题:** currentPhase 是怎么流转的？驱动机制是什么？

**参考答案:**
7 个阶段：INPUT → TITLE_GENERATING → TITLE_SELECTING → OUTLINE_GENERATING → OUTLINE_EDITING → CONTENT_GENERATING → COMPLETED。驱动机制是 SSE 消息 + 用户操作的双重驱动：SSE 消息自动推进自动阶段（正在生成中），用户点击确认推进交互阶段（选择标题、编辑大纲）。

**追问:** 为什么不用 Promise 链而是 SSE 驱动？
**提示点:** SSE 支持流式输出 + 服务端主动推送
**评分要点:** 理解"事件驱动"vs"流程驱动"

---

### Q29. OutlineEditingStage 拖拽排序实现？

**难度:** ⭐⭐  **方向:** 技术

**问题:** 大纲编辑器的拖拽排序怎么实现的？支持几层？

**参考答案:**
基于 sortablejs 库绑定到大纲列表容器。onEnd 回调更新响应式数组实现重新排序。支持两层：节（section）级拖拽 + 点（point）级拖拽。同时支持行内添加/删除节和点，以及 AI 辅助修改（VIP 专属）。

**追问:** 拖拽后数据怎么同步到后端？
**提示点:** 用户点击确认时调用 confirmOutline API
**评分要点:** 理解前端交互 + 后端同步的分离

---

### Q30. COS 图片上传如何适配不同数据类型？

**难度:** ⭐⭐  **方向:** 技术

**问题:** 图片来源不同（API/CLI/LLM/爬虫），返回格式也不同，怎么统一处理上传？

**参考答案:**
统一封装 ImageData 类支持三种数据类型：BYTES（字节数组）、URL（网络 URL）、DATA_URL（Base64 编码）。CosService.uploadImageData() 根据 dataType 分支处理：
- BYTES 直接上传流
- URL 用 OkHttp 下载再上传
- DATA_URL 先 Base64 解码再上传

统一生成 UUID 文件名，返回 CDN URL。

**追问:** 为什么用 UUID 文件名？
**提示点:** 避免冲突 + 防止覆盖
**评分要点:** 理解多态封装的设计

---

### Q31. 文章图片占位符怎么工作？

**难度:** ⭐⭐  **方向:** 技术

**问题:** AI 生成正文时怎么标注需要插入图片的位置？

**参考答案:**
ContentGeneratorAgent 生成内容时插入 {{IMAGE_PLACEHOLDER_N}}。ImageAnalyzerAgent 分析图片需求时生成 imageRequirements，每个 requirement 也包含 placeholderId。ContentMergerAgent 遍历 images 列表，用正则匹配替换 {{IMAGE_PLACEHOLDER_N}} 为 ![](imageUrl)。

**追问:** 为什么用占位符而不是直接嵌入图片 URL？
**提示点:** 图片生成是并行的，正文生成时图片还没好
**评分要点:** 理解"生成顺序"决定的设计

---

### Q32. 前端 Markdown 渲染？

**难度:** ⭐  **方向:** 技术

**问题:** 后端返回的 Markdown 格式文章，前端怎么展示？

**参考答案:**
使用 marked 库将 Markdown 字符串转换为 HTML（支持标题、列表、代码块、表格、图片等标准语法），通过 v-html 指令渲染。utils/markdown.ts 封装了 markdownToHtml(markdown) 函数。

**追问:** v-html 有什么安全风险？
**提示点:** XSS 攻击，需要 sanitize
**评分要点:** 知道 XSS 风险及防范

---

### Q33. AuthInterceptor 权限控制？

**难度:** ⭐⭐  **方向:** 技术

**问题:** @AuthCheck 注解是怎么做到只允许 admin 访问特定接口的？

**参考答案:**
@Around 环绕 @AuthCheck 标注的方法。从 HTTP Session 获取当前用户信息，比较用户角色与 mustRole。角色层级简单：admin 可以访问所有接口，user/vip 不能访问 admin 专属接口。如果 mustRole 为 null 或空则放行（公共接口）。

**追问:** 这种角色层级有什么局限？
**提示点:** 不支持细粒度权限（如 RBAC）
**评分要点:** 理解 AOP 权限校验的执行时机

---

### Q34. Vue Router 路由守卫？

**难度:** ⭐  **方向:** 技术

**问题:** 前端路由权限控制怎么实现？未登录用户访问 /admin 会怎样？

**参考答案:**
router.beforeEach 路由守卫。首次加载时调用 loginUserStore.fetchLoginUser() 初始化用户状态。对于 /admin 路径，检查 userRole !== 'admin' 则重定向到 /user/login?redirect=原路径。其他路由均开放访问。

**追问:** 前端权限控制和后端 @AuthCheck 重复吗？
**提示点:** 不重复，前端是体验优化，后端是安全保障
**评分要点:** 理解前后端权限校验的分工

---

### Q35. ArticleState 核心共享状态设计？

**难度:** ⭐⭐  **方向:** 技术

**问题:** ArticleState 包含哪些内部类？为什么这样设计？

**参考答案:**
7 个内部静态类：
- TitleOption（mainTitle, subTitle）
- TitleResult（mainTitle, subTitle）
- OutlineResult（List<OutlineSection>）
- OutlineSection（section, title, points）
- ImageRequirement（position, type, sectionTitle, keywords, imageSource, prompt, placeholderId）
- ImageResult（position, url, method, keywords）
- Agent4Result（contentWithPlaceholders, imageRequirements）

每个内部类对应一个 Agent 的输出格式，职责单一，便于 StateGraph 中各节点独立处理。

**追问:** 为什么用内部静态类而不是独立文件？
**提示点:** 内聚性强 + 减少文件数量
**评分要点:** 理解"状态对象"的职责划分

---

## 三、疑难问题类

### Q36. StateGraph 跨 ClassLoader 类型转换问题

**难度:** ⭐⭐⭐  **方向:** 疑难

**问题:** StateGraph 跨 ClassLoader 类型转换问题具体是什么？怎么排查和解决的？

**参考答案:**
StateGraph 内部对状态做序列化/反序列化时，对象的 ClassLoader 变了。当用 instanceof 检查类型时，即使类名完全一样也会返回 false。排查时发现 Agent 中自定义类型（如 ArticleState.OutlineResult）的 instanceof 判断总是 false。解决：
- 用 getClass().getName() 字符串比较替代 instanceof
- 用 GsonUtils 的 toJson/fromJson 做深拷贝，重建正确的类型实例

**追问:** 为什么会出现 ClassLoader 不同？
**提示点:** 序列化/反序列化使用不同的 ClassLoader
**评分要点:** 能讲清"现象→排查→解决"的完整链路

---

### Q37. Spring AOP 内部方法调用失效

**难度:** ⭐⭐⭐  **方向:** 疑难

**问题:** Spring AOP 为什么内部方法调用不生效？具体怎么解决？

**参考答案:**
Spring AOP 基于 JDK 动态代理或 CGLIB 代理，只有外部调用（通过代理对象）才触发切面。ArticleAgentService.agent1() 中内部调用 agent2()，agent2() 上的 @AgentExecution 注解不会触发 AOP 切面。解决：在 ArticleAgentService 内部用 AopContext.currentProxy() 获取代理对象，通过代理对象调用内部方法。需要 @EnableAspectJAutoProxy(exposeProxy = true) 启用。

**追问:** 为什么 Spring AOP 不支持内部调用？
**提示点:** 代理是包装外部引用，this 指向原始对象
**评分要点:** 理解代理模式的本质局限

---

### Q38. SSE Consumer 不可直接传入 StateGraph

**难度:** ⭐⭐⭐  **方向:** 疑难

**问题:** StateGraph 中怎么实现流式输出？SSE 的 Consumer 为什么不能直接传给 Agent？

**参考答案:**
StateGraph 的 OverAllState 中所有字段会经过序列化/反序列化，而 Consumer<String> 是 Lambda 表达式，不可序列化。直接用会抛 NotSerializableException。解决：StreamHandlerContext（ThreadLocal 封装）作为侧通道，在 Graph.execute() 前 set，Agent 内部 get 使用，finally 中 clear。

**追问:** ThreadLocal 有什么风险？
**提示点:** 内存泄漏 + 线程池复用导致脏数据
**评分要点:** 理解 ThreadLocal 的使用注意事项

---

### Q39. LLM JSON 截断/格式错误处理

**难度:** ⭐⭐  **方向:** 疑难

**问题:** 如果 LLM 返回的 JSON 格式不对或缺斤少两，有哪几层保障？

**参考答案:**
三层保障：
- **Prompt 层**: 明确要求 JSON 格式，用 FixedFormat 指定结构化输出格式
- **代码层**: tryFixJson() 算法补全截断的括号
- **解析层**: Gson 的 Lenient 模式（宽松解析），容忍小格式问题

三层结合显著降低解析失败率。

**追问:** 为什么不直接用 StructuredOutputConverter？
**提示点:** 部分场景需要更灵活的处理
**评分要点:** 理解多层防御的设计

---

### Q40. 并发场景配额超扣问题

**难度:** ⭐⭐⭐  **方向:** 疑难

**问题:** 配额扣减的并发安全问题？为什么不用 synchronized？

**参考答案:**
传统"读-判断-写"三步操作有竞态条件：线程 A 读 quota=1，判断 >0，线程 B 也读 quota=1，线程 A 扣为 0，线程 B 也扣为 -1（超扣）。synchronized 锁住整个方法在单实例有效，但在分布式多实例部署时失效。

方案：SQL CAS 单条原子操作 UPDATE user SET quota = quota - 1 WHERE id = ? AND quota > 0，数据库保证原子性，quota > 0 条件防止负值，affectedRows 判断成功失败。天然线程安全、分布式安全。

**追问:** 如果扣减后业务失败了，配额怎么回滚？
**提示点:** @Transactional 事务回滚
**评分要点:** 理解 CAS + 事务的配合

---

### Q41. Stripe Webhook 重复回调

**难度:** ⭐⭐  **方向:** 疑难

**问题:** Stripe 重复发送 Webhook（网络问题重试），怎么防止重复处理？

**参考答案:**
幂等性处理：handlePaymentSuccess() 中先根据 stripeSessionId 或 paymentIntentId 查找已存在记录，如果 status 已经是 SUCCEEDED 则直接返回（log warn），不再重复处理。这是状态检查幂等，简单有效，无需分布式锁。

**追问:** 状态检查幂等有什么局限？
**提示点:** 高并发下仍可能有竞态（需唯一索引兜底）
**评分要点:** 理解幂等设计的多种方案

---

### Q42. Session Redis JSON vs JDK 序列化

**难度:** ⭐⭐  **方向:** 疑难

**问题:** Spring Session 默认用 JDK 序列化，为什么改成 JSON？

**参考答案:**
JDK 序列化问题：
- 二进制不可读
- 跨版本兼容性差（类字段变更导致反序列化失败）
- 需要序列化类在 ClassPath 中
- 安全漏洞历史

JSON 序列化（GenericJackson2JsonRedisSerializer + JavaTimeModule）：
- 可读
- 跨语言
- 版本演进友好
- 不需要类实现 Serializable

**追问:** JSON 序列化有什么缺点？
**提示点:** 体积稍大 + 类型信息丢失
**评分要点:** 能辩证对比两种方案

---

### Q43. Mermaid 图表生成坑

**难度:** ⭐⭐  **方向:** 疑难

**问题:** Mermaid 图表生成在实际使用中遇到什么问题？

**参考答案:**
- 首次调用需要安装 Node.js 环境，mmdc CLI 启动慢
- 输出路径需要绝对路径，相对路径容易找不到
- 超时问题：默认 30s 超时，大图可能超时
- 依赖本地环境，部署到 Docker 时需要额外安装 Node

**追问:** 如何优化 Mermaid 生成性能？
**提示点:** 预渲染服务 + 缓存
**评分要点:** 有实际踩坑经验

---

### Q44. 图片生成失败降级策略

**难度:** ⭐⭐  **方向:** 疑难

**问题:** 某个图片来源（如 Pexels API 挂了）失败了，会影响整个文章生成吗？

**参考答案:**
不会。ImageServiceStrategy.getImageAndUpload() 中每个图片生成包装在 try-catch 中。任一源失败后，统一调用 getFallbackImage()（返回 Picsum 随机图片 URL），同样上传 COS。保证文章配图不缺失，只是降级为随机图。

**追问:** 降级图片和原图差异大，用户体验怎么处理？
**提示点:** 标注"占位图" + 提供重新生成入口
**评分要点:** 理解容错 vs 用户体验的平衡

---

### Q45. 前端 SSE 连接中断恢复

**难度:** ⭐⭐  **方向:** 疑难

**问题:** 前端页面刷新或 SSE 连接断了，正在生成的文章怎么办？

**参考答案:**
EventSource 原生支持自动重连。后端 SseEmitterManager 通过 onCompletion/onTimeout 回调自动清理断开的连接。前端刷新后重新进入 ArticleCreatePage，调用 getArticle(taskId) 获取当前 phase，展示对应状态。生成中的内容通过后端已保存的数据恢复显示。

**追问:** 如果后端异步任务还在跑，但 SSE 连接断了，任务结果怎么通知前端？
**提示点:** 任务完成后写入 DB，前端轮询或重连后查询
**评分要点:** 理解"连接断开"vs"任务中断"的区别

---

### Q46. 跨域问题解决

**难度:** ⭐  **方向:** 疑难

**问题:** 前后端分离开发时跨域怎么处理？

**参考答案:**
双方案：
- **开发环境**: Vite proxy 配置 /api 代理到 localhost:8567，浏览器请求同源
- **后端**: CorsConfig 配置跨域允许（allowedOrigins, allowedMethods, allowedHeaders, allowCredentials）

生产环境 Nginx 反向代理不涉及跨域。

**追问:** 为什么不只用后端 CORS？
**提示点:** 开发体验 + Cookie 跨域复杂
**评分要点:** 理解开发/生产环境的差异

---

### Q47. AI 生成标题质量不高怎么办？

**难度:** ⭐⭐  **方向:** 疑难

**问题:** 如果 AI 生成的 5 个标题都不满意怎么办？

**参考答案:**
双重保障：
- Prompt 要求生成 3-5 个标题，提供多个选择
- TitleSelectingStage 支持用户自定义输入标题，不强制从 AI 生成的选项中选择

用户可以使用"自定义"功能手动输入标题，点击确认后进入下一阶段。

**追问:** 如何提升 AI 生成标题的质量？
**提示点:** Few-shot 示例 + 风格引导 + 温度参数
**评分要点:** 理解 Prompt 工程的优化方向

---

## 四、项目经历模拟

### 场景 1: 项目介绍自由叙述

**引导语:** 请先介绍一下这个项目，包括项目背景、技术栈、你在其中的角色，以及核心功能。

**参考答案要点:**
- **项目名称**: 灵犀写作（AI Passage Creator）
- **定位**: Spring Boot 3 + Spring AI Alibaba 构建的 AI 智能图文创作平台
- **核心功能**: 用户输入主题，系统通过多智能体协作自动生成标题、大纲、正文和配图
- **技术栈**: Spring Boot 3.5.13, Java 21, MyBatis-Flex, Redis, Spring AI Alibaba, SSE, Stripe, Vue 3 + Ant Design Vue
- **个人角色**: 后端开发，负责多智能体编排、异步任务体系、策略模式配图、AOP 日志、SSE 流式推送

**常见追问:**
- StateGraph 和传统工作流引擎有什么区别？
- 为什么选择 Spring AI Alibaba 而不是其他 AI 框架？
- 系统每天能处理多少篇文章？瓶颈在哪？

**评分要点:** 介绍结构清晰，能突出技术亮点，不流水账

---

### 场景 2: 多智能体编排追问

**引导语:** 可以详细讲讲 StateGraph 多智能体编排的实现细节吗？Agent 之间是怎么协作的？遇到过什么坑？

**参考答案要点:**
- 5 个 Agent 的分工：标题生成、大纲生成、正文生成、配图分析、内容合并
- 三阶段流程，Feature Toggle 切换 StateGraph / 传统串行
- 坑 1: 跨 ClassLoader 类型转换 → className 比较 + Gson 深拷贝
- 坑 2: SSE Consumer 不可序列化 → ThreadLocal 桥接

**常见追问:**
- 如果执行到第三个 Agent 出错了，前面的 Agent 输出怎么处理？
- 为什么不直接在一个 Agent 里做完所有事？

**评分要点:** 能讲清"为什么用多 Agent"+ 实际踩坑经验

---

### 场景 3: 异步高并发追问

**引导语:** 你提到异步任务体系，能讲讲具体怎么设计的吗？线程池参数是怎么考虑的？任务状态追踪怎么做的？

**参考答案要点:**
- ThreadPoolTaskExecutor: core=5, max=10, queue=100, CallerRunsPolicy
- 为什么 core=5: AI 调用是 IO 密集型，线程数不宜过多（外部 API 并发限制）
- CallerRunsPolicy: 队列满时由调用线程执行，天然背压
- 双重状态机: status 生命周期 + phase 工作流阶段
- 用户确认操作触发阶段推进

**常见追问:**
- @Async 同类内部调用会失效，怎么处理？
- 如果线程池满了怎么办？
- 相比于消息队列（RabbitMQ）方案，直接 @Async 有什么优劣？

**评分要点:** 理解线程池参数的"为什么" + 状态机设计

---

### 场景 4: Stripe 支付追问

**引导语:** 你们是怎么集成 Stripe 支付的？支付成功后的 VIP 升级流程是怎样的？

**参考答案要点:**
- Stripe Checkout Session（托管支付页面）
- Webhook（签体验证）异步接收支付结果
- 幂等性: status 检查防止重复处理
- VIP 升级: 角色改为 vip，设置 vipTime
- 退款: 角色降级，配额重置为 5

**常见追问:**
- Webhook 签名验证是怎么实现的？伪造的请求能通过吗？
- 用户付款后 Webhook 还没到，前端怎么知道成功了？
- 退款后为什么配额要重置为 5？

**评分要点:** 理解支付流程的完整链路 + 幂等设计

---

### 场景 5: 系统设计综合

**引导语:** 如果让你从头设计这个系统，哪些地方你会做得不一样？或者哪些地方你觉得设计得不错可以保留？

**参考答案要点:**
**保留的设计:**
- StateGraph 多智能体编排: 任务拆分清晰，可观测性强
- 策略模式配图: 扩展性好，新增图源零修改
- 双重状态机: 生命周期和工作流分离
- SSE 流式推送: 用户实时感知进度
- SQL CAS 配额扣减: 简洁高效的并发方案

**优化的方向:**
- SSE 连接用本地 Map 存，多实例需 Redis Pub/Sub
- 加入消息队列削峰
- AI 结果缓存避免重复调用
- Phase 状态配置化支持工作流动态编排

**常见追问:**
- 这些优化为什么没做？
- 优先级怎么排？

**评分要点:** 能辩证看待自己的设计，有改进意识

---

### 场景 6: 性能优化细节

**引导语:** 这个系统在性能方面做了哪些优化？哪些地方还可以进一步优化？

**参考答案要点:**
**已做优化:**
- 异步任务 @Async 解耦耗时 AI 调用
- 并行图片生成 CompletableFuture（按源分组并行）
- SSE 流式推送，避免前端等待空转
- SQL CAS 配额扣减，无锁并发
- Redis 会话，减少数据库读压力
- CallerRunsPolicy 背压防止任务堆积

**可进一步优化:**
- Redis 缓存文章列表/统计信息
- AI 结果语义缓存
- 数据库读写分离
- CDN 图片访问优化

**常见追问:**
- 压测过吗？QPS 多少？
- 最慢的环节在哪？

**评分要点:** 有量化意识 + 能识别瓶颈

---

## 附: 高频考点速记

| 考点 | 一句话回答 |
|---|---|
| StateGraph | DAG 工作流引擎，支持 Agent 串行/并行编排 |
| ReplaceStrategy | 新值替换旧值，适用于独立 Key |
| StreamHandlerContext | ThreadLocal 桥接不可序列化的 SSE Consumer |
| AopContext.currentProxy() | 解决同类内部方法调用 AOP 失效 |
| tryFixJson() | 统计未闭合括号数量补全截断 JSON |
| SQL CAS | UPDATE ... WHERE quota > 0，原子防超扣 |
| CallerRunsPolicy | 队列满时调用线程执行，背压机制 |
| 策略模式自动注册 | @PostConstruct 扫描所有实现注册到 EnumMap |
| SSE vs WebSocket | 单向推送用 SSE，双向通信用 WebSocket |
| status + phase | 生命周期 + 工作流阶段，关注点分离 |
| Stripe 幂等 | 状态检查防止重复处理 Webhook |
| 并行图片生成 | 按源分组，不同组并行同组串行 |
| 占位符替换 | {{IMAGE_PLACEHOLDER_N}} → ![](url) |
| JSON 序列化 Session | 比 JDK 序列化可读 + 版本兼容 |
| 降级策略 | 图片生成失败兜底 Picsum 随机图 |

---

> 💡 **使用建议**: 面试前重点复习"疑难问题类"，这些是区分候选人深度的关键。
> 交互式练习请使用 `/mentor` 命令启动陪练模式。
