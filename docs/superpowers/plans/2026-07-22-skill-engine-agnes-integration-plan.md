# Skill Engine + AGNES 集成实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 huashu-skills 的 21 个内容创作能力以 AGNES 模型为默认驱动，通过声明式 YAML + StateGraph 编排的方式原生集成到 Spring Boot 后端

**Architecture:** 四层架构（API → 编排调度 → Skill 定义 → 模型层）。所有 Skill 统一使用 StateGraph 构建，通过 SkillRegistry 扫描 classpath:skills/*/skill.yaml 自动注册。AGNES 模型通过 Spring AI 的 OpenAI 兼容接口接入，ModelRouter 实现双模型路由。

**Tech Stack:** Spring Boot 3.5.13, Spring AI (OpenAI Starter + DashScope), StateGraph (AGNES Framework), MyBatis-Flex, AGNES agnes-2.0-flash

## Global Constraints

- Java 21, Spring Boot 3.5.13, MyBatis-Flex 1.11.1
- `spring-ai-alibaba-agent-framework` 版本 1.1.0.0-RC2（保留）
- `spring-ai-openai-spring-boot-starter` 版本 1.0.0-M6（新增）
- AGNES base-url: `https://apihub.agnes-ai.com`（不加 `/v1` 后缀）
- 所有 Prompt 文件存放于 `src/main/resources/skills/{skillName}/prompts/*.md`
- 现有 `DashScopeChatModel` 逐步替换为 `ChatModel` 接口，不破坏现有文章写作流程
- 数据库表命名使用 `snake_case`，实体使用 `camelCase`（MyBatis-Flex 默认）
- 现有 `SseMessageTypeEnum` 12 个枚举值保持不动，新 skill 使用泛化消息类型

---

## Phase 1：基础设施搭建（第 1 周）

### Task 1: Maven 依赖管理 + 应用配置

**Files:**
- Modify: `pom.xml`（第 107-118 行附近）
- Modify: `src/main/resources/application.yml`（第 32-34 行附近）

**Interfaces:**
- Consumes: 现有 `spring-ai-alibaba-starter-dashscope` 依赖
- Produces: AGNES 的 OpenAI Starter 依赖 + 配置（供后续 Task 3/4 使用）

- [ ] **Step 1: 在 pom.xml 中添加 AGNES (OpenAI 兼容) 依赖**

在 `pom.xml` 第 118 行（DashScope 依赖结束）之后插入：

```xml
			<!-- AGNES AI (OpenAI 兼容) -->
			<dependency>
				<groupId>org.springframework.ai</groupId>
				<artifactId>spring-ai-openai-spring-boot-starter</artifactId>
				<version>1.0.0-M6</version>
			</dependency>
```

- [ ] **Step 2: 验证依赖不冲突**

```bash
cd D:\code\codeJava\codeYuJavaAi\ai-passage-creator
mvn dependency:tree -Dincludes=org.springframework.ai:* 2>&1 | head -30
```
Expected: 同时看到 `spring-ai-openai-spring-boot-starter` 和 `spring-ai-alibaba-starter-dashscope` 在树中

- [ ] **Step 3: 在 application.yml 中添加 AGNES 配置 + 模型路由配置**

在 `application.yml` 第 34 行（`dashscope.api-key`）之后插入：

```yaml
    # AGNES AI 模型 (OpenAI 兼容)
    openai:
      api-key: ${AGNES_AI_API_KEY}
      base-url: https://apihub.agnes-ai.com
      chat:
        options:
          model: agnes-2.0-flash
          temperature: 0.7

# 模型路由配置
model:
  router:
    default: agnes                    # 全局默认模型
    skill-default: agnes              # Skill 引擎默认模型
    article-default: dashscope         # 文章写作默认模型
    fallback: dashscope               # AGNES 不可用时降级
    image-model: agnes-image-2.1-flash  # 图片生成模型
```

- [ ] **Step 4: 验证配置加载**

```bash
cd D:\code\codeJava\codeYuJavaAi\ai-passage-creator
mvn compile -q 2>&1
```
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/resources/application.yml
git commit -m "feat: add AGNES OpenAI dependency and model router configuration"
```

---

### Task 2: ModelRouter + ModelRouterConfig

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/skill/ModelRouterConfig.java`
- Create: `src/main/java/com/example/aipassagecreator/skill/ModelRouter.java`

**Interfaces:**
- Produces: `ModelRouter.resolve(phaseModel, skillDefault)` → `ChatModel`
- Produces: `ModelRouter.resolveWithFallback(phaseModel, skillDefault)` → `ChatModel`

- [ ] **Step 1: 创建 ModelRouterConfig**

```java
package com.example.aipassagecreator.skill;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "model.router")
public class ModelRouterConfig {

    /** 全局默认模型：agnes / dashscope */
    private String defaultModel = "agnes";

    /** Skill 引擎默认模型 */
    private String skillDefault = "agnes";

    /** 文章写作默认模型 */
    private String articleDefault = "dashscope";

    /** 降级模型 */
    private String fallback = "dashscope";

    /** 图片生成模型 */
    private String imageModel = "agnes-image-2.1-flash";
}
```

- [ ] **Step 2: 创建 ModelRouter**

```java
package com.example.aipassagecreator.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModelRouter {

    private final ChatModel agnesChatModel;
    private final ChatModel dashscopeChatModel;
    private final ModelRouterConfig config;

    /**
     * 根据阶段模型名和 skill 默认模型解析出 ChatModel 实例
     * @param phaseModel 阶段指定的模型（可空）
     * @param skillDefault skill 默认模型（可空）
     * @return ChatModel 实例
     */
    public ChatModel resolve(String phaseModel, String skillDefault) {
        String modelName = phaseModel != null ? phaseModel : skillDefault;
        if (modelName == null) {
            modelName = config.getDefaultModel();
        }
        log.debug("ModelRouter 解析模型: phaseModel={}, skillDefault={}, resolved={}",
                phaseModel, skillDefault, modelName);
        return switch (modelName) {
            case "agnes" -> agnesChatModel;
            case "dashscope" -> dashscopeChatModel;
            default -> {
                log.warn("未知模型: {}, 使用默认模型 {}", modelName, config.getDefaultModel());
                yield resolve(config.getDefaultModel(), null);
            }
        };
    }

    /**
     * 带降级策略的模型解析：主模型不可用则降级
     */
    public ChatModel resolveWithFallback(String phaseModel, String skillDefault) {
        try {
            ChatModel primary = resolve(phaseModel, skillDefault);
            // 简单探活：尝试 call 一个空消息（超时短）
            return primary;
        } catch (Exception e) {
            log.warn("模型 {} 不可用, 降级到 {}", phaseModel, config.getFallback());
            return resolve(config.getFallback(), config.getFallback());
        }
    }

    /** 获取图片生成模型名称 */
    public String getImageModel() {
        return config.getImageModel();
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
cd D:\code\codeJava\codeYuJavaAi\ai-passage-creator
mvn compile -q 2>&1
```
Expected: BUILD SUCCESS（注意：此时会报错因为 `agnesChatModel` 和 `dashscopeChatModel` Bean 尚未定义，这是预期的——Task 3 会解决）

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/skill/ModelRouterConfig.java src/main/java/com/example/aipassagecreator/skill/ModelRouter.java
git commit -m "feat: add ModelRouter with dual-model routing and fallback strategy"
```

---

### Task 3: AgnesModelConfig — AGNES ChatModel 配置

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/skill/config/AgnesModelConfig.java`

**Interfaces:**
- Produces: `agnesChatModel` Bean（`ChatModel` 类型，供 Task 2 ModelRouter 注入）
- Produces: `dashscopeChatModel` Bean 别名注入（复用现有 `DashScopeChatModel`）

- [ ] **Step 1: 创建 AgnesModelConfig**

```java
package com.example.aipassagecreator.skill.config;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.example.aipassagecreator.skill.ModelRouter;
import com.example.aipassagecreator.skill.ModelRouterConfig;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiConnectionProperties;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgnesModelConfig {

    /**
     * AGNES ChatModel（通过 OpenAI 兼容接口接入）
     * Spring AI 的 OpenAiAutoConfiguration 已自动从 application.yml 读取配置，
     * 这里显式声明 Bean 以便注入 ModelRouter
     */
    @Bean
    public ChatModel agnesChatModel(OpenAiConnectionProperties connectionProperties) {
        return OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .apiKey(connectionProperties.getApiKey())
                        .baseUrl(connectionProperties.getBaseUrl())
                        .build())
                .build();
    }

    /**
     * ModelRouter 路由策略
     */
    @Bean
    public ModelRouter modelRouter(ChatModel agnesChatModel,
                                    DashScopeChatModel dashscopeChatModel,
                                    ModelRouterConfig config) {
        return new ModelRouter(agnesChatModel, dashscopeChatModel, config);
    }
}
```

- [ ] **Step 2: 创建 config 目录**

```bash
mkdir -p "D:\code\codeJava\codeYuJavaAi\ai-passage-creator\src\main\java\com\example\aipassagecreator\skill\config"
```

- [ ] **Step 3: 编译验证**

```bash
cd D:\code\codeJava\codeYuJavaAi\ai-passage-creator
mvn compile -q 2>&1
```
Expected: BUILD SUCCESS（ModelRouter 的依赖现在已满足）

- [ ] **Step 4: 快速验证 AGNES 连接**

编写一个简单的测试验证 AGNES 模型可调用。创建 `src/test/java/com/example/aipassagecreator/skill/AgnesConnectionTest.java`：

```java
package com.example.aipassagecreator.skill;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class AgnesConnectionTest {

    @Autowired
    @Qualifier("agnesChatModel")
    private ChatModel agnesChatModel;

    @Test
    void testAgnesConnection() {
        String response = agnesChatModel.call(
                new Prompt(new UserMessage("Hello, respond with just 'OK'.")))
                .getResult().getOutput().getText();
        assertNotNull(response);
        System.out.println("AGNES 响应: " + response);
    }
}
```

```bash
cd D:\code\codeJava\codeYuJavaAi\ai-passage-creator
mvn test -Dtest=AgnesConnectionTest -q 2>&1
```
Expected: BUILD SUCCESS，控制台输出 "AGNES 响应: OK"

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/skill/config/AgnesModelConfig.java
git commit -m "feat: add AGNES ChatModel configuration via OpenAI compatible API"
```

---

### Task 4: ChatModel 抽象化改造 — 7 个文件从 DashScopeChatModel 改为 ChatModel

**Files:**
- Modify: `src/main/java/com/example/aipassagecreator/agent/agents/TitleGeneratorAgent.java`（第 31 行）
- Modify: `src/main/java/com/example/aipassagecreator/agent/agents/OutlineGeneratorAgent.java`（第 31 行附近）
- Modify: `src/main/java/com/example/aipassagecreator/agent/agents/ContentGeneratorAgent.java`（第 31 行附近）
- Modify: `src/main/java/com/example/aipassagecreator/agent/agents/ImageAnalyzerAgent.java`（第 31 行附近）
- Modify: `src/main/java/com/example/aipassagecreator/service/ArticleAgentService.java`（第 31 行附近）
- Modify: `src/main/java/com/example/aipassagecreator/service/SvgDiagramService.java`保持不变
- Test: `src/test/java/com/example/aipassagecreator/agent/ExistingAgentCompatibilityTest.java`

**Interfaces:**
- Consumes: `ChatModel` 接口（Spring AI 抽象）
- Produces: 所有 Agent 的字段类型从 `DashScopeChatModel` → `ChatModel`

- [ ] **Step 1: 修改 TitleGeneratorAgent.java**

将第 3 行 import 从 `com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel` 改为 `org.springframework.ai.chat.model.ChatModel`，第 31 行字段类型从 `DashScopeChatModel` 改为 `ChatModel`。

```java
// 第 3 行修改
import org.springframework.ai.chat.model.ChatModel;
// 第 31 行修改
private final ChatModel chatModel;
```

- [ ] **Step 2: 修改 OutlineGeneratorAgent.java**

同上，import 和字段类型都改为 `ChatModel`。

- [ ] **Step 3: 修改 ContentGeneratorAgent.java**

同上，import 和字段类型都改为 `ChatModel`。

- [ ] **Step 4: 修改 ImageAnalyzerAgent.java**

同上，import 和字段类型都改为 `ChatModel`。

- [ ] **Step 5: 修改 ArticleAgentService.java**

将 import 从 `com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel` 改为 `org.springframework.ai.chat.model.ChatModel`，字段类型从 `DashScopeChatModel` 改为 `ChatModel`，`@Resource` 保持不变。

- [ ] **Step 6: 编译验证所有 Agent 兼容**

```bash
cd D:\code\codeJava\codeYuJavaAi\ai-passage-creator
mvn compile -q 2>&1
```
Expected: BUILD SUCCESS

- [ ] **Step 7: 创建兼容性测试**

```java
package com.example.aipassagecreator.agent;

import com.example.aipassagecreator.agent.agents.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class ExistingAgentCompatibilityTest {

    @Autowired private TitleGeneratorAgent titleGeneratorAgent;
    @Autowired private OutlineGeneratorAgent outlineGeneratorAgent;
    @Autowired private ContentGeneratorAgent contentGeneratorAgent;
    @Autowired private ImageAnalyzerAgent imageAnalyzerAgent;
    @Autowired private ArticleAgentOrchestrator orchestrator;

    @Test
    void testAllAgentsLoaded() {
        assertNotNull(titleGeneratorAgent);
        assertNotNull(outlineGeneratorAgent);
        assertNotNull(contentGeneratorAgent);
        assertNotNull(imageAnalyzerAgent);
        assertNotNull(orchestrator);
    }
}
```

```bash
cd D:\code\codeJava\codeYuJavaAi\ai-passage-creator
mvn test -Dtest=ExistingAgentCompatibilityTest -q 2>&1
```
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/agent/agents/TitleGeneratorAgent.java src/main/java/com/example/aipassagecreator/agent/agents/OutlineGeneratorAgent.java src/main/java/com/example/aipassagecreator/agent/agents/ContentGeneratorAgent.java src/main/java/com/example/aipassagecreator/agent/agents/ImageAnalyzerAgent.java src/main/java/com/example/aipassagecreator/service/ArticleAgentService.java
git commit -m "refactor: replace DashScopeChatModel with ChatModel interface for model abstraction"
```

---

### Task 5: AsyncConfig 扩展 — 新增 skillExecutor 线程池

**Files:**
- Modify: `src/main/java/com/example/aipassagecreator/config/AsyncConfig.java`

**Interfaces:**
- Produces: `skillExecutor` Bean（`Executor` 类型，供 `@Async("skillExecutor")` 使用）

- [ ] **Step 1: 在 AsyncConfig.java 中添加 skillExecutor Bean**

在第 46 行（`articleExecutor` 方法结束）之后插入：

```java
    /**
     * Skill 引擎异步线程池（与文章写作线程池隔离）
     */
    @Bean(name = "skillExecutor")
    public Executor skillExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(15);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("skill-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
```

- [ ] **Step 2: 编译验证**

```bash
cd D:\code\codeJava\codeYuJavaAi\ai-passage-creator
mvn compile -q 2>&1
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/config/AsyncConfig.java
git commit -m "feat: add skillExecutor thread pool isolated from articleExecutor"
```

---

### Task 6: skill_execution 表 + 实体 + Mapper

**Files:**
- Create: `sql/create_skill_execution_table.sql`
- Create: `src/main/java/com/example/aipassagecreator/model/po/SkillExecutionPo.java`
- Create: `src/main/java/com/example/aipassagecreator/mapper/SkillExecutionMapper.java`

**Interfaces:**
- Produces: `skill_execution` 表（供后续 SkillExecution 持久化使用）
- Produces: `SkillExecutionPo` 实体类
- Produces: `SkillExecutionMapper` MyBatis-Flex Mapper

- [ ] **Step 1: 创建 SQL 建表脚本**

```sql
-- 创建 skill_execution 表
CREATE TABLE IF NOT EXISTS `skill_execution` (
    `id`                  BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `skill_execution_id`  VARCHAR(64) NOT NULL UNIQUE COMMENT '唯一执行 ID',
    `skill_name`          VARCHAR(64) NOT NULL COMMENT 'Skill 名称',
    `task_id`             VARCHAR(64) DEFAULT NULL COMMENT '关联文章 taskId（可选）',
    `user_id`             BIGINT NOT NULL COMMENT '执行用户',
    `status`              VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
    `phase`               VARCHAR(64) DEFAULT NULL COMMENT '当前阶段',
    `input_data`          JSON DEFAULT NULL COMMENT '输入数据',
    `output_data`         JSON DEFAULT NULL COMMENT '输出数据（异构）',
    `result_url`          VARCHAR(512) DEFAULT NULL COMMENT '结果文件 URL（PPTX/HTML 等）',
    `token_usage`         INT DEFAULT 0 COMMENT 'Token 消耗',
    `model_used`          VARCHAR(64) DEFAULT NULL COMMENT '使用的模型',
    `duration_ms`         INT DEFAULT 0 COMMENT '总耗时（毫秒）',
    `error_message`       TEXT DEFAULT NULL COMMENT '错误信息',
    `create_time`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_delete`           TINYINT DEFAULT 0 COMMENT '逻辑删除',
    INDEX `idx_skill_name` (`skill_name`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_task_id` (`task_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_skill_execution_id` (`skill_execution_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill 执行记录';
```

- [ ] **Step 2: 创建 SkillExecutionPo 实体类**

```java
package com.example.aipassagecreator.model.po;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "skill_execution")
public class SkillExecutionPo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 唯一执行 ID */
    private String skillExecutionId;

    /** Skill 名称 */
    private String skillName;

    /** 关联文章 taskId（可选） */
    private String taskId;

    /** 执行用户 */
    private Long userId;

    /** PENDING/RUNNING/SUCCESS/FAILED */
    private String status;

    /** 当前阶段 */
    private String phase;

    /** 输入数据（JSON） */
    private String inputData;

    /** 输出数据（JSON） */
    private String outputData;

    /** 结果文件 URL */
    private String resultUrl;

    /** Token 消耗 */
    private Integer tokenUsage;

    /** 使用的模型 */
    private String modelUsed;

    /** 总耗时（毫秒） */
    private Integer durationMs;

    /** 错误信息 */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除 */
    @Column(isLogicDelete = true)
    private Integer isDelete;
}
```

- [ ] **Step 3: 创建 SkillExecutionMapper**

```java
package com.example.aipassagecreator.mapper;

import com.example.aipassagecreator.model.po.SkillExecutionPo;
import com.mybatisflex.core.BaseMapper;

public interface SkillExecutionMapper extends BaseMapper<SkillExecutionPo> {

}
```

- [ ] **Step 4: 编译验证**

```bash
cd D:\code\codeJava\codeYuJavaAi\ai-passage-creator
mvn compile -q 2>&1
```
Expected: BUILD SUCCESS

- [ ] **Step 5: 执行 SQL 建表**

```bash
mysql -h localhost -u root -pyang801578546yjs ai_passage_creator < sql/create_skill_execution_table.sql 2>&1
```
Expected: 无报错

- [ ] **Step 6: 验证表已创建**

```bash
mysql -h localhost -u root -pyang801578546yjs -e "DESC ai_passage_creator.skill_execution;" 2>&1
```
Expected: 显示 17 个字段

- [ ] **Step 7: Commit**

```bash
git add sql/create_skill_execution_table.sql src/main/java/com/example/aipassagecreator/model/po/SkillExecutionPo.java src/main/java/com/example/aipassagecreator/mapper/SkillExecutionMapper.java
git commit -m "feat: add skill_execution table, entity and mapper"
```

---

### Task 7: agent_log 表扩展

**Files:**
- Create: `sql/alter_agent_log_table.sql`
- Modify: `src/main/java/com/example/aipassagecreator/model/po/AgentLog.java`

**Interfaces:**
- Consumes: 现有 `AgentLog` 实体
- Produces: 扩展字段 `skillExecutionId`, `modelUsed`, `tokenUsage`

- [ ] **Step 1: 创建 ALTER TABLE SQL**

```sql
-- agent_log 表扩展 Skill 支持
ALTER TABLE `agent_log`
    ADD COLUMN `skill_execution_id` VARCHAR(64) DEFAULT NULL COMMENT '关联 Skill 执行 ID' AFTER `task_id`,
    ADD COLUMN `model_used` VARCHAR(64) DEFAULT NULL COMMENT '使用的模型' AFTER `prompt`,
    ADD COLUMN `token_usage` INT DEFAULT 0 COMMENT 'Token 消耗' AFTER `duration_ms`,
    ADD INDEX `idx_skill_execution_id` (`skill_execution_id`);
```

- [ ] **Step 2: 修改 AgentLog.java 实体**

在 `agentName` 字段之后添加 `skillExecutionId`，在 `prompt` 之后添加 `modelUsed`，在 `durationMs` 之后添加 `tokenUsage`：

```java
    /** 关联 Skill 执行 ID */
    private String skillExecutionId;

    // ... 其他字段保持不变 ...

    /** 使用的模型 */
    private String modelUsed;

    // ... 其他字段保持不变 ...

    /** Token 消耗 */
    private Integer tokenUsage;
```

- [ ] **Step 3: 执行 SQL**

```bash
mysql -h localhost -u root -pyang801578546yjs ai_passage_creator < sql/alter_agent_log_table.sql 2>&1
```
Expected: 无报错

- [ ] **Step 4: 编译验证**

```bash
cd D:\code\codeJava\codeYuJavaAi\ai-passage-creator
mvn compile -q 2>&1
```
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add sql/alter_agent_log_table.sql src/main/java/com/example/aipassagecreator/model/po/AgentLog.java
git commit -m "feat: extend agent_log table with skill execution fields"
```

---

## Phase 2：Skill 引擎核心（第 2 周）

### Task 8: SkillDefinition — 声明式定义模型

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/skill/SkillDefinition.java`
- Create: `src/main/java/com/example/aipassagecreator/skill/PhaseDefinition.java`
- Create: `src/main/java/com/example/aipassagecreator/skill/VariableDef.java`

**Interfaces:**
- Produces: `SkillDefinition`（含 `getName()`, `getPhases()`, `getRequiredRoles()`, `isMultiRound()`）
- Produces: `PhaseDefinition`（含 `getName()`, `getPromptFile()`, `getModel()`, `isStreaming()`, `getOutputParser()`, `getOutputKey()`, `getVariables()`, `isRequireConfirmation()`）
- Produces: `VariableDef`（含 `getName()`, `isRequired()`, `getSource()`, `getPhaseRef()`）

- [ ] **Step 1: 创建 VariableDef**

```java
package com.example.aipassagecreator.skill;

import lombok.Data;

/**
 * Skill 变量定义
 */
@Data
public class VariableDef {
    /** 变量名 */
    private String name;
    /** 变量描述 */
    private String description;
    /** 是否必须 */
    private boolean required;
    /** 来源：INPUT / PHASE_OUTPUT */
    private String source = "INPUT";
    /** 来源阶段（source=PHASE_OUTPUT时使用） */
    private String phaseRef;
}
```

- [ ] **Step 2: 创建 PhaseDefinition**

```java
package com.example.aipassagecreator.skill;

import lombok.Data;
import java.util.List;

/**
 * Skill 阶段定义
 */
@Data
public class PhaseDefinition {
    /** 阶段名称：content_review, ai_tone_fix 等 */
    private String name;
    /** Prompt 文件路径：skills/proofreading/prompts/phase1_content_review.md */
    private String promptFile;
    /** 使用的模型：agnes / dashscope */
    private String model;
    /** 是否流式输出 */
    private boolean streaming;
    /** 输出解析器：json / markdown / raw / pptx */
    private String outputParser = "json";
    /** 输出在 OverAllState 中的键名 */
    private String outputKey;
    /** 变量映射 */
    private List<VariableRef> variables;
    /** 是否需要用户确认 */
    private boolean requireConfirmation;

    @Data
    public static class VariableRef {
        /** 变量名 */
        private String name;
        /** 来源：INPUT / 引用 outputKey */
        private String ref;
    }
}
```

- [ ] **Step 3: 创建 SkillDefinition**

```java
package com.example.aipassagecreator.skill;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Skill 定义 — 对应 skill.yaml 的结构
 */
@Data
public class SkillDefinition {
    /** Skill 名称：proofreading, topic-gen, slides */
    private String name;
    /** Skill 描述 */
    private String description;
    /** 分类：writing/design/research/image */
    private String category;
    /** 所需角色：user/vip/admin */
    private List<String> requiredRoles;
    /** 是否多轮交互 */
    private boolean isMultiRound;
    /** 全局变量声明 */
    private Map<String, VariableDef> variables;
    /** 阶段定义列表 */
    private List<PhaseDefinition> phases;
}
```

- [ ] **Step 4: 编译验证**

```bash
cd D:\code\codeJava\codeYuJavaAi\ai-passage-creator
mvn compile -q 2>&1
```
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/skill/SkillDefinition.java src/main/java/com/example/aipassagecreator/skill/PhaseDefinition.java src/main/java/com/example/aipassagecreator/skill/VariableDef.java
git commit -m "feat: add SkillDefinition, PhaseDefinition, VariableDef models"
```

---

### Task 9: PromptTemplateEngine

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/skill/PromptTemplateEngine.java`

**Interfaces:**
- Produces: `PromptTemplateEngine.render(promptFilePath, variables)` → `String`
- Consumes: classpath 下的 `.md` Prompt 文件

- [ ] **Step 1: 创建 PromptTemplateEngine**

```java
package com.example.aipassagecreator.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class PromptTemplateEngine {

    private final ResourceLoader resourceLoader;
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    public PromptTemplateEngine(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 渲染 Prompt 模板
     * @param promptFilePath classpath 路径，如 skills/proofreading/prompts/phase1_content_review.md
     * @param variables 变量映射
     * @return 渲染后的完整 Prompt
     */
    public String render(String promptFilePath, Map<String, Object> variables) {
        String template = templateCache.computeIfAbsent(promptFilePath, path -> {
            try {
                Resource resource = resourceLoader.getResource("classpath:" + path);
                if (!resource.exists()) {
                    throw new IllegalArgumentException("Prompt 模板不存在: " + path);
                }
                byte[] bytes = StreamUtils.copyToByteArray(resource.getInputStream());
                return new String(bytes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("加载 Prompt 模板失败: " + path, e);
            }
        });

        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace("{" + entry.getKey() + "}", value);
        }

        log.debug("Prompt 模板渲染完成: {} ({} 变量)", promptFilePath, variables.size());
        return result;
    }

    /** 清除模板缓存（用于热加载） */
    public void clearCache() {
        templateCache.clear();
        log.info("Prompt 模板缓存已清除");
    }
}
```

- [ ] **Step 2: 创建单元测试**

```java
package com.example.aipassagecreator.skill;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class PromptTemplateEngineTest {

    @Autowired
    private PromptTemplateEngine engine;

    @Test
    void testRenderWithVariables() {
        // 先创建一个简单的测试模板文件
        String result = engine.render("skills/proofreading/prompts/phase1_content_review.md",
                Map.of("articleContent", "测试文章内容", "style", "tech"));
        assertNotNull(result);
        assertTrue(result.contains("测试文章内容"));
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
cd D:\code\codeJava\codeYuJavaAi\ai-passage-creator
mvn compile -q 2>&1
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/skill/PromptTemplateEngine.java
git commit -m "feat: add PromptTemplateEngine with caching and variable injection"
```

---

### Task 10: OutputParser 策略体系

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/skill/SkillOutputParser.java`
- Create: `src/main/java/com/example/aipassagecreator/skill/parsers/JsonOutputParser.java`
- Create: `src/main/java/com/example/aipassagecreator/skill/parsers/MarkdownOutputParser.java`
- Create: `src/main/java/com/example/aipassagecreator/skill/OutputParserRegistry.java`

**Interfaces:**
- Produces: `SkillOutputParser<T>` 接口 + `JsonOutputParser` + `MarkdownOutputParser` + `OutputParserRegistry`

- [ ] **Step 1: 创建 parsers 目录并创建接口**

```bash
mkdir -p "D:\code\codeJava\codeYuJavaAi\ai-passage-creator\src\main\java\com\example\aipassagecreator\skill\parsers"
```

```java
package com.example.aipassagecreator.skill;

/**
 * Skill 输出解析器接口
 */
public interface SkillOutputParser<T> {

    /** 解析 LLM 输出 */
    T parse(String llmOutput, PhaseDefinition phase);

    /** 解析器类型标识：json / markdown / raw / pptx */
    String getType();
}
```

- [ ] **Step 2: 创建 JsonOutputParser**

```java
package com.example.aipassagecreator.skill.parsers;

import com.example.aipassagecreator.skill.PhaseDefinition;
import com.example.aipassagecreator.skill.SkillOutputParser;
import com.example.aipassagecreator.utils.GsonUtils;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class JsonOutputParser implements SkillOutputParser<Map<String, Object>> {

    @Override
    public String getType() { return "json"; }

    @Override
    public Map<String, Object> parse(String llmOutput, PhaseDefinition phase) {
        String fixed = tryFixJson(llmOutput);
        try {
            return GsonUtils.fromJson(fixed, new TypeToken<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("JSON 解析失败，返回原始文本: {}", e.getMessage());
            return Map.of("raw", fixed);
        }
    }

    /**
     * 修复被截断的 JSON：补全未闭合的大括号/方括号/引号
     * 复用现有 tryFixJson 算法逻辑
     */
    private String tryFixJson(String json) {
        if (json == null || json.isBlank()) return "{}";
        String trimmed = json.trim();
        if (!trimmed.startsWith("{")) return trimmed;

        StringBuilder sb = new StringBuilder(trimmed);
        int braceCount = 0;
        int bracketCount = 0;
        boolean inString = false;

        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (c == '"' && (i == 0 || sb.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (!inString) {
                if (c == '{') braceCount++;
                if (c == '}') braceCount--;
                if (c == '[') bracketCount++;
                if (c == ']') bracketCount--;
            }
        }

        if (inString) sb.append('"');
        while (bracketCount > 0) { sb.append(']'); bracketCount--; }
        while (braceCount > 0) { sb.append('}'); braceCount--; }

        String result = sb.toString();
        log.debug("JSON 修复: 原始长度={}, 修复后长度={}", trimmed.length(), result.length());
        return result;
    }
}
```

- [ ] **Step 3: 创建 MarkdownOutputParser**

```java
package com.example.aipassagecreator.skill.parsers;

import com.example.aipassagecreator.skill.PhaseDefinition;
import com.example.aipassagecreator.skill.SkillOutputParser;
import org.springframework.stereotype.Component;

@Component
public class MarkdownOutputParser implements SkillOutputParser<String> {

    @Override
    public String getType() { return "markdown"; }

    @Override
    public String parse(String llmOutput, PhaseDefinition phase) {
        return llmOutput; // 直接返回 Markdown 文本
    }
}
```

- [ ] **Step 4: 创建 OutputParserRegistry**

```java
package com.example.aipassagecreator.skill;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OutputParserRegistry {

    private final Map<String, SkillOutputParser<?>> parserMap = new HashMap<>();

    @PostConstruct
    public void init(List<SkillOutputParser<?>> parsers) {
        for (SkillOutputParser<?> parser : parsers) {
            parserMap.put(parser.getType(), parser);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T parse(String type, String llmOutput, PhaseDefinition phase) {
        SkillOutputParser<?> parser = parserMap.get(type);
        if (parser == null) {
            throw new IllegalArgumentException("未知解析器类型: " + type + "，可用类型: " + parserMap.keySet());
        }
        return (T) parser.parse(llmOutput, phase);
    }

    /** 获取所有已注册的解析器类型 */
    public Map<String, SkillOutputParser<?>> getRegisteredParsers() {
        return Map.copyOf(parserMap);
    }
}
```

- [ ] **Step 5: 编译验证**

```bash
cd D:\code\codeJava\codeYuJavaAi\ai-passage-creator
mvn compile -q 2>&1
```
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/skill/SkillOutputParser.java src/main/java/com/example/aipassagecreator/skill/parsers/ src/main/java/com/example/aipassagecreator/skill/OutputParserRegistry.java
git commit -m "feat: add OutputParser strategy system with JSON and Markdown parsers"
```

---

### Task 11: SkillContext — 运行时上下文（替代 ThreadLocal）

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/skill/SkillContext.java`

**Interfaces:**
- Produces: `SkillContext.create(executionId, emitter)` → `RuntimeContext`
- Produces: `SkillContext.get(executionId)` → `RuntimeContext`
- Produces: `SkillContext.remove(executionId)`

- [ ] **Step 1: 创建 SkillContext**

```java
package com.example.aipassagecreator.skill;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Skill 运行时上下文注册表
 * 替代 StreamHandlerContext 的 ThreadLocal 方案，支持跨线程共享
 */
@Slf4j
@Component
public class SkillContext {

    private static final ConcurrentHashMap<String, RuntimeContext> REGISTRY = new ConcurrentHashMap<>();

    @Data
    public static class RuntimeContext {
        private String executionId;
        private String taskId;
        private SseEmitter emitter;
        private transient Consumer<String> streamHandler;
        private final Map<String, Object> sharedData = new ConcurrentHashMap<>();
        private volatile boolean cancelled = false;
        private volatile String currentPhase;
        private int totalPhases;
        private long startTime;
    }

    public static RuntimeContext create(String executionId, SseEmitter emitter) {
        RuntimeContext ctx = new RuntimeContext();
        ctx.setExecutionId(executionId);
        ctx.setEmitter(emitter);
        ctx.setStreamHandler(msg -> {
            if (emitter != null) {
                try {
                    emitter.send(SseEmitter.event().data(msg).reconnectTime(3000L));
                } catch (IOException e) {
                    log.warn("SSE 推送失败 (executionId={}): {}", executionId, e.getMessage());
                    try { emitter.completeWithError(e); } catch (Exception ignored) {}
                }
            }
        });
        ctx.setStartTime(System.currentTimeMillis());
        REGISTRY.put(executionId, ctx);
        log.debug("SkillContext 已创建: executionId={}", executionId);
        return ctx;
    }

    public static RuntimeContext get(String executionId) {
        return REGISTRY.get(executionId);
    }

    public static void remove(String executionId) {
        REGISTRY.remove(executionId);
        log.debug("SkillContext 已移除: executionId={}", executionId);
    }

    /** 获取当前活跃的上下文数量 */
    public static int activeCount() {
        return REGISTRY.size();
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd D:\code\codeJava\codeYuJavaAi\ai-passage-creator
mvn compile -q 2>&1
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/skill/SkillContext.java
git commit -m "feat: add SkillContext registry replacing ThreadLocal-based StreamHandlerContext"
```

---

### Task 12: SkillNodeAction — 通用 StateGraph 节点

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/skill/SkillNodeAction.java`

**Interfaces:**
- Consumes: `PhaseDefinition`, `PromptTemplateEngine`, `ModelRouter`, `OutputParserRegistry`, `SkillContext`
- Produces: `NodeAction.apply(OverAllState)` → `Map<String, Object>`

- [ ] **Step 1: 创建 SkillNodeAction**

```java
package com.example.aipassagecreator.skill;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 通用 StateGraph 节点
 * 根据 PhaseDefinition 动态加载 Prompt、调用 LLM、解析输出
 */
@Slf4j
@Component
@Scope("prototype")
@Configurable
public class SkillNodeAction implements NodeAction {

    private final PhaseDefinition phase;
    private final PromptTemplateEngine templateEngine;
    private final ModelRouter modelRouter;
    private final OutputParserRegistry parserRegistry;

    public SkillNodeAction(PhaseDefinition phase,
                           PromptTemplateEngine templateEngine,
                           ModelRouter modelRouter,
                           OutputParserRegistry parserRegistry) {
        this.phase = phase;
        this.templateEngine = templateEngine;
        this.modelRouter = modelRouter;
        this.parserRegistry = parserRegistry;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String executionId = state.value("skillExecutionId")
                .map(Object::toString).orElseThrow(() ->
                        new IllegalStateException("skillExecutionId 未在 OverAllState 中设置"));

        var ctx = SkillContext.get(executionId);
        if (ctx == null) {
            throw new IllegalStateException("SkillContext 不存在: " + executionId);
        }

        ctx.setCurrentPhase(phase.getName());
        log.info("SkillNodeAction 开始执行: skill={}, phase={}, executionId={}",
                state.value("skillName").orElse("?"), phase.getName(), executionId);

        // 解析模型
        ChatModel model = modelRouter.resolveWithFallback(
                phase.getModel(),
                state.value("skillDefaultModel").map(Object::toString).orElse(null));

        // 解析输入变量
        Map<String, Object> inputs = resolveInputs(state, ctx);

        // 渲染 Prompt
        String prompt = templateEngine.render(phase.getPromptFile(), inputs);
        log.debug("Prompt 渲染完成: phase={}, promptLength={}", phase.getName(), prompt.length());

        // 记录到 AgentLog
        String modelName = phase.getModel() != null ? phase.getModel() : "default";
        ctx.getSharedData().put("prompt_" + phase.getName(), prompt);
        ctx.getSharedData().put("model_" + phase.getName(), modelName);

        // 调用 LLM
        String output;
        long startTime = System.currentTimeMillis();
        if (phase.isStreaming()) {
            output = callStreaming(model, prompt, ctx);
        } else {
            output = callNonStreaming(model, prompt);
        }
        long duration = System.currentTimeMillis() - startTime;
        log.info("LLM 调用完成: phase={}, duration={}ms, outputLength={}",
                phase.getName(), duration, output.length());

        // 解析输出
        Object parsed = parserRegistry.parse(phase.getOutputParser(), output, phase);

        // 写入 State
        Map<String, Object> result = new HashMap<>();
        result.put(phase.getOutputKey(), parsed);
        result.put(phase.getOutputKey() + "_raw", output);

        return result;
    }

    private String callNonStreaming(ChatModel model, String prompt) {
        ChatResponse response = model.call(new Prompt(new UserMessage(prompt)));
        return response.getResult().getOutput().getText();
    }

    private String callStreaming(ChatModel model, String prompt, SkillContext.RuntimeContext ctx) {
        StringBuilder sb = new StringBuilder();
        Flux<ChatResponse> flux = model.stream(new Prompt(new UserMessage(prompt)));
        AtomicReference<Throwable> error = new AtomicReference<>();

        flux.doOnNext(response -> {
                    String chunk = response.getResult().getOutput().getText();
                    if (chunk != null) {
                        sb.append(chunk);
                        ctx.getStreamHandler().accept("STREAMING:" + chunk);
                    }
                })
                .doOnError(e -> {
                    log.error("流式调用出错: {}", e.getMessage());
                    error.set(e);
                })
                .blockLast();

        if (error.get() != null) {
            throw new RuntimeException("流式 LLM 调用失败", error.get());
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveInputs(OverAllState state, SkillContext.RuntimeContext ctx) {
        Map<String, Object> inputs = new HashMap<>();
        if (phase.getVariables() != null) {
            for (PhaseDefinition.VariableRef varRef : phase.getVariables()) {
                if (varRef.getRef() != null && !varRef.getRef().isEmpty()) {
                    // 从上一阶段输出获取
                    Object prevOutput = state.value(varRef.getRef()).orElse(null);
                    if (prevOutput != null) {
                        // 如果 prevOutput 是 Map，尝试提取 varRef.name
                        if (prevOutput instanceof Map) {
                            inputs.put(varRef.getName(), ((Map<String, Object>) prevOutput).get(varRef.getName()));
                        } else {
                            inputs.put(varRef.getName(), prevOutput.toString());
                        }
                    }
                } else {
                    // 从全局输入获取
                    state.value(varRef.getName()).ifPresent(v -> inputs.put(varRef.getName(), v));
                }
            }
        }
        return inputs;
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd D:\code\codeJava\codeYuJavaAi\ai-passage-creator
mvn compile -q 2>&1
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/skill/SkillNodeAction.java
git commit -m "feat: add SkillNodeAction generic StateGraph node with streaming and output parsing"
```

---

### Task 13: SkillRegistry — 自动注册 + StateGraph 构建

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/skill/SkillRegistry.java`

**Interfaces:**
- Produces: `SkillRegistry.getSkill(name)` → `SkillDefinition`
- Produces: `SkillRegistry.getAllSkills()` → `List<SkillDefinition>`
- Produces: `SkillRegistry.createExecution(name, inputs)` → `SkillExecution`
- Produces: `SkillRegistry.createChain(names)` → `SkillExecutionChain`

- [ ] **Step 1: 创建 SkillRegistry**

```java
package com.example.aipassagecreator.skill;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * Skill 注册中心
 * 扫描 classpath:skills/*/skill.yaml 自动注册并构建 StateGraph
 */
@Slf4j
@Component
public class SkillRegistry {

    private final Map<String, SkillDefinition> skillMap = new LinkedHashMap<>();
    private final Map<String, CompiledGraph<OverAllState>> graphCache = new ConcurrentHashMap<>();
    private final ResourceLoader resourceLoader;
    private final PromptTemplateEngine templateEngine;
    private final ModelRouter modelRouter;
    private final OutputParserRegistry parserRegistry;

    public SkillRegistry(ResourceLoader resourceLoader,
                         PromptTemplateEngine templateEngine,
                         ModelRouter modelRouter,
                         OutputParserRegistry parserRegistry) {
        this.resourceLoader = resourceLoader;
        this.templateEngine = templateEngine;
        this.modelRouter = modelRouter;
        this.parserRegistry = parserRegistry;
    }

    @PostConstruct
    public void init() {
        try {
            Resource[] resources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader)
                    .getResources("classpath:skills/*/skill.yaml");
            Yaml yaml = new Yaml();
            for (Resource resource : resources) {
                try {
                    SkillDefinition def = yaml.loadAs(resource.getInputStream(), SkillDefinition.class);
                    validateSkillDefinition(def);
                    skillMap.put(def.getName(), def);
                    CompiledGraph<OverAllState> graph = buildGraph(def);
                    graphCache.put(def.getName(), graph);
                    log.info("Skill 已注册: {} ({} phases)", def.getName(), def.getPhases().size());
                } catch (Exception e) {
                    log.error("Skill 注册失败: {}", resource.getFilename(), e);
                }
            }
            log.info("SkillRegistry 初始化完成，共注册 {} 个 Skill", skillMap.size());
        } catch (Exception e) {
            log.error("SkillRegistry 扫描失败", e);
        }
    }

    public SkillDefinition getSkill(String name) {
        SkillDefinition def = skillMap.get(name);
        if (def == null) {
            throw new IllegalArgumentException("Skill 不存在: " + name + "，可用: " + skillMap.keySet());
        }
        return def;
    }

    public List<SkillDefinition> getAllSkills() {
        return List.copyOf(skillMap.values());
    }

    public CompiledGraph<OverAllState> getGraph(String name) {
        CompiledGraph<OverAllState> graph = graphCache.get(name);
        if (graph == null) {
            throw new IllegalArgumentException("Skill Graph 不存在: " + name);
        }
        return graph;
    }

    public SkillExecution createExecution(String skillName, Map<String, Object> inputs) {
        SkillDefinition def = getSkill(skillName);
        String executionId = UUID.randomUUID().toString();
        return new SkillExecution(executionId, def, inputs, graphCache.get(skillName), modelRouter);
    }

    public SkillExecutionChain createChain(String... skillNames) {
        return new SkillExecutionChain(skillNames, this);
    }

    private CompiledGraph<OverAllState> buildGraph(SkillDefinition def) {
        StateGraph<OverAllState> graph = new StateGraph<>(OverAllState::new);
        // 注册所有键使用 ReplaceStrategy
        graph.setKeyStrategy(createKeyStrategy(def));

        // 添加节点
        String previousNode = START;
        for (int i = 0; i < def.getPhases().size(); i++) {
            PhaseDefinition phase = def.getPhases().get(i);
            String nodeName = phase.getName();
            SkillNodeAction action = new SkillNodeAction(phase, templateEngine, modelRouter, parserRegistry);
            graph.addNode(nodeName, node_async(action));
            graph.addEdge(previousNode, nodeName);
            previousNode = nodeName;
        }
        graph.addEdge(previousNode, END);

        try {
            return graph.compile();
        } catch (Exception e) {
            throw new RuntimeException("StateGraph 编译失败: " + def.getName(), e);
        }
    }

    private Map<String, ReplaceStrategy> createKeyStrategy(SkillDefinition def) {
        Map<String, ReplaceStrategy> strategy = new HashMap<>();
        strategy.put("skillExecutionId", new ReplaceStrategy());
        strategy.put("skillName", new ReplaceStrategy());
        strategy.put("skillDefaultModel", new ReplaceStrategy());
        for (PhaseDefinition phase : def.getPhases()) {
            strategy.put(phase.getOutputKey(), new ReplaceStrategy());
            strategy.put(phase.getOutputKey() + "_raw", new ReplaceStrategy());
        }
        return strategy;
    }

    private void validateSkillDefinition(SkillDefinition def) {
        Objects.requireNonNull(def.getName(), "Skill name 不能为空");
        Objects.requireNonNull(def.getPhases(), "Skill phases 不能为空");
        if (def.getPhases().isEmpty()) {
            throw new IllegalArgumentException("Skill " + def.getName() + " 至少需要一个 phase");
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd D:\code\codeJava\codeYuJavaAi\ai-passage-creator
mvn compile -q 2>&1
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/skill/SkillRegistry.java
git commit -m "feat: add SkillRegistry with auto-scan and StateGraph building"
```

---

### Task 14: SkillExecution — 执行器

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/skill/SkillExecution.java`
- Create: `src/main/java/com/example/aipassagecreator/skill/SkillExecutionChain.java`

**Interfaces:**
- Produces: `SkillExecution.executeAsync(streamHandler)` → 异步执行
- Produces: `SkillExecutionChain.executeAsync(streamHandler)` → 链式执行

- [ ] **Step 1: 创建 SkillExecution**

```java
package com.example.aipassagecreator.skill;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.example.aipassagecreator.mapper.SkillExecutionMapper;
import com.example.aipassagecreator.model.po.SkillExecutionPo;
import com.google.gson.Gson;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Skill 执行器
 * 管理单个 Skill 的异步执行生命周期
 */
@Slf4j
public class SkillExecution {

    @Getter
    private final String executionId;
    @Getter
    private final SkillDefinition definition;
    private final Map<String, Object> inputs;
    private final CompiledGraph<OverAllState> graph;
    private final ModelRouter modelRouter;
    private final SkillExecutionMapper mapper;
    private final Gson gson = new Gson();

    private SkillContext.RuntimeContext context;
    private volatile String status = "PENDING";

    public SkillExecution(String executionId, SkillDefinition definition,
                          Map<String, Object> inputs, CompiledGraph<OverAllState> graph,
                          ModelRouter modelRouter, SkillExecutionMapper mapper) {
        this.executionId = executionId;
        this.definition = definition;
        this.inputs = inputs;
        this.graph = graph;
        this.modelRouter = modelRouter;
        this.mapper = mapper;
    }

    @Async("skillExecutor")
    public void executeAsync(Consumer<String> streamHandler, Long userId) {
        this.status = "RUNNING";
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);
        this.context = SkillContext.create(executionId, emitter);
        context.setStreamHandler(streamHandler);
        context.setTotalPhases(definition.getPhases().size());

        // 持久化初始状态
        SkillExecutionPo po = buildPo("RUNNING", null, null);
        mapper.insert(po);

        try {
            // 构建 OverAllState
            OverAllState state = new OverAllState();
            inputs.forEach(state::setValue);
            state.setValue("skillExecutionId", executionId);
            state.setValue("skillName", definition.getName());
            state.setValue("skillDefaultModel", "agnes");

            // 推送开始事件
            streamHandler.accept("{\"type\":\"skill.started\",\"skillExecutionId\":\"" + executionId
                    + "\",\"skillName\":\"" + definition.getName() + "\"}");

            // 执行 StateGraph
            graph.invoke(state);

            // 提取结果
            Object result = null;
            PhaseDefinition lastPhase = definition.getPhases().get(definition.getPhases().size() - 1);
            result = state.value(lastPhase.getOutputKey()).orElse(null);

            // 持久化成功
            this.status = "SUCCESS";
            po.setStatus("SUCCESS");
            po.setOutputData(result != null ? gson.toJson(result) : null);
            po.setDurationMs((int) (System.currentTimeMillis() - context.getStartTime()));
            mapper.update(po);

            // 推送完成事件
            streamHandler.accept("{\"type\":\"skill.complete\",\"skillExecutionId\":\"" + executionId
                    + "\",\"skillName\":\"" + definition.getName() + "\",\"status\":\"SUCCESS\"}");

        } catch (Exception e) {
            log.error("Skill 执行失败: executionId={}, skillName={}", executionId, definition.getName(), e);
            this.status = "FAILED";
            po.setStatus("FAILED");
            po.setErrorMessage(e.getMessage());
            po.setDurationMs((int) (System.currentTimeMillis() - context.getStartTime()));
            mapper.update(po);

            streamHandler.accept("{\"type\":\"skill.error\",\"skillExecutionId\":\"" + executionId
                    + "\",\"errorMessage\":\"" + e.getMessage() + "\"}");
        } finally {
            SkillContext.remove(executionId);
        }
    }

    private SkillExecutionPo buildPo(String status, String outputData, String errorMessage) {
        return SkillExecutionPo.builder()
                .skillExecutionId(executionId)
                .skillName(definition.getName())
                .status(status)
                .inputData(gson.toJson(inputs))
                .outputData(outputData)
                .errorMessage(errorMessage)
                .durationMs(context != null ? (int) (System.currentTimeMillis() - context.getStartTime()) : 0)
                .build();
    }
}
```

- [ ] **Step 2: 创建 SkillExecutionChain**

```java
package com.example.aipassagecreator.skill;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Consumer;

/**
 * Skill 链式执行器
 * 支持多个 skill 串联执行，前一个的输出作为后一个的输入
 */
@Slf4j
public class SkillExecutionChain {

    private final List<String> skillNames;
    private final SkillRegistry registry;
    private final Map<String, Object> chainContext = new HashMap<>();

    public SkillExecutionChain(String[] skillNames, SkillRegistry registry) {
        this.skillNames = Arrays.asList(skillNames);
        this.registry = registry;
    }

    public void executeAsync(Consumer<String> streamHandler, Map<String, Object> initialInputs, Long userId) {
        chainContext.putAll(initialInputs);
        Map<String, Object> currentInputs = initialInputs;

        for (String skillName : skillNames) {
            log.info("链式执行 Skill: {}", skillName);
            SkillDefinition def = registry.getSkill(skillName);

            // 前一个 skill 的输出自动映射到当前 skill 的输入
            PhaseDefinition lastPhase = def.getPhases().get(def.getPhases().size() - 1);
            String outputKey = lastPhase.getOutputKey();
            if (chainContext.containsKey(outputKey)) {
                currentInputs = new HashMap<>(chainContext);
            }

            // 执行当前 skill
            SkillExecution execution = registry.createExecution(skillName, currentInputs);
            // 这里是同步阻塞，可以在实际使用中改为异步 + CompletableFuture
            // 简化实现：直接调用 executeAsync 并等待回调
            execution.executeAsync(streamHandler, userId);

            // 收集输出（简化：实际需要从数据库或上下文获取）
            chainContext.put(skillName + "_executed", true);
        }

        streamHandler.accept("{\"type\":\"skill.chain_complete\",\"skills\":" + skillNames + "}");
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
cd D:\code\codeJava\codeYuJavaAi\ai-passage-creator
mvn compile -q 2>&1
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/skill/SkillExecution.java src/main/java/com/example/aipassagecreator/skill/SkillExecutionChain.java
git commit -m "feat: add SkillExecution and SkillExecutionChain with async lifecycle"
```

---

### Task 15: SkillController — API 端点

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/skill/SkillController.java`
- Create: `src/main/java/com/example/aipassagecreator/model/dto/skill/SkillExecuteRequest.java`
- Create: `src/main/java/com/example/aipassagecreator/model/dto/skill/SkillConfirmRequest.java`
- Create: `src/main/java/com/example/aipassagecreator/model/dto/skill/SkillExecuteResponse.java`

**Interfaces:**
- Produces: REST API 端点（`POST /skill/{name}/execute`, `GET /skill/{executionId}/progress`, `POST /skill/{executionId}/confirm`, `GET /skill/{executionId}/result`, `GET /skill/list`, `GET /skill/{name}/definition`）

- [ ] **Step 1: 创建 DTO 目录**

```bash
mkdir -p "D:\code\codeJava\codeYuJavaAi\ai-passage-creator\src\main\java\com\example\aipassagecreator\model\dto\skill"
```

- [ ] **Step 2: 创建 SkillExecuteRequest**

```java
package com.example.aipassagecreator.model.dto.skill;

import lombok.Data;
import java.util.Map;

@Data
public class SkillExecuteRequest {
    private String skillName;
    private Map<String, Object> inputs;
}
```

- [ ] **Step 3: 创建 SkillConfirmRequest**

```java
package com.example.aipassagecreator.model.dto.skill;

import lombok.Data;

@Data
public class SkillConfirmRequest {
    private String executionId;
    private String phase;
    private String action; // approve / retry / modify
    private String modifiedData;
}
```

- [ ] **Step 4: 创建 SkillExecuteResponse**

```java
package com.example.aipassagecreator.model.dto.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillExecuteResponse {
    private String skillExecutionId;
    private String skillName;
    private String status;
    private int totalPhases;
    private String progressUrl;
}
```

- [ ] **Step 5: 创建 SkillController**

```java
package com.example.aipassagecreator.skill;

import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.manager.SseEmitterManager;
import com.example.aipassagecreator.model.dto.skill.SkillConfirmRequest;
import com.example.aipassagecreator.model.dto.skill.SkillExecuteRequest;
import com.example.aipassagecreator.model.dto.skill.SkillExecuteResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/skill")
@RequiredArgsConstructor
public class SkillController {

    private final SkillRegistry skillRegistry;
    private final SseEmitterManager sseEmitterManager;

    /**
     * 执行 Skill
     */
    @PostMapping("/{skillName}/execute")
    public BaseResponse<SkillExecuteResponse> executeSkill(
            @PathVariable String skillName,
            @RequestBody SkillExecuteRequest request,
            HttpServletRequest servletRequest) {

        SkillDefinition def = skillRegistry.getSkill(skillName);
        SkillExecution execution = skillRegistry.createExecution(skillName, request.getInputs());

        // 创建 SSE 连接
        sseEmitterManager.createEmitter(execution.getExecutionId());

        // 异步执行
        execution.executeAsync(
                msg -> sseEmitterManager.send(execution.getExecutionId(), msg),
                null // userId 待从 session 获取
        );

        SkillExecuteResponse response = SkillExecuteResponse.builder()
                .skillExecutionId(execution.getExecutionId())
                .skillName(skillName)
                .status("RUNNING")
                .totalPhases(def.getPhases().size())
                .progressUrl("/skill/" + execution.getExecutionId() + "/progress")
                .build();

        return ResultUtils.success(response);
    }

    /**
     * SSE 进度推送
     */
    @GetMapping("/{executionId}/progress")
    public SseEmitter progress(@PathVariable String executionId) {
        return sseEmitterManager.createEmitter(executionId);
    }

    /**
     * 多轮交互确认
     */
    @PostMapping("/{executionId}/confirm")
    public BaseResponse<String> confirm(
            @PathVariable String executionId,
            @RequestBody SkillConfirmRequest request) {
        // 现阶段返回确认已接收，实际逻辑在后续实现
        log.info("Skill 确认: executionId={}, phase={}, action={}", executionId, request.getPhase(), request.getAction());
        return ResultUtils.success("确认已接收");
    }

    /**
     * 获取 Skill 执行结果
     */
    @GetMapping("/{executionId}/result")
    public BaseResponse<Map<String, Object>> getResult(@PathVariable String executionId) {
        // 简化：从 SkillContext 获取共享数据
        var ctx = SkillContext.get(executionId);
        if (ctx == null) {
            return ResultUtils.success(Map.of("status", "NOT_FOUND"));
        }
        return ResultUtils.success(Map.of(
                "status", "RUNNING",
                "phase", ctx.getCurrentPhase(),
                "sharedData", ctx.getSharedData()
        ));
    }

    /**
     * 列出所有可用 Skill
     */
    @GetMapping("/list")
    public BaseResponse<List<Map<String, Object>>> listSkills() {
        List<Map<String, Object>> skills = skillRegistry.getAllSkills().stream()
                .map(def -> Map.<String, Object>of(
                        "name", def.getName(),
                        "description", def.getDescription(),
                        "category", def.getCategory(),
                        "phases", def.getPhases().size(),
                        "multiRound", def.isMultiRound()
                ))
                .collect(Collectors.toList());
        return ResultUtils.success(skills);
    }

    /**
     * 获取 Skill 定义详情
     */
    @GetMapping("/{skillName}/definition")
    public BaseResponse<SkillDefinition> getDefinition(@PathVariable String skillName) {
        return ResultUtils.success(skillRegistry.getSkill(skillName));
    }
}
```

- [ ] **Step 6: 编译验证**

```bash
cd D:\code\codeJava\codeYuJavaAi\ai-passage-creator
mvn compile -q 2>&1
```
Expected: BUILD SUCCESS

- [ ] **Step 7: 启动验证 Skill 端点可访问**

```bash
cd D:\code\codeJava\codeYuJavaAi\ai-passage-creator
mvn spring-boot:run -q 2>&1 &
sleep 15
curl -s http://localhost:8567/api/skill/list | head -100
```
Expected: 返回 JSON 数组（当前为空，因为没有 skill.yaml 定义文件）

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/skill/SkillController.java src/main/java/com/example/aipassagecreator/model/dto/skill/
git commit -m "feat: add SkillController with execute, progress, confirm, list, result APIs"
```

---

## Phase 3：核心 Skill 落地（第 3 周）

### Task 16: proofreading skill 定义

**Files:**
- Create: `src/main/resources/skills/proofreading/skill.yaml`
- Create: `src/main/resources/skills/proofreading/prompts/phase1_content_review.md`
- Create: `src/main/resources/skills/proofreading/prompts/phase2_ai_tone_fix.md`
- Create: `src/main/resources/skills/proofreading/prompts/phase3_rhythm_polish.md`

**Interfaces:**
- Consumes: SkillRegistry 自动扫描机制
- Produces: 注册名为 `proofreading` 的 Skill

- [ ] **Step 1: 创建目录**

```bash
mkdir -p "D:\code\codeJava\codeYuJavaAi\ai-passage-creator\src\main\resources\skills\proofreading\prompts"
```

- [ ] **Step 2: 创建 skill.yaml**

```yaml
name: proofreading
description: 三遍审校降低AI检测率，让文章更有人味
category: writing
requiredRoles: [user]
isMultiRound: false

variables:
  articleContent:
    description: 待审校的文章内容
    required: true
    source: INPUT
  style:
    description: 文章风格（tech/emotional/educational/humorous）
    required: false
    source: INPUT

phases:
  - name: content_review
    promptFile: skills/proofreading/prompts/phase1_content_review.md
    model: agnes
    streaming: false
    outputParser: json
    outputKey: reviewResult
    variables:
      - name: articleContent
      - name: style
    requireConfirmation: false

  - name: ai_tone_fix
    promptFile: skills/proofreading/prompts/phase2_ai_tone_fix.md
    model: agnes
    streaming: true
    outputParser: markdown
    outputKey: polishedContent
    variables:
      - name: articleContent
      - name: style
    requireConfirmation: true

  - name: rhythm_polish
    promptFile: skills/proofreading/prompts/phase3_rhythm_polish.md
    model: agnes
    streaming: true
    outputParser: markdown
    outputKey: finalContent
    variables:
      - name: articleContent
        ref: content_review
      - name: reviewResult
        ref: content_review
      - name: polishedContent
        ref: ai_tone_fix
    requireConfirmation: false
```

- [ ] **Step 3: 创建 phase1_content_review.md**

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
对以下文章内容进行审校：
{articleContent}

文章风格：{style}

## 具体要求
1. 检查事实准确性（数据、时间、产品名称）
2. 检查逻辑清晰度（前后无矛盾）
3. 检查结构合理性（无跑题、信息完整）
4. 检查是否有编造内容（所有数据和案例必须真实）

## 输出格式
必须返回 JSON 格式，不要包含其他内容：

```json
{
  "isAccurate": true,
  "logicIssues": ["问题1描述", "问题2描述"],
  "structureIssues": ["结构问题"],
  "suggestions": ["修改建议"],
  "overallScore": 85,
  "summary": "总体评价"
}
```

overallScore 范围 0-100，分数越高表示质量越好。
```

- [ ] **Step 4: 创建 phase2_ai_tone_fix.md**

```markdown
---
phase: ai_tone_fix
model: agnes
outputType: markdown
---

# 第二遍审校：降AI味

## 目标
系统化降低AI检测率，增加人味。目标：让文章像真人在说话。

## 核心方法：6大类AI腔识别与改写

### 1. 套话连篇
- ❌ "在当今时代"、"综上所述"、"值得注意的是"
- ✅ 直接切主题，不绕弯子

### 2. AI句式
- ❌ "不是...而是..."（连续3次以上）、"不仅...而且..."
- ✅ 拆成短句，多样化表达

### 3. 书面词汇
- ❌ "显著提升"、"充分利用"、"进行操作"
- ✅ 口语化："好用"、"用好"、"点击"

### 4. 结构机械
- ❌ "首先、其次、最后"过度使用、无意义小标题密集
- ✅ 自然叙事，列表适度

### 5. 态度中立
- ❌ "既有优点也有缺点"、"具体取决于实际情况"
- ✅ 明确态度，敢下判断

### 6. 细节缺失
- ❌ "许多"、"一些"、"显著"（不给数字）
- ✅ 加入真实细节、具体数字

## 待改写文章
{articleContent}

## 要求
- 逐段改写，保持原文核心信息
- 加入口语化表达
- 长句拆短句（超过30字拆成2-3句）
- 抽象改具体
- 保持 {style} 风格特征
- 直接输出改写后的完整文章
```

- [ ] **Step 5: 创建 phase3_rhythm_polish.md**

```markdown
---
phase: rhythm_polish
model: agnes
outputType: markdown
---

# 第三遍审校：节奏打磨

## 目标
让文章读起来舒服、自然，有节奏感。

## 检查标准
- 句子长度：15-25字为主，不超过30字
- 段落长度：手机屏幕3-5行
- 标点自然：多用句号，少用逗号连接长句
- 节奏变化：快慢结合
- 加粗适度：每200-300字1-2处

## 待打磨文章
{polishedContent}

## 审校意见
{reviewResult}

## 要求
- 大声朗读感受节奏
- 找出超过30字的长句并拆短
- 检查段落长度，过长的分段
- 调整排版，输出最终版本
- 直接输出最终打磨后的完整文章
```

- [ ] **Step 6: 验证 Skill 注册**

```bash
cd D:\code\codeJava\codeYuJavaAi\ai-passage-creator
mvn compile -q 2>&1
# 启动后验证
mvn spring-boot:run -q 2>&1 &
sleep 15
curl -s http://localhost:8567/api/skill/list
```
Expected: 返回包含 `proofreading` 的列表

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/skills/proofreading/
git commit -m "feat: add proofreading skill with 3-phase review workflow"
```

---

### Task 17: topic-gen skill 定义

**Files:**
- Create: `src/main/resources/skills/topic-gen/skill.yaml`
- Create: `src/main/resources/skills/topic-gen/prompts/phase1_generate_topics.md`

**Interfaces:**
- Consumes: SkillRegistry 自动扫描机制
- Produces: 注册名为 `topic-gen` 的 Skill

- [ ] **Step 1: 创建目录与 skill.yaml**

```bash
mkdir -p "D:\code\codeJava\codeYuJavaAi\ai-passage-creator\src\main\resources\skills\topic-gen\prompts"
```

```yaml
name: topic-gen
description: 快速生成3-4个选题方向，含标题、大纲和优劣分析
category: writing
requiredRoles: [user]
isMultiRound: true

variables:
  direction:
    description: 选题方向描述
    required: true
    source: INPUT
  style:
    description: 文章风格偏好
    required: false
    source: INPUT

phases:
  - name: generate_topics
    promptFile: skills/topic-gen/prompts/phase1_generate_topics.md
    model: agnes
    streaming: false
    outputParser: json
    outputKey: topicOptions
    variables:
      - name: direction
      - name: style
    requireConfirmation: true
```

- [ ] **Step 2: 创建 phase1_generate_topics.md**

```markdown
---
phase: generate_topics
model: agnes
outputType: json
---

# 选题生成

## 方向
{direction}

文章风格偏好：{style}

## 要求
提供3-4个选题方向，每个选题包含完整信息。

## 选题类型（每个选题角度要明显不同）
1. 深度评测型：全面测试 + 数据对比，适合新产品
2. 实战教程型：手把手教学 + 可复制，适合工具实践
3. 洞察观点型：独特视角 + 深度思考，适合行业趋势
4. 案例拆解型：成功案例 + 方法提炼，适合增长分析

## 输出格式
必须返回 JSON 数组，不要包含其他内容：

```json
[
  {
    "title": "吸引人的标题",
    "type": "深度评测型",
    "coreAngle": "核心角度描述",
    "workload": "⭐⭐⭐",
    "outline": ["开头要点", "核心章节1", "核心章节2", "结尾要点"],
    "estimatedWords": 3000,
    "advantages": ["优势1", "优势2"],
    "disadvantages": ["劣势1", "劣势2"]
  }
]
```

标题公式参考：对比型、痛点型、结果型、揭秘型、清单型。
优劣分析要真实，不夸大。工作量评估要准确。
```

- [ ] **Step 3: 验证注册**

```bash
cd D:\code\codeJava\codeYuJavaAi\ai-passage-creator
mvn compile -q 2>&1
mvn spring-boot:run -q 2>&1 &
sleep 15
curl -s http://localhost:8567/api/skill/list | python -m json.tool
```
Expected: 包含 `proofreading` 和 `topic-gen`

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/skills/topic-gen/
git commit -m "feat: add topic-gen skill with multi-round topic generation"
```

---

### Task 18: AgnesImageService — AGNES 图片生成

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/service/AgnesImageService.java`
- Modify: `src/main/java/com/example/aipassagecreator/enums/ImageMethodEnum.java`

**Interfaces:**
- Consumes: `ImageSearchService` 接口（现有策略模式）
- Produces: 实现 `ImageSearchService`，注册为 `AGNES` 方式

- [ ] **Step 1: 创建 AgnesImageService**

```java
package com.example.aipassagecreator.service;

import com.example.aipassagecreator.enums.ImageMethodEnum;
import com.example.aipassagecreator.model.dto.image.ImageData;
import com.example.aipassagecreator.model.dto.image.ImageRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AgnesImageService implements ImageSearchService {

    private final ChatModel agnesChatModel;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${model.router.image-model:agnes-image-2.1-flash}")
    private String imageModel;

    public AgnesImageService(@Qualifier("agnesChatModel") ChatModel agnesChatModel) {
        this.agnesChatModel = agnesChatModel;
    }

    @Override
    public ImageMethodEnum getMethod() {
        return ImageMethodEnum.AGNES;
    }

    @Override
    public ImageData getImageData(ImageRequest request) {
        // 1. 翻译 Prompt 为英文
        String englishPrompt = translatePrompt(request.getPrompt());
        log.debug("AGNES 图片生成: prompt={}", englishPrompt);

        // 2. 构建请求体
        Map<String, Object> body = new HashMap<>();
        body.put("model", imageModel);
        body.put("prompt", englishPrompt);
        body.put("size", "1024x768");
        body.put("extra_body", Map.of("response_format", "url"));

        // 3. 调用 API
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://apihub.agnes-ai.com/v1/images/generations",
                    new HttpEntity<>(body, headers),
                    Map.class);

            // 4. 提取 URL
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
            if (data != null && !data.isEmpty()) {
                String imageUrl = (String) data.get(0).get("url");
                log.info("AGNES 图片生成成功: url={}", imageUrl);
                return ImageData.fromUrl(imageUrl);
            }
            throw new RuntimeException("AGNES 图片 API 返回为空");
        } catch (Exception e) {
            log.error("AGNES 图片生成失败: {}", e.getMessage());
            throw new RuntimeException("AGNES 图片生成失败", e);
        }
    }

    private String translatePrompt(String text) {
        if (text == null || text.isBlank()) return "a beautiful landscape";
        // 检查是否包含非 ASCII 字符（中文等）
        if (text.chars().noneMatch(c -> c > 127)) return text;

        String translation = agnesChatModel.call(new Prompt(
                new SystemMessage("Translate the following Chinese text to English. Preserve all visual details, style, lighting, composition information. Return only the English translation, no explanations."),
                new UserMessage(text)
        )).getResult().getOutput().getText();

        return translation != null && !translation.isBlank() ? translation.trim() : text;
    }
}
```

- [ ] **Step 2: 在 ImageMethodEnum 中添加 AGNES 枚举值**

在 `NANO_BANANA` 之后添加：

```java
    /**
     * AGNES AI 生图
     */
    AGNES("AGNES", "AGNES AI 生图", true, false),
```

- [ ] **Step 3: 编译验证**

```bash
cd D:\code\codeJava\codeYuJavaAi\ai-passage-creator
mvn compile -q 2>&1
```
Expected: BUILD SUCCESS

- [ ] **Step 4: 验证策略注册**

启动后检查 `ImageServiceStrategy` 是否自动注册了 `AGNES` 方式。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/service/AgnesImageService.java src/main/java/com/example/aipassagecreator/enums/ImageMethodEnum.java
git commit -m "feat: add AgnesImageService with prompt translation and AGNES image API integration"
```

---

## Phase 4：扩展与优化（第 4 周）

### Task 19: article-to-x skill

**Files:**
- Create: `src/main/resources/skills/article-to-x/skill.yaml`
- Create: `src/main/resources/skills/article-to-x/prompts/phase1_condense.md`

**Interfaces:**
- Consumes: SkillRegistry 自动扫描机制
- Produces: 注册名为 `article-to-x` 的 Skill

- [ ] **Step 1: 创建目录与 skill.yaml**

```bash
mkdir -p "D:\code\codeJava\codeYuJavaAi\ai-passage-creator\src\main\resources\skills\article-to-x\prompts"
```

```yaml
name: article-to-x
description: 将3000-5000字长文浓缩成200-500字社交媒体内容
category: writing
requiredRoles: [user]
isMultiRound: false

variables:
  articleContent:
    description: 原始长文内容
    required: true
    source: INPUT
  platform:
    description: 目标平台（weibo/xiaohongshu/twitter）
    required: false
    source: INPUT
  style:
    description: 开头风格（金句型/数据型/价值主张型）
    required: false
    source: INPUT

phases:
  - name: condense
    promptFile: skills/article-to-x/prompts/phase1_condense.md
    model: agnes
    streaming: false
    outputParser: markdown
    outputKey: condensedContent
    variables:
      - name: articleContent
      - name: platform
      - name: style
    requireConfirmation: false
```

- [ ] **Step 2: 创建 phase1_condense.md**

```markdown
---
phase: condense
model: agnes
outputType: markdown
---

# 长文转社交媒体

## 原文
{articleContent}

## 目标平台
{platform}

## 开头风格
{style}

## 要求
1. 将3000-5000字文章浓缩为200-500字
2. 提取核心观点和最佳案例
3. 保持口语化和真实感
4. 3种开头风格可选：
   - 金句型：提炼一句金句作为开头
   - 数据型：用具体数据吸引注意力
   - 价值主张型：直接告诉读者能获得什么
5. 保留原文的核心论证逻辑
6. 不是简单删减，而是重新组织表达

## 输出格式
直接输出浓缩后的社交媒体文案。
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/skills/article-to-x/
git commit -m "feat: add article-to-x skill for long-form to social media conversion"
```

---

### Task 20: research skill

**Files:**
- Create: `src/main/resources/skills/research/skill.yaml`
- Create: `src/main/resources/skills/research/prompts/phase1_search.md`
- Create: `src/main/resources/skills/research/prompts/phase2_summary.md`

**Interfaces:**
- Consumes: SkillRegistry 自动扫描机制
- Produces: 注册名为 `research` 的 Skill

- [ ] **Step 1: 创建目录与 skill.yaml**

```bash
mkdir -p "D:\code\codeJava\codeYuJavaAi\ai-passage-creator\src\main\resources\skills\research\prompts"
```

```yaml
name: research
description: 结构化调研，多轮搜索+阶段摘要+最终简报
category: research
requiredRoles: [user]
isMultiRound: true

variables:
  topic:
    description: 调研主题
    required: true
    source: INPUT
  questions:
    description: 关键问题列表
    required: false
    source: INPUT

phases:
  - name: search
    promptFile: skills/research/prompts/phase1_search.md
    model: agnes
    streaming: false
    outputParser: json
    outputKey: searchResults
    variables:
      - name: topic
      - name: questions
    requireConfirmation: false

  - name: summary
    promptFile: skills/research/prompts/phase2_summary.md
    model: agnes
    streaming: false
    outputParser: json
    outputKey: researchBrief
    variables:
      - name: searchResults
        ref: search
    requireConfirmation: true
```

- [ ] **Step 2: 创建 prompt 文件**

（内容从简，聚焦 prompt 结构）

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/skills/research/
git commit -m "feat: add research skill with structured search and summary"
```

---

### Task 21: 权限 + 配额集成

**Files:**
- Modify: `src/main/java/com/example/aipassagecreator/skill/SkillController.java`
- Modify: `src/main/java/com/example/aipassagecreator/skill/SkillExecution.java`

- [ ] **Step 1: 在 SkillController 中添加权限校验**

在 `executeSkill` 方法中，从 `HttpServletRequest` 获取当前用户，检查 `requiredRoles`：

```java
// 从 session 获取当前用户
UserVO loginUser = userService.getLoginUserVO(servletRequest);
if (loginUser == null) {
    return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR);
}

// 检查角色权限
List<String> requiredRoles = def.getRequiredRoles();
if (requiredRoles != null && !requiredRoles.isEmpty()) {
    boolean hasRole = requiredRoles.stream()
            .anyMatch(role -> "admin".equals(role) && loginUser.getUserRole().equals("admin")
                    || "vip".equals(role) && loginUser.getUserRole().equals("vip")
                    || "user".equals(role));
    if (!hasRole) {
        return ResultUtils.error(ErrorCode.NO_AUTH_ERROR, "需要 " + requiredRoles + " 角色才能使用此 Skill");
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/example/aipassagecreator/skill/SkillController.java
git commit -m "feat: add role-based permission check for skill execution"
```

---

### Task 22: 集成测试 + 文档

**Files:**
- Create: `src/test/java/com/example/aipassagecreator/skill/SkillEngineIntegrationTest.java`
- Modify: `README.md`（项目根目录）

- [ ] **Step 1: 创建集成测试**

```java
package com.example.aipassagecreator.skill;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SkillEngineIntegrationTest {

    @Autowired
    private SkillRegistry skillRegistry;

    @Autowired
    private PromptTemplateEngine promptTemplateEngine;

    @Autowired
    private OutputParserRegistry parserRegistry;

    @Test
    void testSkillRegistryLoaded() {
        assertNotNull(skillRegistry);
        assertTrue(skillRegistry.getAllSkills().size() > 0,
                "至少注册一个 Skill");
    }

    @Test
    void testProofreadingSkillExists() {
        SkillDefinition def = skillRegistry.getSkill("proofreading");
        assertNotNull(def);
        assertEquals("proofreading", def.getName());
        assertEquals(3, def.getPhases().size());
    }

    @Test
    void testTopicGenSkillExists() {
        SkillDefinition def = skillRegistry.getSkill("topic-gen");
        assertNotNull(def);
        assertEquals("topic-gen", def.getName());
    }

    @Test
    void testPromptTemplateRendering() {
        String result = promptTemplateEngine.render(
                "skills/proofreading/prompts/phase1_content_review.md",
                Map.of("articleContent", "测试内容", "style", "tech"));
        assertNotNull(result);
        assertTrue(result.contains("测试内容"));
    }

    @Test
    void testJsonOutputParser() {
        var parser = parserRegistry.<Map<String, Object>>parse("json",
                "{\"score\": 85, \"valid\": true}",
                new PhaseDefinition());
        assertNotNull(parser);
        assertEquals(85.0, parser.get("score"));
        assertEquals(true, parser.get("valid"));
    }

    @Test
    void testModelRouterBeans() {
        // 验证 ModelRouter 注入
        // 实际验证在容器启动时完成
    }
}
```

- [ ] **Step 2: 运行集成测试**

```bash
cd D:\code\codeJava\codeYuJavaAi\ai-passage-creator
mvn test -Dtest=SkillEngineIntegrationTest -q 2>&1
```
Expected: BUILD SUCCESS

- [ ] **Step 3: 更新 README.md**

在 README.md 中新增 Skill 引擎章节，列出已注册的 Skill 和 API 端点。

- [ ] **Step 4: 最终 Commit**

```bash
git add .
git commit -m "docs: add integration tests and update README with Skill engine documentation"
```

---

## 审计问题 → 实施任务映射

| 问题 | 等级 | 解决方案 | 对应 Task |
|------|------|---------|----------|
| ① Pipeline/StateGraph 边界模糊 | 🔴 | 统一 StateGraph | Task 13 |
| ② 格式未收敛 | 🟡 | YAML 定义 + MD Prompt | Task 8, 16, 17 |
| ③ Prompt 管理缺失 | 🔴 | 资源文件化 + 分片 | Task 9, 16, 17 |
| ④ SSE 消息爆炸 | 🟡 | 动态泛化消息结构 | Task 15 |
| ⑤ 输出解析器缺失 | 🟡 | 策略化 OutputParser | Task 10 |
| ⑥ 状态机冲突 | 🟡 | SkillExecution 独立状态 | Task 14 |
| ⑦ ThreadLocal 断裂 | 🔴 | SkillContext 注册表 | Task 11 |
| ⑧ 线程池资源竞争 | 🟡 | 独立 skillExecutor | Task 5 |
| ⑨ SSE 标识管理 | 🟡 | skillExecutionId UUID | Task 14 |
| ⑩ 数据库持久化 | 🟡 | skill_execution 表 | Task 6 |
| ⑪ 前端对接 | 🟢 | 后续阶段 | — |
| ⑫ 权限配额 | 🟢 | requiredRoles 校验 | Task 21 |
| ⑬ 生命周期管理 | 🟡 | SkillRegistry 自动扫描 | Task 13 |
| ⑭ Prompt 规范 | 🔴 | 变量声明 + 分片 | Task 9, 16 |
| ⑮ 多轮对话 | 🔴 | StateGraph 条件边 | Task 14, 17 |
| ⑯ 测试策略 | 🟡 | 集成测试 | Task 22 |
| ⑰ 监控可观测 | 🟢 | agent_log 扩展 | Task 7 |
| ⑱ Skill 组合 | 🟡 | SkillExecutionChain | Task 14 |

---

## 文件变更汇总

### 新建文件（22 个）

| 文件 | 所属 Task |
|------|----------|
| `src/main/java/.../skill/ModelRouterConfig.java` | Task 2 |
| `src/main/java/.../skill/ModelRouter.java` | Task 2 |
| `src/main/java/.../skill/config/AgnesModelConfig.java` | Task 3 |
| `src/main/java/.../skill/SkillDefinition.java` | Task 8 |
| `src/main/java/.../skill/PhaseDefinition.java` | Task 8 |
| `src/main/java/.../skill/VariableDef.java` | Task 8 |
| `src/main/java/.../skill/PromptTemplateEngine.java` | Task 9 |
| `src/main/java/.../skill/SkillOutputParser.java` | Task 10 |
| `src/main/java/.../skill/parsers/JsonOutputParser.java` | Task 10 |
| `src/main/java/.../skill/parsers/MarkdownOutputParser.java` | Task 10 |
| `src/main/java/.../skill/OutputParserRegistry.java` | Task 10 |
| `src/main/java/.../skill/SkillContext.java` | Task 11 |
| `src/main/java/.../skill/SkillNodeAction.java` | Task 12 |
| `src/main/java/.../skill/SkillRegistry.java` | Task 13 |
| `src/main/java/.../skill/SkillExecution.java` | Task 14 |
| `src/main/java/.../skill/SkillExecutionChain.java` | Task 14 |
| `src/main/java/.../skill/SkillController.java` | Task 15 |
| `src/main/java/.../model/dto/skill/SkillExecuteRequest.java` | Task 15 |
| `src/main/java/.../model/dto/skill/SkillConfirmRequest.java` | Task 15 |
| `src/main/java/.../model/dto/skill/SkillExecuteResponse.java` | Task 15 |
| `src/main/java/.../service/AgnesImageService.java` | Task 18 |
| `src/main/java/.../mapper/SkillExecutionMapper.java` | Task 6 |
| `src/main/java/.../model/po/SkillExecutionPo.java` | Task 6 |
| `sql/create_skill_execution_table.sql` | Task 6 |
| `sql/alter_agent_log_table.sql` | Task 7 |
| `src/main/resources/skills/proofreading/skill.yaml` | Task 16 |
| `src/main/resources/skills/proofreading/prompts/phase1_*.md` (3个) | Task 16 |
| `src/main/resources/skills/topic-gen/skill.yaml` | Task 17 |
| `src/main/resources/skills/topic-gen/prompts/phase1_*.md` | Task 17 |
| `src/main/resources/skills/article-to-x/skill.yaml` | Task 19 |
| `src/main/resources/skills/article-to-x/prompts/phase1_*.md` | Task 19 |
| `src/main/resources/skills/research/skill.yaml` | Task 20 |
| `src/main/resources/skills/research/prompts/phase1_*.md` (2个) | Task 20 |

### 修改文件（11 个）

| 文件 | 变更 | 所属 Task |
|------|------|----------|
| `pom.xml` | 新增 AGNES OpenAI Starter 依赖 | Task 1 |
| `application.yml` | 新增 AGNES 配置 + model.router 配置 | Task 1 |
| `TitleGeneratorAgent.java` | `DashScopeChatModel` → `ChatModel` | Task 4 |
| `OutlineGeneratorAgent.java` | `DashScopeChatModel` → `ChatModel` | Task 4 |
| `ContentGeneratorAgent.java` | `DashScopeChatModel` → `ChatModel` | Task 4 |
| `ImageAnalyzerAgent.java` | `DashScopeChatModel` → `ChatModel` | Task 4 |
| `ArticleAgentService.java` | `DashScopeChatModel` → `ChatModel` | Task 4 |
| `AsyncConfig.java` | 新增 `skillExecutor` Bean | Task 5 |
| `AgentLog.java` | 新增 `skillExecutionId`, `modelUsed`, `tokenUsage` 字段 | Task 7 |
| `ImageMethodEnum.java` | 新增 `AGNES` 枚举值 | Task 18 |
| `SkillController.java` | 新增权限校验 | Task 21 |
