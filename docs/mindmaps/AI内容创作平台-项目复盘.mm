<map version="1.0.1">
<!-- AI内容创作平台-项目复盘 -->

<node ID="root" TEXT="AI内容创作平台-项目复盘">
    <!-- 项目基本信息 -->
    <node ID="info" TEXT="项目基本信息" POSITION="left">
        <node TEXT="项目名称: AI Passage Creator"/>
        <node TEXT="定位: 企业级AI内容创作平台"/>
        <node TEXT="技术栈: Spring Boot 3.5 + Spring AI Alibaba"/>
        <node TEXT="核心技术: 多Agent编排 + Stripe支付"/>
        <node TEXT="复盘日期: 2026-06-01"/>
    </node>

    <!-- 用户故事 -->
    <node ID="stories" TEXT="一、用户故事" POSITION="right">
        <node TEXT="内容创作者">
            <node TEXT="故事1: 快速生成爆款文章标题">
                <node TEXT="输入选题→自动生成3-5个标题方案"/>
                <node TEXT="验收标准">
                    <node TEXT="5秒内生成"/>
                    <node TEXT="包含主标题和副标题"/>
                    <node TEXT="符合新媒体爆款风格"/>
                    <node TEXT="支持不同风格(科技/情感/教育/幽默)"/>
                </node>
            </node>
            <node TEXT="故事2: 可视化大纲编辑">
                <node TEXT="确认标题→自动生成结构化大纲→AI修改支持"/>
                <node TEXT="验收标准">
                    <node TEXT="生成3-5个章节大纲"/>
                    <node TEXT="每个章节有标题和要点"/>
                    <node TEXT="AI根据建议自动修改"/>
                    <node TEXT="支持手动编辑"/>
                </node>
            </node>
            <node TEXT="故事3: 实时查看文章生成进度">
                <node TEXT="SSE实时推送进度"/>
                <node TEXT="验收标准">
                    <node TEXT="显示当前执行阶段"/>
                    <node TEXT="支持流式显示大纲和正文"/>
                    <node TEXT="显示错误信息"/>
                </node>
            </node>
            <node TEXT="故事4: 自动配图生成">
                <node TEXT="根据文章内容自动生成配图"/>
                <node TEXT="验收标准">
                    <node TEXT="多种配图方式(Pexels/AI生图/Mermaid/Iconify)"/>
                    <node TEXT="封面图自动生成"/>
                    <node TEXT="支持并行生成多张配图"/>
                </node>
            </node>
        </node>
        <node TEXT="VIP用户">
            <node TEXT="故事5: 购买永久会员">
                <node TEXT="Stripe支付购买永久VIP会员"/>
                <node TEXT="验收标准">
                    <node TEXT="支持Stripe支付"/>
                    <node TEXT="支付成功立即升级VIP"/>
                    <node TEXT="VIP无限创作配额"/>
                    <node TEXT="支持退款流程"/>
                </node>
            </node>
        </node>
        <node TEXT="系统管理员">
            <node TEXT="故事6: 统计分析">
                <node TEXT="查看系统运营统计数据"/>
                <node TEXT="验收标准">
                    <node TEXT="今日/本周/本月创作数量"/>
                    <node TEXT="任务成功率"/>
                    <node TEXT="平均生成耗时"/>
                    <node TEXT="活跃用户数和VIP用户数"/>
                </node>
            </node>
            <node TEXT="故事7: Agent执行日志">
                <node TEXT="查看AI Agent执行日志和性能数据"/>
                <node TEXT="验收标准">
                    <node TEXT="记录执行时间"/>
                    <node TEXT="记录输入输出数据"/>
                    <node TEXT="记录执行状态和错误"/>
                </node>
            </node>
        </node>
    </node>

    <!-- 功能需求描述 -->
    <node ID="requirements" TEXT="二、功能需求描述" POSITION="right">
        <node TEXT="模块1: 多Agent文章生成系统">
            <node TEXT="标题生成Agent - P0"/>
            <node TEXT="大纲生成Agent - P0"/>
            <node TEXT="正文生成Agent - P0"/>
            <node TEXT="配图分析Agent - P0"/>
            <node TEXT="图片生成Agent - P0"/>
            <node TEXT="图文合成Agent - P0"/>
        </node>
        <node TEXT="模块2: 人机协作流程">
            <node TEXT="三阶段流程: 标题选择→大纲编辑→正文生成 - P0"/>
            <node TEXT="状态机管理: PENDING/PROCESSING/COMPLETED/FAILED - P0"/>
            <node TEXT="SSE进度推送 - P0"/>
            <node TEXT="AI修改大纲 - P1"/>
        </node>
        <node TEXT="模块3: 多智能体编排">
            <node TEXT="StateGraph编排 - P0"/>
            <node TEXT="并行配图生成 - P1"/>
            <node TEXT="状态共享(ArticleState) - P0"/>
        </node>
        <node TEXT="模块4: 会员与支付系统">
            <node TEXT="VIP购买(Stripe支付) - P0"/>
            <node TEXT="Webhook回调 - P0"/>
            <node TEXT="配额管理 - P0"/>
            <node TEXT="退款处理 - P1"/>
        </node>
        <node TEXT="模块5: 日志与统计">
            <node TEXT="Agent执行日志(AOP) - P1"/>
            <node TEXT="运营统计 - P1"/>
            <node TEXT="Redis缓存 - P1"/>
        </node>
        <node TEXT="配图方式支持">
            <node TEXT="PEXELS: 真实场景/产品照片/人物照片"/>
            <node TEXT="NANO_BANANA: 创意插画/抽象概念/艺术风格"/>
            <node TEXT="MERMAID: 流程图/架构图/时序图"/>
            <node TEXT="ICONIFY: 图标/符号/装饰性图标"/>
            <node TEXT="EMOJI_PACK: 轻松幽默配图"/>
            <node TEXT="SVG_DIAGRAM: 思维导图/逻辑关系"/>
        </node>
    </node>

    <!-- 业务逻辑流程分析 -->
    <node ID="flow" TEXT="三、业务逻辑流程" POSITION="right">
        <node TEXT="整体业务流程">
            <node TEXT="阶段1: 标题生成">
                <node TEXT="用户输入选题"/>
                <node TEXT="→ TitleGeneratorAgent"/>
                <node TEXT="→ 3-5个标题方案"/>
            </node>
            <node TEXT="阶段2: 大纲生成">
                <node TEXT="用户选择标题+补充描述"/>
                <node TEXT="→ OutlineGeneratorAgent"/>
                <node TEXT="→ 文章大纲"/>
                <node TEXT="支持AI修改大纲"/>
            </node>
            <node TEXT="阶段3: 正文+配图生成">
                <node TEXT="ContentGeneratorAgent → 正文"/>
                <node TEXT="ImageAnalyzerAgent → 配图需求"/>
                <node TEXT="ParallelImageGenerator → 并行配图"/>
                <node TEXT="ContentMergerAgent → 图文合成"/>
                <node TEXT="→ 完整图文输出"/>
            </node>
        </node>
        <node TEXT="多智能体编排(StateGraph)">
            <node TEXT="节点定义">
                <node TEXT="content_generator"/>
                <node TEXT="image_analyzer"/>
                <node TEXT="parallel_image_generator"/>
                <node TEXT="content_merger"/>
            </node>
            <node TEXT="边定义: 顺序执行">
                <node TEXT="START → content_generator"/>
                <node TEXT="→ image_analyzer"/>
                <node TEXT="→ parallel_image_generator"/>
                <node TEXT="→ content_merger → END"/>
            </node>
        </node>
        <node TEXT="并行配图生成流程">
            <node TEXT="配图需求列表: PEXELS/MERMAID/ICONIFY/AI生图"/>
            <node TEXT="CompletableFuture并行执行"/>
            <node TEXT="Thread 1: Pexels图库检索"/>
            <node TEXT="Thread 2: Mermaid图表生成"/>
            <node TEXT="Thread 3: Iconify图标获取"/>
            <node TEXT="Thread 4: AI生图"/>
            <node TEXT="汇总结果按position排序"/>
        </node>
        <node TEXT="SSE实时推送流程">
            <node TEXT="客户端: GET /article/progress/{taskId}"/>
            <node TEXT="服务端: 创建SseEmitter存入Manager"/>
            <node TEXT="推送消息序列">
                <node TEXT="AGENT1_COMPLETE"/>
                <node TEXT="AGENT2_STREAMING/AGENT2_COMPLETE"/>
                <node TEXT="AGENT3_STREAMING/AGENT3_COMPLETE"/>
                <node TEXT="IMAGE_COMPLETE"/>
                <node TEXT="ALL_COMPLETE"/>
            </node>
        </node>
        <node TEXT="支付流程">
            <node TEXT="POST /payment/vip → 创建Checkout Session"/>
            <node TEXT="返回支付链接 → 跳转支付页面"/>
            <node TEXT="Webhook支付成功 → 更新VIP身份"/>
            <node TEXT="支付成功跳转成功页面"/>
        </node>
    </node>

    <!-- 关键代码视图窗口 -->
    <node ID="code" TEXT="四、关键代码实现" POSITION="right">
        <node TEXT="项目结构">
            <node TEXT="agent/ - 多智能体模块">
                <node TEXT="ArticleAgentOrchestrator.java - 核心编排器"/>
                <node TEXT="agents/ - 各Agent实现"/>
                <node TEXT="parallel/ - 并行处理"/>
                <node TEXT="tools/ - Agent工具"/>
            </node>
            <node TEXT="controller/ - 控制器层"/>
            <node TEXT="service/ - 服务层"/>
            <node TEXT="model/ - 数据模型"/>
            <node TEXT="aop/ - AOP切面"/>
            <node TEXT="enums/ - 枚举类"/>
            <node TEXT="config/ - 配置类"/>
        </node>
        <node TEXT="核心类关系">
            <node TEXT="ArticleController → ArticleAsyncService"/>
            <node TEXT="→ ArticleAgentOrchestrator"/>
            <node TEXT="→ 各Agent实现(TitleGenerator/OutlineGenerator/ContentGenerator)"/>
        </node>
        <node TEXT="ArticleState状态对象">
            <node TEXT="任务标识: taskId/topic/userDescription/style"/>
            <node TEXT="阶段输出">
                <node TEXT="阶段1: titleOptions/title"/>
                <node TEXT="阶段2: outline"/>
                <node TEXT="阶段3: content/imageRequirements/images/fullContent"/>
            </node>
            <node TEXT="内部类">
                <node TEXT="TitleOption: mainTitle/subTitle"/>
                <node TEXT="OutlineSection: section/title/points"/>
                <node TEXT="ImageRequirement: position/type/imageSource/keywords"/>
            </node>
        </node>
        <node TEXT="SSE消息类型枚举">
            <node TEXT="AGENT1_COMPLETE - 标题方案生成完成"/>
            <node TEXT="TITLES_GENERATED - 标题方案已生成"/>
            <node TEXT="AGENT2_STREAMING - 大纲流式输出"/>
            <node TEXT="AGENT2_COMPLETE - 大纲生成完成"/>
            <node TEXT="AGENT3_STREAMING - 正文流式输出"/>
            <node TEXT="AGENT3_COMPLETE - 正文生成完成"/>
            <node TEXT="IMAGE_COMPLETE - 单张配图完成"/>
            <node TEXT="ALL_COMPLETE - 全部完成"/>
            <node TEXT="ERROR - 错误"/>
        </node>
    </node>

    <!-- 原理剖析 -->
    <node ID="principle" TEXT="五、原理剖析" POSITION="right">
        <node TEXT="Spring AI Alibaba多智能体编排">
            <node TEXT="StateGraph核心概念">
                <node TEXT="创建状态图: new StateGraph(keyStrategyFactory)"/>
                <node TEXT="添加节点: addNode(name, node_async(agent))"/>
                <node TEXT="定义边: addEdge控制流程"/>
                <node TEXT="编译执行: compile().invoke(inputs)"/>
            </node>
            <node TEXT="状态共享机制">
                <node TEXT="KeyStrategyFactory: 定义状态键存储策略"/>
                <node TEXT="OverAllState: 所有Agent共享状态容器"/>
                <node TEXT="NodeAction接口: apply(OverAllState)方法"/>
            </node>
            <node TEXT="Agent实现示例">
                <node TEXT="@Component implements NodeAction"/>
                <node TEXT="apply方法: 从state读取输入→执行→返回结果合并"/>
            </node>
        </node>
        <node TEXT="异步处理与SSE推送">
            <node TEXT="异步任务执行">
                <node TEXT="@Async(articleExecutor)使用自定义线程池"/>
                <node TEXT="执行Agent编排"/>
                <node TEXT="推送完成消息"/>
            </node>
            <node TEXT="SSE推送机制">
                <node TEXT="控制器创建SseEmitter"/>
                <node TEXT="SseEmitterManager用ConcurrentHashMap管理连接"/>
                <node TEXT="send方法推送消息"/>
            </node>
            <node TEXT="ThreadLocal传递流式处理器">
                <node TEXT="StreamContextHolder: ThreadLocal&lt;Consumer&lt;String&gt;&gt;"/>
                <node TEXT="解决StateGraph内部无法直接访问SSE问题"/>
                <node TEXT="Agent中使用: streamHandler.accept(message)"/>
            </node>
        </node>
        <node TEXT="并行配图生成原理">
            <node TEXT="分组并行策略">
                <node TEXT="按imageSource分组"/>
                <node TEXT="不同类型并行执行"/>
                <node TEXT="同类型串行执行避免API限流"/>
            </node>
            <node TEXT="实现关键">
                <node TEXT="CompletableFuture.runAsync()并行"/>
                <node TEXT="CopyOnWriteArrayList收集结果"/>
                <node TEXT="CompletableFuture.allOf().join()等待完成"/>
            </node>
            <node TEXT="设计权衡">
                <node TEXT="不同类型并行: 提升效率"/>
                <node TEXT="同类型串行: 避免限流"/>
                <node TEXT="线程安全: CopyOnWriteArrayList"/>
            </node>
        </node>
        <node TEXT="配额管理原理">
            <node TEXT="原子更新防并发">
                <node TEXT="管理员和VIP跳过检查"/>
                <node TEXT="原子更新: UPDATE...SET quota=quota-1 WHERE quota&gt;0"/>
                <node TEXT="affectedRows==0则配额不足"/>
            </node>
            <node TEXT="配额检查时机">
                <node TEXT="创建文章请求"/>
                <node TEXT="→ checkAndConsumeQuota()"/>
                <node TEXT="→ 创建文章任务保存数据库"/>
            </node>
        </node>
        <node TEXT="AOP日志切面原理">
            <node TEXT="@Around注解拦截AgentExecution"/>
            <node TEXT="记录开始时间提取taskId和输入数据"/>
            <node TEXT="执行目标方法"/>
            <node TEXT="记录成功/失败状态和耗时"/>
            <node TEXT="异步保存日志"/>
        </node>
        <node TEXT="支付幂等性设计">
            <node TEXT="查找PaymentRecord"/>
            <node TEXT="幂等性检查: 已处理则直接返回"/>
            <node TEXT="更新支付记录状态"/>
            <node TEXT="升级用户为VIP"/>
        </node>
    </node>

    <!-- 技术亮点总结 -->
    <node ID="highlight" TEXT="六、技术亮点总结" POSITION="right">
        <node TEXT="架构层面">
            <node TEXT="多智能体编排: StateGraph实现复杂工作流"/>
            <node TEXT="人机协作: 三阶段流程提升内容质量"/>
            <node TEXT="并行处理: 配图并行生成提升效率"/>
        </node>
        <node TEXT="技术实现">
            <node TEXT="SSE实时推送: 实时查看生成进度"/>
            <node TEXT="流式输出: 减少等待感"/>
            <node TEXT="ThreadLocal跨层传递: 解决StateGraph访问SSE问题"/>
            <node TEXT="原子配额扣减: 防止并发问题"/>
            <node TEXT="AOP日志切面: 无侵入式记录"/>
        </node>
        <node TEXT="工程实践">
            <node TEXT="支付幂等性: Webhook回调幂等处理"/>
            <node TEXT="状态机管理: 清晰任务状态管理"/>
            <node TEXT="缓存优化: Redis缓存统计数据"/>
            <node TEXT="多种配图方式: 6种方式满足不同场景"/>
        </node>
    </node>

    <!-- 待优化项 -->
    <node ID="optimize" TEXT="七、待优化项" POSITION="right">
        <node TEXT="错误恢复机制: 任务失败后从断点恢复"/>
        <node TEXT="配额套餐: 支持多种配额套餐购买"/>
        <node TEXT="文章模板: 支持用户自定义模板"/>
        <node TEXT="批量生成: 支持批量生成多篇文章"/>
        <node TEXT="A/B测试: 支持标题和配图测试"/>
    </node>
</node>
</map>