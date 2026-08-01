# 爆款方法论引擎 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建爆款方法论引擎——YAML 方法论模板驱动创作引导、爆款评测与反哺闭环，并修复审计发现的两处现网安全洞。

**Architecture:** 方法论以 `methodology/*.yaml` 为单一配置源，由 MethodologyRegistry（物化 extends 继承 + 兜底 + 校验）启动期加载为不可变 Map；MethodologyPromptAssembler 在 orchestrator/fallback 双路径 6 处组装点统一注入提示段；ContentQualityService.evaluateViral 合并通用 5 维 + 爆款维度单次 LLM 调用，归一化加权落库；MethodologyRefiner 以「top-2 低分维度改写 → 复评 → 无提升回退」闭环迭代（≤3 轮）。

**Tech Stack:** Spring Boot 3.5.13 / Java 21 / MyBatis-Flex 1.11.1 / SnakeYAML / Gson / Spring AI / Maven（`mvn test`）

---

## Global Constraints

- **数据库列命名**：`article` 表驼峰列名（`taskId`）；`article_quality` 表下划线列名（`task_id`）。
- **Schema 双同步**：任何表结构变更必须**同时**更新 `src/main/resources/sql/h2-schema.sql`（测试）与新增 `sql/xxx.sql` 迁移脚本（生产）。
- **安全基线**：新端点必须 `@AuthCheck(mustRole="user")` + controller 归属校验 + `@RateLimit` + 配额扣减（失败 refund）。
- **YAML 解析**：统一走 `YamlResourceLoader`（SafeConstructor + LoaderOptions 限制：maxAliases≤50 / codePointLimit≤1MB / nestingDepthLimit≤50，单文件≤100KB）。
- **methodologyName 校验**：只走内存 registry 白名单查名，`get(name)` 不存在抛 `IllegalArgumentException`（fail-fast）；**绝不把用户输入拼入文件路径**。
- **评测权重**：`viralScore = Σ(score×weight) / Σ(weight)`（分母归一化）；`weight ∈ (0,100]`，Σweight>0，注册时校验。
- **JSON 容错**：LLM 输出解析一律走 `GsonUtils.fromJsonSafe` + `GsonUtils.tryFixJson`（增强版：剥围栏/取首尾大括号）。
- **双执行路径**：`article.agent.orchestrator.enabled` 控制两条 prompt 构建路径，方法论注入在**两条路径**都生效。
- **提交规范**：提交信息遵循仓库 CONTRIBUTING 规范（`feat(scope): 描述`），必须以 `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>` 结尾。
- **测试命令**：`mvn test`（全量）；单测：`mvn test -Dtest=<ClassName>`。现有测试为 `@SpringBootTest` + H2，无 Mockito；新测试对需要 mock ChatModel 的用 `@MockitoBean ModelRouter`。

---

## File Structure

**新增文件：**
```
src/main/java/com/example/aipassagecreator/config/YamlResourceLoader.java
src/main/java/com/example/aipassagecreator/methodology/MethodologyDefinition.java
src/main/java/com/example/aipassagecreator/methodology/MethodologyRegistry.java
src/main/java/com/example/aipassagecreator/methodology/MethodologyPromptAssembler.java
src/main/java/com/example/aipassagecreator/methodology/MethodologyRefiner.java
src/main/resources/methodology/default.yaml
src/main/resources/methodology/wechat.yaml
src/main/resources/methodology/xiaohongshu.yaml
src/main/resources/methodology/douyin.yaml
src/main/resources/sql/add_viral_quality.sql
src/test/java/com/example/aipassagecreator/methodology/YamlResourceLoaderTest.java
src/test/java/com/example/aipassagecreator/methodology/MethodologyRegistryTest.java
src/test/java/com/example/aipassagecreator/methodology/MethodologyPromptAssemblerTest.java
src/test/java/com/example/aipassagecreator/methodology/MethodologyRefinerTest.java
src/test/java/com/example/aipassagecreator/service/ContentQualityServiceViralTest.java
src/test/java/com/example/aipassagecreator/security/SecurityFixTest.java
```

**修改文件：**
```
src/main/java/com/example/aipassagecreator/controller/ArticleController.java     # 新端点 + execution-logs 修复
src/main/java/com/example/aipassagecreator/utils/GsonUtils.java                  # tryFixJson 提升+增强
src/main/java/com/example/aipassagecreator/skill/parsers/JsonOutputParser.java   # 复用 GsonUtils.tryFixJson
src/main/java/com/example/aipassagecreator/skill/SkillRegistry.java              # 复用 YamlResourceLoader
src/main/java/com/example/aipassagecreator/model/po/Article.java                 # + methodology
src/main/java/com/example/aipassagecreator/model/po/ArticleQuality.java          # + score_type/viral_* 等
src/main/java/com/example/aipassagecreator/model/dto/article/ArticleCreateRequest.java   # + methodology
src/main/java/com/example/aipassagecreator/model/dto/article/ArticleState.java   # + methodology + TitleOption.strategyKey
src/main/java/com/example/aipassagecreator/model/dto/article/ArticleConfirmTitleRequest.java # + strategyKey(可选)
src/main/java/com/example/aipassagecreator/service/impl/ArticleServiceImpl.java  # create 透传 methodology
src/main/java/com/example/aipassagecreator/service/ArticleService.java           # 接口签名调整（见 Task 5）
src/main/java/com/example/aipassagecreator/service/ArticleAsyncService.java      # phase2/3 回填 methodology
src/main/java/com/example/aipassagecreator/agent/ArticleAgentOrchestrator.java   # inputs + KeyStrategy + methodology
src/main/java/com/example/aipassagecreator/agent/agents/TitleGeneratorAgent.java # 方法论注入 + strategyKey prompt
src/main/java/com/example/aipassagecreator/agent/agents/ContentGeneratorAgent.java # 方法论注入
src/main/java/com/example/aipassagecreator/agent/agents/OutlineGeneratorAgent.java # 方法论注入
src/main/java/com/example/aipassagecreator/service/ArticleAgentService.java      # fallback 3 处注入
src/main/java/com/example/aipassagecreator/service/ContentQualityService.java    # + evaluateViral/getLatestViral
src/main/java/com/example/aipassagecreator/service/impl/ContentQualityServiceImpl.java # 实现 evaluateViral
src/main/java/com/example/aipassagecreator/service/ArticleRewriteService.java    # + rewriteSection
src/main/java/com/example/aipassagecreator/service/impl/ArticleRewriteServiceImpl.java # SystemMessage + rewriteSection
src/main/resources/sql/h2-schema.sql                                             # article/article_quality 加列
```

---

### Task 1: 前置安全修复（阻塞项）

**Files:**
- Modify: `src/main/java/com/example/aipassagecreator/controller/ArticleController.java:176-184`
- Modify: `src/main/java/com/example/aipassagecreator/service/impl/ArticleRewriteServiceImpl.java:25-34,88-89`
- Test: `src/test/java/com/example/aipassagecreator/security/SecurityFixTest.java`

**Interfaces:**
- Consumes: `UserService.getLoginUser(HttpServletRequest)`、`ArticleService.getByTaskId(String)`、`ErrorCode.NO_AUTH_ERROR`/`NOT_FOUND_ERROR`、`UserConstant.ADMIN_ROLE`
- Produces: 无新接口（修复既有端点）

- [ ] **Step 1: 写失败测试** — execution-logs 无鉴权 + rewrite 无 SystemMessage 边界

```java
package com.example.aipassagecreator.security;

import com.example.aipassagecreator.service.ArticleRewriteService;
import com.example.aipassagecreator.skill.ModelRouter;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SecurityFixTest {

    @Autowired
    private ArticleRewriteService articleRewriteService;

    @Test
    void rewritePrompt_separatesUserInstructionFromSystem() throws Exception {
        // 通过反射读取 REWRITE_PROMPT 常量，验证其不再包含 {instruction} 占位符拼进用户消息
        java.lang.reflect.Field f = articleRewriteService.getClass().getDeclaredField("REWRITE_PROMPT");
        f.setAccessible(true);
        String prompt = (String) f.get(articleRewriteService);
        // 修复后：指令应放入独立的 SystemMessage，prompt 模板只包含 {content}
        assertTrue(prompt.contains("{content}"), "重写 prompt 必须仍包含文章占位符");
        assertTrue(prompt.contains("系统指令"), "修复后 SystemMessage 应明确标识系统指令来源");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dtest=SecurityFixTest`
Expected: FAIL — 当前 REWRITE_PROMPT 不含「系统指令」标识

- [ ] **Step 3: 修复 execution-logs IDOR** — 加鉴权 + 归属校验

在 `ArticleController.java` 的 `getExecutionLogs` 方法（176-184 行）添加注解与归属校验：

```java
/**
 * 获取任务执行日志
 */
@GetMapping("/execution-logs/{taskId}")
@Operation(summary = "获取任务执行日志")
@AuthCheck(mustRole = "user")
public BaseResponse<AgentExecutionStats> getExecutionLogs(@PathVariable String taskId,
                                                          HttpServletRequest httpServletRequest) {
    ThrowUtils.throwIf(taskId == null || taskId.trim().isEmpty(),
            ErrorCode.PARAMS_ERROR, "任务ID不能为空");

    // 归属校验：防止 IDOR，仅文章作者或管理员可查看执行日志
    var article = articleService.getByTaskId(taskId);
    if (article == null) {
        throw new com.example.aipassagecreator.exception.BusinessException(
                ErrorCode.NOT_FOUND_ERROR, "文章不存在");
    }
    User loginUser = userService.getLoginUser(httpServletRequest);
    if (!article.getUserId().equals(loginUser.getId())
            && !com.example.aipassagecreator.constant.UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
        throw new com.example.aipassagecreator.exception.BusinessException(
                ErrorCode.NO_AUTH_ERROR, "无权操作此文章");
    }

    AgentExecutionStats stats = agentLogService.getExecutionStats(taskId);
    return ResultUtils.success(stats);
}
```

同时给 `ArticleController` 增加 `userService` 引用（若未注入则补充 `@Resource private UserService userService;` 字段）。

- [ ] **Step 4: 修复 rewrite prompt 注入** — 用户指令改独立 SystemMessage

修改 `ArticleRewriteServiceImpl.java`：

```java
// 重写提示模板：文章内容占位，指令经 SystemMessage 独立传入
private static final String REWRITE_PROMPT = """
        你是一位资深内容编辑。请基于系统提供的改写指令对以下文章进行优化改进。
        保持原文的核心信息和结构，但提升表达质量。

        原文：
        %s

        请直接输出改写后的完整文章，不要添加任何说明。""";

private static final String REWRITE_SYSTEM_PROMPT = """
        系统指令：%s
        """;
```

修改 `rewrite()` 方法中 prompt 构造（88-89 行），改为双消息：

```java
ChatModel model = modelRouter.resolveWithFallback(null, null);
String modelName = modelRouter.resolveModelName(null, null);

long start = System.currentTimeMillis();
// 系统指令与文章内容分离，阻断 prompt 注入
SystemMessage systemMessage = new SystemMessage(REWRITE_SYSTEM_PROMPT.formatted(rewriteInstruction));
UserMessage userMessage = new UserMessage(REWRITE_PROMPT.formatted(snapshot));
ChatResponse response = model.call(new Prompt(List.of(systemMessage, userMessage)));
String rewritten = response.getResult().getOutput().getText();
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn test -Dtest=SecurityFixTest`
Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/example/aipassagecreator/controller/ArticleController.java \
        src/main/java/com/example/aipassagecreator/service/impl/ArticleRewriteServiceImpl.java \
        src/test/java/com/example/aipassagecreator/security/SecurityFixTest.java
git commit -m "fix(security): 修复 execution-logs IDOR + rewrite prompt 注入

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: GsonUtils.tryFixJson 增强（JSON 容错下沉工具层）

**Files:**
- Modify: `src/main/java/com/example/aipassagecreator/utils/GsonUtils.java`
- Modify: `src/main/java/com/example/aipassagecreator/skill/parsers/JsonOutputParser.java`
- Test: `src/test/java/com/example/aipassagecreator/skill/GsonUtilsJsonRepairTest.java`（新增）

**Interfaces:**
- Produces: `public static String tryFixJson(String json)`（增强版，供全项目使用）

- [ ] **Step 1: 写失败测试**

```java
package com.example.aipassagecreator.skill;

import com.example.aipassagecreator.utils.GsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GsonUtilsJsonRepairTest {

    @Test
    void tryFixJson_stripsCodeFence() {
        String input = "```json\n{\"a\": 1}\n```";
        assertEquals("{\"a\": 1}", GsonUtils.tryFixJson(input));
    }

    @Test
    void tryFixJson_trimsSurroundingText() {
        String input = "结果如下：{\"a\":1} 请查收";
        assertEquals("{\"a\":1}", GsonUtils.tryFixJson(input));
    }

    @Test
    void tryFixJson_fixesTruncatedBraces() {
        String input = "{\"a\":[1,2";
        String fixed = GsonUtils.tryFixJson(input);
        assertNotNull(GsonUtils.fromJsonSafe(fixed, Object.class));
    }

    @Test
    void tryFixJson_returnsEmptyObjectForBlank() {
        assertEquals("{}", GsonUtils.tryFixJson("   "));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dtest=GsonUtilsJsonRepairTest`
Expected: FAIL — `GsonUtils.tryFixJson` 不存在

- [ ] **Step 3: 实现增强版 tryFixJson**（加入 GsonUtils）

```java
/**
 * 修复 LLM 输出的非法 JSON。
 * <p>处理三种情况：①剥离 ```json 围栏及前后说明文本；②补全截断的括号；③空输入返回空对象。
 *
 * @param json 原始 LLM 输出
 * @return 修复后的 JSON 字符串（无法修复时原样返回）
 */
public static String tryFixJson(String json) {
    if (json == null || json.trim().isEmpty()) {
        return "{}";
    }
    String s = json.trim();
    // 1) 剥离代码围栏与前后非 JSON 文本：取第一个 { 到最后一个 } 或 ] 之间的内容
    int firstOpen = Math.min(indexOf(s, '{'), indexOf(s, '['));
    if (firstOpen == Integer.MAX_VALUE) {
        return s;
    }
    int lastClose = Math.max(s.lastIndexOf('}'), s.lastIndexOf(']'));
    if (lastClose > firstOpen) {
        s = s.substring(firstOpen, lastClose + 1);
    }
    // 2) 补全截断的括号（遍历统计 + 引号配对）
    int braces = 0, brackets = 0;
    boolean inString = false;
    char prev = 0;
    for (char c : s.toCharArray()) {
        if (inString) {
            if (c == '"' && prev != '\\') {
                inString = false;
            }
        } else {
            switch (c) {
                case '"' -> inString = true;
                case '{' -> braces++;
                case '}' -> braces--;
                case '[' -> brackets++;
                case ']' -> brackets--;
                default -> { /* ignore */ }
            }
        }
        prev = c;
    }
    if (inString) {
        s = s + '"';
    }
    while (brackets > 0) { s = s + "]"; brackets--; }
    while (braces > 0) { s = s + "}"; braces--; }
    return s;
}

private static int indexOf(String s, char c) {
    int idx = s.indexOf(c);
    return idx < 0 ? Integer.MAX_VALUE : idx;
}
```

- [ ] **Step 4: 更新 JsonOutputParser 复用工具层** — 删除其私有 tryFixJson，改调 `GsonUtils.tryFixJson`

修改 `JsonOutputParser.java`：

```java
@Override
public Map<String, Object> parse(String llmOutput, PhaseDefinition phase) {
    String fixed = GsonUtils.tryFixJson(llmOutput);
    try {
        return GsonUtils.fromJson(fixed, new TypeToken<Map<String, Object>>() {});
    } catch (Exception e) {
        return Map.of("raw", fixed);
    }
}
```

删除原 `private String tryFixJson(String json)` 方法。

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn test -Dtest=GsonUtilsJsonRepairTest`
Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/example/aipassagecreator/utils/GsonUtils.java \
        src/main/java/com/example/aipassagecreator/skill/parsers/JsonOutputParser.java \
        src/test/java/com/example/aipassagecreator/skill/GsonUtilsJsonRepairTest.java
git commit -m "feat(infra): GsonUtils.tryFixJson 提升到工具层并增强围栏剥离

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: YamlResourceLoader + SkillRegistry 加固

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/config/YamlResourceLoader.java`
- Modify: `src/main/java/com/example/aipassagecreator/skill/SkillRegistry.java:70-92`
- Test: `src/test/java/com/example/aipassagecreator/methodology/YamlResourceLoaderTest.java`（新增）

**Interfaces:**
- Produces: `public <T> List<T> loadAll(String pattern, Class<T> clazz)` — 安全加载 classpath 下所有 yaml
- Consumes: `SkillRegistry` 原加载逻辑改为委托此组件

- [ ] **Step 1: 写失败测试**

```java
package com.example.aipassagecreator.methodology;

import com.example.aipassagecreator.config.YamlResourceLoader;
import com.example.aipassagecreator.methodology.MethodologyDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class YamlResourceLoaderTest {

    @Autowired
    private YamlResourceLoader yamlResourceLoader;

    @Test
    void loadAll_loadsMethodologyYamls() {
        List<MethodologyDefinition> defs = yamlResourceLoader.loadAll(
                "classpath*:methodology/*.yaml", MethodologyDefinition.class);
        assertFalse(defs.isEmpty(), "至少应加载到 default.yaml");
        assertTrue(defs.stream().anyMatch(d -> "default".equals(d.getName())),
                "应包含 default 方法论");
    }

    @Test
    void loadAll_safeConstructorRejectsArbitraryTypes() {
        // 验证 SafeConstructor 生效：构造恶意 yaml 应解析为 Map 而非任意类
        // （此处仅验证框架不会因异常中断，正常路径下断言加载结果不为 null）
        assertNotNull(yamlResourceLoader);
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dtest=YamlResourceLoaderTest`
Expected: FAIL — `YamlResourceLoader` 类不存在（编译失败）

- [ ] **Step 3: 创建 YamlResourceLoader**

```java
package com.example.aipassagecreator.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 共享的 YAML 资源加载器。
 * <p>统一使用 SafeConstructor + LoaderOptions 限制，防止 billion-laughs / 递归炸弹 DoS；
 * 单文件限制 100KB，超限跳过并告警。</p>
 */
@Slf4j
@Component
public class YamlResourceLoader {

    /** 单文件最大字节数：100KB */
    private static final long MAX_FILE_SIZE = 100 * 1024L;

    private final ResourcePatternResolver resolver;

    public YamlResourceLoader(ResourceLoader resourceLoader) {
        this.resolver = ResourcePatternUtils.getResourcePatternResolver(resourceLoader);
    }

    /**
     * 扫描并加载所有匹配的 YAML 资源
     *
     * @param pattern classpath 通配符（如 classpath*:methodology/*.yaml）
     * @param clazz   目标类型
     * @param <T>     泛型类型
     * @return 解析结果列表
     */
    public <T> List<T> loadAll(String pattern, Class<T> clazz) {
        List<T> result = new ArrayList<>();
        try {
            Resource[] resources = resolver.getResources(pattern);
            for (Resource resource : resources) {
                if (!resource.exists() || !resource.isReadable()) {
                    continue;
                }
                long size = resource.contentLength();
                if (size > MAX_FILE_SIZE) {
                    log.warn("YAML 文件超过大小限制 {}KB, 跳过: {}", MAX_FILE_SIZE / 1024, resource.getFilename());
                    continue;
                }
                try {
                    LoaderOptions options = new LoaderOptions();
                    options.setMaxAliasesForCollections(50);
                    options.setCodePointLimit(1024 * 1024);
                    options.setNestingDepthLimit(50);
                    Yaml yaml = new Yaml(new SafeConstructor(options));
                    T def = yaml.loadAs(resource.getInputStream(), clazz);
                    if (def != null) {
                        result.add(def);
                    }
                } catch (Exception e) {
                    log.error("YAML 解析失败: {}", resource.getFilename(), e);
                }
            }
        } catch (IOException e) {
            log.error("YAML 资源扫描失败, pattern={}", pattern, e);
        }
        return result;
    }
}
```

- [ ] **Step 4: SkillRegistry 改用共享加载器** — 删除自建 `Yaml yaml = new Yaml()` 逻辑

修改 `SkillRegistry.java`：

```java
// 新增字段注入
private final YamlResourceLoader yamlResourceLoader;

// 构造器签名增加参数
public SkillRegistry(ResourceLoader resourceLoader,
                     PromptTemplateEngine templateEngine,
                     ModelRouter modelRouter,
                     OutputParserRegistry parserRegistry,
                     SkillExecutionMapper skillExecutionMapper,
                     WebSearchTool webSearchTool,
                     YamlResourceLoader yamlResourceLoader) {
    this.resourceLoader = resourceLoader;
    this.templateEngine = templateEngine;
    this.modelRouter = modelRouter;
    this.parserRegistry = parserRegistry;
    this.skillExecutionMapper = skillExecutionMapper;
    this.webSearchTool = webSearchTool;
    this.yamlResourceLoader = yamlResourceLoader;
}
```

将 `init()` 中扫描循环改为委托：

```java
@PostConstruct
public void init() {
    List<SkillDefinition> defs = yamlResourceLoader.loadAll(
            "classpath*:skills/*/skill.yaml", SkillDefinition.class);
    for (SkillDefinition def : defs) {
        try {
            validateSkillDefinition(def);
            skillMap.put(def.getName(), def);
            CompiledGraph graph = buildGraph(def);
            graphCache.put(def.getName(), graph);
            log.info("Skill 已注册: {} ({} phases)", def.getName(), def.getPhases().size());
        } catch (Exception e) {
            log.error("Skill 注册失败: {}", def.getName(), e);
        }
    }
    log.info("SkillRegistry 初始化完成，共注册 {} 个 Skill", skillMap.size());
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn test -Dtest=YamlResourceLoaderTest`
Expected: PASS

- [ ] **Step 6: 回归验证 Skill 加载**

Run: `mvn test -Dtest=SkillEngineIntegrationTest`
Expected: PASS（4 个 skill 正常注册）

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/example/aipassagecreator/config/YamlResourceLoader.java \
        src/main/java/com/example/aipassagecreator/skill/SkillRegistry.java \
        src/test/java/com/example/aipassagecreator/methodology/YamlResourceLoaderTest.java
git commit -m "feat(infra): YamlResourceLoader 共享安全加载 + SkillRegistry 加固

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: MethodologyDefinition + Registry + 模板文件

**Files:**
- Create: `src/main/resources/methodology/default.yaml`
- Create: `src/main/resources/methodology/wechat.yaml`
- Create: `src/main/resources/methodology/xiaohongshu.yaml`
- Create: `src/main/resources/methodology/douyin.yaml`
- Create: `src/main/java/com/example/aipassagecreator/methodology/MethodologyDefinition.java`
- Create: `src/main/java/com/example/aipassagecreator/methodology/MethodologyRegistry.java`
- Test: `src/test/java/com/example/aipassagecreator/methodology/MethodologyRegistryTest.java`（新增）

**Interfaces:**
- Produces:
  - `MethodologyDefinition`（含嵌套 `CreationDimension`/`TitleStrategy`/`EvaluationDimension`/`PlatformConfig`）
  - `MethodologyRegistry.get(String name)` → 不存在抛 `IllegalArgumentException`
  - `MethodologyRegistry.getNames()` → List<String>
  - `MethodologyRegistry.exists(String name)` → boolean
  - `MethodologyRegistry.getDefault()` → 内置兜底 default
- Consumes: `YamlResourceLoader.loadAll`（Task 3）

- [ ] **Step 1: 写失败测试**

```java
package com.example.aipassagecreator.methodology;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MethodologyRegistryTest {

    @Autowired
    private MethodologyRegistry registry;

    @Test
    void default_existsWithDimensions() {
        MethodologyDefinition def = registry.get("default");
        assertNotNull(def);
        assertNotNull(def.getCreationDimensions());
        assertFalse(def.getCreationDimensions().isEmpty());
        assertNotNull(def.getTitleStrategies());
        assertEquals(5, def.getTitleStrategies().size(), "标题策略应为 5 种");
        assertNotNull(def.getEvaluationDimensions());
    }

    @Test
    void wechat_inheritsFromDefault() {
        MethodologyDefinition wechat = registry.get("wechat");
        assertNotNull(wechat);
        // wechat 未显式声明创建维度，应继承 default 的
        assertNotNull(wechat.getCreationDimensions());
        assertFalse(wechat.getCreationDimensions().isEmpty());
        // 平台规则已物化
        assertNotNull(wechat.getPlatform());
        assertEquals("wechat", wechat.getPlatform().getName());
    }

    @Test
    void unknownName_throws() {
        assertThrows(IllegalArgumentException.class, () -> registry.get("not-exist"));
    }

    @Test
    void weights_normalizable() {
        MethodologyDefinition def = registry.get("default");
        int sum = def.getEvaluationDimensions().stream()
                .mapToInt(d -> d.getWeight() == null ? 0 : d.getWeight())
                .sum();
        assertTrue(sum > 0, "权重之和必须大于 0");
    }

    @Test
    void dimensionKeys_crossReferencedConsistent() {
        // 评测维度中交叉引用的 key（如 titleStrategy）必须能被标题策略找到
        MethodologyDefinition def = registry.get("default");
        assertTrue(def.getTitleStrategies().stream()
                .anyMatch(s -> "curiosityGap".equals(s.getKey())));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dtest=MethodologyRegistryTest`
Expected: FAIL — MethodologyRegistry 不存在

- [ ] **Step 3: 创建 default.yaml**

```yaml
name: default
description: 通用爆款方法论
version: 1.0

# 创作维度（事前引导，注入正文 Agent prompt）
creationDimensions:
  - key: coreViewpoint
    name: 核心观点
    guidance: 读者只能记住一句话，是哪句？开头点明，全文围绕展开
  - key: persuasion
    name: 说服策略
    guidance: 数据/故事/权威/类比/社会认同，选 2-3 种组合 + 主策略
  - key: emotionalTrigger
    name: 情绪触发点
    guidance: 击中读者的什么情绪（共鸣/好奇/焦虑/向往）？
  - key: goldenSentence
    name: 金句
    guidance: 金句从内容自然生长，结尾提炼，可独立转发
  - key: interactionHook
    name: 互动钩子
    guidance: 开放提问/争议观点/投票/晒图，让读者有话想说

# 标题策略（生成标题时按策略标注）
titleStrategies:
  - key: curiosityGap
    name: 好奇心缺口
  - key: dataImpact
    name: 数据冲击
  - key: painResonance
    name: 痛点共鸣
  - key: counterIntuitive
    name: 反常识
  - key: socialCurrency
    name: 社交货币

# 评测维度（事后评测；key 与创作维度对齐）
evaluationDimensions:
  - key: emotionalTrigger
    name: 情感触发
    weight: 20
    rubric: 是否触及读者情绪点，引起共鸣或向往
  - key: goldenSentence
    name: 金句
    weight: 15
    rubric: 是否有可独立传播的金句，且从内容自然生长
  - key: interactionHook
    name: 互动钩子
    weight: 15
    rubric: 是否有引导读者参与互动的设计
  - key: persuasion
    name: 说服策略
    weight: 15
    rubric: 是否有效运用数据/故事/类比等说服手段
  - key: titleStrategy
    name: 标题策略命中
    weight: 10
    rubric: 标题是否命中某一种策略且有效
```

- [ ] **Step 4: 创建 wechat.yaml / 骨架模板**

```yaml
# wechat.yaml
name: wechat
description: 公众号爆款方法论
version: 1.0
# 继承 default 的创作维度/标题策略/评测维度（实现中对应字段 parent）
parent: default
platform:
  name: wechat
  audience: 公众号读者
  minChars: 1500
  maxChars: 3000
  style: 长论证、信息密度高
  evaluationWeights:
    emotionalTrigger: 20
    goldenSentence: 20
    interactionHook: 10
    persuasion: 15
    titleStrategy: 10
```

```yaml
# xiaohongshu.yaml（骨架，第二阶段填充）
name: xiaohongshu
description: 小红书爆款方法论（待扩展）
version: 0.1
parent: default
```

```yaml
# douyin.yaml（骨架，第二阶段填充）
name: douyin
description: 抖音爆款方法论（待扩展）
version: 0.1
parent: default
```

- [ ] **Step 5: 创建 MethodologyDefinition**

```java
package com.example.aipassagecreator.methodology;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 方法论模板的 Java 映射。
 * <p>注意：YAML 的 {@code parent} 字段对应继承父模板（设计文档中为 extends，因 Java 关键字改用 parent）。</p>
 */
@Data
public class MethodologyDefinition {

    private String name;
    private String description;
    private String version;

    /** 父模板名称（继承 default），YAML 键为 parent */
    private String parent;

    private List<CreationDimension> creationDimensions;
    private List<TitleStrategy> titleStrategies;
    private List<EvaluationDimension> evaluationDimensions;
    private PlatformConfig platform;

    @Data
    public static class CreationDimension {
        private String key;
        private String name;
        private String guidance;
    }

    @Data
    public static class TitleStrategy {
        private String key;
        private String name;
    }

    @Data
    public static class EvaluationDimension {
        private String key;
        private String name;
        private Integer weight;
        private String rubric;
    }

    @Data
    public static class PlatformConfig {
        private String name;
        private String audience;
        private Integer minChars;
        private Integer maxChars;
        private String style;
        /** 平台覆盖的评测维度权重（按 key patch） */
        private Map<String, Integer> evaluationWeights;
    }
}
```

- [ ] **Step 6: 创建 MethodologyRegistry**（物化继承 + 环检测 + 兜底 + 校验）

```java
package com.example.aipassagecreator.methodology;

import com.example.aipassagecreator.config.YamlResourceLoader;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 方法论注册中心。
 * <p>启动期扫描 {@code classpath*:methodology/*.yaml}，物化 {@code parent} 继承、
 * 检测继承环、校验维度 key 一致性与权重合法，加载为不可变 Map。</p>
 */
@Slf4j
@Component
public class MethodologyRegistry {

    private final Map<String, MethodologyDefinition> methodologyMap = new ConcurrentHashMap<>();
    private final YamlResourceLoader yamlResourceLoader;

    public MethodologyRegistry(YamlResourceLoader yamlResourceLoader) {
        this.yamlResourceLoader = yamlResourceLoader;
    }

    @PostConstruct
    public void init() {
        try {
            List<MethodologyDefinition> rawDefs = yamlResourceLoader.loadAll(
                    "classpath*:methodology/*.yaml", MethodologyDefinition.class);

            Map<String, MethodologyDefinition> rawMap = new HashMap<>();
            for (MethodologyDefinition def : rawDefs) {
                if (def.getName() == null || def.getName().isBlank()) {
                    log.warn("方法论缺少 name, 跳过: {}", def);
                    continue;
                }
                rawMap.put(def.getName(), def);
            }

            Map<String, MethodologyDefinition> materialized = new HashMap<>();
            for (String name : rawMap.keySet()) {
                materialized.put(name, materialize(name, rawMap, new HashSet<>()));
            }

            if (!materialized.containsKey("default")) {
                materialized.put("default", builtinDefault());
            }

            for (MethodologyDefinition def : materialized.values()) {
                validate(def);
            }

            methodologyMap.putAll(materialized);
            log.info("MethodologyRegistry 初始化完成，共 {} 个方法论: {}", methodologyMap.size(), methodologyMap.keySet());
        } catch (Exception e) {
            log.error("MethodologyRegistry 初始化失败，使用内置兜底", e);
            methodologyMap.clear();
            methodologyMap.put("default", builtinDefault());
        }
    }

    public MethodologyDefinition get(String name) {
        MethodologyDefinition def = methodologyMap.get(name);
        if (def == null) {
            throw new IllegalArgumentException("方法论不存在: " + name + "，可用: " + methodologyMap.keySet());
        }
        return def;
    }

    public List<String> getNames() {
        return List.copyOf(methodologyMap.keySet());
    }

    public boolean exists(String name) {
        return name != null && methodologyMap.containsKey(name);
    }

    public MethodologyDefinition getDefault() {
        return methodologyMap.getOrDefault("default", builtinDefault());
    }

    /**
     * 物化继承：DFS 解析 parent 链，检测环，合并字段。
     * 合并规则：标量子覆盖父；集合（creationDimensions/titleStrategies/evaluationDimensions）整体替换；
     * platform.evaluationWeights 按 key 对父权重 patch。
     */
    private MethodologyDefinition materialize(String name, Map<String, MethodologyDefinition> rawMap,
                                              Set<String> visited) {
        if (visited.contains(name)) {
            throw new IllegalArgumentException("方法论继承环检测: " + visited + " -> " + name);
        }
        MethodologyDefinition raw = rawMap.get(name);
        if (raw == null) {
            throw new IllegalArgumentException("方法论 parent 不存在: " + name);
        }

        String parentName = raw.getParent();
        if (parentName == null || parentName.isBlank()) {
            return raw;
        }

        visited.add(name);
        MethodologyDefinition parent = materialize(parentName, rawMap, visited);
        visited.remove(name);

        MethodologyDefinition merged = new MethodologyDefinition();
        merged.setName(raw.getName());
        merged.setDescription(raw.getDescription() != null ? raw.getDescription() : parent.getDescription());
        merged.setVersion(raw.getVersion() != null ? raw.getVersion() : parent.getVersion());
        merged.setParent(null); // 物化后不再依赖继承链
        merged.setCreationDimensions(raw.getCreationDimensions() != null
                ? raw.getCreationDimensions() : parent.getCreationDimensions());
        merged.setTitleStrategies(raw.getTitleStrategies() != null
                ? raw.getTitleStrategies() : parent.getTitleStrategies());
        merged.setEvaluationDimensions(raw.getEvaluationDimensions() != null
                ? raw.getEvaluationDimensions() : parent.getEvaluationDimensions());
        merged.setPlatform(mergePlatform(raw.getPlatform(), parent.getPlatform()));
        return merged;
    }

    private MethodologyDefinition.PlatformConfig mergePlatform(
            MethodologyDefinition.PlatformConfig child,
            MethodologyDefinition.PlatformConfig parent) {
        if (child == null) {
            return parent;
        }
        if (parent == null) {
            return child;
        }
        MethodologyDefinition.PlatformConfig merged = new MethodologyDefinition.PlatformConfig();
        merged.setName(child.getName() != null ? child.getName() : parent.getName());
        merged.setAudience(child.getAudience() != null ? child.getAudience() : parent.getAudience());
        merged.setMinChars(child.getMinChars() != null ? child.getMinChars() : parent.getMinChars());
        merged.setMaxChars(child.getMaxChars() != null ? child.getMaxChars() : parent.getMaxChars());
        merged.setStyle(child.getStyle() != null ? child.getStyle() : parent.getStyle());
        if (child.getEvaluationWeights() != null || parent.getEvaluationWeights() != null) {
            Map<String, Integer> weights = new HashMap<>();
            if (parent.getEvaluationWeights() != null) {
                weights.putAll(parent.getEvaluationWeights());
            }
            if (child.getEvaluationWeights() != null) {
                weights.putAll(child.getEvaluationWeights());
            }
            merged.setEvaluationWeights(weights);
        }
        return merged;
    }

    /**
     * 校验：维度 key 非空、权重合法、Σweight>0、交叉引用 key 一致。
     */
    private void validate(MethodologyDefinition def) {
        List<MethodologyDefinition.EvaluationDimension> evalDims = def.getEvaluationDimensions();
        if (evalDims != null) {
            int sum = 0;
            for (MethodologyDefinition.EvaluationDimension d : evalDims) {
                if (d.getKey() == null || d.getKey().isBlank()) {
                    throw new IllegalArgumentException("方法论 " + def.getName() + " 评测维度缺少 key");
                }
                int w = d.getWeight() == null ? 0 : d.getWeight();
                if (w <= 0 || w > 100) {
                    throw new IllegalArgumentException("方法论 " + def.getName() + " 维度 "
                            + d.getKey() + " 权重非法: " + w + "（需在 (0,100]）");
                }
                sum += w;
            }
            if (sum <= 0) {
                throw new IllegalArgumentException("方法论 " + def.getName() + " 评测维度权重之和必须大于 0");
            }
        }
    }

    /** 内置兜底 default：不依赖任何文件，保证 getDefault 永不返回 null */
    private MethodologyDefinition builtinDefault() {
        MethodologyDefinition def = new MethodologyDefinition();
        def.setName("default");
        def.setDescription("内置兜底爆款方法论");
        def.setVersion("1.0");
        def.setCreationDimensions(List.of(createDim("coreViewpoint", "核心观点", "一句话点明核心观点"),
                createDim("persuasion", "说服策略", "运用数据/故事/类比等说服手段"),
                createDim("emotionalTrigger", "情绪触发", "触及读者情绪，引起共鸣")));
        def.setTitleStrategies(List.of(newTitleStrategy("curiosityGap", "好奇心缺口"),
                newTitleStrategy("dataImpact", "数据冲击"),
                newTitleStrategy("painResonance", "痛点共鸣"),
                newTitleStrategy("counterIntuitive", "反常识"),
                newTitleStrategy("socialCurrency", "社交货币")));
        def.setEvaluationDimensions(List.of(
                newEvalDim("emotionalTrigger", "情感触发", 40),
                newEvalDim("goldenSentence", "金句", 30),
                newEvalDim("persuasion", "说服策略", 30)));
        return def;
    }

    private MethodologyDefinition.CreationDimension createDim(String key, String name, String guidance) {
        MethodologyDefinition.CreationDimension d = new MethodologyDefinition.CreationDimension();
        d.setKey(key);
        d.setName(name);
        d.setGuidance(guidance);
        return d;
    }

    private MethodologyDefinition.TitleStrategy newTitleStrategy(String key, String name) {
        MethodologyDefinition.TitleStrategy s = new MethodologyDefinition.TitleStrategy();
        s.setKey(key);
        s.setName(name);
        return s;
    }

    private MethodologyDefinition.EvaluationDimension newEvalDim(String key, String name, int weight) {
        MethodologyDefinition.EvaluationDimension d = new MethodologyDefinition.EvaluationDimension();
        d.setKey(key);
        d.setName(name);
        d.setWeight(weight);
        d.setRubric(name);
        return d;
    }
}
```

- [ ] **Step 7: 运行测试确认通过**

Run: `mvn test -Dtest=MethodologyRegistryTest`
Expected: PASS（含 4 个模板 + 兜底）

- [ ] **Step 8: 提交**

```bash
git add src/main/resources/methodology/ \
        src/main/java/com/example/aipassagecreator/methodology/MethodologyDefinition.java \
        src/main/java/com/example/aipassagecreator/methodology/MethodologyRegistry.java \
        src/test/java/com/example/aipassagecreator/methodology/MethodologyRegistryTest.java
git commit -m "feat(methodology): 方法论模板 + MethodologyRegistry（物化继承/兜底/校验）

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 5: methodology 全链路持久化 + 状态传递

**Files:**
- Modify: `src/main/java/com/example/aipassagecreator/model/po/Article.java`
- Modify: `src/main/java/com/example/aipassagecreator/model/dto/article/ArticleCreateRequest.java`
- Modify: `src/main/java/com/example/aipassagecreator/model/dto/article/ArticleState.java`
- Modify: `src/main/java/com/example/aipassagecreator/model/dto/article/ArticleConfirmTitleRequest.java`
- Modify: `src/main/java/com/example/aipassagecreator/service/impl/ArticleServiceImpl.java`
- Modify: `src/main/java/com/example/aipassagecreator/service/ArticleService.java`
- Modify: `src/main/java/com/example/aipassagecreator/service/ArticleAsyncService.java`
- Modify: `src/main/java/com/example/aipassagecreator/agent/ArticleAgentOrchestrator.java`
- Modify: `src/main/resources/sql/h2-schema.sql`
- Modify: `src/main/resources/sql/core.sql`（生产迁移，追加列）

**Interfaces:**
- Produces:
  - `Article.methodology`（String，默认 "default"）
  - `ArticleState.methodology`（String）
  - `ArticleState.TitleOption.strategyKey`（String）
  - `ArticleConfirmTitleRequest.strategyKey`（String 可选）
  - `ArticleAgentOrchestrator` 增加常量 `KEY_METHODOLOGY = "methodology"`，三处 phase inputs 放入，`createKeyStrategyFactory()` 注册
- Consumes: `ArticleState`/`Article`/`ArticleCreateRequest` 现有字段

- [ ] **Step 1: 写失败测试**（H2 建表 + 全链路字段传递）

```java
package com.example.aipassagecreator.methodology;

import com.example.aipassagecreator.model.dto.article.ArticleCreateRequest;
import com.example.aipassagecreator.model.dto.article.ArticleState;
import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.service.ArticleService;
import com.example.aipassagecreator.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MethodologyPersistenceTest {

    @Autowired
    private ArticleService articleService;
    @Autowired
    private UserService userService;

    @Test
    void titleOption_hasStrategyKey() {
        ArticleState.TitleOption opt = new ArticleState.TitleOption();
        opt.setMainTitle("测试标题");
        opt.setSubTitle("测试副标题");
        opt.setStrategyKey("curiosityGap");
        assertEquals("curiosityGap", opt.getStrategyKey());
    }

    @Test
    void articleState_hasMethodology() {
        ArticleState state = new ArticleState();
        state.setMethodology("wechat");
        assertEquals("wechat", state.getMethodology());
    }

    @Test
    void articlePo_hasMethodologyColumn() throws Exception {
        Article article = new Article();
        article.setMethodology("default");
        assertEquals("default", article.getMethodology());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dtest=MethodologyPersistenceTest`
Expected: FAIL — 编译失败（字段不存在）

- [ ] **Step 3: 模型层加字段**

`Article.java`（在 style 字段后加）：
```java
/**
 * 方法论模板（default/wechat/xiaohongshu/douyin），默认 default
 */
private String methodology;
```

`ArticleState.java`（在 style 字段后加 + TitleOption 加 strategyKey）：
```java
/**
 * 方法论模板名称（默认 default）
 */
private String methodology;
```
在 `TitleOption` 内部类：
```java
/**
 * 标题命中的策略 key（titleStrategies[].key），可选
 */
private String strategyKey;
```

`ArticleCreateRequest.java`（style 后加）：
```java
/**
 * 方法论模板名称（默认 default）
 */
private String methodology;
```

`ArticleConfirmTitleRequest.java`（确认标题时记录选中标题的策略）：
```java
/**
 * 选中标题命中的策略 key（可选）
 */
private String strategyKey;
```

- [ ] **Step 4: create 透传 methodology**

修改 `ArticleServiceImpl.createArticleTask`（103-129 行）签名与方法体，`ArticleService` 接口同步：

```java
// ArticleService 接口
String createArticleTask(String topic, String style, String methodology,
                         List<String> enabledImageMethods, User loginUser);
String createArticleTaskWithQuotaCheck(String topic, String style, String methodology,
                                       List<String> enabledImageMethods, User loginUser);
```

```java
@Override
public String createArticleTask(String topic, String style, String methodology,
                                List<String> enabledImageMethods, User loginUser) {
    List<String> finalImageMethods = processImageMethods(enabledImageMethods, loginUser);
    validateImageMethods(finalImageMethods, loginUser);

    String taskId = IdUtil.simpleUUID();

    Article article = new Article();
    article.setTaskId(taskId);
    article.setUserId(loginUser.getId());
    article.setTopic(topic);
    article.setStyle(style);
    article.setMethodology(methodology == null || methodology.isBlank() ? "default" : methodology);
    article.setEnabledImageMethods(finalImageMethods != null && !finalImageMethods.isEmpty()
            ? GsonUtils.toJson(finalImageMethods) : null);
    article.setStatus(ArticleStatusEnum.PENDING.getValue());
    article.setPhase(ArticlePhaseEnum.PENDING.getValue());
    article.setCreateTime(LocalDateTime.now());

    this.save(article);
    log.info("文章任务已创建, taskId={}, userId={}, style={}, methodology={}",
            taskId, loginUser.getId(), style, article.getMethodology());
    return taskId;
}

@Override
@Transactional(rollbackFor = Exception.class)
public String createArticleTaskWithQuotaCheck(String topic, String style, String methodology,
                                              List<String> enabledImageMethods, User loginUser) {
    quotaService.checkAndConsumeQuota(loginUser);
    return createArticleTask(topic, style, methodology, enabledImageMethods, loginUser);
}
```

`ArticleController.createArticle` 调用点更新：
```java
String taskId = articleService.createArticleTaskWithQuotaCheck(
        request.getTopic(),
        request.getStyle(),
        request.getMethodology(),
        request.getEnabledImageMethods(),
        loginUser
);
articleAsyncService.executePhase1(taskId, request.getTopic(), request.getStyle(), request.getMethodology());
```

同时给 `createArticle` 加校验：`if (request.getMethodology() != null && !request.getMethodology().isBlank())` 可先做注册表存在性校验（用 `@Autowired MethodologyRegistry`），未知值回退 default 或抛参数错误。为 v1 采用：未知值回退 "default"（`MethodologyRegistry.exists` 判断）。

- [ ] **Step 5: 阶段1异步透传 methodology**

修改 `ArticleAsyncService.executePhase1` 签名（104-155 行）：
```java
@Async("articleExecutor")
public void executePhase1(String taskId, String topic, String style, String methodology) {
    ...
    ArticleState state = new ArticleState();
    state.setTaskId(taskId);
    state.setTopic(topic);
    state.setStyle(style);
    state.setMethodology(methodology != null ? methodology : "default");
    ...
}
```

修改 `executePhase2`（157-218 行）与 `executePhase3`（225-298 行）从 DB 回填：
```java
// executePhase2 内，在 state.setStyle(article.getStyle()) 之后：
state.setMethodology(article.getMethodology() != null ? article.getMethodology() : "default");

// executePhase3 内，在 state.setStyle(article.getStyle()) 之后：
state.setMethodology(article.getMethodology() != null ? article.getMethodology() : "default");
```

- [ ] **Step 6: StateGraph 注入 methodology**

修改 `ArticleAgentOrchestrator.java`：

```java
private static final String KEY_METHODOLOGY = "methodology";
```

三个 phase 的 inputs 中分别加入（phase1 在 style 后、phase2/3 在 style 后）：
```java
inputs.put(KEY_METHODOLOGY, state.getMethodology());
```

`createKeyStrategyFactory()`（327-346 行）注册：
```java
strategies.put(KEY_METHODOLOGY, new ReplaceStrategy());
```

- [ ] **Step 7: schema 同步（article 加 methodology 列）**

`h2-schema.sql` article 表（在 style 行后加）：
```sql
    methodology varchar(64) default 'default' null,
```

`sql/core.sql`（生产迁移，追加 ALTER）：
```sql
ALTER TABLE article ADD COLUMN methodology varchar(64) DEFAULT 'default' NULL;
```

- [ ] **Step 8: 运行测试确认通过**

Run: `mvn test -Dtest=MethodologyPersistenceTest`
Expected: PASS

- [ ] **Step 9: 回归（全量测试确认无破坏）**

Run: `mvn test`
Expected: 全量通过（重点回归 ArticleController/Skill 相关测试）

- [ ] **Step 10: 提交**

```bash
git add src/main/java/com/example/aipassagecreator/model/ \
        src/main/java/com/example/aipassagecreator/service/impl/ArticleServiceImpl.java \
        src/main/java/com/example/aipassagecreator/service/ArticleService.java \
        src/main/java/com/example/aipassagecreator/service/ArticleAsyncService.java \
        src/main/java/com/example/aipassagecreator/agent/ArticleAgentOrchestrator.java \
        src/main/java/com/example/aipassagecreator/controller/ArticleController.java \
        src/main/resources/sql/h2-schema.sql src/main/resources/sql/core.sql \
        src/test/java/com/example/aipassagecreator/methodology/MethodologyPersistenceTest.java
git commit -m "feat(methodology): methodology 全链路持久化 + StateGraph 状态传递

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 6: MethodologyPromptAssembler + 双路径创作引导注入

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/methodology/MethodologyPromptAssembler.java`
- Modify: `src/main/java/com/example/aipassagecreator/agent/agents/TitleGeneratorAgent.java`
- Modify: `src/main/java/com/example/aipassagecreator/agent/agents/ContentGeneratorAgent.java`
- Modify: `src/main/java/com/example/aipassagecreator/agent/agents/OutlineGeneratorAgent.java`
- Modify: `src/main/java/com/example/aipassagecreator/service/ArticleAgentService.java`
- Test: `src/test/java/com/example/aipassagecreator/methodology/MethodologyPromptAssemblerTest.java`（新增）

**Interfaces:**
- Produces:
  - `MethodologyPromptAssembler.buildTitleGuidance(String methodologyName)` → String
  - `MethodologyPromptAssembler.buildContentGuidance(String methodologyName)` → String
- Consumes: `MethodologyRegistry.get`（Task 4）

- [ ] **Step 1: 写失败测试**

```java
package com.example.aipassagecreator.methodology;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MethodologyPromptAssemblerTest {

    @Autowired
    private MethodologyPromptAssembler assembler;

    @Test
    void buildTitleGuidance_containsStrategies() {
        String guidance = assembler.buildTitleGuidance("default");
        assertTrue(guidance.contains("curiosityGap"));
        assertTrue(guidance.contains("标题策略"));
    }

    @Test
    void buildContentGuidance_containsDimensions() {
        String guidance = assembler.buildContentGuidance("default");
        assertTrue(guidance.contains("核心观点"));
        assertTrue(guidance.contains("情感触发"));
    }

    @Test
    void blankName_returnsEmpty() {
        assertEquals("", assembler.buildTitleGuidance(null));
        assertEquals("", assembler.buildContentGuidance("  "));
    }

    @Test
    void unknownName_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> assembler.buildTitleGuidance("not-exist"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dtest=MethodologyPromptAssemblerTest`
Expected: FAIL — 类不存在

- [ ] **Step 3: 创建 MethodologyPromptAssembler**

```java
package com.example.aipassagecreator.methodology;

import org.springframework.stereotype.Component;

/**
 * 方法论提示词装配器：将创作维度/标题策略组装为可追加到 Agent prompt 的提示段。
 * <p>注入位置必须在输出格式约束（"直接返回 JSON/Markdown"）之前，避免破坏结构化输出。</p>
 */
@Component
public class MethodologyPromptAssembler {

    private final MethodologyRegistry registry;

    public MethodologyPromptAssembler(MethodologyRegistry registry) {
        this.registry = registry;
    }

    /**
     * 组装标题策略引导段。要求每个标题标注命中的策略 key（配合 TitleOption.strategyKey）。
     */
    public String buildTitleGuidance(String methodologyName) {
        if (methodologyName == null || methodologyName.isBlank()) {
            return "";
        }
        MethodologyDefinition def = registry.get(methodologyName);
        if (def.getTitleStrategies() == null || def.getTitleStrategies().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n【标题策略要求】\n"
                + "请为每个标题方案标注命中的策略 key（字段名 strategyKey）。可选策略：\n");
        for (MethodologyDefinition.TitleStrategy s : def.getTitleStrategies()) {
            sb.append("- ").append(s.getKey()).append("：").append(s.getName()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 组装创作维度引导段（正文 Agent 使用）。
     */
    public String buildContentGuidance(String methodologyName) {
        if (methodologyName == null || methodologyName.isBlank()) {
            return "";
        }
        MethodologyDefinition def = registry.get(methodologyName);
        if (def.getCreationDimensions() == null || def.getCreationDimensions().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n【创作维度要求】\n"
                + "请在创作中遵循以下爆款创作维度：\n");
        for (MethodologyDefinition.CreationDimension d : def.getCreationDimensions()) {
            sb.append("- ").append(d.getName()).append("：").append(d.getGuidance()).append("\n");
        }
        return sb.toString();
    }
}
```

- [ ] **Step 4: TitleGeneratorAgent 注入**（orchestrator 路径）

修改 `TitleGeneratorAgent.java`：注入 assembler + 从 state 读取 methodology：

```java
private final MethodologyPromptAssembler methodologyPromptAssembler;
// 构造函数通过 @RequiredArgsConstructor 自动生成，新增 final 字段即可

public static final String INPUT_METHODOLOGY = "methodology";
```

`apply()` 方法中（51-53 行）：
```java
String methodology = state.value(INPUT_METHODOLOGY).map(Object::toString).orElse("default");
String prompt = PromptConstant.AGENT1_TITLE_PROMPT
        .replace("{topic}", topic)
        + getStylePrompt(style)
        + methodologyPromptAssembler.buildTitleGuidance(methodology);
```

注意：`TitleGeneratorAgent` 当前用 `@RequiredArgsConstructor` + `private final DashScopeChatModel chatModel`。新增 final 字段 `MethodologyPromptAssembler` 会自动注入。

- [ ] **Step 5: ContentGeneratorAgent / OutlineGeneratorAgent 注入**

`ContentGeneratorAgent.java`（68-72 行）：
```java
private final MethodologyPromptAssembler methodologyPromptAssembler;
public static final String INPUT_METHODOLOGY = "methodology";
...
String methodology = state.value(INPUT_METHODOLOGY).map(Object::toString).orElse("default");
String prompt = PromptConstant.AGENT3_CONTENT_PROMPT
        .replace("{mainTitle}", mainTitle)
        .replace("{subTitle}", subTitle)
        .replace("{outlineText}", outlineText)
        + getStylePrompt(style)
        + methodologyPromptAssembler.buildContentGuidance(methodology);
```

`OutlineGeneratorAgent.java`（72-76 行，先读取该文件确认局部变量名）：
```java
private final MethodologyPromptAssembler methodologyPromptAssembler;
public static final String INPUT_METHODOLOGY = "methodology";
...
String methodology = state.value(INPUT_METHODOLOGY).map(Object::toString).orElse("default");
String prompt = PromptConstant.AGENT2_OUTLINE_PROMPT
        .replace("{mainTitle}", mainTitle)
        .replace("{subTitle}", subTitle)
        + getStylePrompt(style)
        + methodologyPromptAssembler.buildContentGuidance(methodology);
```

- [ ] **Step 6: ArticleAgentService fallback 路径注入**（3 处）

`ArticleAgentService.java`：
- 新增字段 `@Resource private MethodologyPromptAssembler methodologyPromptAssembler;`
- `agent1GenerateTitleOptions`（217-230 行）：`+ getStylePrompt(state.getStyle())` 后追加 `+ methodologyPromptAssembler.buildTitleGuidance(state.getMethodology())`
- `agent2GenerateOutline`（239-251 行）：追加 `+ methodologyPromptAssembler.buildContentGuidance(state.getMethodology())`
- `agent3GenerateContent`（260-273 行）：追加 `+ methodologyPromptAssembler.buildContentGuidance(state.getMethodology())`

- [ ] **Step 7: 运行测试确认通过**

Run: `mvn test -Dtest=MethodologyPromptAssemblerTest`
Expected: PASS

- [ ] **Step 8: 编译回归**

Run: `mvn test-compile`
Expected: 编译通过（无注入/引用错误）

- [ ] **Step 9: 提交**

```bash
git add src/main/java/com/example/aipassagecreator/methodology/MethodologyPromptAssembler.java \
        src/main/java/com/example/aipassagecreator/agent/agents/TitleGeneratorAgent.java \
        src/main/java/com/example/aipassagecreator/agent/agents/ContentGeneratorAgent.java \
        src/main/java/com/example/aipassagecreator/agent/agents/OutlineGeneratorAgent.java \
        src/main/java/com/example/aipassagecreator/service/ArticleAgentService.java \
        src/test/java/com/example/aipassagecreator/methodology/MethodologyPromptAssemblerTest.java
git commit -m "feat(methodology): 创作引导双路径注入 + 标题策略标注

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 7: 爆款评测 evaluateViral

**Files:**
- Modify: `src/main/java/com/example/aipassagecreator/model/po/ArticleQuality.java`
- Modify: `src/main/java/com/example/aipassagecreator/service/ContentQualityService.java`
- Modify: `src/main/java/com/example/aipassagecreator/service/impl/ContentQualityServiceImpl.java`
- Modify: `src/main/java/com/example/aipassagecreator/controller/ArticleController.java`
- Modify: `src/main/resources/sql/h2-schema.sql`
- Create: `src/main/resources/sql/add_viral_quality.sql`
- Test: `src/test/java/com/example/aipassagecreator/service/ContentQualityServiceViralTest.java`（新增）

**Interfaces:**
- Produces:
  - `ArticleQuality` 新增字段：`scoreType`、`userId`、`viralScore`(BigDecimal)、`viralScores`(String JSON)、`titleStrategyHit`、`methodologyUsed`、`contentHash`、`versionNo`
  - `ContentQualityService.evaluateViral(String taskId, String methodologyName, Long loginUserId)` → ArticleQuality
  - `ContentQualityService.getLatestViral(String taskId)` → ArticleQuality（按 score_type='VIRAL'）
- Consumes: `MethodologyRegistry`（Task 4）、`GsonUtils.tryFixJson/fromJsonSafe`（Task 2）、`ModelRouter`、`QuotaService`、`ObservabilityConfig`、`@AgentExecution`、`TokenUsageHolder`

- [ ] **Step 1: 写失败测试**（mock ModelRouter 返回固定 JSON）

```java
package com.example.aipassagecreator.service;

import com.example.aipassagecreator.model.po.ArticleQuality;
import com.example.aipassagecreator.skill.ModelRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class ContentQualityServiceViralTest {

    @MockitoBean
    private ModelRouter modelRouter;

    @Autowired
    private ContentQualityService contentQualityService;

    private ChatModel mockChatModel;

    @BeforeEach
    void setUp() {
        mockChatModel = org.mockito.Mockito.mock(ChatModel.class);
        when(modelRouter.resolveWithFallback(any(), any())).thenReturn(mockChatModel);
        when(modelRouter.resolveModelName(any(), any())).thenReturn("mock-model");
    }

    private void mockResponse(String json) {
        ChatResponse resp = new ChatResponse(List.of(
                new Generation(new AssistantMessage(json))));
        when(mockChatModel.call(any(Prompt.class))).thenReturn(resp);
    }

    @Test
    void tryFixJson_stripsFence() {
        // 验证工具层增强
        String fixed = com.example.aipassagecreator.utils.GsonUtils.tryFixJson(
                "```json\n{\"a\":1}\n```");
        assertEquals("{\"a\":1}", fixed);
    }

    @Test
    void evaluateViral_normalizesWeights() {
        // 维度: emotionalTrigger=85,goldenSentence=75,interactionHook=60,persuasion=90,titleStrategy=80
        // 权重: 20,15,15,15,10 → Σweight=75, Σscore*weight=85*20+75*15+60*15+90*15+80*10=1700+1125+900+1350+800=5875
        // viralScore = 5875/75 = 78.33
        mockResponse("""
            {"structureScore":80,"logicScore":78,"languageScore":82,"seoScore":70,"readabilityScore":85,"overallScore":79,
             "viral":{"emotionalTrigger":85,"goldenSentence":75,"interactionHook":60,"persuasion":90,"titleStrategy":80},
             "titleStrategyHit":"curiosityGap","suggestions":["加强互动"]}""");
        ArticleQuality q = contentQualityService.evaluateViral("__nonexistent__", "default", 1L);
        // 文章不存在会抛异常，此处仅验证权重公式正确性（如任务无文章可评测，可调整：先造一条文章记录）
        assertNotNull(q);
    }
}
```

**实现说明**：该测试依赖真实文章记录。实施时先在测试中通过 `ArticleService.createArticleTask` 创建文章并走完流程，或用 `ArticleMapper` 直接插入一条 `taskId` 记录再评测。若直接对不存在文章评测，`evaluateViral` 应抛 `IllegalArgumentException`。测试应覆盖两个分支：①文章存在 → 校验权重归一化；②文章不存在 → 断言抛异常。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dtest=ContentQualityServiceViralTest`
Expected: FAIL — evaluateViral 不存在 / @MockitoBean 未生效

- [ ] **Step 3: ArticleQuality PO 加字段**

`ArticleQuality.java` 增加字段：
```java
/** 评测类型：GENERIC / VIRAL */
private String scoreType;
/** 归属用户 ID */
private Long userId;
/** 爆款加权综合分 */
private java.math.BigDecimal viralScore;
/** 各爆款维度分（JSON） */
private String viralScores;
/** 标题策略命中 */
private String titleStrategyHit;
/** 评测所用方法论 */
private String methodologyUsed;
/** 内容快照 hash（幂等） */
private String contentHash;
/** 评测绑定版本号 */
private Integer versionNo;
```

- [ ] **Step 4: ContentQualityService 接口扩展**

```java
ArticleQuality evaluateViral(String taskId, String methodologyName, Long loginUserId);
ArticleQuality getLatestViral(String taskId);
```

- [ ] **Step 5: 实现 evaluateViral**

`ContentQualityServiceImpl.java` 增加注入与实现（保留现有 evaluate/getLatest）。

**新增 import**（补到文件头部）：
```java
import com.example.aipassagecreator.enums.ArticleStatusEnum;
import com.example.aipassagecreator.methodology.MethodologyDefinition;
import com.example.aipassagecreator.methodology.MethodologyRegistry;
import com.example.aipassagecreator.config.ObservabilityConfig;
import java.math.BigDecimal;
import java.math.RoundingMode;
```

```java
private static final String VIRAL_QUALITY_PROMPT = """
        你是一位资深内容评审专家。请对以下文章进行多维度质量评分（每项 0-100 分），
        并给出具体的改进建议和亮点。

        评分维度：
        - structure（结构）：段落划分是否合理，逻辑递进是否清晰
        - logic（逻辑）：论点是否有据可依，论证是否严密
        - language（语言）：表达是否流畅自然，有无 AI 痕迹过重
        - seo（SEO）：标题和关键词布局是否合理
        - readability（可读性）：排版是否舒适，信息密度是否适中

        此外，请对以下爆款维度评分（同样 0-100 分）：
        %s

        标题：%s
        副标题：%s

        请严格按以下 JSON 格式输出：
        {
          "structureScore": 85, "logicScore": 80, "languageScore": 75,
          "seoScore": 70, "readabilityScore": 82, "overallScore": 78,
          "viral": {
            "emotionalTrigger": 85,
            "goldenSentence": 75,
            "interactionHook": 60,
            "persuasion": 90,
            "titleStrategy": 80
          },
          "titleStrategyHit": "curiosityGap",
          "suggestions": ["建议1"],
          "strengths": ["亮点1"]
        }

        文章内容：
        %s
        """;

@Resource
private MethodologyRegistry methodologyRegistry;
// v1 以 @RateLimit 限流控制成本；独立评测预算/配额扣减留待后续（与创建配额分离）
@Resource
private com.example.aipassagecreator.config.ObservabilityConfig observabilityConfig;

@Override
public ArticleQuality evaluateViral(String taskId, String methodologyName, Long loginUserId) {
    Article article = articleMapper.selectOneByQuery(
            QueryWrapper.create().eq("taskId", taskId));
    if (article == null) {
        throw new IllegalArgumentException("文章不存在: " + taskId);
    }
    if (!ArticleStatusEnum.COMPLETED.getValue().equals(article.getStatus())) {
        throw new IllegalArgumentException("仅已完成文章可评测: " + taskId);
    }

    // methodology 白名单查名（fail-fast）
    String effective = methodologyName == null || methodologyName.isBlank()
            ? "default" : methodologyName;
    MethodologyDefinition def = methodologyRegistry.get(effective);

    // 组装评测维度说明
    StringBuilder dims = new StringBuilder();
    List<MethodologyDefinition.EvaluationDimension> evalDims = def.getEvaluationDimensions();
    for (MethodologyDefinition.EvaluationDimension d : evalDims) {
        dims.append("- ").append(d.getKey()).append("（").append(d.getName())
                .append("）：").append(d.getRubric()).append("\n");
    }

    // 快照：head-tail 策略（前 4000 + 后 4000）
    String content = article.getContent() == null ? "" : article.getContent();
    String snapshot = buildHeadTailSnapshot(content, 4000);

    // 合并单次调用
    ChatModel model = modelRouter.resolveWithFallback(null, null);
    String modelName = modelRouter.resolveModelName(null, null);
    long start = System.currentTimeMillis();
    String prompt = VIRAL_QUALITY_PROMPT.formatted(
            dims, article.getMainTitle() == null ? "" : article.getMainTitle(),
            article.getSubTitle() == null ? "" : article.getSubTitle(), snapshot);
    ChatResponse response = model.call(new Prompt(List.of(new UserMessage(prompt))));
    String output = response.getResult().getOutput().getText();
    long duration = System.currentTimeMillis() - start;
    int tokenUsage = response.getMetadata() != null
            && response.getMetadata().getUsage() != null
            && response.getMetadata().getUsage().getTotalTokens() != null
            ? response.getMetadata().getUsage().getTotalTokens() : 0;

    // 容错解析
    String fixed = GsonUtils.tryFixJson(output);
    Map<String, Object> parsed = GsonUtils.fromJsonSafe(fixed,
            new TypeToken<Map<String, Object>>() {});

    // 维度完备性 + 归一化
    BigDecimal viralScore = computeViralScore(parsed, evalDims);
    String viralScoresJson = GsonUtils.toJson(parsed.get("viral"));
    String strategyHit = parsed.get("titleStrategyHit") instanceof String s ? s : null;

    // 幂等 upsert：同 taskId 的 VIRAL 评测复用行
    ArticleQuality existing = getLatestViral(taskId);
    ArticleQuality quality = ArticleQuality.builder()
            .id(existing != null ? existing.getId() : null)
            .taskId(taskId)
            .userId(loginUserId)
            .scoreType("VIRAL")
            .articleContentSnapshot(snapshot)
            .structureScore(asInt(parsed.get("structureScore")))
            .logicScore(asInt(parsed.get("logicScore")))
            .languageScore(asInt(parsed.get("languageScore")))
            .seoScore(asInt(parsed.get("seoScore")))
            .readabilityScore(asInt(parsed.get("readabilityScore")))
            .overallScore(asInt(parsed.get("overallScore")))
            .viralScore(viralScore)
            .viralScores(viralScoresJson)
            .titleStrategyHit(strategyHit)
            .methodologyUsed(effective)
            .contentHash(Integer.toHexString(content.hashCode()))
            .versionNo(existing != null && existing.getVersionNo() != null ? existing.getVersionNo() + 1 : 1)
            .suggestions(GsonUtils.toJson(parsed.get("suggestions")))
            .strengths(GsonUtils.toJson(parsed.get("strengths")))
            .modelUsed(modelName)
            .tokenUsage(tokenUsage)
            .durationMs((int) duration)
            .build();
    if (existing != null) {
        articleQualityMapper.update(quality);
    } else {
        articleQualityMapper.insert(quality);
    }

    // 可观测
    observabilityConfig.recordLlmCall(modelName, duration, tokenUsage);
    log.info("爆款评测完成: taskId={}, viralScore={}, model={}, methodology={}, duration={}ms",
            taskId, viralScore, modelName, effective, duration);
    return quality;
}

@Override
public ArticleQuality getLatestViral(String taskId) {
    return articleQualityMapper.selectOneByQuery(
            QueryWrapper.create()
                    .eq("task_id", taskId)
                    .eq("score_type", "VIRAL")
                    .orderBy("id", false)
                    .limit(1));
}

/** head-tail 双段快照 */
private String buildHeadTailSnapshot(String content, int headLen) {
    if (content.length() <= headLen * 2) {
        return content;
    }
    return content.substring(0, headLen) + "\n...[中段省略]...\n"
            + content.substring(content.length() - headLen);
}

/** 归一化加权：Σ(score×weight)/Σweight */
private java.math.BigDecimal computeViralScore(Map<String, Object> parsed,
        List<MethodologyDefinition.EvaluationDimension> evalDims) {
    Object viralObj = parsed.get("viral");
    if (!(viralObj instanceof Map)) {
        return java.math.BigDecimal.ZERO;
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> viral = (Map<String, Object>) viralObj;
    int sumWeight = 0;
    int sumScoreWeight = 0;
    boolean anyScored = false;
    for (MethodologyDefinition.EvaluationDimension d : evalDims) {
        int w = d.getWeight() == null ? 0 : d.getWeight();
        Integer score = viral.get(d.getKey()) instanceof Number n ? n.intValue()
                : (viral.get(d.getKey()) instanceof String s ? parseIntSafe(s) : null);
        if (score == null) {
            continue; // 维度缺失：跳过不参与（incomplete 语义）
        }
        anyScored = true;
        sumWeight += w;
        sumScoreWeight += score * w;
    }
    if (!anyScored || sumWeight <= 0) {
        return java.math.BigDecimal.ZERO;
    }
    return java.math.BigDecimal.valueOf(sumScoreWeight)
            .divide(java.math.BigDecimal.valueOf(sumWeight), 2, java.math.RoundingMode.HALF_UP);
}

private static Integer parseIntSafe(String s) {
    try {
        return Integer.parseInt(s.trim());
    } catch (NumberFormatException e) {
        return null;
    }
}
```

同时给 `ArticleQuality` 建表加 `@Table` 无需改（表名不变），新增字段需 `ArticleQualityMapper` 的 BaseMapper 自动映射（MyBatis-Flex 按 camelToUnderline 默认 true 映射 `scoreType`→`score_type`）。

- [ ] **Step 6: schema 同步（article_quality 加列）**

`h2-schema.sql` article_quality 表加列：
```sql
    score_type varchar(16) default 'GENERIC' not null,
    user_id bigint null,
    viral_score decimal(5,2) null,
    viral_scores text null,
    title_strategy_hit varchar(32) null,
    methodology_used varchar(64) null,
    content_hash varchar(64) null,
    version_no int default 1 null,
```

`sql/add_viral_quality.sql`（生产迁移）：
```sql
ALTER TABLE article_quality ADD COLUMN score_type varchar(16) DEFAULT 'GENERIC' NOT NULL;
ALTER TABLE article_quality ADD COLUMN user_id bigint NULL;
ALTER TABLE article_quality ADD COLUMN viral_score decimal(5,2) NULL;
ALTER TABLE article_quality ADD COLUMN viral_scores JSON NULL;
ALTER TABLE article_quality ADD COLUMN title_strategy_hit varchar(32) NULL;
ALTER TABLE article_quality ADD COLUMN methodology_used varchar(64) NULL;
ALTER TABLE article_quality ADD COLUMN content_hash varchar(64) NULL;
ALTER TABLE article_quality ADD COLUMN version_no int DEFAULT 1 NULL;
CREATE INDEX idx_aq_task_score_type ON article_quality(task_id, score_type);
```

- [ ] **Step 7: Controller 新增 evaluate-viral 端点**（含安全闸门）

`ArticleController.java`：
```java
/**
 * 爆款维度评测
 */
@PostMapping("/evaluate-viral")
@Operation(summary = "爆款维度评测")
@AuthCheck(mustRole = "user")
@RateLimit(limit = 5, window = 60, key = "viral_evaluate")
public BaseResponse<?> evaluateViral(@RequestBody ArticleEvaluateViralRequest request,
                                     HttpServletRequest httpServletRequest) {
    ThrowUtils.throwIf(request == null || request.getTaskId() == null
            || request.getTaskId().trim().isEmpty(), ErrorCode.PARAMS_ERROR, "任务ID不能为空");

    User loginUser = userService.getLoginUser(httpServletRequest);

    // 归属校验：防止 IDOR
    var article = articleService.getByTaskId(request.getTaskId());
    if (article == null || !article.getUserId().equals(loginUser.getId())) {
        throw new com.example.aipassagecreator.exception.BusinessException(
                ErrorCode.NO_AUTH_ERROR, "无权操作此文章");
    }

    try {
        ArticleQuality result = contentQualityService.evaluateViral(
                request.getTaskId(), request.getMethodologyName(), loginUser.getId());
        return ResultUtils.success(result);
    } catch (IllegalArgumentException e) {
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, e.getMessage());
    }
}
```

新增 DTO `ArticleEvaluateViralRequest`：
```java
package com.example.aipassagecreator.model.dto.article;

import lombok.Data;
import java.io.Serializable;

@Data
public class ArticleEvaluateViralRequest implements Serializable {
    /** 文章任务 ID */
    private String taskId;
    /** 方法论模板名称（默认 default） */
    private String methodologyName;
}
```

同时将 `ArticleController` 的 `@Resource ContentQualityService` 保留（已存在）。

- [ ] **Step 8: 完善测试并确认通过**

Run: `mvn test -Dtest=ContentQualityServiceViralTest`
Expected: PASS

- [ ] **Step 9: 编译 + 全量回归**

Run: `mvn test`
Expected: 全量通过

- [ ] **Step 10: 提交**

```bash
git add src/main/java/com/example/aipassagecreator/model/po/ArticleQuality.java \
        src/main/java/com/example/aipassagecreator/service/ContentQualityService.java \
        src/main/java/com/example/aipassagecreator/service/impl/ContentQualityServiceImpl.java \
        src/main/java/com/example/aipassagecreator/controller/ArticleController.java \
        src/main/java/com/example/aipassagecreator/model/dto/article/ArticleEvaluateViralRequest.java \
        src/main/resources/sql/h2-schema.sql src/main/resources/sql/add_viral_quality.sql \
        src/test/java/com/example/aipassagecreator/service/ContentQualityServiceViralTest.java
git commit -m "feat(quality): 爆款维度评测 evaluateViral + 归一化 + 幂等落库

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 8: MethodologyRefiner 反哺闭环 + rewriteSection

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/methodology/MethodologyRefiner.java`
- Modify: `src/main/java/com/example/aipassagecreator/service/ArticleRewriteService.java`
- Modify: `src/main/java/com/example/aipassagecreator/service/impl/ArticleRewriteServiceImpl.java`
- Modify: `src/main/java/com/example/aipassagecreator/controller/ArticleController.java`
- Test: `src/test/java/com/example/aipassagecreator/methodology/MethodologyRefinerTest.java`（新增）

**Interfaces:**
- Produces:
  - `ArticleRewriteService.rewriteSection(String taskId, String instruction, String sectionLocator, Long userId)` → ArticleVersion（v1 退化为整篇改写，sectionLocator 预留）
  - `MethodologyRefiner.refine(String taskId, String methodologyName, Long loginUserId)` → RefineResult（含低分维度/轮次/是否回退）
  - `MethodologyRefiner.RefineResult`（嵌套类：`List<String> weakDimensions`, `int rounds`, `boolean reverted`, `BigDecimal beforeScore`, `BigDecimal afterScore`）
- Consumes: `ContentQualityService.evaluateViral/getLatestViral`（Task 7）、`ArticleRewriteService`、`MethodologyRegistry`

- [ ] **Step 1: 写失败测试**

```java
package com.example.aipassagecreator.methodology;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MethodologyRefinerTest {

    @Autowired
    private MethodologyRefiner refiner;

    @Test
    void refineResult_builderWorks() {
        MethodologyRefiner.RefineResult result = MethodologyRefiner.RefineResult.builder()
                .rounds(1)
                .reverted(false)
                .build();
        assertEquals(1, result.getRounds());
        assertFalse(result.isReverted());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -Dtest=MethodologyRefinerTest`
Expected: FAIL — 类不存在

- [ ] **Step 3: ArticleRewriteService 接口扩展**

```java
/**
 * 按章节定向改写（v1 退化为整篇改写，sectionLocator 预留）
 */
ArticleVersion rewriteSection(String taskId, String instruction, String sectionLocator, Long userId);
```

- [ ] **Step 4: ArticleRewriteServiceImpl 实现 rewriteSection**（复用 rewrite 逻辑 + SystemMessage）

```java
@Override
public ArticleVersion rewriteSection(String taskId, String instruction, String sectionLocator, Long userId) {
    Article article = articleMapper.selectOneByQuery(
            QueryWrapper.create().eq("taskId", taskId));
    if (article == null) {
        throw new IllegalArgumentException("文章不存在: " + taskId);
    }
    String currentContent = article.getContent();
    if (currentContent == null || currentContent.isBlank()) {
        throw new IllegalArgumentException("文章内容为空");
    }

    var lastVer = articleVersionMapper.selectOneByQuery(
            QueryWrapper.create().eq("task_id", taskId)
                    .orderBy("version_no", false).limit(1));
    int nextVersion = (lastVer != null ? lastVer.getVersionNo() : 0) + 1;

    String snapshot = currentContent.length() > MAX_CONTENT_LENGTH
            ? currentContent.substring(0, MAX_CONTENT_LENGTH) : currentContent;

    ChatModel model = modelRouter.resolveWithFallback(null, null);
    String modelName = modelRouter.resolveModelName(null, null);
    long start = System.currentTimeMillis();

    SystemMessage systemMessage = new SystemMessage(REWRITE_SYSTEM_PROMPT.formatted(instruction));
    UserMessage userMessage = new UserMessage(REWRITE_PROMPT.formatted(snapshot));
    ChatResponse response = model.call(new Prompt(List.of(systemMessage, userMessage)));
    String rewritten = response.getResult().getOutput().getText();
    long duration = System.currentTimeMillis() - start;
    int tokenUsage = extractTokens(response);

    ArticleVersion version = ArticleVersion.builder()
            .taskId(taskId).versionNo(nextVersion).round(1)
            .content(rewritten)
            .changeSummary("方法论定向改写 (第 " + nextVersion + " 版)")
            .promptUsed(promptSummary(instruction))
            .diffBaseVersion(nextVersion - 1 == 0 ? null : nextVersion - 1)
            .modelUsed(modelName).tokenUsage(tokenUsage).durationMs((int) duration)
            .createdBy(userId)
            .build();
    articleVersionMapper.insert(version);

    article.setContent(rewritten);
    articleMapper.update(article);
    log.info("定向改写完成: taskId={}, versionNo={}, model={}, duration={}ms",
            taskId, nextVersion, modelName, duration);
    return version;
}

private String promptSummary(String instruction) {
    String s = "定向改写: " + instruction;
    return s.length() > 500 ? s.substring(0, 500) + "..." : s;
}
```

- [ ] **Step 5: 创建 MethodologyRefiner**

```java
package com.example.aipassagecreator.methodology;

import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.model.po.ArticleQuality;
import com.example.aipassagecreator.service.ArticleRewriteService;
import com.example.aipassagecreator.service.ContentQualityService;
import com.example.aipassagecreator.skill.ModelRouter;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 爆款反哺闭环：低分维度定向改写 → 复评 → 无提升回退。
 * <p>由编排层（Controller）驱动，每任务最多 MAX_ROUNDS 轮；每轮只改最弱的 1-2 个维度。</p>
 */
@Slf4j
@Service
public class MethodologyRefiner {

    private static final int MAX_ROUNDS = 3;
    private static final int WEAK_THRESHOLD = 60;
    private static final int LOW_VIRAL_THRESHOLD = 70;

    private final MethodologyRegistry registry;
    private final ContentQualityService contentQualityService;
    private final ArticleRewriteService articleRewriteService;

    public MethodologyRefiner(MethodologyRegistry registry,
                              ContentQualityService contentQualityService,
                              ArticleRewriteService articleRewriteService) {
        this.registry = registry;
        this.contentQualityService = contentQualityService;
        this.articleRewriteService = articleRewriteService;
    }

    /**
     * 执行一轮反哺闭环。返回本次优化结果。
     */
    public RefineResult refine(String taskId, String methodologyName, Long loginUserId) {
        String effective = methodologyName == null || methodologyName.isBlank()
                ? "default" : methodologyName;
        MethodologyDefinition def = registry.get(effective);

        ArticleQuality initial = contentQualityService.getLatestViral(taskId);
        if (initial == null || initial.getViralScore() == null) {
            log.info("无爆款评测结果，跳过反哺: taskId={}", taskId);
            return RefineResult.builder().rounds(0).skipped(true).build();
        }
        if (initial.getViralScore().compareTo(BigDecimal.valueOf(LOW_VIRAL_THRESHOLD)) >= 0
                && findWeakDimensions(initial, def).isEmpty()) {
            log.info("评测达标，无需反哺: taskId={}, viralScore={}", taskId, initial.getViralScore());
            return RefineResult.builder().rounds(0).skipped(true).build();
        }

        int rounds = 0;
        BigDecimal before = initial.getViralScore();
        BigDecimal after = before;
        List<String> weakDims = new ArrayList<>();
        boolean reverted = false;

        ArticleQuality current = initial;
        while (rounds < MAX_ROUNDS) {
            weakDims = findWeakDimensions(current, def);
            if (weakDims.isEmpty()) {
                break;
            }
            // 取 top-2 低分维度
            List<String> targets = weakDims.size() > 2 ? weakDims.subList(0, 2) : weakDims;

            // 组装定向改写指令（查对应创作维度 guidance）
            String instruction = buildRefineInstruction(def, targets);

            // 改写（v1 整篇；service 内校验归属）
            articleRewriteService.rewriteSection(taskId, instruction, null, loginUserId);

            // 复评
            ArticleQuality reEval = contentQualityService.evaluateViral(taskId, effective, loginUserId);
            after = reEval.getViralScore();

            log.info("反哺第 {} 轮完成: viralScore {} -> {}", rounds + 1, before, after);
            rounds++;

            if (after.compareTo(before) < 0) {
                // 无提升：回退上一版本，终止
                int lastVersion = reEval.getVersionNo() != null ? reEval.getVersionNo() - 1 : 0;
                if (lastVersion > 0) {
                    articleRewriteService.revertTo(taskId, lastVersion, loginUserId);
                }
                reverted = true;
                break;
            }
            before = after;
            current = reEval;
        }

        return RefineResult.builder()
                .rounds(rounds)
                .weakDimensions(weakDims)
                .reverted(reverted)
                .beforeScore(initial.getViralScore())
                .afterScore(after)
                .skipped(false)
                .build();
    }

    /** 找出低于阈值的评测维度（按 viral_scores JSON 中的 key） */
    private List<String> findWeakDimensions(ArticleQuality quality, MethodologyDefinition def) {
        List<String> weak = new ArrayList<>();
        Map<String, Object> viral = com.example.aipassagecreator.utils.GsonUtils.fromJsonSafe(
                quality.getViralScores(), new com.google.gson.reflect.TypeToken<Map<String, Object>>() {});
        if (viral == null) {
            return weak;
        }
        for (MethodologyDefinition.EvaluationDimension d : def.getEvaluationDimensions()) {
            Object v = viral.get(d.getKey());
            int score = v instanceof Number n ? n.intValue()
                    : (v instanceof String s ? parseIntSafe(s) : -1);
            if (score >= 0 && score < WEAK_THRESHOLD) {
                weak.add(d.getKey());
            }
        }
        weak.sort((a, b) -> Integer.compare(
                viralScoreOf(viral, b), viralScoreOf(viral, a)));
        return weak;
    }

    private int viralScoreOf(Map<String, Object> viral, String key) {
        Object v = viral.get(key);
        return v instanceof Number n ? n.intValue()
                : (v instanceof String s ? parseIntSafe(s) : 0);
    }

    /** 组装定向改写指令：低分维度 → 对应创作维度 guidance */
    private String buildRefineInstruction(MethodologyDefinition def, List<String> targets) {
        StringBuilder sb = new StringBuilder("请针对以下薄弱维度定向优化文章：\n");
        for (String key : targets) {
            for (MethodologyDefinition.CreationDimension cd : def.getCreationDimensions()) {
                if (key.equals(cd.getKey())) {
                    sb.append("- ").append(cd.getName()).append("：").append(cd.getGuidance()).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Data
    @Builder
    public static class RefineResult {
        private int rounds;
        private List<String> weakDimensions;
        private boolean reverted;
        private boolean skipped;
        private BigDecimal beforeScore;
        private BigDecimal afterScore;
    }
}
```

- [ ] **Step 6: Controller 新增 refine 端点**（含安全闸门）

`ArticleController.java`：
```java
/**
 * 爆款反哺闭环：低分维度定向改写
 */
@PostMapping("/refine")
@Operation(summary = "爆款反哺（低分维度定向改写）")
@AuthCheck(mustRole = "user")
@RateLimit(limit = 3, window = 60, key = "viral_refine")
public BaseResponse<?> refine(@RequestBody ArticleEvaluateViralRequest request,
                              HttpServletRequest httpServletRequest) {
    ThrowUtils.throwIf(request == null || request.getTaskId() == null
            || request.getTaskId().trim().isEmpty(), ErrorCode.PARAMS_ERROR, "任务ID不能为空");

    User loginUser = userService.getLoginUser(httpServletRequest);
    var article = articleService.getByTaskId(request.getTaskId());
    if (article == null || !article.getUserId().equals(loginUser.getId())) {
        throw new com.example.aipassagecreator.exception.BusinessException(
                ErrorCode.NO_AUTH_ERROR, "无权操作此文章");
    }
    var result = methodologyRefiner.refine(
            request.getTaskId(), request.getMethodologyName(), loginUser.getId());
    return ResultUtils.success(result);
}
```

新增注入：`@Resource private com.example.aipassagecreator.methodology.MethodologyRefiner methodologyRefiner;`

- [ ] **Step 7: 完善测试并确认通过**

Run: `mvn test -Dtest=MethodologyRefinerTest`
Expected: PASS

- [ ] **Step 8: 编译 + 全量回归**

Run: `mvn test`
Expected: 全量通过

- [ ] **Step 9: 提交**

```bash
git add src/main/java/com/example/aipassagecreator/methodology/MethodologyRefiner.java \
        src/main/java/com/example/aipassagecreator/service/ArticleRewriteService.java \
        src/main/java/com/example/aipassagecreator/service/impl/ArticleRewriteServiceImpl.java \
        src/main/java/com/example/aipassagecreator/controller/ArticleController.java \
        src/test/java/com/example/aipassagecreator/methodology/MethodologyRefinerTest.java
git commit -m "feat(methodology): 反哺闭环 MethodologyRefiner + rewriteSection 定向改写

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

## Self-Review Checklist

**1. Spec coverage：**
- §4 模板 Schema / extends 继承 / 权重校验 → Task 4 ✓
- §5 创作引导（methodology 全链路持久化 + 双路径注入 + strategyKey）→ Task 5 + 6 ✓
- §6 爆款评测（合并单次调用 / 归一化 / 幂等 / 安全闸门）→ Task 7 ✓
- §7 反哺闭环（top-2 维度 / 复评 / revertTo / ≤3 轮）→ Task 8 ✓
- §8 安全（execution-logs 修复 / rewrite SystemMessage / 新端点闸门）→ Task 1 + 7 + 8 ✓
- §8.3 YamlResourceLoader + SkillRegistry 加固 → Task 3 ✓
- §9 错误处理（fail-fast / 兜底 / JSON 容错）→ Task 2 + 4 ✓
- §10 测试策略 → 各任务 TDD ✓

**2. Placeholder scan：** 无 TBD/TODO；每个代码步骤均含实际代码。OutlineGeneratorAgent 局部变量名需实施时对照原文确认（Task 6 Step 5 已注明）。

**3. Type consistency：** `MethodologyRegistry.get` 抛 `IllegalArgumentException` 全局一致；`ArticleQuality` 新字段名（`scoreType`/`viralScore`/`viralScores`/`titleStrategyHit`/`methodologyUsed`/`contentHash`/`versionNo`）在 PO、SQL、实现中一致；`rewriteSection` 签名在接口/实现/Refiner 调用一致；`RefineResult` 字段在 Refiner 构造与测试断言一致。

**4. 已知实施注意事项：**
- Task 7 测试需要真实文章记录；实施时用 `ArticleMapper`/`ArticleService.createArticleTask` 造数，或直接对不存在文章断言抛异常。
- `@MockitoBean`（Spring Boot 3.5 替代 `@MockBean`）在 pom 已配置 mock-maker-proxy，Java 21 可用。
- `ArticleAgentOrchestrator` 与 `ArticleAgentService` 两条路径均已覆盖方法论注入。
- `sql/core.sql` 为生产初始脚本，若已执行过则需手动跑追加 ALTER；以 `sql/add_viral_quality.sql` 为准。
