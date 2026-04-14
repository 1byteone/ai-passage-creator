# AI 文章生成平台 (ai-passage-creator) 技术架构笔记

## 1. 项目背景与需求分析

### 1.1 项目简介
**ai-passage-creator** 是一个基于 **Spring Boot 3 + Java 21** 构建的智能化内容创作平台。它通过编排多个垂直领域的 AI Agent（智能体），实现了从选题输入、标题策划、大纲设计、正文撰写到多模态配图生成的全自动化文章生产闭环，旨在为新媒体运营者提供高质量、高效率的图文内容解决方案。

### 1.2 核心痛点
*   **创作效率低**：传统人工撰写一篇深度长文需数小时，且配图检索耗时。
*   **内容同质化**：人工创作易陷入思维定势，缺乏结构化的爆款逻辑。
*   **多模态协同难**：文字与图片的逻辑匹配度低，手动排版繁琐。

### 1.3 业务流程
1.  **任务初始化**：用户输入选题及风格偏好，系统创建异步任务并返回 `taskId`。
2.  **阶段一：标题策划**：Agent 根据选题生成 3-5 个“爆款”标题方案供用户选择。
3.  **阶段二：大纲设计**：用户确认标题后，Agent 依据主副标题生成分章节的结构化大纲。
4.  **阶段三：内容生成**：
    *   **正文撰写**：Agent 根据大纲分块生成 Markdown 格式正文。
    *   **配图分析**：Agent 识别正文关键节点，生成配图需求（位置、类型、关键词）。
    *   **并行配图**：根据来源策略（Pexels 图库 / Nano Banana AI 生图）并行获取或生成图片。
    *   **图文合成**：将占位符替换为实际图片 URL，输出完整图文。
5.  **实时反馈**：全程通过 **SSE (Server-Sent Events)** 推送流式进度至前端。

---

## 2. 系统架构与模块设计

### 2.1 总体架构（分层设计）
*   **接入层 (Controller)**：处理 HTTP 请求，负责 SSE 连接建立与任务触发。
*   **业务编排层 (Service/AgentOrchestrator)**：核心逻辑层，利用 **Spring AI Alibaba Graph** 实现多智能体状态机编排。
*   **智能体执行层 (Agents)**：封装 LLM 调用、Prompt 管理及结果解析的具体执行单元。
*   **基础设施层 (Infrastructure)**：包含 MySQL (持久化)、Redis (会话/缓存)、OSS (图片存储) 及 DashScope (大模型服务)。

### 2.2 核心模块详解

| 模块名称 | 核心职责 | 关键技术点 |
| :--- | :--- | :--- |
| **ArticleAsyncService** | 异步任务调度中心 | 使用 `@Async` 配合自定义线程池，解耦请求响应与耗时计算。 |
| **ArticleAgentOrchestrator** | 智能体编排引擎 | 基于 **StateGraph** 定义节点（Node）与边（Edge），管理任务流转状态。 |
| **OutlineGeneratorAgent** | 大纲生成智能体 | 集成流式输出与 JSON 自动修复逻辑，确保结构化数据完整性。 |
| **ParallelImageGenerator** | 并行配图处理器 | 采用并行流/CompletableFuture 优化多图生成耗时，支持多种图片来源策略。 |
| **SseEmitterManager** | SSE 消息管理器 | 维护 `taskId` 与 `SseEmitter` 的映射关系，实现精准的消息推送。 |

### 2.3 模块间交互
*   **状态共享**：各 Agent 之间不直接调用，而是通过 `OverAllState` 在 StateGraph 中交换数据（如标题、大纲、正文）。
*   **异步通信**：Controller 触发 Service 后立即返回，Service 通过 SSE Emitter 将 Agent 的执行进度（流式文本、完成信号）推送到前端。

---

## 3. 技术栈与实现方案

| 技术组件 | 具体技术 | 应用场景与解决的问题 |
| :--- | :--- | :--- |
| **核心框架** | Spring Boot 3.5 + Java 21 | 利用虚拟线程潜力与最新 LTS 特性，提升并发处理能力。 |
| **AI 编排** | Spring AI Alibaba Graph | 解决多 Agent 协作中的状态管理与复杂流程控制问题。 |
| **数据库** | MySQL + MyBatis-Flex | 存储文章元数据及生成结果，MyBatis-Flex 提供轻量高效的 ORM 体验。 |
| **对象映射** | Jackson ObjectMapper | **解决 StateGraph 跨类加载器导致的类型转换失败问题**，实现深拷贝转换。 |
| **流式通信** | SSE (Server-Sent Events) | 替代 WebSocket，实现轻量级、单向的实时进度推送。 |
| **JSON 处理** | Gson + 自定义修复算法 | 应对 LLM 输出截断问题，通过括号补全算法提高解析成功率。 |
| **对象存储** | Tencent COS | 存储 AI 生成的封面图及插图，提供稳定的 CDN 访问链接。 |

---

## 4. 核心业务后端实现流程

以 **“阶段二：大纲生成”** 为例，描述从 Controller 到 Agent 的执行链路：

1.  **Controller 层**：接收用户确认标题的请求，调用 `articleAsyncService.executePhase2(taskId)`。
2.  **Service 层**：
    *   查询数据库获取已选定的标题信息。
    *   构建 `ArticleState` 上下文对象。
    *   调用 `articleAgentOrchestrator.executePhase2_GenerateOutline(state, streamHandler)`。
3.  **Orchestrator 层 (编排器)**：
    *   初始化 `StateGraph`，注册 `outline_generator` 节点。
    *   调用 `compiledGraph.invoke(inputs)` 启动图执行。
4.  **Agent 层 (执行器)**：
    *   `OutlineGeneratorAgent.apply()` 被触发。
    *   组装 Prompt，调用 `DashScopeChatModel.stream()` 进行流式对话。
    *   **关键处理**：捕获流式返回的 JSON，若解析失败则触发 `tryFixJson()` 自动补全截断的括号。
    *   将解析后的 `OutlineResult` 放入 StateGraph 的状态 Map 中。
5.  **结果回传**：
    *   Orchestrator 从 `finalState` 提取大纲数据。
    *   **难点攻克**：使用 `objectMapper.convertValue(v, ArticleState.OutlineResult.class)` 解决 StateGraph 内部对象序列化导致的类型不匹配问题。
    *   通过 `streamHandler` 发送 `AGENT2_COMPLETE` 信号，并更新数据库。

---

## 6. 快速上手指南

### 6.1 环境准备
*   **JDK**: Java 21+ (推荐使用 JDK 21 LTS)
*   **Maven**: 3.8+
*   **MySQL**: 8.0+
*   **Redis**: 7.0+
*   **IDE**: IntelliJ IDEA (推荐安装 Lombok 插件)

### 6.2 数据库初始化
1.  创建数据库：`CREATE DATABASE ai_passage_creator CHARACTER SET utf8mb4;`
2.  执行初始化脚本（按顺序）：
    ```bash
    sql/init.sql              # 基础表结构
    sql/core.sql              # 文章核心表
    sql/add_phase_fields.sql  # 阶段控制字段
    sql/add_vip_payment.sql   # 会员与支付相关
    ```

### 6.3 核心配置修改
在 `src/main/resources/application.yml` 中修改以下配置：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai_passage_creator
    username: root
    password: YOUR_DB_PASSWORD
  data:
    redis:
      host: localhost
      port: 6379
  ai:
    dashscope:
      api-key: sk-your-api-key  # 阿里云 DashScope API Key
```

### 6.4 项目启动
```bash
# 编译并打包
mvn clean package -DskipTests

# 启动应用
java -jar target/ai-passage-creator-0.0.1-SNAPSHOT.jar
```
启动成功后，访问 Knife4j 接口文档：`http://localhost:8567/api/doc.html`

---

## 7. 核心目录结构

```
ai-passage-creator/
├── src/main/java/com/example/aipassagecreator/
│   ├── agent/                  # AI 智能体编排层
│   │   ├── agents/             # 具体 Agent 实现 (标题、大纲、正文等)
│   │   ├── parallel/           # 并行配图生成器
│   │   └── context/            # SSE 上下文管理
│   ├── controller/             # RESTful 接口层
│   ├── service/                # 业务逻辑层
│   ├── mapper/                 # MyBatis-Flex 数据访问层
│   ├── model/                  # 实体类与 DTO
│   ├── config/                 # 全局配置 (COS, Redis, Stripe)
│   └── constant/               # 常量定义 (Prompt, 枚举)
├── sql/                        # 数据库初始化脚本
└── resources/
    └── application.yml         # 核心配置文件
```

---

## 8. 开发调试建议

### 8.1 常用调试技巧
*   **SSE 调试**：建议使用 Postman 或前端页面直接观察流式输出效果。
*   **Agent 日志**：关注控制台输出的 `Stage X (Multi-Agent Orchestration)` 日志，定位流转断点。
*   **JSON 修复**：若遇到大纲生成失败，检查 `OutlineGeneratorAgent` 中的 `tryFixJson` 日志。

### 8.2 常见问题排查
| 问题现象 | 可能原因 | 解决方案 |
| :--- | :--- | :--- |
| 任务一直处于 PENDING | Redis 未启动或连接失败 | 检查 `application.yml` 中的 Redis 配置 |
| 类型转换报错 | StateGraph 状态提取异常 | 确认已使用 `ObjectMapper.convertValue` |
| 图片无法显示 | COS 密钥过期或权限不足 | 检查腾讯云 COS 的 Bucket 权限设置 |
| JSON 解析失败 | LLM 返回格式不规范 | 查看 `PromptConstant` 中的约束提示词 |

---

## 5. 难点攻克与优化

### 5.1 StateGraph 跨类加载器类型转换失效
*   **现象**：Agent 返回的对象在 StateGraph 流转后，使用 `instanceof` 检查失败，强制转换报错。
*   **原因**：Spring AI Graph 在内部处理状态时可能涉及对象的序列化/反序列化，导致运行时类加载器不一致。
*   **解决方案**：引入 **Jackson ObjectMapper**。利用其 `convertValue` 方法，先将对象序列化为中间态（JSON 树），再反序列化为目标类型。这种方式绕过了直接的引用检查，实现了“深度类型转换”。

### 5.2 LLM 输出截断导致的 JSON 解析异常
*   **现象**：在生成复杂大纲时，LLM 因 Token 限制或网络波动返回未闭合的 JSON，导致 `Gson` 抛出 `MalformedJsonException`。
*   **解决方案**：实现 **容错解析机制**。
    1.  首先尝试正常解析。
    2.  若失败，进入 `tryFixJson` 逻辑：遍历字符串统计未闭合的 `{` 和 `[` 数量。
    3.  自动在末尾补全对应的 `}` 和 `]`。
    4.  再次尝试解析，显著提升了大纲生成的成功率。

### 5.3 异步任务的实时感知
*   **挑战**：文章生成耗时较长（30s-60s），传统轮询体验差且浪费服务器资源。
*   **解决方案**：采用 **SSE (Server-Sent Events)**。
    *   后端维护一个 `ConcurrentHashMap<String, SseEmitter>`。
    *   在 Agent 执行的每一个关键节点（如标题生成完毕、流式文本产出）主动调用 `emitter.send()`。
    *   前端监听事件流，实现类似打字机的实时渲染效果。
