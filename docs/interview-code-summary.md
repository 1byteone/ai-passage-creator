# 灵犀写作（AI Passage Creator）面试代码摘要与心得

> 生成时间：2026-07-10 | 用途：面试前快速回顾

---

## 目录

1. [StateGraph 多智能体编排](#1-stategraph-多智能体编排)
2. [跨 ClassLoader 类型转换](#2-跨-classloader-类型转换)
3. [配额 CAS 原子扣减](#3-配额-cas-原子扣减)
4. [策略模式自动注册](#4-策略模式自动注册)
5. [SSE 连接管理](#5-sse-连接管理)
6. [ArticlePhaseEnum 状态机](#6-articlephaseenum-状态机)
7. [AOP @AgentExecution 日志切面](#7-aop-agentexecution-日志切面)
8. [Stripe Webhook 幂等性](#8-stripe-webhook-幂等性)
9. [StreamHandlerContext ThreadLocal 桥接](#9-streamhandlercontext-threadlocal-桥接)
10. [异步线程池配置](#10-异步线程池配置)
11. [系统全景速查表](#11-系统全景速查表)

---

## 1. StateGraph 多智能体编排

### 关键代码

```java
// ArticleAgentOrchestrator.java:306-321
private StateGraph buildPhase3Graph() throws GraphStateException {
    return new StateGraph(keyStrategyFactory)
            .addNode("content_generator", node_async(contentGeneratorAgent))
            .addNode("image_analyzer", node_async(imageAnalyzerAgent))
            .addNode("parallel_image_generator", node_async(parallelImageGenerator))
            .addNode("content_merger", node_async(contentMergerAgent))
            .addEdge(START, "content_generator")
            .addEdge("content_generator", "image_analyzer")
            .addEdge("image_analyzer", "parallel_image_generator")
            .addEdge("parallel_image_generator", "content_merger")
            .addEdge("content_merger", END);
}
```

### 执行方式

```java
CompiledGraph compiledGraph = graph.compile();
Optional<OverAllState> result = compiledGraph.invoke(inputs);
```

### 理解心得

- **为什么分 3 个独立 StateGraph 而不是 1 个？** 因为每个 Phase 之间有用户交互（选标题、编辑大纲），不是连续执行。Phase 1 结束后用户可能 2 小时才回来，如果是一个大 Graph，在内存中等待会耗尽资源。

- **所有 Key 都用 ReplaceStrategy**：每个 Agent 产出的 Key 互不重叠（titleOptions、outline、content...），新值直接替换旧值。如果多个 Agent 修改同一个 Key 的不同子字段（比如 userInfo.name 和 userInfo.email），才需要 MergeStrategy 按字段合并。

- **面试话术：** "我们不是在一个大 Prompt 里让 AI 一次性生成所有内容，那样长上下文会导致注意力分散。通过 StateGraph 把标题、大纲、正文、配图拆成 5 个独立 Agent，每个 Agent 专注于单一任务。Phase 3 的 4 个节点形成 DAG 流水线：正文生成→配图需求分析→并行图片生成→图文合成。"

---

## 2. 跨 ClassLoader 类型转换

### 关键代码

```java
// ArticleAgentOrchestrator.java:153
// StateGraph 序列化/反序列化后，instanceof 对同一类型返回 false
// 解决方案：用 className 字符串比较替代 instanceof
if (v != null && v.getClass().getName()
        .equals(ArticleState.OutlineResult.class.getName())) {
    return (ArticleState.OutlineResult) v;
}

// 备选方案：Gson 深拷贝（ContentGeneratorAgent.java:56 使用）
return GsonUtils.fromJson(GsonUtils.toJson(v), ArticleState.OutlineResult.class);
```

### 理解心得

- **根因：** StateGraph 内部的序列化/反序列化机制，导致反序列化后对象的 `ClassLoader` 和原始类不在同一个 ClassLoader 上下文中。同一个类名，不同的 ClassLoader 加载出来，`instanceof` 返回 false。

- **排查过程：** 发现 Agent 内部对 `ArticleState.OutlineResult` 做 `instanceof` 判断始终返回 false，但 `getClass().getName()` 打印出来的类名完全一样 → 确认是 ClassLoader 隔离问题。

- **两种方案的适用场景：** `className` 比较适合简单类型判断；Gson 深拷贝适合复杂嵌套对象，能完整重建类型实例。实际项目两种都用。

- **面试话术：** "这是开发中遇到的最棘手的 bug。StateGraph 序列化后 instanceof 失效，我通过日志发现类名字符串一样但 instanceof 返回 false，定位到是 ClassLoader 隔离。解决方案是用 getClass().getName() 字符串比较替代 instanceof，复杂对象用 Gson 做 JSON 序列化再反序列化来重建正确的类型实例。"

---

## 3. 配额 CAS 原子扣减

### 关键代码

```java
// UserMapper.java:17
@Update("UPDATE user SET quota = quota - 1 WHERE id = #{userId} AND quota > 0")
int decrementQuota(@Param("userId") Long userId);

// QuotaServiceImpl.java:50-58
int affectedRows = userMapper.decrementQuota(user.getId());
if (affectedRows > 0) {
    log.info("用户配额已消耗, userId={}", user.getId());
} else {
    log.warn("用户配额扣减失败（可能配额不足或并发冲突）");
}
```

### 理解心得

- **为什么不用 `synchronized`？** synchronized 只在单 JVM 有效，多实例部署时完全失效。而且锁住整个方法会导致所有用户排队，性能极差。

- **为什么 `affectedRows` 就能判断？** MySQL 的 UPDATE 在 InnoDB 下是行级锁，同一行数据的并发 UPDATE 会被串行化。`WHERE quota > 0` 确保不会扣成负数，`affectedRows=0` 说明条件不满足（配额已被其他请求扣完）。

- **面试话术：** "传统的读-判断-写三步操作有竞态条件。我们用数据库的原子 UPDATE 一步完成，WHERE quota > 0 当 guard condition，affectedRows 判断成功失败。不需要分布式锁，单条 SQL 天然线程安全且跨实例一致。"

---

## 4. 策略模式自动注册

### 关键代码

```java
// ImageServiceStrategy.java:42-53
@PostConstruct
public void init() {
    // 遍历 ApplicationContext 中所有 ImageSearchService Bean
    for (ImageSearchService service : imageSearchServices) {
        ImageMethodEnum method = service.getMethod();
        serviceMap.put(method, service);  // 注册到 EnumMap
    }
}

// 降级 fallback（关键！）
private ImageResult handleFallbackWithUpload(Integer position) {
    String fallbackUrl = getFallbackImage(position);  // Picsum 随机图
    ImageData fallbackData = ImageData.fromUrl(fallbackUrl);
    String cosUrl = cosService.uploadImageData(fallbackData, "fallback");
    return new ImageResult(finalUrl, ImageMethodEnum.getFallbackMethod());
}
```

### 理解心得

- **为什么要自动注册？** 如果要新增一个图片来源（如百度图片），只需新建一个类实现 `ImageSearchService` 接口加 `@Service` 注解，系统自动识别，零改动已有代码。这就是开闭原则（Open-Closed Principle）的落地。

- **为什么用 EnumMap 而不是 HashMap？** `EnumMap` 以枚举值为 Key，内部用数组实现，比 HashMap 更快（无 hash 计算）、更省内存。对于这种枚举Key固定且数量少的场景，EnumMap 是最佳选择。

- **降级为什么重要？** AI 系统不稳定是常态。Pexels API 可能宕机、DALL·E 可能超时、Mermaid CLI 可能环境问题。降级到 Picsum 随机图保证用户永远能看到完整的图文结果。

- **面试话术：** "图片来源用了策略模式，接口是 ImageSearchService，6 个实现包括 Pexels、Mermaid、Iconify 等。通过 @PostConstruct 自动扫描 Spring 容器注册到 EnumMap。新增图源只需要加一个类，零修改。每个策略都有 try-catch 兜底，失败后降级到 Picsum 随机图片并上传 COS，保证文章配图不缺失。"

---

## 5. SSE 连接管理

### 关键代码

```java
// SseEmitterManager.java:21-54
private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();

public SseEmitter createEmitter(String taskId) {
    SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);  // 10分钟超时
    emitter.onTimeout(() -> emitterMap.remove(taskId));
    emitter.onCompletion(() -> emitterMap.remove(taskId));
    emitter.onError((e) -> emitterMap.remove(taskId));
    emitterMap.put(taskId, emitter);
    return emitter;
}

// 心跳保持连接
public void sendHeartbeat(String taskId) {
    emitter.send(SseEmitter.event()
            .comment("heartbeat")           // 注释类型，前端不触发事件
            .reconnectTime(3000L));          // 3秒重连
}
```

### 理解心得

- **为什么 10 分钟超时？** AI 生成文章最长可能要 2-3 分钟，10 分钟给够缓冲。超时后自动清理防止连接泄露。

- **心跳为什么是 comment 类型？** `comment` 类型消息不会触发前端 EventSource 的 `onmessage` 事件，纯保持连接活跃。如果发 data 消息，前端每次都要做无用解析。

- **为什么用 ConcurrentHashMap？** 多个用户可能同时建立/断开连接，ConcurrentHashMap 的分段锁机制保证并发安全且性能好。

- **多实例问题：** 当前 SseEmitterManager 用本地 Map 存连接，只支持单实例。多实例部署时用户可能连到实例 A，但 SSE 消息在实例 B 生成 → 需要通过 Redis Pub/Sub 广播。

- **面试话术：** "SSE 用 SseEmitter 实现，ConcurrentHashMap 以 taskId 为 Key 管理连接。10 分钟超时+3 秒重连，心跳用 comment 消息保持连接但不触发前端解析。三个回调（timeout/completion/error）都自动从 Map 移除防止内存泄漏。多实例部署需要用 Redis Pub/Sub 广播。"

---

## 6. ArticlePhaseEnum 状态机

### 关键代码

```java
// ArticlePhaseEnum.java:59-72
public boolean canTransitionTo(ArticlePhaseEnum targetPhase) {
    return switch (this) {
        case PENDING            -> targetPhase == TITLE_GENERATING;
        case TITLE_GENERATING   -> targetPhase == TITLE_SELECTING;
        case TITLE_SELECTING    -> targetPhase == OUTLINE_GENERATING;
        case OUTLINE_GENERATING -> targetPhase == OUTLINE_EDITING;
        case OUTLINE_EDITING    -> targetPhase == CONTENT_GENERATING;
        case CONTENT_GENERATING -> false;  // 终态，不可再转换
    };
}
```

### 理解心得

- **status vs phase 的区别：** status（PENDING/PROCESSING/COMPLETED/FAILED）是粗糙的生命周期，用于列表筛选和展示。phase（7 个阶段）是精细的工作流状态，用于控制阶段流转。两个维度独立演进，互不干扰。

- **为什么 CONTENT_GENERATING 是终态？** 因为 Phase 3 内部是 4 个 Agent 的自动流水线，生成完文章就结束了，不需要用户再交互。所以从外部看，进入 CONTENT_GENERATING 后不能再转换到其他阶段。

- **面试话术：** "文章有两个状态维度：status 表示生命周期（PENDING/PROCESSING/COMPLETED），phase 表示工作流阶段（TITLE_GENERATING→TITLE_SELECTING→OUTLINE_GENERATING...）。canTransitionTo() 方法用 switch 表达式严格约束了 6 步单向流转，禁止跳过或回退。这样设计是为了分离关注点——生命周期和工作流各管各的。"

---

## 7. AOP @AgentExecution 日志切面

### 关键代码

```java
// AgentExecutionAspect.java:35-86
@Around("@annotation(agentExecution)")
public Object aroundAgentExecution(ProceedingJoinPoint pjp, 
                                    AgentExecution agentExecution) throws Throwable {
    long startTime = System.currentTimeMillis();
    // 创建 RUNNING 状态的日志
    AgentLog agentLog = AgentLog.builder()
            .taskId(extractTaskId(pjp))
            .agentName(agentExecution.value())
            .startTime(LocalDateTime.now())
            .status("RUNNING")
            .build();
    try {
        result = pjp.proceed();      // 执行目标方法
        agentLog.setStatus("SUCCESS");
        agentLog.setDurationMs((int)(System.currentTimeMillis() - startTime));
    } catch (Throwable e) {
        agentLog.setStatus("FAILED");
        agentLog.setErrorMessage(e.getMessage());
        throw e;                     // 重新抛出，不吞异常
    } finally {
        agentLogService.saveLogAsync(agentLog);  // 异步保存不阻塞主流程
    }
    return result;
}
```

### 理解心得

- **为什么在 finally 中保存？** 无论成功还是失败都要记录日志。如果放在 try 块末尾，失败了就不记录；如果放在 catch 块里，成功了就不记录。finally 保证两次路径都有日志。

- **为什么是异步保存？** `saveLogAsync` 是数据库写操作，如果同步保存会拖慢 Agent 执行速度。异步写入后日志记录对业务流程零影响。

- **Spring AOP 内部调用不生效的坑：** Agent 方法 A 内部调用 `this.methodB()`，methodB 上的 `@AgentExecution` 切面不触发，因为 `this` 是原始对象不是代理对象。解决用 `AopContext.currentProxy()` 获取代理再调用。启动类需要 `@EnableAspectJAutoProxy(exposeProxy = true)`。

- **面试话术：** "@Around 环绕通知记录每个 Agent 的执行时间和结果。方法执行前创建 RUNNING 日志，成功后标记 SUCCESS 并记录耗时，失败时记录 errorMessage 并重新抛出异常，finally 中异步保存到 agent_log 表。遇到的一个坑是 Spring AOP 同类内部调用不走代理，用 AopContext.currentProxy() 解决。"

---

## 8. Stripe Webhook 幂等性

### 关键代码

```java
// StripeWebhookController.java:27-65
@PostMapping("/stripe")
public String handleStripeWebhook(
        @RequestBody String payload,
        @RequestHeader("Stripe-Signature") String sigHeader) {
    // 签名验证（安全第一层）
    Event event = paymentService.constructEvent(payload, sigHeader);
    
    switch (event.getType()) {
        case "checkout.session.completed":
            Session session = extractSessionFromEvent(event);
            paymentService.handlePaymentSuccess(session);
            break;
    }
    return "success";
}

// Session 提取的三级 fallback
private Session extractSessionFromEvent(Event event) {
    // 1. 标准反序列化
    event.getDataObjectDeserializer().getObject();
    // 2. 不安全反序列化（API版本不匹配时兜底）
    event.getDataObjectDeserializer().deserializeUnsafe();
    // 3. 从JSON手动提取ID后主动查询
    String sessionId = extractFromRawJson(event);
    return Session.retrieve(sessionId);
}
```

### 理解心得

- **为什么 Webhook 不校验登录？** Stripe 的 Webhook 是 Stripe 服务器调用的，不是用户浏览器调用的。通过签名验证保证请求确实来自 Stripe，比用户登录验证更安全。

- **三级 fallback 的设计哲学：** Stripe SDK 的 API 版本和实际 Event 的 API 版本可能不一致，导致标准反序列化失败。三级 fallback 从严格到宽松逐级降级，最后兜底方案直接查 Stripe API（`Session.retrieve()`）保证 100% 能拿到数据。

- **幂等性实现在 Service 层：** `handlePaymentSuccess()` 中先查 `payment_record.status`，如果已经是 SUCCEEDED 就直接返回。这是状态检查幂等，简单有效。

- **面试话术：** "Webhook 安全通过 Stripe SDK 的签名验证保证。幂等性通过检查 payment_record.status 实现，已处理的直接跳过。Webhook 事件的 Session 提取设计了三级 fallback：标准反序列化→不安全反序列化（兼容 API 版本差异）→从原始 JSON 提取 ID 主动查询 Stripe API。这保证了在任何 Stripe API 版本变更下都能正确解析。"

---

## 9. StreamHandlerContext ThreadLocal 桥接

### 关键代码

```java
// StreamHandlerContext.java（完整类，仅 46 行）
public class StreamHandlerContext {
    private static final ThreadLocal<Consumer<String>> STREAM_HANDLER = new ThreadLocal<>();
    
    public static void set(Consumer<String> handler) { STREAM_HANDLER.set(handler); }
    public static Consumer<String> get() { return STREAM_HANDLER.get(); }
    public static void clear() { STREAM_HANDLER.remove(); }
}

// 使用方：ArticleAgentOrchestrator.java
try {
    StreamHandlerContext.set(streamHandler);     // invoke 前 set
    compiledGraph.invoke(inputs);
} finally {
    StreamHandlerContext.clear();                // invoke 后 clear
}

// Agent 内部获取：ContentGeneratorAgent.java
Consumer<String> streamHandler = StreamHandlerContext.get();
streamHandler.accept("AGENT3_STREAMING:" + chunk);
```

### 理解心得

- **问题的本质：** StateGraph 内部会对 OverAllState 做序列化/反序列化，因为框架可能需要在不同线程间传递状态（虽然当前场景是单线程执行）。`Consumer<String>` 是 Lambda 表达式（`s -> sseEmitter.send(s)`），Lambda 本质上是一个匿名内部类的实例，需要捕获外部变量，不支持 JDK 序列化。如果把 Consumer 放进 OverAllState，序列化时抛 `NotSerializableException`。

- **为什么用 ThreadLocal 而不是其他方案？** ThreadLocal 是 Java 标准库中最轻量级的线程级变量隔离方案。当前场景下 Agent 都在同一个线程中执行（`@Async` 分配的线程），ThreadLocal 天然绑定了当前线程。

- **为什么要 clear？** ThreadLocal 如果不清理，在线程池复用时可能导致内存泄漏或数据污染。当前线程池线程处理完任务 A 后可能被复用处理任务 B，如果不 clear，任务 B 会错误地拿到任务 A 的 streamHandler。

- **面试话术：** "StateGraph 的 OverAllState 要序列化/反序列化，但 SSE 的 Consumer Lambda 不可序列化。我们用 ThreadLocal 做了一个侧通道：invoke 前把 streamHandler set 进去，Agent 内部 get 使用，invoke 后 finally 中 clear 清理。这是一种绕过框架限制的工程技巧，在不能修改框架源码的情况下最轻量的方案。"

---

## 10. 异步线程池配置

### 关键代码

```java
// AsyncConfig.java:18-47
@Bean(name = "articleExecutor")
public Executor articleExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);                                    // 核心线程
    executor.setMaxPoolSize(10);                                    // 最大线程
    executor.setQueueCapacity(100);                                 // 队列容量
    executor.setThreadNamePrefix("article-async-");                  // 线程名前缀
    executor.setRejectedExecutionHandler(
        new ThreadPoolExecutor.CallerRunsPolicy());                  // 背压策略
    executor.setWaitForTasksToCompleteOnShutdown(true);              // 优雅关闭
    executor.setAwaitTerminationSeconds(60);
    executor.initialize();
    return executor;
}
```

### 理解心得

- **为什么 core=5？** AI 调用是 IO 密集型（等待 API 响应），不是 CPU 密集型。线程数多了反而增加上下文切换开销。5 个核心线程在处理 DashScope API 的并发限制下是比较合理的值。

- **为什么选 CallerRunsPolicy？** 四种拒绝策略对比：
  - `AbortPolicy`：抛异常，任务丢了
  - `DiscardPolicy`：静默丢弃，用户懵了
  - `DiscardOldestPolicy`：丢最老的，用户的老任务没了
  - `CallerRunsPolicy`：由调用者线程（Tomcat 线程）直接执行 → **天然背压**，调用者线程被占用后不再接收新请求，HTTP 请求排队

- **背压（Backpressure）的价值：** 当队列满时，CallerRunsPolicy 让 Tomcat 线程亲自执行任务，这个线程就不能处理新请求了。Tomcat 的线程池也会排队，形成级联背压，最终通过 HTTP 响应变慢让客户端感知到压力。这比直接丢弃任务导致的"任务消失"好得多。

- **优雅关闭：** `waitForTasksToCompleteOnShutdown=true` 保证应用关闭时不会强行中断正在执行的任务，等待最多 60 秒。

- **面试话术：** "线程池配置是 core=5、max=10、queue=100，拒绝策略用了 CallerRunsPolicy。AI 调用是 IO 密集型的，5 个核心线程足够。CallerRunsPolicy 让队列满时由调用线程执行，形成天然的背压机制——调用线程被占用就无法接收新请求，Tomcat 线程池也会跟着排队，压力层层传导但不会丢任务。"

---

## 11. 系统全景速查表

### 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 语言 | Java 21 | LTS |
| 框架 | Spring Boot | 3.5.13 |
| ORM | MyBatis-Flex | 1.11.1 |
| AI | Spring AI Alibaba | 1.1.0.0-RC2 |
| 缓存 | Redis | — |
| 支付 | Stripe | 31.2.0 |
| 存储 | Tencent COS | 5.6.228 |

### 数据库表（4 张）

| 表名 | 用途 | 关键字段 |
|------|------|----------|
| `user` | 用户 | id (Snowflake), quota, userRole, vipTime |
| `article` | 文章 | taskId (UUID), status, phase, content, images(JSON) |
| `payment_record` | 支付记录 | stripeSessionId, amount, status, productType |
| `agent_log` | Agent日志 | taskId, agentName, durationMs, status, inputData/outputData(JSON) |

### 6+1 种图片来源

| 策略 | 方法 | 权限要求 |
|------|------|:--------:|
| Pexels | API 检索图库 | 普通用户 |
| Mermaid | CLI 生成流程图 | 普通用户 |
| Iconify | API 搜索图标 | 普通用户 |
| EmojiPack | Bing 搜索表情包 | 普通用户 |
| NANO_BANANA | AI 图片生成 | **VIP** |
| SVG_Diagram | LLM 生成 SVG | **VIP** |
| PICSUM | **降级兜底** | 所有用户 |

### 三阶段 Human-in-loop 流程

```
用户输入主题
    → Phase 1: AI 生成标题 → 用户选择
    → Phase 2: AI 生成大纲 → 用户编辑
    → Phase 3: 正文→配图分析→并行配图→图文合成（全自动）
```

### 设计模式速查

| 模式 | 位置 |
|------|------|
| 策略模式 | `ImageSearchService` 接口 + `ImageServiceStrategy` |
| 状态机 | `ArticlePhaseEnum.canTransitionTo()` |
| 代理模式 | `AopContext.currentProxy()` |
| 模板方法 | `ImageSearchService` 默认方法 |
| 观察者 | SSE 推送 (SseEmitterManager + Consumer) |
| ThreadLocal | `StreamHandlerContext` |
| 工厂方法 | `ImageData.fromUrl() / fromBytes() / fromDataUrl()` |

### 面试预演清单（入场面 5 分钟看）

- [ ] StateGraph 4 节点顺序：正→分→并→合
- [ ] 跨 ClassLoader 坑：className 字符串比较 + Gson 深拷贝
- [ ] CAS 扣减：`UPDATE ... WHERE quota > 0`
- [ ] 策略注册：@PostConstruct → EnumMap
- [ ] SSE：ConcurrentHashMap + 10min超时 + 3s重连
- [ ] 状态机：6 步 switch 单向流转
- [ ] AOP：@Around → finally async save
- [ ] Stripe：签名验证 + status 幂等 + 3 级 fallback
- [ ] ThreadLocal：set → invoke → clear
- [ ] 线程池：5-10-100 + CallerRunsPolicy
