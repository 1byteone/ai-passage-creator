# AI 文章生成平台（`ai-passage-creator`）面试导向深度复盘

> 说明：你给的“项目背景 / 技术栈 / 原始功能描述”是空白的，所以我基于当前仓库源码做了一版**可直接用于面试**的复盘笔记；内容严格贴合现有实现，不虚构不存在的中间件和链路。

---

## 一、项目整体概述（面试 1 分钟版本）

- 这是一个面向内容创作者/新媒体运营人员的 AI 文章生成平台。
- 用户输入选题后，系统会分三个阶段自动完成：**标题生成 → 大纲生成 → 正文与配图生成**，最终输出完整 Markdown 图文内容。
- 我主要负责后端核心链路设计，包括**异步任务编排、SSE 实时推送、多 Agent 协作、配额与 VIP 权限控制、并行配图生成**等。
- 它解决的核心问题是：传统文章创作链路长、人工找图慢、结果不可视、生成体验差，而这个系统把整个图文生产流程做成了可追踪、可流式反馈、可分阶段确认的人机协同链路。

### 可直接背诵的话术

“我做的是一个 AI 文章生成平台，主要服务内容创作者和新媒体运营场景。用户输入选题后，系统不是一次性生成全文，而是拆成标题、大纲、正文和配图几个阶段，通过多 Agent 编排逐步完成，同时用 SSE 把生成进度实时推给前端。这样既提升了可控性，也降低了大模型一次性长文本生成的不稳定性。我主要负责后端架构和核心链路，包括任务异步化、状态流转、配额扣减、VIP 能力控制，以及并行配图和最终图文合成。”  

---

## 二、系统架构设计

### 1）分层架构

- **Controller 层**
  - 接收文章创建、确认标题、确认大纲、SSE 订阅、支付等请求。
  - 代表接口：`/article/create`、`/article/confirm-title`、`/article/confirm-outline`、`/article/progress/{taskId}`。

- **Service 层**
  - `ArticleService`：任务创建、阶段推进、文章持久化、权限校验。
  - `ArticleAsyncService`：异步执行三个阶段，负责状态更新和 SSE 消息投递。
  - `QuotaService`：配额检查与原子扣减。
  - `PaymentService`：Stripe 支付、Webhook 回调、VIP 升级/退款。

- **Agent 编排层**
  - `ArticleAgentOrchestrator` 基于 Spring AI Alibaba Graph 编排多 Agent。
  - Phase1：`TitleGeneratorAgent`
  - Phase2：`OutlineGeneratorAgent`
  - Phase3：`ContentGeneratorAgent` → `ImageAnalyzerAgent` → `ParallelImageGenerator` → `ContentMergerAgent`

- **DAO / Mapper 层**
  - 使用 MyBatis-Flex 操作 MySQL。
  - `UserMapper` 中提供 `UPDATE user SET quota = quota - 1 WHERE id = ? AND quota > 0` 的原子扣减 SQL。

- **存储与基础设施**
  - **MySQL**：存文章任务、标题方案、大纲、正文、图片结果、支付记录、用户信息。
  - **Redis + Spring Session**：存登录态，避免单机 Session。
  - **SSE Emitter Manager（内存 ConcurrentHashMap）**：维护 `taskId -> SseEmitter` 映射，实现实时推送。
  - **腾讯 COS**：统一承接图片上传，屏蔽不同图片来源差异。
  - **DashScope**：大模型能力提供方。
  - **Stripe**：VIP 购买与退款。

### 2）技术选型原因

- **为什么用 Spring Boot**
  - 快速搭建 REST + AOP + Async + Session + Redis 的完整后端体系，适合中后台快速迭代。

- **为什么用 MyBatis-Flex**
  - 比传统 XML 写法更轻量，保留 SQL 可控性，适合本项目这种“业务字段多、更新逻辑明确”的场景。

- **为什么用 Redis**
  - 当前项目 Redis 主要承接 **Spring Session 登录态**，避免多实例部署时 Session 丢失；不是拿 Redis 做文章缓存，而是做会话层基础设施。

- **为什么用 SSE，而不是 WebSocket**
  - 这是典型的**服务端单向推送**场景，前端只需要看进度和流式文本，不需要双向交互。
  - SSE 实现更轻、协议简单、接入成本低，适合大模型流式输出。

- **为什么用 StateGraph / 多 Agent 编排**
  - 文章生成不是单步动作，而是阶段式流程。
  - 用图编排后，节点职责清晰，后续增加“审核 Agent / 润色 Agent / 敏感词校验 Agent”会更容易扩展。

- **为什么图片统一上传到 COS**
  - 不同来源（图库、AI 生图、Mermaid、SVG）格式不同，统一上传后对外只暴露稳定 URL，方便前端渲染和 Markdown 合成。

### 3）架构说明（文字版）

用户先调用文章创建接口，后端完成登录校验、配额扣减、任务入库，然后立即异步触发标题生成。  
前端通过 SSE 订阅任务进度，后端在各 Agent 执行过程中把标题、大纲、正文流式文本、单张图片完成事件实时推送给前端。  
当用户确认标题后进入大纲生成；确认大纲后进入正文和配图生成。  
正文生成完成后，系统再分析配图需求，根据允许的图片策略并行生成图片，最后把占位符替换为真实图片 URL，输出完整图文内容并落库。  

---

## 三、核心功能模块拆解（重点）

## 模块一：文章任务创建 + 配额扣减 + 异步启动

### 1）功能描述（业务角度）

- 用户发起一次文章创作时，系统先做参数校验、登录校验、会员权限校验和配额扣减。
- 校验通过后创建文章任务，并立即异步触发第一阶段“标题生成”。
- 这样做的价值是：**接口快速返回、任务可追踪、额度不超扣**。

### 2）业务流程

1. 用户调用 `/article/create`
2. Controller 校验 topic、style
3. 根据 Session 从 Redis 读取登录态
4. `ArticleService#createArticleTaskWithQuotaCheck`
5. `QuotaService#checkAndConsumeQuota` 原子扣减配额
6. 创建 `article` 记录，状态置为 `PENDING`
7. 异步调用 `ArticleAsyncService#executePhase1`
8. 前端拿到 `taskId` 后订阅 SSE

### 3）关键数据流转

- 输入：`topic`、`style`、`enabledImageMethods`
- 用户态：Redis Session 中只存 `userId`
- 数据库：
  - `user.quota`：扣减 1 次
  - `article.taskId/status/phase/topic/style/enabledImageMethods`
- 输出：`taskId`

### 4）核心代码示例（Java 简化版）

```java
// Controller
@PostMapping("/article/create")
public BaseResponse<String> create(@RequestBody ArticleCreateRequest req, HttpServletRequest request) {
    User loginUser = userService.getLoginUser(request); // Redis Session -> userId -> DB
    String taskId = articleService.createArticleTaskWithQuotaCheck(
            req.getTopic(), req.getStyle(), req.getEnabledImageMethods(), loginUser
    );
    articleAsyncService.executePhase1(taskId, req.getTopic(), req.getStyle());
    return ResultUtils.success(taskId);
}

// Service
@Transactional(rollbackFor = Exception.class)
public String createArticleTaskWithQuotaCheck(String topic, String style, List<String> methods, User user) {
    quotaService.checkAndConsumeQuota(user);
    return createArticleTask(topic, style, methods, user);
}

// 配额扣减
@Transactional(rollbackFor = Exception.class)
public void checkAndConsumeQuota(User user) {
    if (isVip(user) || isAdmin(user)) {
        return;
    }
    int affected = userMapper.decrementQuota(user.getId());
    if (affected == 0) {
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "配额不足");
    }
}

// Mapper
@Update("UPDATE user SET quota = quota - 1 WHERE id = #{userId} AND quota > 0")
int decrementQuota(@Param("userId") Long userId);
```

### 5）设计亮点

- **配额扣减和任务创建放在同一事务**，避免“扣了配额但任务没建成功”。
- **扣减用单条 SQL 原子更新**，避免“先查再改”带来的并发超扣。
- **接口异步化**，Controller 不阻塞等待大模型结果，面试里可以强调“任务受理”和“任务执行”解耦。

---

## 模块二：大纲生成 + 流式反馈 + JSON 容错

### 1）功能描述（业务角度）

- 用户确认标题后，系统生成结构化大纲。
- 这里不是一次性返回结果，而是把大模型生成过程通过 SSE 实时推送给前端。
- 同时考虑到 LLM 返回 JSON 可能截断，我做了**JSON 自动修复机制**，提升生成成功率。

### 2）业务流程

1. 用户调用 `/article/confirm-title`
2. 后端保存用户选择的主副标题和补充描述
3. 异步触发 `executePhase2`
4. `ArticleAgentOrchestrator` 构建 Phase2 Graph
5. `OutlineGeneratorAgent` 调用 DashScope 流式生成大纲 JSON
6. 流式内容实时通过 SSE 推给前端
7. 若 JSON 解析失败，执行 `tryFixJson`
8. 解析成功后把 `outline` 落库，并把阶段更新为 `OUTLINE_EDITING`

### 3）关键数据流转

- 输入：`taskId`、`mainTitle`、`subTitle`、`userDescription`
- 状态对象：`ArticleState`
- 图状态容器：`OverAllState`
- 输出：
  - SSE：`AGENT2_STREAMING`
  - DB：`article.outline`
  - 最终事件：`OUTLINE_GENERATED`

### 4）核心代码示例（Java 简化版）

```java
// Controller
@PostMapping("/article/confirm-title")
public BaseResponse<Void> confirmTitle(@RequestBody ArticleConfirmTitleRequest req, HttpServletRequest request) {
    User loginUser = userService.getLoginUser(request);
    articleService.confirmTitle(req.getTaskId(), req.getSelectedMainTitle(),
            req.getSelectedSubTitle(), req.getUserDescription(), loginUser);
    articleAsyncService.executePhase2(req.getTaskId());
    return ResultUtils.success(null);
}

// AsyncService
@Async("articleExecutor")
public void executePhase2(String taskId) {
    Article article = articleService.getByTaskId(taskId);
    ArticleState state = buildStateFromDb(article);
    articleAgentOrchestrator.executePhase2_GenerateOutline(state, message -> {
        handleAgentMessage(taskId, message, state); // SSE 推送
    });
    articleService.saveOutline(taskId, state.getOutline());
    articleService.updatePhase(taskId, ArticlePhaseEnum.OUTLINE_EDITING);
}

// Orchestrator
public void executePhase2_GenerateOutline(ArticleState state, Consumer<String> streamHandler) {
    StreamHandlerContext.set(streamHandler);
    StateGraph graph = new StateGraph(createKeyStrategyFactory())
            .addNode("outline_generator", node_async(outlineGeneratorAgent))
            .addEdge(START, "outline_generator")
            .addEdge("outline_generator", END);
    OverAllState finalState = graph.compile().invoke(inputs).orElseThrow();
    ArticleState.OutlineResult outline = convertOutline(finalState);
    state.setOutline(outline);
}

// Agent
public Map<String, Object> apply(OverAllState state) {
    String content = callLlmWithStreaming(prompt, chunk -> {
        streamHandler.accept("AGENT2_STREAMING:" + chunk);
    });
    ArticleState.OutlineResult outline;
    try {
        outline = GsonUtils.fromJson(content, OutlineResult.class);
    } catch (Exception e) {
        String fixed = tryFixJson(content);
        outline = GsonUtils.fromJson(fixed, OutlineResult.class);
    }
    return Map.of("outline", outline);
}
```

### 5）设计亮点

- **把一次长链路任务拆成用户可确认的大纲阶段**，提高结果可控性。
- **SSE + 流式输出**，让用户实时看到生成过程，体验明显优于轮询。
- **JSON 修复机制**是非常好的面试亮点，说明你不是只会调模型，而是考虑了大模型不稳定输出的工程兜底。
- **ThreadLocal 挂载 streamHandler**，让 Graph 内部 Agent 节点也能统一推送流式消息。

---

## 模块三：正文生成 + 配图需求分析 + 并行配图 + 图文合成

### 1）功能描述（业务角度）

- 用户确认大纲后，系统先生成正文，再自动分析哪些位置需要图片、该用什么图片方式，最后并行生成图片并插入正文。
- 这一块是真正的“图文一体化生产”核心能力。

### 2）业务流程

1. 用户调用 `/article/confirm-outline`
2. 异步执行 Phase3
3. `ContentGeneratorAgent` 生成正文 Markdown，并流式推送
4. `ImageAnalyzerAgent` 分析正文，返回带占位符的正文 + 配图需求列表
5. `ParallelImageGenerator` 按 `imageSource` 分组并行生成图片
6. 单张图片生成成功后立即推送 `IMAGE_COMPLETE`
7. `ContentMergerAgent` 用真实 URL 替换占位符
8. 落库 `content/fullContent/images/coverImage`

### 3）关键数据流转

- 输入：标题、大纲、允许的配图方式
- 中间态：
  - `content`
  - `contentWithPlaceholders`
  - `imageRequirements`
  - `images`
- 输出：
  - `fullContent`
  - `coverImage`
  - `images`

### 4）核心代码示例（Java 简化版）

```java
// Phase3 编排
private StateGraph buildPhase3Graph() {
    return new StateGraph(createKeyStrategyFactory())
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

// 正文生成
public Map<String, Object> apply(OverAllState state) {
    String content = callLlmWithStreaming(prompt, chunk -> {
        streamHandler.accept("AGENT3_STREAMING:" + chunk);
    });
    return Map.of("content", content);
}

// 配图需求分析
public Map<String, Object> apply(OverAllState state) {
    Agent4Result result = chatModel.call(prompt);
    List<ImageRequirement> validRequirements =
            validateAndFilterImageRequirements(result.getImageRequirements(), enabledMethods);
    return Map.of(
            "contentWithPlaceholders", result.getContentWithPlaceholders(),
            "content", result.getContentWithPlaceholders(),
            "imageRequirements", validRequirements
    );
}

// 并行配图
private List<ImageResult> executeParallel(Map<String, List<ImageRequirement>> grouped) {
    CopyOnWriteArrayList<ImageResult> allImages = new CopyOnWriteArrayList<>();
    List<CompletableFuture<Void>> futures = grouped.entrySet().stream()
            .map(entry -> CompletableFuture.runAsync(() -> {
                for (ImageRequirement req : entry.getValue()) {
                    ImageGenerationResult result = imageGenerationTool.generateImageDirect(...);
                    if (result.isSuccess()) {
                        allImages.add(convert(result));
                        streamHandler.accept("IMAGE_COMPLETE:" + GsonUtils.toJson(convert(result)));
                    }
                }
            })).toList();
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    return allImages;
}

// 图文合成
private String mergeImagesIntoContent(String content, List<ImageResult> images) {
    for (ImageResult image : images) {
        String markdown = "![" + image.getDescription() + "](" + image.getUrl() + ")";
        content = content.replace(image.getPlaceholderId(), markdown);
    }
    return content;
}
```

### 5）设计亮点

- **正文、配图需求、图片生成、图文合成分阶段处理**，比“让大模型一次性输出带图文章”更稳定。
- **按 `imageSource` 分组并发**，避免所有图片串行生成。
- **允许图片策略受用户角色控制**，普通用户和 VIP 用户能力边界清晰。
- **图片统一上传 COS**，图库 / AI 生图 / 图表类输出都被抽象成统一 URL。
- **图片完成即推送**，用户不是最后一次性看到所有图，而是边生成边感知。

---

## 四、核心业务流程梳理（完整链路）

### 链路一：创建文章

- 用户操作：输入选题、风格、可选配图方式，点击“开始生成”
- 后端处理：Controller 校验参数 → 读取登录态 → 校验并扣减配额 → 创建任务 → 异步启动标题生成
- 数据变化：`user.quota - 1`，新增 `article` 记录，状态 `PENDING/PROCESSING`
- 返回结果：返回 `taskId`

### 链路二：标题生成

- 用户操作：前端拿到 `taskId` 后建立 SSE 连接
- 后端处理：异步调用标题 Agent → 生成多个标题方案
- 数据变化：`article.titleOptions` 更新，阶段改为 `TITLE_SELECTING`
- 返回结果：SSE 推送 `TITLES_GENERATED`

### 链路三：确认标题并生成大纲

- 用户操作：选择主标题、副标题，可补充个性化描述
- 后端处理：保存用户选择 → 启动大纲 Agent → 流式推送生成内容 → JSON 容错解析
- 数据变化：`article.mainTitle/subTitle/userDescription/outline` 更新，阶段改为 `OUTLINE_EDITING`
- 返回结果：SSE 推送 `OUTLINE_GENERATED`

### 链路四：确认大纲并生成正文与配图

- 用户操作：确认或编辑大纲
- 后端处理：正文 Agent 生成正文 → 配图分析 Agent 输出占位符和配图需求 → 并行配图 → 图文合成
- 数据变化：`article.content/fullContent/images/coverImage/status` 更新，状态改为 `COMPLETED`
- 返回结果：SSE 按阶段推送正文流、图片完成、合成完成，最终推送 `ALL_COMPLETE`

### 链路五：VIP 支付闭环

- 用户操作：点击购买 VIP
- 后端处理：创建 Stripe Checkout Session → 支付成功后 Stripe 回调 Webhook → 幂等更新支付记录 → 升级用户 VIP
- 数据变化：`payment_record.status` 更新，`user.userRole` 从 `user` 升为 `vip`
- 返回结果：返回支付链接；回调成功后用户获得高级能力

---

## 五、技术难点与解决方案（面试重点）

### 难点一：配额扣减的并发安全

- **问题背景**
  - 如果多个请求同时创建文章，采用“先查 quota > 0，再 quota -1”的方式会出现超扣。

- **解决方案**
  - 直接使用单条 SQL：
    - `UPDATE user SET quota = quota - 1 WHERE id = ? AND quota > 0`
  - 通过影响行数判断是否扣减成功。
  - 并且把“扣减配额 + 创建任务”放在同一个事务里。

- **为什么这样设计（trade-off）**
  - 优点：实现简单、性能高、天然支持并发竞争。
  - 代价：牺牲了一些业务表达层的优雅性，但这是典型的用数据库原子语义换一致性，非常值。

### 难点二：LLM 输出不稳定，导致大纲 JSON 解析失败

- **问题背景**
  - 大模型流式输出时，可能因为长度、网络或生成中断导致 JSON 不完整。

- **解决方案**
  - 先按正常 JSON 解析。
  - 若失败，执行 `tryFixJson`：
    - 检测字符串是否闭合
    - 统计 `{`、`[` 未闭合数量
    - 自动补全 `"`、`]`、`}`
  - 修复后再次解析。

- **为什么这样设计（trade-off）**
  - 优点：不依赖模型 100% 稳定，系统成功率更高。
  - 代价：只适合“截断类错误”，对语义错误无能为力；但已经覆盖高频异常。

### 难点三：长耗时任务如何让用户“有感知”

- **问题背景**
  - 标题、大纲、正文、图片全部生成完再返回，用户等待时间长，且不知道系统卡在哪一步。

- **解决方案**
  - 使用 `@Async` 将生成任务放到线程池执行。
  - 使用 SSE 将每个阶段的进度、流式文本、单图完成事件实时推送给前端。
  - `SseEmitterManager` 用 `ConcurrentHashMap` 管理任务连接。

- **为什么这样设计（trade-off）**
  - 优点：前端体验显著提升，且后端实现简单。
  - 代价：SSE 更适合单向消息，不适合复杂双向互动；但本场景完全匹配。

### 难点四：多种图片来源如何统一接入且支持降级

- **问题背景**
  - 项目支持图库、AI 生图、Mermaid、Icon、Emoji、SVG 等不同来源，输入输出格式并不一致。

- **解决方案**
  - 用 `ImageServiceStrategy` 统一注册所有 `ImageSearchService`。
  - 根据 `ImageMethodEnum` 自动路由到对应服务。
  - 图片获取失败时自动降级到 fallback 图。
  - 最后统一上传到 COS。

- **为什么这样设计（trade-off）**
  - 优点：扩展性强，后续新增图片来源只需加实现类。
  - 代价：抽象层次稍高，但长期收益明显。

### 难点五：Graph 状态里的对象类型转换问题

- **问题背景**
  - `OverAllState` 内部在状态流转时，可能出现对象反序列化后的类型不匹配问题。

- **解决方案**
  - 在读取状态对象时，做显式转换和 JSON 回转兜底。
  - 对大纲、图片结果等复杂对象，不完全依赖 `instanceof`。

- **为什么这样设计（trade-off）**
  - 优点：解决框架层状态对象不稳定问题。
  - 代价：多了一层转换开销，但相对于大模型调用耗时，这个成本可以接受。

---

## 六、性能优化点

> 说明：仓库里没有现成压测报告，因此下面写法是**基于源码可被面试官接受的真实优化点**；如果你后续有自己的压测数据，可以把“收益表达”替换成真实数字。

### 1）同步生成改异步受理

- **优化前**
  - 如果接口同步等待完整生成，单次请求会被大模型和配图链路长时间阻塞。

- **优化后**
  - 创建接口只负责校验、落库、启动异步任务，快速返回 `taskId`。
  - 用户通过 SSE 订阅后续结果。

- **收益表达**
  - 把“请求耗时”变成“任务受理耗时 + 后台执行耗时”，显著降低接口阻塞风险，提高系统吞吐。

### 2）串行配图改并行配图

- **优化前**
  - 多张图片串行生成，总耗时接近所有图片耗时之和。

- **优化后**
  - `ParallelImageGenerator` 按 `imageSource` 分组并发执行，不同来源并行、同来源串行。

- **收益表达**
  - 总耗时从“全部串行累加”收敛为“最慢分组 + 少量调度开销”，尤其在一篇文章需要多张图时效果明显。

### 3）配额扣减从先查后改改成单 SQL 原子更新

- **优化前**
  - 需要一次查询 + 一次更新，并且存在并发超扣风险。

- **优化后**
  - 合并为单条更新 SQL，通过影响行数判断成功与否。

- **收益表达**
  - 降低数据库交互次数，同时避免重试和补偿逻辑。

### 4）图片统一上传 COS，减少前端兼容成本

- **优化前**
  - 前端可能要兼容不同来源 URL、图片格式甚至外链失效问题。

- **优化后**
  - 后端统一收口，转成稳定的 COS 地址。

- **收益表达**
  - 降低前端复杂度，也减少第三方资源不稳定带来的展示问题。

### 5）流式输出替代轮询

- **优化前**
  - 前端轮询会带来大量无效请求，实时性也差。

- **优化后**
  - 服务端有变化才推送，正文 chunk、图片完成事件实时到达。

- **收益表达**
  - 降低无效请求，提升实时体验。

---

## 七、Java 面试官高频追问点（非常重要）

### 1. 你为什么把文章生成拆成三个阶段？

- **标准回答思路**
  - 因为标题、大纲、正文是不同粒度的生成任务。
  - 一次性生成全文可控性差，而且失败后不可恢复。
  - 分阶段后，用户可以在标题和大纲阶段参与确认，减少最终结果偏差。
  - 从工程上看，也更方便做阶段状态管理、失败重试和进度展示。

### 2. Redis 在你这个项目里主要干什么？

- **标准回答思路**
  - 当前项目 Redis 主要用于 Spring Session 存储登录态，不是做文章内容缓存。
  - 我们在 Session 里只存 `userId`，再从数据库查最新用户信息，避免 Redis 里存完整用户对象导致序列化和脏数据问题。

### 3. 为什么用 SSE，不用 WebSocket？

- **标准回答思路**
  - 场景是服务端单向推送生成进度，前端不需要持续双向交互。
  - SSE 更轻量，接入简单，天然适合大模型流式文本输出。
  - 如果未来要做“协同编辑、双向对话”，那再升级到 WebSocket 更合适。

### 4. 配额扣减怎么避免并发超扣？

- **标准回答思路**
  - 不做“先查再扣”，直接用原子 SQL：
    - `update user set quota = quota - 1 where id = ? and quota > 0`
  - 影响行数为 0 就说明被别人抢先扣完了。
  - 这是数据库层的一致性保障，比 Java 锁更直接。

### 5. 大模型返回 JSON 不规范怎么办？

- **标准回答思路**
  - 先通过 prompt 约束格式。
  - 后端再做兜底：如果是截断类错误，就自动补全引号、括号、花括号再重试解析。
  - 如果是语义错误，就打日志并标记失败，避免脏数据入库。

### 6. 你为什么要用多 Agent 编排，而不是一个 Service 里串起来？

- **标准回答思路**
  - 短期看 Service 串行也能做，但可扩展性差。
  - 我这里把标题、大纲、正文、配图需求、配图生成、图文合成拆成独立节点，是为了后续更容易增加新 Agent，比如审核、润色、事实校验。
  - 本质是在做“职责单一 + 流程可编排”。

### 7. `ParallelImageGenerator` 为什么是“按来源分组并行、组内串行”？

- **标准回答思路**
  - 因为不同图片来源的调用方式和速率特征不同。
  - 来源之间可以天然并发；同一来源内部串行可以避免打爆第三方接口，降低限流风险。
  - 这是吞吐和稳定性的折中。

### 8. 如果 SSE 连接断了怎么办？

- **标准回答思路**
  - `SseEmitter` 设置了超时、完成和异常回调，会自动从管理器里移除。
  - 前端可以根据 `reconnectTime` 做自动重连。
  - 任务结果本身是落库的，所以即使 SSE 断了，也可以通过详情接口补查最终结果。

### 9. 这个系统如何扩展成微服务？

- **标准回答思路**
  - 可以按域拆成：用户/权限服务、文章任务服务、AI 编排服务、图片服务、支付服务。
  - 文章生成链路建议改成“任务服务 + MQ 异步编排”。
  - SSE 可以保留在网关层或任务聚合层，对前端暴露统一进度订阅。

### 10. 高并发下你最担心哪个点？

- **标准回答思路**
  - 不是 MySQL，而是第三方大模型和图片接口的限流与耗时抖动。
  - 我会优先加：任务队列、限流、熔断、重试、降级。
  - 其次是把图片生成、支付回调、日志统计等链路做进一步异步化。

### 11. 为什么登录态里只存 userId？

- **标准回答思路**
  - 减少 Session 序列化负担，避免用户对象结构变更带来的兼容问题。
  - 用户角色、配额、VIP 状态都是强业务字段，应该以数据库最新值为准。

### 12. 支付回调如何保证幂等？

- **标准回答思路**
  - 通过 `sessionId` 找支付记录，如果状态已经是 `SUCCEEDED`，直接返回，不重复升级 VIP。
  - 这是典型的“支付结果落库 + 状态机判重”思路。

---

## 八、项目亮点总结（用于简历 / 面试收尾）

- 我把 AI 文章生成做成了**分阶段、可确认、可流式反馈**的任务链路，而不是粗暴地一次性调模型。
- 我在后端引入了**多 Agent 编排**，把标题、大纲、正文、配图需求、配图生成和图文合成解耦，扩展性更强。
- 我针对大模型工程化落地做了**JSON 容错修复、SSE 实时推送、图片统一收口、任务状态持久化**，不是只停留在“能调通 API”层面。
- 我处理了业务侧关键一致性问题，比如**配额原子扣减、支付回调幂等、VIP 权限边界控制**。
- 这个项目的价值不只是“AI 生成文章”，而是把**内容生产流程产品化、可观测化、可运营化**了。

---

## 补充：建议你在面试中主动强调的技术栈

- `Spring Boot 3.5`
- `Java 21`
- `MyBatis-Flex`
- `MySQL`
- `Redis + Spring Session`
- `Spring AI Alibaba Graph`
- `DashScope`
- `SSE`
- `Stripe`
- `腾讯 COS`

---

## 补充：一句话收尾模板

“这个项目我最核心的工作，不是单纯把大模型接进来，而是把一个不稳定、长耗时的 AI 生成过程，做成了一个可拆阶段、可实时反馈、可控可扩展的后端系统。”  

