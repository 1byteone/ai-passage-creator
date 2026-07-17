# ✍️ AI Passage Creator — 灵犀写作

<div align="center">

![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.13-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![Spring AI](https://img.shields.io/badge/Spring_AI-Alibaba-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![StateGraph](https://img.shields.io/badge/StateGraph-多智能体编排-7B42BC?style=for-the-badge)
![Vue 3](https://img.shields.io/badge/Vue-3.5-4FC08D?style=for-the-badge&logo=vuedotjs&logoColor=white)
![DashScope](https://img.shields.io/badge/LLM-DashScope(Qwen)-FF6F00?style=for-the-badge)
![Gemini](https://img.shields.io/badge/图片-Gemini_Nano_Banana-4285F4?style=for-the-badge&logo=google&logoColor=white)
![Stripe](https://img.shields.io/badge/Payment-Stripe-008CDD?style=for-the-badge&logo=stripe&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=for-the-badge&logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7.0-DC382D?style=for-the-badge&logo=redis&logoColor=white)

**AI 驱动的内容创作平台 — StateGraph 多智能体编排 · 6 策略并行配图 · Stripe VIP 商业闭环**

</div>

---

## 🎯 一句话说清楚

> 输入一个选题，AI 自动走完 **标题策划 → 大纲设计 → 正文撰写 → 配图分析 → 并行配图 → 图文合并** 的全流程，生成可直接发表的精美文章。整个过程通过 **SSE 实时反馈**，关键节点支持**人工确认/编辑**，一键发布到你的内容平台。

---

## 🧠 多智能体协作流程

```mermaid
flowchart TB
    U["👤 用户输入选题"]

    subgraph P1["阶段 1 · 人机协作"]
        A1["📋 TitleGenerator<br/>生成 3-5 标题"]
        H1{"用户选定标题"}
    end
    subgraph P2["阶段 2 · 人机协作"]
        A2["📝 OutlineGenerator<br/>生成大纲 · SSE 流式"]
        H2{"用户编辑 / AI 优化"}
    end
    subgraph P3["阶段 3 · 图文生成"]
        A3["✍️ ContentGenerator<br/>正文 · SSE 流式"]
        A4["🔎 ImageAnalyzer<br/>配图需求分析"]
        A5["🎨 ParallelImageGenerator<br/>并行生成配图"]
        A6["🧩 ContentMerger<br/>图文合并"]
    end

    LLM["🧠 DashScope / Qwen"]
    IMG["🖼️ Gemini · 图库 · 降级 Picsum"]
    COS["☁️ 腾讯云 COS"]
    PAY["💳 Stripe VIP"]
    OUT["📤 图文成品"]

    U --> A1 --> H1 --> A2 --> H2 --> A3
    A3 --> A4 --> A5 --> A6 --> OUT

    A1 --> LLM
    A2 --> LLM
    A3 --> LLM
    A5 --> IMG
    A5 --> COS
    PAY -.-> P3

    classDef agent fill:#7B42BC,stroke:#0d1117,color:#fff
    classDef ext fill:#238636,stroke:#0d1117,color:#fff
    class A1,A2,A3,A4,A5,A6 agent
    class LLM,IMG,COS,PAY ext
```

### 🤖 5 大专业 Agent + 1 合并 Agent

| Agent | 职责 | 输出 | 特色 |
|-------|------|------|------|
| **TitleGenerator** | 基于选题分析爆款规律，生成 3-5 个标题选项 | `List<TitleOption>` | 人机选择确认 |
| **OutlineGenerator** | 生成文章大纲 + 各章节要点 | `OutlineResult` | **SSE 流式**输出，用户可拖拽编辑 |
| **ContentGenerator** | 按大纲生成 Markdown 正文 | 带占位符的完整正文 | **SSE 流式**，JSON 截断自动修复 |
| **ImageAnalyzer** | 分析正文内容，为每个配图点生成策略 | `List<ImageRequirement>` | 智能匹配配图方法 |
| **ParallelImageGenerator** | 按配图策略**并行**拉取/生成图片 | `List<ImageResult>` | CompletableFuture 分组并发 |
| **ContentMerger** | 将图片替换占位符，合并为最终文章 | Markdown 成品 | |

---

## 🎨 6 策略并行配图

```
用户配图策略 → ImageRequirement 列表
    │
    ├─ Pexels 组 ────→ Pexels API 关键词搜索（全部用户可用）
    ├─ Mermaid 组 ───→ mermaid-cli 生成流程图（全部用户可用）
    ├─ Iconify 组 ───→ Iconify API 图标搜索（全部用户可用）
    ├─ 表情包 组 ───→ Bing 图片搜索结果（全部用户可用）
    │
    ├─ Nano Banana 组 → Gemini API AI 生图（✨ VIP 独占）
    └─ SVG 组 ──────→ AI 生成 SVG 概念图（✨ VIP 独占）
    │
    └─ Picsum 降级 ──→ 所有策略失败时的最终兜底
    │
    └─ 上传至 腾讯云 COS → 返回图片 URL 列表
```

---

## 💳 Stripe VIP 商业闭环

| 组件 | 实现 |
|------|------|
| **Checkout Session** | `/vip` 接口创建 Stripe Hosted Checkout |
| **Webhook 签名校验** | `StripeWebhookController` 验证 `Stripe-Signature` |
| **状态幂等** | 按 `paymentIntent.id` 去重，防止重复升级 |
| **终身 VIP** | `$199` 一次付费永久解锁 → 无限额度 + Nano Banana + SVG 高级配图 |
| **7 日退款** | Webhook `charge.refunded` 事件自动降级额度 |

---

## 📊 可观测性

### AOP Agent 执行日志

```java
@AgentExecution(agentName = "ContentGenerator", phase = 3)
public void execute(Map<String, Object> state) { ... }
```

`@AgentExecution` 注解 + `AgentExecutionAspect` 切面，自动记录每次 Agent 调用的：
- Agent 名称、阶段、输入参数、输出结果
- 执行耗时（ms）、成功/失败状态
- 持久化至 `agent_log` 表 → 管理端 ECharts 统计面板

### SSE 流式推送

`SseEmitterManager`（ConcurrentHashMap）管理每个生成任务的 SSE 连接：
- `AGENT1_COMPLETE` → `AGENT2_STREAMING` → `CONTENT_COMPLETE` → `IMAGES_COMPLETE` → `MERGE_COMPLETE`
- 心跳保活（30s）+ 按 `taskId` 精准路由

---

## 🏗️ 技术栈

| 层级 | 技术选型 |
|------|---------|
| **运行时** | Java 21 · Spring Boot 3.5.13（虚拟线程） |
| **AI 编排** | Spring AI Alibaba Graph 1.1.0（StateGraph + NodeAction） |
| **LLM** | DashScope / 通义千问（Qwen） |
| **ORM** | MyBatis-Flex 1.11.1 |
| **缓存 / Session** | Redis 7 + Spring Session Data Redis |
| **对象存储** | 腾讯云 COS |
| **文档** | Knife4j 4.4（OpenAPI 3） |
| **前端** | Vue 3.5 + TypeScript 5.8 + Ant Design Vue 4.2 + ECharts 6 |

---

## 🚀 快速启动

```bash
git clone https://github.com/1byteone/ai-passage-creator.git
cd ai-passage-creator

# 配置环境变量（LLM Key, Stripe Key, COS Key 等）
cp .env.example .env

# 一键启动（Java 后端 + MySQL + Redis + Vue 前端）
docker-compose up -d

# 访问
# 前端: http://localhost
# API 文档: http://localhost:8123/api/doc.html
```

---

## 📂 项目结构

```
ai-passage-creator/
├── src/main/java/com/example/aipassagecreator/
│   ├── agent/                  # 🧠 AI Agent 编排层
│   │   ├── agents/             #   6 个 NodeAction Agent 实现
│   │   ├── parallel/           #   ParallelImageGenerator 并行配图
│   │   ├── tools/              #   @Tool 注解工具
│   │   ├── config/             #   StateGraph 配置 + MemorySaver
│   │   ├── context/            #   ThreadLocal SSE StreamHandler
│   │   └── ArticleAgentOrchestrator.java  # StateGraph 总调度
│   ├── controller/             # 🌐 REST + SSE 控制器
│   ├── service/                # 📦 业务逻辑 + 图片策略 + Stripe
│   ├── mapper/                 # 🗄️ MyBatis-Flex 数据层
│   ├── annotation/aop/         # 📊 @AgentExecution AOP 切面
│   └── model/                  # 📄 po/dto/vo
├── frontend/                   # 🎨 Vue 3 + Ant Design 前端
└── sql/                        # 🗃️ 初始化脚本（含 agent_log 表）
```

---

## 📄 License

[MIT](LICENSE) © 1byteone
