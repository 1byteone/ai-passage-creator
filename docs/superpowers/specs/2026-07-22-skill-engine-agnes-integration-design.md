# Skill Engine + AGNES 深度融合架构设计文档

> 基于 3 轮审计（18 个问题）的完整解决方案设计
> 日期：2026-07-22
> 项目：AI Passage Creator（灵犀写作）

---

## 目录

1. [设计目标与原则](#1-设计目标与原则)
2. [整体架构](#2-整体架构)
3. [核心组件设计](#3-核心组件设计)
4. [AGNES 深度集成方案](#4-agnes-深度集成方案)
5. [Skill 定义规范](#5-skill-定义规范)
6. [数据持久化](#6-数据持久化)
7. [API 设计](#7-api-设计)
8. [SSE 消息协议](#8-sse-消息协议)
9. [目录结构](#9-目录结构)
10. [分阶段实施计划](#10-分阶段实施计划)

---

## 1. 设计目标与原则

### 核心目标

将 huashu-skills 的 21 个内容创作能力原生集成到 Spring Boot 后端，以 AGNES 模型为默认驱动，复用现有 StateGraph 编排体系，实现可插拔的 Skill 能力生态。

### 设计原则

| 原则 | 说明 |
|------|------|
| **统一编排** | 所有 Skill 统一使用 StateGraph 构建，无独立 Pipeline Engine |
| **模型抽象** | 所有 Agent 通过 `ChatModel` 接口调用，不依赖具体实现 |
| **AGNES 优先** | 新 Skill 默认使用 AGNES 模型，DashScope 作为降级/辅助 |
| **声明式定义** | Skill 通过 YAML 声明，无需编写 Java 编排代码 |
| **渐进增强** | 现有文章写作功能保持兼容，逐步迁移到 Skill 体系 |
| **可观测性** | 每个 Skill 执行有完整日志、耗时、Token 消耗追踪 |

---

## 2. 整体架构

### 2.1 四层架构

```
┌─────────────────────────────────────────────────────────────────┐
│  API 层                                                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │ SkillController│  │ ArticleCtrl  │  │ SseEmitter           │  │
│  │ /skill/*      │  │ (原样保留)    │  │ (扩展支持 skill)     │  │
│  └──────┬───────┘  └──────────────┘  └──────────────────────┘  │
├─────────┼───────────────────────────────────────────────────────┤
│  编排调度层                                                      │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ SkillRegistry                 SkillExecutionChain         │  │
│  │ (扫描 → 注册 → 构建 StateGraph)  (组合/链式调用)           │  │
│  └───────────────────┬───────────────────────────────────────┘  │
│                      │ 委托                                       │
│  ┌───────────────────▼───────────────────────────────────────┐  │
│  │ StateGraph 编排器 (现有复用)                               │  │
│  │ 动态构建: YAML 配置 → StateGraphBuilder → compiledGraph   │  │
│  └───────────────────────────────────────────────────────────┘  │
├────────────────────────────────────────────────────────────────┤
│  Skill 定义层                                                   │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ resources/skills/{skillName}/                              │  │
│  │   ├── skill.yaml          # 元数据 + 阶段定义 + 变量声明   │  │
│  │   └── prompts/            # Prompt 模板文件 (.md)          │  │
│  │       ├── phase1_xxx.md                                    │  │
│  │       ├── phase2_xxx.md                                    │  │
│  │       └── ...                                              │  │
│  └───────────────────────────────────────────────────────────┘  │
├────────────────────────────────────────────────────────────────┤
│  模型层 (ChatModel 抽象)                                        │
│  ┌────────────────────┐    ┌───────────────────────────────┐   │
│  │ AGNES ChatModel    │    │ DashScope ChatModel           │   │
│  │ (agnes-2.0-flash)  │    │ (qwen-max / 现有模型)         │   │
│  │ 默认 Skill 模型     │    │ 文章写作 / 降级               │   │
│  └────────┬───────────┘    └───────────┬───────────────────┘   │
│           └──────────┬─────────────────┘                       │
│                      ▼                                          │
│              ModelRouter (策略路由)                              │
└────────────────────────────────────────────────────────────────┘
```

### 2.2 核心流程

```
用户请求 → POST /skill/proofreading/execute
  ↓
SkillController → SkillRegistry.getSkill("proofreading")
  ↓
SkillExecution 创建 → 分配 skillExecutionId (UUID)
  ↓
skill_execution 表 INSERT status=RUNNING
  ↓
StateGraph.execute() 构建并执行 Graph
  ├── Phase 1: Content Review Node
  │     ├── 加载 prompts/phase1_content_review.md
  │     ├── 注入变量 {articleContent, style}
  │     ├── AGNES ChatModel.call() / .stream()
  │     ├── 解析输出 → 写入 OverAllState
  │     └── SSE 推送 {type:"skill.progress", phase:1, ...}
  ├── Phase 2: AI Tone Fix Node
  │     └── (同上)
  └── Phase 3: Rhythm Polish Node
        └── (同上)
  ↓
skill_execution 表 UPDATE status=SUCCESS, output_data=...
  ↓
返回 {skillExecutionId, status, result}
```

---

## 3. 核心组件设计

### 3.1 SkillRegistry — 自动注册与生命周期管理

```java
@Component
public class SkillRegistry implements InitializingBean {

    private final Map<String, SkillDefinition> skillMap = new LinkedHashMap<>();
    private final Map<String, CompiledGraph> graphCache = new ConcurrentHashMap<>();
    private final ResourceLoader resourceLoader;
    private final ChatModel agnesChatModel;
    private final ChatModel dashscopeChatModel;
    private final ModelRouter modelRouter;

    @Override
    public void afterPropertiesSet() {
        // 扫描 classpath:skills/*/skill.yaml
        Resource[] resources = resourceLoader.getResources("classpath:skills/*/skill.yaml");
        for (Resource resource : resources) {
            SkillDefinition def = parseYaml(resource);
            skillMap.put(def.getName(), def);
            // 预构建 StateGraph（延迟构建也可）
            graphCache.put(def.getName(), buildGraph(def));
        }
        log.info("SkillRegistry 初始化完成，共注册 {} 个 Skill", skillMap.size());
    }

    public SkillExecution createExecution(String skillName, Map<String, Object> inputs) {
        SkillDefinition def = getSkill(skillName);
        String executionId = UUID.randomUUID().toString();
        return new SkillExecution(executionId, def, inputs, graphCache.get(skillName), modelRouter);
    }

    public SkillExecutionChain createChain(String... skillNames) {
        return new SkillExecutionChain(skillNames);
    }
}
```

### 3.2 SkillDefinition — 声明式定义模型

```java
public class SkillDefinition {
    private String name;                    // proofreading, topic-gen, slides
    private String description;             // 描述
    private String category;                // writing/design/research/image
    private List<String> requiredRoles;     // user/vip/admin
    private boolean isMultiRound;           // 是否多轮交互
    private List<PhaseDefinition> phases;   // 阶段定义
    private Map<String, VariableDef> variables;  // 全局变量声明
}

public class PhaseDefinition {
    private String name;                    // content_review
    private String promptFile;              // phase1_content_review.md
    private String model;                   // agnes / dashscope（默认 skill-default）
    private boolean streaming;              // 是否流式输出
    private String outputParser;            // json / markdown / raw / pptx
    private String outputKey;               // 写入 OverAllState 的键名
    private List<VariableRef> variables;    // 本阶段变量映射
    private boolean requireConfirmation;    // 是否需要用户确认
}

public class VariableDef {
    private String name;                    // articleContent
    private String description;             // 文章内容
    private boolean required;               // 是否必须
    private String source;                  // INPUT / PHASE_OUTPUT
    private String phaseRef;                // 来源阶段（source=PHASE_OUTPUT时）
}
```

### 3.3 SkillExecution — 执行上下文

```java
public class SkillExecution {
    private final String executionId;           // UUID
    private final SkillDefinition definition;
    private final Map<String, Object> inputs;
    private final CompiledGraph graph;
    private final ModelRouter modelRouter;
    private final SkillContext context;         // 运行时上下文

    // 异步执行
    @Async("skillExecutor")
    public void executeAsync(Consumer<String> streamHandler) {
        context.setStreamHandler(streamHandler);
        try {
            // 构建 StateGraph 输入
            OverAllState state = new OverAllState();
            inputs.forEach(state::setValue);
            state.setValue("skillExecutionId", executionId);
            state.setValue("modelRouter", modelRouter);

            // 执行 StateGraph
            graph.invoke(state);

            // 提取结果
            Object result = state.value("output").orElse(null);
            context.setResult(result);
            // 持久化
            persistSuccess(result);
        } catch (Exception e) {
            persistFailure(e);
        } finally {
            context.clear();
        }
    }
}
```

### 3.4 SkillContext — 运行时上下文（替代 ThreadLocal）

```java
@Component
public class SkillContext {

    private static final ConcurrentHashMap<String, RuntimeContext> REGISTRY = new ConcurrentHashMap<>();

    @Data
    public static class RuntimeContext {
        private String executionId;
        private String taskId;                   // 关联文章 taskId（可选）
        private SseEmitter emitter;              // 直接持有 emitter
        private Consumer<String> streamHandler;  // 流式回调
        private Map<String, Object> sharedData;  // 阶段间共享
        private volatile boolean cancelled;
    }

    public static RuntimeContext create(String executionId, SseEmitter emitter) {
        RuntimeContext ctx = new RuntimeContext();
        ctx.setExecutionId(executionId);
        ctx.setEmitter(emitter);
        ctx.setStreamHandler(msg -> {
            try {
                emitter.send(SseEmitter.event().data(msg).reconnectTime(3000L));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        });
        ctx.setSharedData(new ConcurrentHashMap<>());
        REGISTRY.put(executionId, ctx);
        return ctx;
    }

    public static RuntimeContext get(String executionId) {
        return REGISTRY.get(executionId);
    }

    public static void remove(String executionId) {
        REGISTRY.remove(executionId);
    }
}
```

### 3.5 ModelRouter — 模型路由策略

```java
@Component
public class ModelRouter {

    private final ChatModel agnesChatModel;
    private final ChatModel dashscopeChatModel;
    private final ModelRouterConfig config;

    public ChatModel resolve(String phaseModel, String skillDefault) {
        String modelName = phaseModel != null ? phaseModel : skillDefault;
        if (modelName == null) modelName = config.getDefaultModel();

        return switch (modelName) {
            case "agnes" -> agnesChatModel;
            case "dashscope" -> dashscopeChatModel;
            default -> throw new IllegalArgumentException("未知模型: " + modelName);
        };
    }

    // 带降级的重试
    public ChatModel resolveWithFallback(String phaseModel, String skillDefault) {
        ChatModel primary = resolve(phaseModel, skillDefault);
        if (primary != null) return primary;
        // 降级到另一个模型
        String fallback = config.getFallback();
        return resolve(fallback, fallback);
    }
}
```

### 3.6 SkillNodeAction — 通用 StateGraph 节点

```java
@Component
@Scope("prototype")
public class SkillNodeAction implements NodeAction {

    private final PhaseDefinition phase;
    private final PromptTemplateEngine templateEngine;
    private final ModelRouter modelRouter;
    private final OutputParserRegistry parserRegistry;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        // 1. 获取 skillExecutionId → 查找 SkillContext
        String executionId = state.value("skillExecutionId")
                .map(Object::toString).orElseThrow();
        var ctx = SkillContext.get(executionId);

        // 2. 解析模型
        ChatModel model = modelRouter.resolveWithFallback(
                phase.getModel(),
                state.value("skillDefaultModel").map(Object::toString).orElse(null));

        // 3. 加载 Prompt 模板并注入变量
        String prompt = templateEngine.render(phase.getPromptFile(), resolveInputs(state, phase));

        // 4. 调用 LLM
        String output;
        if (phase.isStreaming()) {
            // 流式调用
            StringBuilder sb = new StringBuilder();
            model.stream(new Prompt(new UserMessage(prompt)))
                    .doOnNext(response -> {
                        String chunk = response.getResult().getOutput().getText();
                        sb.append(chunk);
                        // 通过 SkillContext 推送
                        ctx.getStreamHandler().accept("STREAMING:" + chunk);
                    })
                    .blockLast();
            output = sb.toString();
        } else {
            ChatResponse response = model.call(new Prompt(new UserMessage(prompt)));
            output = response.getResult().getOutput().getText();
        }

        // 5. 解析输出
        Object parsed = parserRegistry.parse(phase.getOutputParser(), output);

        // 6. 写入 State
        return Map.of(phase.getOutputKey(), parsed);
    }
}
```

### 3.7 PromptTemplateEngine — 模板渲染引擎

```java
@Component
public class PromptTemplateEngine {

    private final ResourceLoader resourceLoader;

    // 缓存加载的模板文件
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    public String render(String promptFilePath, Map<String, Object> variables) {
        // 1. 加载模板文件
        String template = templateCache.computeIfAbsent(promptFilePath, path -> {
            try {
                Resource resource = resourceLoader.getResource("classpath:" + path);
                return IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("加载 Prompt 模板失败: " + path, e);
            }
        });

        // 2. 变量注入 {variableName}
        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}",
                    entry.getValue() != null ? entry.getValue().toString() : "");
        }

        return result;
    }
}
```

### 3.8 OutputParser 策略体系

```java
public interface SkillOutputParser<T> {
    T parse(String llmOutput, PhaseDefinition phase);
    String getType();  // json / markdown / raw / pptx
}

@Component
public class JsonOutputParser implements SkillOutputParser<Map<String, Object>> {
    @Override
    public String getType() { return "json"; }

    @Override
    public Map<String, Object> parse(String llmOutput, PhaseDefinition phase) {
        String fixed = tryFixJson(llmOutput);
        return GsonUtils.fromJson(fixed, new TypeToken<Map<String, Object>>() {});
    }

    // 复用现有 tryFixJson() 算法
    private String tryFixJson(String json) { /* 现有算法 */ }
}

@Component
public class MarkdownOutputParser implements SkillOutputParser<String> {
    @Override
    public String getType() { return "markdown"; }

    @Override
    public String parse(String llmOutput, PhaseDefinition phase) {
        // 直接返回，不做解析
        return llmOutput;
    }
}

@Order
@Component
public class OutputParserRegistry {
    private final Map<String, SkillOutputParser<?>> parserMap = new HashMap<>();

    @PostConstruct
    public void init(List<SkillOutputParser<?>> parsers) {
        parsers.forEach(p -> parserMap.put(p.getType(), p));
    }

    public <T> T parse(String type, String llmOutput, PhaseDefinition phase) {
        SkillOutputParser<?> parser = parserMap.get(type);
        if (parser == null) throw new IllegalArgumentException("未知解析器: " + type);
        return (T) parser.parse(llmOutput, phase);
    }
}
```

---

## 4. AGNES 深度集成方案

### 4.1 Maven 依赖

```xml
<!-- AGNES (OpenAI 兼容) -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    <version>1.0.0-M6</version>
</dependency>

<!-- 保留 DashScope -->
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter-dashscope</artifactId>
</dependency>
```

### 4.2 配置

```yaml
spring:
  ai:
    # AGNES 模型 (默认)
    openai:
      api-key: ${AGNES_AI_API_KEY}
      base-url: https://apihub.agnes-ai.com
      chat:
        options:
          model: agnes-2.0-flash
          temperature: 0.7
    # DashScope 模型 (辅助)
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}

model:
  router:
    default: agnes                    # 全局默认
    skill-default: agnes              # Skill 引擎默认模型
    article-default: dashscope         # 文章写作默认模型
    fallback: dashscope               # AGNES 不可用时降级
    image-model: agnes-image-2.1-flash  # 图片生成模型
```

### 4.3 AGNES ChatModel 配置类

```java
@Configuration
public class AgnesModelConfig {

    @Bean
    public ChatModel agnesChatModel(OpenAiConnectionProperties connectionProperties) {
        // 通过 spring.ai.openai 配置自动注入
        // Spring AI 的 OpenAiChatModel 会自动读取 application.yml 配置
        return new OpenAiChatModel(OpenAiApi.builder()
                .apiKey(connectionProperties.getApiKey())
                .baseUrl(connectionProperties.getBaseUrl())
                .build());
    }

    @Bean
    public ModelRouter modelRouter(ChatModel agnesChatModel,
                                    DashScopeChatModel dashscopeChatModel,
                                    ModelRouterConfig config) {
        return new ModelRouter(agnesChatModel, dashscopeChatModel, config);
    }
}
```

### 4.4 图像生成集成

```java
@Component
public class AgnesImageService implements ImageSearchService {

    private final String apiKey;
    private final String modelName;
    private final ChatModel agnesChatModel;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public ImageMethodEnum getMethod() { return ImageMethodEnum.AGNES; }

    @Override
    public ImageData getImageData(ImageRequest request) {
        // 1. 翻译 Prompt 为英文
        String englishPrompt = translatePrompt(request.getPrompt());

        // 2. 构建请求体
        Map<String, Object> body = new HashMap<>();
        body.put("model", "agnes-image-2.1-flash");
        body.put("prompt", englishPrompt);
        body.put("size", "1024x768");
        body.put("extra_body", Map.of("response_format", "url"));

        // 3. 调用 API
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "https://apihub.agnes-ai.com/v1/images/generations",
                new HttpEntity<>(body, headers),
                Map.class);

        // 4. 提取 URL
        String imageUrl = extractUrl(response.getBody());
        return ImageData.fromUrl(imageUrl);
    }

    private String translatePrompt(String text) {
        // 仅非英文需要翻译
        if (text == null || text.chars().noneMatch(c -> c > 127)) return text;
        return agnesChatModel.call(new Prompt(
                new SystemMessage("Translate to English. Preserve all visual details, style, lighting, composition. Return only English."),
                new UserMessage(text)
        )).getResult().getOutput().getText();
    }
}
```

### 4.5 模型使用场景总表

| 场景 | 默认模型 | 可切换 | 备注 |
|------|---------|--------|------|
| Skill 引擎 (所有 skill) | AGNES agnes-2.0-flash | DashScope | 通过 skill.yaml 的 model 字段 |
| 文章写作 (Title/Outline/Content Agent) | DashScope qwen-max | AGNES | 兼容现有逻辑 |
| 图片生成 (NANO_BANANA) | AGNES agnes-image-2.1-flash | DashScope | 新增 AgnesImageService |
| SVG 生成 | DashScope (原) | — | 保持不变 |
| Prompt 翻译 (非英→英) | AGNES agnes-2.0-flash | — | 温度 0，专用 |
| 备用降级 | DashScope | — | AGNES 不可用时自动切换 |

---

## 5. Skill 定义规范

### 5.1 目录结构

```
resources/skills/
├── proofreading/
│   ├── skill.yaml
│   └── prompts/
│       ├── phase1_content_review.md
│       ├── phase2_ai_tone_fix.md
│       └── phase3_rhythm_polish.md
├── topic-gen/
│   ├── skill.yaml
│   └── prompts/
│       └── phase1_generate_topics.md
├── slides/
│   ├── skill.yaml
│   └── prompts/
│       ├── step1_content_struct.md
│       ├── step2_design_style.md
│       ├── step3_create_illustrations.md
│       ├── step4_assemble_slides.md
│       └── step5_polish.md
├── data-pro/
│   ├── skill.yaml
│   └── prompts/
│       ├── phase1_data_analysis.md
│       ├── phase2_insight_extraction.md
│       └── phase3_report_writing.md
├── article-to-x/
│   ├── skill.yaml
│   └── prompts/
│       └── phase1_condense.md
├── research/
│   ├── skill.yaml
│   └── prompts/
│       └── phase1_structured_search.md
└── wechat-image/
    ├── skill.yaml
    └── prompts/
        ├── phase1_style_select.md
        └── phase2_generate_image.md
```

### 5.2 skill.yaml 模板

```yaml
name: proofreading
description: 三遍审校降低AI检测率，让文章更有人味
category: writing
requiredRoles: [user]
isMultiRound: false

# 全局变量声明
variables:
  articleContent:
    description: 待审校的文章内容
    required: true
    source: INPUT
  style:
    description: 文章风格
    required: false
    source: INPUT

# 阶段定义
phases:
  - name: content_review
    promptFile: skills/proofreading/prompts/phase1_content_review.md
    model: agnes
    streaming: false
    outputParser: json
    outputKey: reviewResult
    variables:
      - name: articleContent
        source: INPUT
      - name: style
        source: INPUT
    requireConfirmation: false

  - name: ai_tone_fix
    promptFile: skills/proofreading/prompts/phase2_ai_tone_fix.md
    model: agnes
    streaming: true
    outputParser: markdown
    outputKey: polishedContent
    variables:
      - name: articleContent
        ref: content_review      # 引用上一阶段输出
      - name: reviewResult
        ref: content_review
    requireConfirmation: true

  - name: rhythm_polish
    promptFile: skills/proofreading/prompts/phase3_rhythm_polish.md
    model: agnes
    streaming: true
    outputParser: markdown
    outputKey: finalContent
    variables:
      - name: polishedContent
        ref: ai_tone_fix
    requireConfirmation: false
```

### 5.3 Prompt 文件规范

- 文件格式：Markdown `.md`
- 变量注入：`{variableName}`
- 分片原则：每个 phase 独立文件，不跨阶段引用
- 文件头可选元数据 YAML frontmatter：

```markdown
---
phase: content_review
model: agnes
outputType: json
---

# 第一遍审校：内容审校

## 目标
确保内容准确、逻辑清晰、结构合理。

## 检查项
- {articleContent}

## 输出格式
```json
{
  "isAccurate": boolean,
  "logicIssues": [],
  "structureIssues": [],
  "overallScore": number
}
```
```

---

## 6. 数据持久化

### 6.1 skill_execution 表

```sql
CREATE TABLE skill_execution (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    skill_execution_id  VARCHAR(64) NOT NULL UNIQUE COMMENT '唯一执行 ID',
    skill_name          VARCHAR(64) NOT NULL COMMENT 'Skill 名称',
    task_id             VARCHAR(64) COMMENT '关联文章 taskId（可选）',
    user_id             BIGINT NOT NULL COMMENT '执行用户',
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
    phase               VARCHAR(64) COMMENT '当前阶段',
    input_data          JSON COMMENT '输入数据',
    output_data         JSON COMMENT '输出数据（异构）',
    result_url          VARCHAR(512) COMMENT '结果文件 URL（PPTX/HTML 等）',
    token_usage         INT DEFAULT 0 COMMENT 'Token 消耗',
    model_used          VARCHAR(64) COMMENT '使用的模型',
    duration_ms         INT DEFAULT 0 COMMENT '总耗时（毫秒）',
    error_message       TEXT COMMENT '错误信息',
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_skill_name (skill_name),
    INDEX idx_user_id (user_id),
    INDEX idx_task_id (task_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill 执行记录';
```

### 6.2 agent_log 表扩展

```sql
ALTER TABLE agent_log
    ADD COLUMN skill_execution_id VARCHAR(64) COMMENT '关联 Skill 执行 ID' AFTER task_id,
    ADD COLUMN model_used VARCHAR(64) COMMENT '使用的模型' AFTER prompt,
    ADD COLUMN token_usage INT DEFAULT 0 COMMENT 'Token 消耗' AFTER duration_ms,
    ADD INDEX idx_skill_execution_id (skill_execution_id);
```

### 6.3 ImageMethodEnum 扩展

```java
// 新增 AGNES 图片来源
public enum ImageMethodEnum {
    PEXELS("pexels", "Pexels 图库", false, false),
    NANO_BANANA("nano-banana", "DashScope AI 生图", true, false),
    AGNES("agnes", "AGNES AI 生图", true, false),  // 新增
    MERMAID("mermaid", "Mermaid 流程图", true, false),
    ICONIFY("iconify", "Iconify 图标库", false, false),
    EMOJI_PACK("emoji-pack", "Bing 表情包", false, false),
    SVG_DIAGRAM("svg-diagram", "SVG 概念图", true, false),
    PICSUM("picsum", "Picsum 降级方案", false, true);
}
```

---

## 7. API 设计

### 7.1 新增 API

| 方法 | 路径 | 功能 | 说明 |
|------|------|------|------|
| POST | `/skill/{skillName}/execute` | 执行 Skill | 异步，返回 executionId |
| GET | `/skill/{executionId}/progress` | SSE 进度推送 | 与文章进度共用机制 |
| POST | `/skill/{executionId}/confirm` | 确认/交互 | 多轮对话用 |
| GET | `/skill/{executionId}/result` | 获取结果 | 同步查询 |
| GET | `/skill/list` | 列出可用 Skill | 动态发现 |
| GET | `/skill/{skillName}/definition` | 获取 Skill 定义 | 供前端动态渲染 |

### 7.2 API 请求/响应

```json
// POST /skill/proofreading/execute
{
    "inputs": {
        "articleContent": "文章内容...",
        "style": "tech"
    }
}
// 响应
{
    "code": 0,
    "data": {
        "skillExecutionId": "sk-exec-xxx",
        "skillName": "proofreading",
        "status": "RUNNING",
        "totalPhases": 3,
        "progressUrl": "/skill/sk-exec-xxx/progress"
    }
}

// POST /skill/sk-exec-xxx/confirm (多轮交互)
{
    "phase": "ai_tone_fix",
    "action": "approve"  // approve / retry / modify
}
```

### 7.3 现有 API 变更

| 现有 API | 变更 |
|---------|------|
| `POST /article/create` | 保持不变 |
| `GET /article/progress/{taskId}` | 保持不变，新增兼容 skill 进度 |
| `POST /article/confirm-title` | 保持不变 |
| `POST /article/confirm-outline` | 保持不变 |

---

## 8. SSE 消息协议

### 8.1 泛化消息结构

```json
{
    "type": "skill.progress",
    "skillExecutionId": "sk-exec-xxx",
    "skillName": "proofreading",
    "phase": "content_review",
    "phaseIndex": 1,
    "totalPhases": 3,
    "status": "RUNNING",
    "timestamp": "2026-07-22T10:30:00Z",
    "data": {}
}
```

### 8.2 消息类型

| type | 触发时机 | data 内容 |
|------|---------|----------|
| `skill.started` | Skill 开始执行 | `{inputs}` |
| `skill.progress` | 阶段执行中 | 取决于阶段，流式内容 |
| `skill.phase_complete` | 阶段完成 | `{outputKey: value}` |
| `skill.awaiting_confirmation` | 等待用户确认 | `{phase, options}` |
| `skill.complete` | 全部完成 | `{result}` |
| `skill.error` | 发生错误 | `{errorMessage}` |

### 8.3 兼容现有消息

现有 `SseMessageTypeEnum` 的 12 个消息类型保持不动，新 skill 消息作为扩展。前端通过 `type` 字段前缀区分：

- `AGENT*` / `TITLES_*` / `MERGE_*` → 文章写作流程
- `skill.*` → Skill 引擎流程

---

## 9. 目录结构

### 9.1 新增目录

```
src/main/java/com/example/aipassagecreator/
├── skill/                              # Skill 引擎核心
│   ├── SkillRegistry.java              # 注册中心
│   ├── SkillDefinition.java            # 定义模型
│   ├── SkillExecution.java             # 执行器
│   ├── SkillContext.java               # 运行时上下文
│   ├── SkillExecutionChain.java        # 链式组合
│   ├── SkillNodeAction.java            # 通用 StateGraph 节点
│   ├── SkillController.java            # API 控制器
│   ├── SkillOutputParser.java          # 输出解析器接口
│   ├── OutputParserRegistry.java       # 解析器注册中心
│   ├── parsers/
│   │   ├── JsonOutputParser.java
│   │   └── MarkdownOutputParser.java
│   ├── ModelRouter.java               # 模型路由
│   ├── ModelRouterConfig.java          # 路由配置
│   ├── PromptTemplateEngine.java       # 模板引擎
│   └── config/
│       └── AgnesModelConfig.java       # AGNES 模型配置
├── mapper/
│   └── SkillExecutionMapper.java       # 新增 Mapper
└── model/
    ├── dto/skill/
    │   ├── SkillExecuteRequest.java
    │   ├── SkillConfirmRequest.java
    │   └── SkillExecuteResponse.java
    └── po/
        └── SkillExecution.java         # 实体类

src/main/resources/
├── skills/                             # Skill 定义文件
│   ├── proofreading/
│   │   ├── skill.yaml
│   │   └── prompts/
│   │       ├── phase1_content_review.md
│   │       ├── phase2_ai_tone_fix.md
│   │       └── phase3_rhythm_polish.md
│   └── topic-gen/
│       ├── skill.yaml
│       └── prompts/
│           └── phase1_generate_topics.md
└── mapper/
    └── SkillExecutionMapper.xml
```

### 9.2 现有文件变更

| 文件 | 变更 |
|------|------|
| `pom.xml` | 新增 `spring-ai-openai-spring-boot-starter` |
| `application.yml` | 新增 AGNES 配置 + model.router 配置 |
| `TitleGeneratorAgent.java` | `DashScopeChatModel` → `ChatModel` |
| `OutlineGeneratorAgent.java` | `DashScopeChatModel` → `ChatModel` |
| `ContentGeneratorAgent.java` | `DashScopeChatModel` → `ChatModel` |
| `ImageAnalyzerAgent.java` | `DashScopeChatModel` → `ChatModel` |
| `ArticleAgentService.java` | `DashScopeChatModel` → `ChatModel` |
| `SvgDiagramService.java` | 保持不变（仍用 DashScope） |
| `ImageMethodEnum.java` | 新增 `AGNES` 枚举值 |
| `SseMessageTypeEnum.java` | 保持不变，新增泛化消息 |
| `AsyncConfig.java` | 新增 `skillExecutor` Bean |
| `StreamHandlerContext.java` | 保留，但新 Skill 使用 SkillContext |

---

## 10. 分阶段实施计划

### Phase 1：基础设施搭建（第 1 周）

| 任务 | 产出 | 工作量 |
|------|------|--------|
| 1.1 pom.xml 依赖管理 | 新增 AGNES + OpenAI Starter 依赖 | 0.5d |
| 1.2 application.yml 配置 | AGNES 配置 + model.router 配置 | 0.5d |
| 1.3 AgnesModelConfig | AGNES ChatModel + ModelRouter Bean | 1d |
| 1.4 ChatModel 抽象化改造 | 7 个文件 `DashScopeChatModel` → `ChatModel` | 1d |
| 1.5 ModelRouter 实现 | 路由策略 + 降级逻辑 | 1d |
| 1.6 AsyncConfig 扩展 | 新增 `skillExecutor` 线程池 | 0.5d |
| 1.7 skill_execution 表 | SQL + 实体 + Mapper | 1d |
| 1.8 agent_log 表扩展 | ALTER TABLE + 适配 | 0.5d |

**里程碑**：AGNES 模型可调用，所有现有 Agent 兼容双模型

### Phase 2：Skill 引擎核心（第 2 周）

| 任务 | 产出 | 工作量 |
|------|------|--------|
| 2.1 SkillRegistry + 扫描机制 | 扫描 classpath:skills/*/skill.yaml | 1.5d |
| 2.2 SkillDefinition 解析 | YAML → Java 对象映射 | 1d |
| 2.3 SkillContext (替代 ThreadLocal) | ConcurrentHashMap 注册表 | 1d |
| 2.4 SkillNodeAction 通用节点 | 通用 StateGraph 节点实现 | 1.5d |
| 2.5 PromptTemplateEngine | 模板加载 + 变量注入 + 缓存 | 1d |
| 2.6 OutputParser 策略体系 | 接口 + JSON/Markdown 实现 | 1d |
| 2.7 SkillController | 4 个 API 端点 | 1d |
| 2.8 SSE 泛化消息协议 | 适配前端消息格式 | 0.5d |

**里程碑**：Skill 引擎可运行，能执行 YAML 定义的简单 skill

### Phase 3：核心 Skill 落地（第 3 周）

| 任务 | 产出 | 工作量 |
|------|------|--------|
| 3.1 proofreading skill 定义 | skill.yaml + 3 个 prompt 文件 | 1.5d |
| 3.2 proofreading 三阶段执行 | 端到端流程验证 | 1d |
| 3.3 topic-gen skill 定义 | skill.yaml + 1 个 prompt 文件 | 1d |
| 3.4 topic-gen 多轮交互 | 用户确认机制 | 1d |
| 3.5 AgnesImageService | 图片生成集成 + 翻译 | 1.5d |
| 3.6 ImageMethodEnum.AGNES 注册 | 策略模式注册 | 0.5d |

**里程碑**：2 个完整 skill 可用，AGNES 图片生成可用

### Phase 4：扩展与优化（第 4 周）

| 任务 | 产出 | 工作量 |
|------|------|--------|
| 4.1 article-to-x skill | 长文转社交媒体 | 1d |
| 4.2 research skill | 结构化调研 | 1d |
| 4.3 SkillExecutionChain | skill 链式组合 | 1d |
| 4.4 权限 + 配额集成 | requiredRoles 校验 | 1d |
| 4.5 集成测试 | 主要 skill 流程测试 | 1d |
| 4.6 文档 + 示例 | README 更新 + API 文档 | 0.5d |

**里程碑**：5 个 skill 可用，具备 skill 组合能力，可发布

---

## 附录：18 审计问题 → 解决方案映射

| 问题 | 等级 | 解决方案 | 对应章节 |
|------|------|---------|---------|
| ① Pipeline/StateGraph 边界模糊 | 🔴 | 统一 StateGraph，无独立 Pipeline | 3.1, 3.6 |
| ② 格式未收敛 (YAML vs Java) | 🟡 | YAML 定义 + MD Prompt 文件 | 5.1, 5.2 |
| ③ Prompt 管理缺失 | 🔴 | 资源文件化 + 分片 + 变量注入 | 5.3, 3.7 |
| ④ SSE 消息类型爆炸 | 🟡 | 动态泛化消息结构 | 8.1, 8.2 |
| ⑤ 输出解析器缺失 | 🟡 | 策略化 OutputParser | 3.8 |
| ⑥ 状态机冲突 | 🟡 | SkillExecution 独立状态 | 6.1, 3.3 |
| ⑦ ThreadLocal 跨阶段断裂 | 🔴 | SkillContext 注册表替代 | 3.4 |
| ⑧ 线程池资源竞争 | 🟡 | 独立 skillExecutor 线程池 | 1.6 |
| ⑨ SSE 标识管理不清 | 🟡 | skillExecutionId 独立 UUID | 3.3, 3.4 |
| ⑩ 数据库持久化缺失 | 🟡 | skill_execution 表 | 6.1 |
| ⑪ 前端对接成本 | 🟢 | 后续阶段处理 | — |
| ⑫ 权限与配额扩展 | 🟢 | skill.yaml 声明 requiredRoles | 5.2 |
| ⑬ 生命周期管理缺失 | 🟡 | SkillRegistry 自动扫描 | 3.1 |
| ⑭ Prompt 规范缺失 | 🔴 | 变量声明 + 分片 + 文件规范 | 5.3 |
| ⑮ 多轮对话管理缺失 | 🔴 | StateGraph 条件边 + MemorySaver | 3.6 |
| ⑯ 测试策略空白 | 🟡 | 后续阶段处理 | — |
| ⑰ 监控可观测性 | 🟢 | agent_log 扩展 + skill_execution | 6.2 |
| ⑱ Skill 间组合与冲突 | 🟡 | SkillExecutionChain | 3.2 |

---

> **维护者**: YangJs
> **项目**: AI Passage Creator（灵犀写作）— huashu-skills 二次开发
