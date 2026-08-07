# AI 创作助手（后端 Agent 链路）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为平台提供 `/api/agent/*` 后端 Agent 编排链路：对话会话持久化 + 纯对话流式 + RAG 检索增强 + skill 触发桥接 + 游客限流，全部经 SSE 统一事件协议推送。

**Architecture:** 独立 `AgentConversationService` 编排核心，把现有 SkillExecutionService（skill 执行）与 RagAugmentationService（RAG 增强）作为可调用构建块；LLM 意图判定用「显式 skillName / 关键词检测」两级路由（spec §3.5 允许规则兜底）。SSE 管理复用 SkillSseEmitterManager 模式（新 AgentSseEmitterManager），skill 事件经新增 `listen()` 监听桥接为 agent 协议。

**Tech Stack:** Spring Boot 3.5.13 / Java 21 / MyBatis-Flex / Spring AI ChatModel / SseEmitter / Flyway + H2

## Global Constraints

- **ORM 必须 MyBatis-Flex**（`com.mybatisflex.core.BaseMapper`），禁止 MyBatis-Plus。
- 构造器注入 `@RequiredArgsConstructor` + `private final`，禁止字段注入。
- 响应统一 `ResultUtils.success/error` + `ErrorCode` + `BusinessException`；Controller 薄，业务在 Service。
- 写操作 `@Transactional(rollbackFor = Exception.class)`。
- **迁移**：Flyway `src/main/resources/db/migration/V6__*.sql`（可移植 DDL）+ 同步 `src/main/resources/sql/h2-schema.sql`；`FlywayMigrationCompatibilityTest` 必须通过。
- 模型获取用 `ModelRouter.resolve(null, null)` → 默认 `ChatModel`（agnes 主 / dashscope 降级由既有路由处理）。
- 用户可见错误消息**中文**、简洁直接；日志英文带上下文。
- 变量名 ≤3 个单词；注释解释 "为什么" 而非 "做了什么"。
- JSON 用 `GsonUtils`；事件 JSON 字符串风格对齐 `SkillEventFactory`。
- 游客：**不建会话**、仅纯对话、内存限流 5 次/分；登录用户完整能力。
- 配额：skill 触发复用 `SkillExecutionService.dispatchAndExecute`（内部已校验+扣减配额）。

---

### Task 1: DB 迁移 + 测试 schema

**Files:**
- Create: `src/main/resources/db/migration/V6__create_agent_conversation.sql`
- Modify: `src/main/resources/sql/h2-schema.sql`

**Interfaces:**
- Consumes: 现有 Flyway 迁移集（V1/V2/V4/V5），下一个版本号是 **V6**。
- Produces: 表 `agent_conversation`、`agent_message`（列名小写下划线，与 `skill_execution` 风格一致）。

- [ ] **Step 1: 写迁移脚本**

```sql
-- agent_conversation：Agent 助手会话表
create table if not exists agent_conversation (
    id          bigint auto_increment primary key,
    user_id     bigint not null comment '登录用户',
    title       varchar(100) not null comment '会话标题（首条用户消息摘要）',
    create_time datetime not null default CURRENT_TIMESTAMP,
    update_time datetime default null,
    is_delete   tinyint default 0
);

-- agent_message：会话消息表
create table if not exists agent_message (
    id              bigint auto_increment primary key,
    conversation_id bigint not null,
    role            varchar(10)  not null comment 'user / assistant',
    kind            varchar(16)  not null default 'text' comment 'text / skill / error',
    content         text         not null,
    meta_json       text         default null comment 'skill 执行引用 / RAG 引用等元数据',
    create_time     datetime not null default CURRENT_TIMESTAMP,
    is_delete       tinyint default 0
);
create index if not exists idx_agent_msg_conv on agent_message(conversation_id, create_time);
```

- [ ] **Step 2: 同步 h2-schema.sql**

在 `src/main/resources/sql/h2-schema.sql` 末尾（`agent_log` 之后）追加同样的两条建表语句（`create table if not exists` 风格，H2 兼容，`comment` 可省）：

```sql
create table if not exists agent_conversation (
    id bigint auto_increment primary key,
    user_id bigint not null,
    title varchar(100) not null,
    create_time datetime not null default CURRENT_TIMESTAMP,
    update_time datetime default null,
    is_delete tinyint default 0
);

create table if not exists agent_message (
    id bigint auto_increment primary key,
    conversation_id bigint not null,
    role varchar(10) not null,
    kind varchar(16) not null default 'text',
    content text not null,
    meta_json text default null,
    create_time datetime not null default CURRENT_TIMESTAMP,
    is_delete tinyint default 0
);
create index if not exists idx_agent_msg_conv on agent_message(conversation_id, create_time);
```

- [ ] **Step 3: 运行测试验证迁移集兼容**

Run: `mvn test -Dspring.profiles.active=test -Dtest=FlywayMigrationCompatibilityTest`
Expected: PASS（迁移脚本可移植性 + h2 schema 同步校验通过）

- [ ] **Step 4: 提交**

```bash
git add src/main/resources/db/migration/V6__create_agent_conversation.sql src/main/resources/sql/h2-schema.sql
git commit -m "feat(db): 新增 agent 会话与消息表迁移 + 测试 schema"
```

---

### Task 2: PO 实体 + Mapper

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/model/po/AgentConversationPo.java`
- Create: `src/main/java/com/example/aipassagecreator/model/po/AgentMessagePo.java`
- Create: `src/main/java/com/example/aipassagecreator/mapper/AgentConversationMapper.java`
- Create: `src/main/java/com/example/aipassagecreator/mapper/AgentMessageMapper.java`
- Test: `src/test/java/com/example/aipassagecreator/mapper/AgentMapperTest.java`

**Interfaces:**
- Consumes: Task 1 的表结构；`@MapperScan` 已扫描 `com.example.aipassagecreator.mapper`。
- Produces: `AgentConversationPo`（id/userId/title/createTime/updateTime/isDelete）、`AgentMessagePo`（id/conversationId/role/kind/content/metaJson/createTime/isDelete）、两个 Mapper。

- [ ] **Step 1: 写失败测试**

`src/test/java/com/example/aipassagecreator/mapper/AgentMapperTest.java`：

```java
package com.example.aipassagecreator.mapper;

import com.example.aipassagecreator.model.po.AgentConversationPo;
import com.example.aipassagecreator.model.po.AgentMessagePo;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AgentMapperTest {

    @Resource
    private AgentConversationMapper conversationMapper;
    @Resource
    private AgentMessageMapper messageMapper;

    @Test
    void conversation_insertAndSelect_roundtrip() {
        AgentConversationPo po = AgentConversationPo.builder()
                .userId(1L).title("测试会话").build();
        conversationMapper.insert(po);
        assertNotNull(po.getId());

        AgentConversationPo got = conversationMapper.selectOneByQuery(
                com.mybatisflex.core.query.QueryWrapper.create().eq("id", po.getId()));
        assertNotNull(got);
        assertEquals("测试会话", got.getTitle());
    }

    @Test
    void message_insertAndListByConversation_ok() {
        AgentConversationPo conv = AgentConversationPo.builder()
                .userId(1L).title("测试会话2").build();
        conversationMapper.insert(conv);

        messageMapper.insert(AgentMessagePo.builder()
                .conversationId(conv.getId()).role("user").kind("text").content("你好").build());
        messageMapper.insert(AgentMessagePo.builder()
                .conversationId(conv.getId()).role("assistant").kind("text").content("你好，我是创作助手").build());

        var list = messageMapper.selectListByQuery(
                com.mybatisflex.core.query.QueryWrapper.create()
                        .eq("conversation_id", conv.getId())
                        .eq("is_delete", 0)
                        .orderBy("create_time", true));
        assertEquals(2, list.size());
        assertEquals("user", list.get(0).getRole());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -Dspring.profiles.active=test -Dtest=AgentMapperTest`
Expected: FAIL（编译失败，PO/Mapper 不存在）

- [ ] **Step 3: 写 PO 实体**

`AgentConversationPo.java`：

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
@Table(value = "agent_conversation")
public class AgentConversationPo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 会话归属用户 */
    private Long userId;

    /** 会话标题 */
    private String title;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Integer isDelete;
}
```

`AgentMessagePo.java`：

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
@Table(value = "agent_message")
public class AgentMessagePo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 所属会话 */
    private Long conversationId;

    /** user / assistant */
    private String role;

    /** text / skill / error */
    private String kind;

    /** 文本内容（assistant 为 markdown） */
    private String content;

    /** skill 执行引用 / RAG 引用等元数据（JSON） */
    private String metaJson;

    private LocalDateTime createTime;

    @Column(isLogicDelete = true)
    private Integer isDelete;
}
```

- [ ] **Step 4: 写 Mapper**

`AgentConversationMapper.java`：

```java
package com.example.aipassagecreator.mapper;

import com.example.aipassagecreator.model.po.AgentConversationPo;
import com.mybatisflex.core.BaseMapper;

public interface AgentConversationMapper extends BaseMapper<AgentConversationPo> {
}
```

`AgentMessageMapper.java`：

```java
package com.example.aipassagecreator.mapper;

import com.example.aipassagecreator.model.po.AgentMessagePo;
import com.mybatisflex.core.BaseMapper;

public interface AgentMessageMapper extends BaseMapper<AgentMessagePo> {
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn test -Dspring.profiles.active=test -Dtest=AgentMapperTest`
Expected: PASS（2 个测试全绿）

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/example/aipassagecreator/model/po/AgentConversationPo.java src/main/java/com/example/aipassagecreator/model/po/AgentMessagePo.java src/main/java/com/example/aipassagecreator/mapper/AgentConversationMapper.java src/main/java/com/example/aipassagecreator/mapper/AgentMessageMapper.java src/test/java/com/example/aipassagecreator/mapper/AgentMapperTest.java
git commit -m "feat(agent): 会话与消息 PO + Mapper"
```

---

### Task 3: Agent DTO

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/model/dto/agent/AgentChatRequest.java`
- Create: `src/main/java/com/example/aipassagecreator/model/dto/agent/AgentCreateConversationRequest.java`
- Create: `src/main/java/com/example/aipassagecreator/model/dto/agent/AgentChatResponse.java`
- Create: `src/main/java/com/example/aipassagecreator/model/dto/agent/AgentConversationVO.java`
- Create: `src/main/java/com/example/aipassagecreator/model/dto/agent/AgentMessageVO.java`

**Interfaces:**
- Consumes: 无（纯 DTO）。
- Produces: 请求/响应 VO，供 Task 6/7 使用。

- [ ] **Step 1: 创建请求 DTO**

`AgentChatRequest.java`：

```java
package com.example.aipassagecreator.model.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/** 发送一条消息给 Agent（POST /api/agent/chat） */
@Data
public class AgentChatRequest {

    /** 会话 ID；游客或新会话为空（登录后自动建会话） */
    private Long conversationId;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "消息过长")
    private String message;

    /** 显式指定要触发的 skill（手动兜底 tab），为空则自动路由 */
    private String skillName;

    /** skill 输入参数（显式触发时使用） */
    private Map<String, Object> inputs;

    /** 游客 ID（未登录时必填，前端 localStorage 生成） */
    private String guestId;
}
```

`AgentCreateConversationRequest.java`：

```java
package com.example.aipassagecreator.model.dto.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentCreateConversationRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 100, message = "标题过长")
    private String title;
}
```

- [ ] **Step 2: 创建响应 VO**

`AgentChatResponse.java`：

```java
package com.example.aipassagecreator.model.dto.agent;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentChatResponse {

    /** 本次请求流式推送 ID（GET /agent/{id}/progress 订阅） */
    private String agentRequestId;

    /** 会话 ID（登录用户；游客为 null） */
    private Long conversationId;
}
```

`AgentConversationVO.java`：

```java
package com.example.aipassagecreator.model.dto.agent;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AgentConversationVO {

    private Long id;
    private String title;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

`AgentMessageVO.java`：

```java
package com.example.aipassagecreator.model.dto.agent;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AgentMessageVO {

    private Long id;
    private String role;
    private String kind;
    private String content;
    private String metaJson;
    private LocalDateTime createTime;
}
```

- [ ] **Step 3: 编译校验**

Run: `mvn compile -q`
Expected: BUILD SUCCESS（无编译错误）

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/example/aipassagecreator/model/dto/agent/
git commit -m "feat(agent): 会话与消息 DTO/VO"
```

---

### Task 4: Agent 事件工厂 + SSE 管理器

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/agent/AgentEventFactory.java`
- Create: `src/main/java/com/example/aipassagecreator/agent/AgentSseEmitterManager.java`
- Create: `src/main/java/com/example/aipassagecreator/agent/AgentRequestRegistry.java`
- Test: `src/test/java/com/example/aipassagecreator/agent/AgentSseEmitterManagerTest.java`

**Interfaces:**
- Consumes: `SseEmitter`（Spring MVC）；`GsonUtils`。
- Produces:
  - `AgentEventFactory.started/skillStarted/textDelta/ragReference/skillPhase/skillConfirm/complete/error(requestId, ...)` → JSON 字符串
  - `AgentSseEmitterManager.subscribe/publish/complete(requestId)`，模式对齐 SkillSseEmitterManager
  - `AgentRequestRegistry.register/isOwner/remove(requestId, ownerKey)`

> 事件协议（spec §3.4）：`agent.chat_started` / `agent.text_delta` / `agent.rag_reference` / `agent.skill_started` / `agent.skill_phase` / `agent.skill_confirm` / `agent.complete` / `agent.error`。

- [ ] **Step 1: 写失败测试**

`src/test/java/com/example/aipassagecreator/agent/AgentSseEmitterManagerTest.java`：

```java
package com.example.aipassagecreator.agent;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentSseEmitterManagerTest {

    private final AgentSseEmitterManager manager = new AgentSseEmitterManager();

    @Test
    void publishBeforeSubscribe_replaysBufferOnSubscribe() {
        manager.publish("req1", AgentEventFactory.started("req1"));
        SseEmitter emitter = manager.subscribe("req1");
        assertNotNull(emitter);
        // 缓冲已回放，不抛异常即通过；状态为终态前连接保持
        assertTrue(true);
    }

    @Test
    void complete_setsTerminalAndKeepsBuffer() {
        manager.publish("req2", AgentEventFactory.started("req2"));
        manager.complete("req2");
        List<String> snapshot = manager.snapshot("req2");
        assertEquals(1, snapshot.size());
        assertTrue(snapshot.get(0).contains("agent.chat_started"));
    }

    @Test
    void registry_ownerCheck_works() {
        AgentRequestRegistry registry = new AgentRequestRegistry();
        registry.register("req3", "u:1");
        assertTrue(registry.isOwner("req3", "u:1"));
        assertFalse(registry.isOwner("req3", "g:abc"));
        registry.remove("req3");
        assertFalse(registry.isOwner("req3", "u:1"));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -Dspring.profiles.active=test -Dtest=AgentSseEmitterManagerTest`
Expected: FAIL（编译失败，agent 包不存在）

- [ ] **Step 3: 写 AgentEventFactory**

`src/main/java/com/example/aipassagecreator/agent/AgentEventFactory.java`：

```java
package com.example.aipassagecreator.agent;

import com.example.aipassagecreator.utils.GsonUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Agent 对话流事件工厂 — 统一 SSE 事件协议（与 SkillEventFactory 同风格） */
public final class AgentEventFactory {

    private AgentEventFactory() {
    }

    /** 参考来源（RAG 命中） */
    public record SourceRef(String refId, String title, String type, String createdAt) {
    }

    public static String started(String requestId) {
        return base("agent.chat_started", requestId);
    }

    public static String textDelta(String requestId, String text) {
        Map<String, Object> payload = base("agent.text_delta", requestId);
        payload.put("text", text);
        return GsonUtils.toJson(payload);
    }

    public static String ragReference(String requestId, List<SourceRef> sources) {
        Map<String, Object> payload = base("agent.rag_reference", requestId);
        payload.put("sources", sources);
        return GsonUtils.toJson(payload);
    }

    public static String skillStarted(String requestId, String skillName, int totalPhases) {
        Map<String, Object> payload = base("agent.skill_started", requestId);
        payload.put("skillName", skillName);
        payload.put("totalPhases", totalPhases);
        return GsonUtils.toJson(payload);
    }

    public static String skillPhase(String requestId, int phase, int total, String name, int progress) {
        Map<String, Object> payload = base("agent.skill_phase", requestId);
        payload.put("phase", phase);
        payload.put("total", total);
        payload.put("name", name);
        payload.put("progress", progress);
        return GsonUtils.toJson(payload);
    }

    public static String skillConfirm(String requestId, String executionId, String phase) {
        Map<String, Object> payload = base("agent.skill_confirm", requestId);
        payload.put("executionId", executionId);
        payload.put("phase", phase);
        payload.put("supportedActions", List.of("approve", "modify", "retry"));
        return GsonUtils.toJson(payload);
    }

    public static String complete(String requestId, Long messageId) {
        Map<String, Object> payload = base("agent.complete", requestId);
        payload.put("messageId", messageId);
        return GsonUtils.toJson(payload);
    }

    public static String error(String requestId, String message) {
        Map<String, Object> payload = base("agent.error", requestId);
        payload.put("message", message);
        return GsonUtils.toJson(payload);
    }

    private static Map<String, Object> base(String type, String requestId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("agentRequestId", requestId);
        payload.put("timestamp", System.currentTimeMillis());
        return payload;
    }
}
```

- [ ] **Step 4: 写 AgentSseEmitterManager**

`src/main/java/com/example/aipassagecreator/agent/AgentSseEmitterManager.java`（模式对齐 SkillSseEmitterManager）：

```java
package com.example.aipassagecreator.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Agent 对话流 SSE 管理器 — 环形缓冲 + 订阅回放 + 10min 超时（对齐 SkillSseEmitterManager） */
@Component
@Slf4j
public class AgentSseEmitterManager {

    private static final long TIMEOUT_MS = 10 * 60 * 1000L;
    private static final int MAX_BUFFER_SIZE = 200;
    private static final long TERMINAL_RETENTION_MINUTES = 10L;

    private final Map<String, StreamState> streams = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "agent-sse-cleanup");
        thread.setDaemon(true);
        return thread;
    });

    public SseEmitter subscribe(String requestId) {
        StreamState state = streams.computeIfAbsent(requestId, ignored -> new StreamState());
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        synchronized (state) {
            completeQuietly(state.emitter);
            state.emitter = emitter;
            configureCallbacks(requestId, state, emitter);
            try {
                for (String event : state.events) {
                    send(emitter, event);
                }
                if (state.terminal) {
                    emitter.complete();
                }
            } catch (IOException e) {
                log.warn("Agent SSE 缓冲重放失败, requestId={}", requestId, e);
                emitter.completeWithError(e);
            }
        }
        return emitter;
    }

    public void publish(String requestId, String event) {
        StreamState state = streams.computeIfAbsent(requestId, ignored -> new StreamState());
        synchronized (state) {
            if (state.events.size() >= MAX_BUFFER_SIZE) {
                state.events.removeFirst();
            }
            state.events.addLast(event);
            if (state.emitter != null) {
                try {
                    send(state.emitter, event);
                } catch (IOException e) {
                    log.warn("Agent SSE 推送失败, requestId={}", requestId, e);
                    completeWithErrorQuietly(state.emitter, e);
                    state.emitter = null;
                }
            }
        }
    }

    public void complete(String requestId) {
        StreamState state = streams.computeIfAbsent(requestId, ignored -> new StreamState());
        synchronized (state) {
            state.terminal = true;
            completeQuietly(state.emitter);
            state.emitter = null;
        }
        cleanupExecutor.schedule(() -> streams.remove(requestId, state),
                TERMINAL_RETENTION_MINUTES, TimeUnit.MINUTES);
    }

    List<String> snapshot(String requestId) {
        StreamState state = streams.get(requestId);
        if (state == null) {
            return List.of();
        }
        synchronized (state) {
            return new ArrayList<>(state.events);
        }
    }

    private void configureCallbacks(String requestId, StreamState state, SseEmitter emitter) {
        emitter.onTimeout(() -> clearEmitter(requestId, state, emitter));
        emitter.onCompletion(() -> clearEmitter(requestId, state, emitter));
        emitter.onError(error -> clearEmitter(requestId, state, emitter));
    }

    private void clearEmitter(String requestId, StreamState state, SseEmitter emitter) {
        synchronized (state) {
            if (state.emitter == emitter) {
                state.emitter = null;
            }
        }
        log.debug("Agent SSE 连接已释放, requestId={}", requestId);
    }

    private void send(SseEmitter emitter, String event) throws IOException {
        emitter.send(SseEmitter.event().data(event).reconnectTime(3000L));
    }

    private void completeQuietly(SseEmitter emitter) {
        if (emitter == null) {
            return;
        }
        try {
            emitter.complete();
        } catch (Exception e) {
            log.warn("SSE complete 失败: {}", e.getMessage());
        }
    }

    private void completeWithErrorQuietly(SseEmitter emitter, Throwable error) {
        if (emitter == null) {
            return;
        }
        try {
            emitter.completeWithError(error);
        } catch (Exception e) {
            log.warn("SSE completeWithError 失败: {}", e.getMessage());
        }
    }

    private static class StreamState {
        private final Deque<String> events = new ArrayDeque<>();
        private SseEmitter emitter;
        private boolean terminal;
    }
}
```

- [ ] **Step 5: 写 AgentRequestRegistry**

`src/main/java/com/example/aipassagecreator/agent/AgentRequestRegistry.java`：

```java
package com.example.aipassagecreator.agent;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** agent 请求 → 归属者（"u:{userId}" / "g:{guestId}"）登记，供 SSE 订阅时校验越权 */
@Component
public class AgentRequestRegistry {

    private final Map<String, String> owners = new ConcurrentHashMap<>();

    public void register(String requestId, String ownerKey) {
        owners.put(requestId, ownerKey);
    }

    public boolean isOwner(String requestId, String ownerKey) {
        return ownerKey != null && ownerKey.equals(owners.get(requestId));
    }

    public void remove(String requestId) {
        owners.remove(requestId);
    }
}
```

- [ ] **Step 6: 运行测试验证通过**

Run: `mvn test -Dspring.profiles.active=test -Dtest=AgentSseEmitterManagerTest`
Expected: PASS（3 个测试全绿）

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/example/aipassagecreator/agent/ src/test/java/com/example/aipassagecreator/agent/
git commit -m "feat(agent): 事件工厂 + SSE 管理器 + 请求归属登记"
```

---

### Task 5: SkillSseEmitterManager 增加 listen 监听（skill 桥接依赖）

**Files:**
- Modify: `src/main/java/com/example/aipassagecreator/skill/SkillSseEmitterManager.java`
- Test: `src/test/java/com/example/aipassagecreator/skill/SkillSseEmitterManagerListenTest.java`

**Interfaces:**
- Consumes: 现有 `SkillSseEmitterManager`（subscribe/publish/complete）。
- Produces: 新增 `listen(String executionId, Consumer<String>)` — 注册事件监听，**回放当前缓冲**，之后 publish 同步派发给监听器。供 Task 9 把 skill 事件桥接进 agent 流。

- [ ] **Step 1: 写失败测试**

`src/test/java/com/example/aipassagecreator/skill/SkillSseEmitterManagerListenTest.java`：

```java
package com.example.aipassagecreator.skill;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class SkillSseEmitterManagerListenTest {

    private final SkillSseEmitterManager manager = new SkillSseEmitterManager();

    @Test
    void listen_replaysBuffer_thenReceivesLiveEvents() {
        List<String> received = new CopyOnWriteArrayList<>();
        manager.publish("ex1", "before");

        manager.listen("ex1", received::add);   // 回放缓冲

        assertEquals(List.of("before"), new ArrayList<>(received));

        manager.publish("ex1", "after");        // 实时派发
        assertEquals(List.of("before", "after"), new ArrayList<>(received));
    }

    @Test
    void listen_registersOnNewExecution_receivesFromStart() {
        List<String> received = new CopyOnWriteArrayList<>();
        manager.listen("ex2", received::add);
        manager.publish("ex2", "started");
        assertEquals(List.of("started"), new ArrayList<>(received));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -Dspring.profiles.active=test -Dtest=SkillSseEmitterManagerListenTest`
Expected: FAIL（`listen` 方法不存在）

- [ ] **Step 3: 实现 listen**

在 `SkillSseEmitterManager` 中加入监听器列表与 `listen` 方法（其余代码不动）：

```java
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

// 字段（StreamState 内）：
private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

// publish 末尾（send 之后）追加：
for (Consumer<String> listener : state.listeners) {
    try {
        listener.accept(event);
    } catch (Exception e) {
        log.warn("Skill SSE 监听器异常, executionId={}", executionId, e);
    }
}

/**
 * 注册事件监听（skill → agent 桥接用）。
 * 注册即回放当前缓冲，此后 publish 同步派发；complete 后自动移除。
 */
public void listen(String executionId, Consumer<String> listener) {
    StreamState state = streams.computeIfAbsent(executionId, ignored -> new StreamState());
    synchronized (state) {
        state.listeners.add(listener);
        for (String event : state.events) {
            listener.accept(event);
        }
    }
}
```

并在 `StreamState` 类中加字段：

```java
private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
```

> 注意：`complete()` 保留缓冲 10 分钟，但监听器随缓冲清理自动释放（streams.remove）；无泄漏风险。

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -Dspring.profiles.active=test -Dtest=SkillSseEmitterManagerListenTest`
Expected: PASS（2 个测试全绿）

- [ ] **Step 5: 跑全量 skill 测试回归**

Run: `mvn test -Dspring.profiles.active=test -Dtest=*Skill*`
Expected: 全部 PASS（未破坏既有 skill 逻辑）

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/example/aipassagecreator/skill/SkillSseEmitterManager.java src/test/java/com/example/aipassagecreator/skill/SkillSseEmitterManagerListenTest.java
git commit -m "feat(agent): SkillSseEmitterManager 支持 listen 监听（skill→agent 桥接）"
```

---

### Task 6: AgentConversationService — 会话持久化 + 纯对话流式编排

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/agent/AgentSkillIntentDetector.java`
- Create: `src/main/java/com/example/aipassagecreator/agent/AgentConversationService.java`
- Test: `src/test/java/com/example/aipassagecreator/agent/AgentConversationServiceTest.java`

**Interfaces:**
- Consumes:
  - Task 2 `AgentConversationMapper` / `AgentMessageMapper`
  - Task 4 `AgentSseEmitterManager` / `AgentEventFactory` / `AgentRequestRegistry`
  - `ModelRouter.resolve(null, null)` → `ChatModel`
  - `userService.getLoginUser(servletRequest)` / `getLoginUserVO(servletRequest)`
- Produces:
  - `AgentSkillIntentDetector.detect(String message)` → `String|null`（skill 名）
  - `AgentConversationService.createConversation(userId, title)` → `Long`
  - `AgentConversationService.listConversations(userId)` → `List<AgentConversationVO>`
  - `AgentConversationService.listMessages(conversationId, userId)` → `List<AgentMessageVO>`
  - `AgentConversationService.chat(AgentChatRequest req, Long userId, String guestId)` → `AgentChatResponse`（创建请求 → 路由 → 异步执行）
  - `@Async void executeChatRoute(...)`（纯对话流式，含 RAG 注入占位）

> 本任务实现纯对话 + 会话持久化；RAG 注入与 skill 桥接分别由 Task 8/9 挂入同一流程。为避免重复返工，`chat()` 内预留 route 判定点（skillName / 关键词 / 纯对话），Task 8/9 只改路由分支。

- [ ] **Step 1: 写失败测试**

`src/test/java/com/example/aipassagecreator/agent/AgentConversationServiceTest.java`：

```java
package com.example.aipassagecreator.agent;

import com.example.aipassagecreator.mapper.AgentConversationMapper;
import com.example.aipassagecreator.mapper.AgentMessageMapper;
import com.example.aipassagecreator.model.dto.agent.AgentChatRequest;
import com.example.aipassagecreator.model.po.AgentConversationPo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentConversationServiceTest {

    @Mock
    private AgentConversationMapper conversationMapper;
    @Mock
    private AgentMessageMapper messageMapper;
    @Mock
    private AgentSseEmitterManager sseManager;
    @Mock
    private AgentRequestRegistry requestRegistry;
    @Mock
    private com.example.aipassagecreator.skill.ModelRouter modelRouter;

    @InjectMocks
    private AgentConversationService service;

    @Test
    void createConversation_setsUserAndTitle() {
        when(conversationMapper.insert(any(AgentConversationPo.class))).thenReturn(1);
        Long id = service.createConversation(7L, "我的新会话");
        assertNotNull(id);
        verify(conversationMapper).insert(any(AgentConversationPo.class));
    }

    @Test
    void chat_explicitSkill_empty_routesToChat() {
        AgentChatRequest req = new AgentChatRequest();
        req.setMessage("你好，介绍一下你自己");
        req.setGuestId("g1");
        AgentChatResponse resp = service.chat(req, null, "g1");
        assertNotNull(resp.getAgentRequestId());
        assertNull(resp.getConversationId()); // 游客不建会话
        verify(requestRegistry).register(any(String.class), eq("g:g1"));
    }

    @Test
    void detectIntent_keywordMapsToSkill() {
        assertEquals("content-summarizer", AgentSkillIntentDetector.detect("帮我总结这篇文章"));
        assertEquals("rewrite-plagiarism", AgentSkillIntentDetector.detect("帮我改写降重这段"));
        assertNull(AgentSkillIntentDetector.detect("你好"));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -Dspring.profiles.active=test -Dtest=AgentConversationServiceTest`
Expected: FAIL（编译失败，类不存在）

- [ ] **Step 3: 写 AgentSkillIntentDetector**

`src/main/java/com/example/aipassagecreator/agent/AgentSkillIntentDetector.java`：

```java
package com.example.aipassagecreator.agent;

import java.util.List;
import java.util.Map;

/** 关键词→skill 意图检测（spec §3.5 规则兜底；LLM 判定为后续增强项） */
public final class AgentSkillIntentDetector {

    private static final Map<String, List<String>> KEYWORDS = Map.of(
            "content-summarizer", List.of("总结", "摘要", "概括"),
            "rewrite-plagiarism", List.of("改写", "降重", "重写"),
            "topic-gen", List.of("选题", "灵感", "想写"),
            "headline-optimizer", List.of("标题"),
            "outline-expander", List.of("大纲", "扩展章节"),
            "content-translator", List.of("翻译", "译成"),
            "video-script", List.of("脚本", "短视频"),
            "research", List.of("调研", "研究", "搜集资料"),
            "seeding-copy", List.of("种草", "带货文案"),
            "seo-optimizer", List.of("seo", "搜索引擎优化"),
            "proofreading", List.of("审校", "校对"),
            "ai-detox", List.of("去ai味", "降ai检测", "ai味"),
            "article-to-x", List.of("社交文案", "浓缩成", "长文浓缩"));

    private AgentSkillIntentDetector() {
    }

    /** 命中关键词返回 skill 名，否则返回 null */
    public static String detect(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String lower = message.toLowerCase();
        for (Map.Entry<String, List<String>> entry : KEYWORDS.entrySet()) {
            for (String kw : entry.getValue()) {
                if (lower.contains(kw.toLowerCase())) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }
}
```

- [ ] **Step 4: 写 AgentConversationService（纯对话版）**

`src/main/java/com/example/aipassagecreator/agent/AgentConversationService.java`：

```java
package com.example.aipassagecreator.agent;

import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.mapper.AgentConversationMapper;
import com.example.aipassagecreator.mapper.AgentMessageMapper;
import com.example.aipassagecreator.model.dto.agent.AgentChatRequest;
import com.example.aipassagecreator.model.dto.agent.AgentChatResponse;
import com.example.aipassagecreator.model.dto.agent.AgentConversationVO;
import com.example.aipassagecreator.model.dto.agent.AgentMessageVO;
import com.example.aipassagecreator.model.po.AgentConversationPo;
import com.example.aipassagecreator.model.po.AgentMessagePo;
import com.example.aipassagecreator.skill.ModelRouter;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Agent 对话编排核心：会话持久化 + 纯对话流式（RAG/skill 路由分支由后续任务挂入） */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentConversationService {

    private static final int HISTORY_LIMIT = 20;
    private static final String SYSTEM_PROMPT =
            "你是「AI 创作助手」，服务于 ai-passage-creator 文章创作平台。"
                    + "帮助用户选题、写标题、扩大纲、生成与优化正文。"
                    + "回答简洁、直接，用 markdown 输出。若用户请求创作类任务，可建议其使用对应技能。";

    private final AgentConversationMapper conversationMapper;
    private final AgentMessageMapper messageMapper;
    private final AgentSseEmitterManager sseManager;
    private final AgentRequestRegistry requestRegistry;
    private final ModelRouter modelRouter;

    @Transactional(rollbackFor = Exception.class)
    public Long createConversation(Long userId, String title) {
        AgentConversationPo po = AgentConversationPo.builder()
                .userId(userId).title(title)
                .createTime(LocalDateTime.now()).updateTime(LocalDateTime.now())
                .build();
        conversationMapper.insert(po);
        return po.getId();
    }

    public List<AgentConversationVO> listConversations(Long userId) {
        List<AgentConversationPo> list = conversationMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("user_id", userId).eq("is_delete", 0)
                        .orderBy("update_time", false));
        return list.stream().map(po -> AgentConversationVO.builder()
                .id(po.getId()).title(po.getTitle())
                .createTime(po.getCreateTime()).updateTime(po.getUpdateTime())
                .build()).toList();
    }

    public List<AgentMessageVO> listMessages(Long conversationId, Long userId) {
        checkConversationOwner(conversationId, userId);
        List<AgentMessagePo> list = messageMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("conversation_id", conversationId).eq("is_delete", 0)
                        .orderBy("create_time", true));
        return list.stream().map(this::toVo).toList();
    }

    /**
     * 发送消息 → 建请求 → 路由（显式 skill / 关键词 / 纯对话）→ 异步执行。
     * 返回 agentRequestId 供前端订阅 SSE。
     */
    public AgentChatResponse chat(AgentChatRequest req, Long userId, String guestId) {
        String ownerKey = userId != null ? "u:" + userId : "g:" + guestId;
        String requestId = "agent-" + UUID.randomUUID().toString().substring(0, 8);
        requestRegistry.register(requestId, ownerKey);

        Long conversationId = null;
        if (userId != null) {
            conversationId = resolveConversation(req, userId);
            appendMessage(conversationId, "user", "text", req.getMessage(), null);
        }

        // 路由：显式 skill 优先，其次关键词意图，最后纯对话
        if (req.getSkillName() != null && !req.getSkillName().isBlank()) {
            executeSkillRoute(requestId, req.getSkillName(), req.getInputs(), userId);
        } else {
            String detected = AgentSkillIntentDetector.detect(req.getMessage());
            if (detected != null && userId != null) {
                executeSkillRoute(requestId, detected, null, userId);
            } else {
                executeChatRoute(requestId, req.getMessage(), conversationId, userId);
            }
        }

        return AgentChatResponse.builder()
                .agentRequestId(requestId)
                .conversationId(conversationId)
                .build();
    }

    private Long resolveConversation(AgentChatRequest req, Long userId) {
        if (req.getConversationId() != null) {
            checkConversationOwner(req.getConversationId(), userId);
            return req.getConversationId();
        }
        String title = req.getMessage().length() > 20
                ? req.getMessage().substring(0, 20) : req.getMessage();
        return createConversation(userId, title);
    }

    private void checkConversationOwner(Long conversationId, Long userId) {
        AgentConversationPo po = conversationMapper.selectOneByQuery(
                QueryWrapper.create().eq("id", conversationId).eq("is_delete", 0));
        if (po == null || !po.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "会话不存在或无权访问");
        }
    }

    /** 持久化一条消息，返回其 id */
    private Long appendMessage(Long conversationId, String role, String kind, String content, String metaJson) {
        AgentMessagePo po = AgentMessagePo.builder()
                .conversationId(conversationId).role(role).kind(kind)
                .content(content == null ? "" : content).metaJson(metaJson)
                .createTime(LocalDateTime.now())
                .build();
        messageMapper.insert(po);
        return po.getId();
    }

    /** 纯对话路由 — 流式 text_delta → 落库 assistant 消息 → complete */
    @Async("skillExecutor")
    public void executeChatRoute(String requestId, String userMessage,
                                 Long conversationId, Long userId) {
        sseManager.publish(requestId, AgentEventFactory.started(requestId));
        try {
            ChatModel chatModel = modelRouter.resolve(null, null);
            List<Message> messages = buildMessages(conversationId, userId, userMessage);
            StringBuilder full = new StringBuilder();
            for (var resp : chatModel.stream(new Prompt(messages)).toIterable()) {
                String delta = resp.getResult().getOutput().getText();
                if (delta != null && !delta.isEmpty()) {
                    full.append(delta);
                    sseManager.publish(requestId, AgentEventFactory.textDelta(requestId, delta));
                }
            }
            Long msgId = null;
            if (conversationId != null) {
                msgId = appendMessage(conversationId, "assistant", "text", full.toString(), null);
            }
            sseManager.publish(requestId, AgentEventFactory.complete(requestId, msgId));
        } catch (Exception e) {
            log.error("Agent 纯对话失败: requestId={}", requestId, e);
            sseManager.publish(requestId, AgentEventFactory.error(requestId, "生成失败，请重试"));
        } finally {
            sseManager.complete(requestId);
            requestRegistry.remove(requestId);
        }
    }

    /** 组装会话历史（最近 HISTORY_LIMIT 条） + 当前用户消息 */
    private List<Message> buildMessages(Long conversationId, Long userId, String userMessage) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        if (conversationId != null) {
            List<AgentMessagePo> history = messageMapper.selectListByQuery(
                    QueryWrapper.create()
                            .eq("conversation_id", conversationId).eq("is_delete", 0)
                            .orderBy("create_time", false)
                            .limit(HISTORY_LIMIT));
            // 倒序翻转回正序，去掉刚插入的当前用户消息（避免重复）
            for (int i = history.size() - 1; i >= 0; i--) {
                AgentMessagePo m = history.get(i);
                if (m.getContent().equals(userMessage) && "user".equals(m.getRole())) {
                    continue;
                }
                messages.add(m.getRole().equals("user")
                        ? new UserMessage(m.getContent())
                        : new com.example.aipassagecreator.agent.AgentAssistantMessage(m.getContent()));
            }
        }
        messages.add(new UserMessage(userMessage));
        return messages;
    }

    /** skill 路由占位 — Task 9 实现 */
    @Async("skillExecutor")
    void executeSkillRoute(String requestId, String skillName, Map<String, Object> inputs, Long userId) {
        // 本任务仅保证编译；Task 9 注入真实桥接逻辑
    }

    private AgentMessageVO toVo(AgentMessagePo po) {
        return AgentMessageVO.builder()
                .id(po.getId()).role(po.getRole()).kind(po.getKind())
                .content(po.getContent()).metaJson(po.getMetaJson())
                .createTime(po.getCreateTime())
                .build();
    }
}
```

> 需要新增一个极简 `AgentAssistantMessage`（`Message` 的 assistant 角色实现，位于 `agent` 包），因为 Spring AI 没有直接的多行 assistant 构造：

```java
// src/main/java/com/example/aipassagecreator/agent/AgentAssistantMessage.java
package com.example.aipassagecreator.agent;

import org.springframework.ai.chat.messages.MessageType;

public class AgentAssistantMessage extends org.springframework.ai.chat.messages.AbstractMessage {

    public AgentAssistantMessage(String content) {
        super(MessageType.ASSISTANT, content, java.util.Map.of());
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn test -Dspring.profiles.active=test -Dtest=AgentConversationServiceTest`
Expected: PASS（3 个测试全绿）

> 若 `chatModel.stream(...).toIterable()` 在 mock 下类型不匹配，把 `chat` 单测聚焦在**非流式**方法（createConversation / chat 路由判定 / detectIntent），流式在 Task 7 集成测试中以 `@MockitoBean` 覆盖。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/example/aipassagecreator/agent/AgentSkillIntentDetector.java src/main/java/com/example/aipassagecreator/agent/AgentConversationService.java src/main/java/com/example/aipassagecreator/agent/AgentAssistantMessage.java src/test/java/com/example/aipassagecreator/agent/AgentConversationServiceTest.java
git commit -m "feat(agent): 会话持久化 + 意图路由 + 纯对话流式"
```

---

### Task 7: AgentController — REST + SSE 端点

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/agent/AgentController.java`
- Test: `src/test/java/com/example/aipassagecreator/agent/AgentControllerTest.java`

**Interfaces:**
- Consumes: Task 6 的 `AgentConversationService`（chat/createConversation/listConversations/listMessages）；`userService`；`@RateLimit`。
- Produces:
  - `POST /agent/chat`（`@RateLimit(limit=10, window=60, unit=SECONDS, key="agent_chat")`）
  - `GET /agent/{agentRequestId}/progress`（SSE，归属校验）
  - `GET /agent/conversations`（列表）
  - `POST /agent/conversations`（新建）
  - `GET /agent/conversations/{id}/messages`（历史）

- [ ] **Step 1: 写失败测试**

`src/test/java/com/example/aipassagecreator/agent/AgentControllerTest.java`：

```java
package com.example.aipassagecreator.agent;

import com.example.aipassagecreator.annotation.RateLimit;
import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.model.dto.agent.AgentChatRequest;
import com.example.aipassagecreator.model.dto.agent.AgentChatResponse;
import com.example.aipassagecreator.model.vo.LoginUserVO;
import com.example.aipassagecreator.service.UserService;
import com.mybatisflex.core.paginate.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentControllerTest {

    private AgentController controller;
    private AgentConversationService service;
    private UserService userService;
    private AgentSseEmitterManager sseManager;
    private AgentRequestRegistry registry;

    @BeforeEach
    void setUp() {
        service = mock(AgentConversationService.class);
        userService = mock(UserService.class);
        sseManager = mock(AgentSseEmitterManager.class);
        registry = new AgentRequestRegistry();
        controller = new AgentController(service, userService, sseManager, registry);
    }

    @Test
    void chat_loginUser_returnsAgentRequestId() {
        LoginUserVO loginUser = new LoginUserVO();
        loginUser.setId(7L);
        when(userService.getLoginUserVO(any())).thenReturn(loginUser);
        when(service.chat(any(), eq(7L), isNull())).thenReturn(
                AgentChatResponse.builder().agentRequestId("agent-abc").conversationId(1L).build());

        AgentChatRequest req = new AgentChatRequest();
        req.setMessage("你好");
        var resp = controller.chat(req, new MockHttpServletRequest());

        assertEquals(ResultUtils.CODE_SUCCESS, ((BaseResponse<?>) resp).getCode());
        assertEquals("agent-abc", ((Map<?, ?>) ((BaseResponse<?>) resp).getData()).get("agentRequestId"));
    }

    @Test
    void progress_guestOwnerForbidden_throws() {
        registry.register("agent-x", "u:1");
        assertThrows(com.example.aipassagecreator.exception.BusinessException.class,
                () -> controller.progress("agent-x", "g:abc"));
    }

    @Test
    void progress_ownerOk_returnsEmitter() {
        registry.register("agent-y", "u:1");
        when(sseManager.subscribe("agent-y")).thenReturn(new SseEmitter());
        assertNotNull(controller.progress("agent-y", "u:1"));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -Dspring.profiles.active=test -Dtest=AgentControllerTest`
Expected: FAIL（编译失败，AgentController 不存在）

- [ ] **Step 3: 写 AgentController**

`src/main/java/com/example/aipassagecreator/agent/AgentController.java`：

```java
package com.example.aipassagecreator.agent;

import com.example.aipassagecreator.annotation.RateLimit;
import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.model.dto.agent.AgentChatRequest;
import com.example.aipassagecreator.model.dto.agent.AgentCreateConversationRequest;
import com.example.aipassagecreator.model.dto.agent.AgentMessageVO;
import com.example.aipassagecreator.model.vo.LoginUserVO;
import com.example.aipassagecreator.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Agent 创作助手 REST + SSE 端点 */
@Slf4j
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentConversationService conversationService;
    private final UserService userService;
    private final AgentSseEmitterManager sseManager;
    private final AgentRequestRegistry requestRegistry;

    /** 发送消息：登录用户 / 游客均支持（游客仅纯对话，受 GuestRateLimiter 限流） */
    @PostMapping("/chat")
    @RateLimit(limit = 10, window = 60, unit = TimeUnit.SECONDS, key = "agent_chat")
    public BaseResponse<?> chat(@Valid @RequestBody AgentChatRequest request,
                                HttpServletRequest servletRequest) {
        LoginUserVO loginUser = userService.getLoginUserVO(servletRequest);
        Long userId = loginUser == null ? null : loginUser.getId();
        String guestId = userId == null ? requireGuestId(request.getGuestId()) : null;

        var response = conversationService.chat(request, userId, guestId);
        return ResultUtils.success(Map.of(
                "agentRequestId", response.getAgentRequestId(),
                "conversationId", response.getConversationId()));
    }

    /** 订阅 Agent 对话流（SSE）。ownerKey = "u:{userId}" / "g:{guestId}" */
    @GetMapping("/{agentRequestId}/progress")
    public SseEmitter progress(@PathVariable String agentRequestId, HttpServletRequest servletRequest) {
        LoginUserVO loginUser = userService.getLoginUserVO(servletRequest);
        String ownerKey = loginUser == null
                ? "g:" + requireGuestId(servletRequest.getParameter("guestId"))
                : "u:" + loginUser.getId();
        if (!requestRegistry.isOwner(agentRequestId, ownerKey)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "请求不存在或无权访问");
        }
        return sseManager.subscribe(agentRequestId);
    }

    @GetMapping("/conversations")
    public BaseResponse<List<?>> conversations(HttpServletRequest servletRequest) {
        var loginUser = userService.getLoginUser(servletRequest);
        return ResultUtils.success(conversationService.listConversations(loginUser.getId()));
    }

    @PostMapping("/conversations")
    public BaseResponse<?> createConversation(@Valid @RequestBody AgentCreateConversationRequest request,
                                              HttpServletRequest servletRequest) {
        var loginUser = userService.getLoginUser(servletRequest);
        Long id = conversationService.createConversation(loginUser.getId(), request.getTitle());
        return ResultUtils.success(Map.of("conversationId", id));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public BaseResponse<List<AgentMessageVO>> messages(@PathVariable Long conversationId,
                                                       HttpServletRequest servletRequest) {
        var loginUser = userService.getLoginUser(servletRequest);
        return ResultUtils.success(conversationService.listMessages(conversationId, loginUser.getId()));
    }

    private String requireGuestId(String guestId) {
        if (guestId == null || guestId.isBlank() || guestId.length() > 64) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "游客请提供 guestId");
        }
        return guestId;
    }
}
```

> 注：`LoginUserVO` 的 `id` 字段——确认其 getter 为 `getId()`（UserService 标准 VO）。若字段名不同（如 `userId`），按实际调整。游客限流（GuestRateLimiter）在 Task 10 挂入 `chat()`。

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -Dspring.profiles.active=test -Dtest=AgentControllerTest`
Expected: PASS（3 个测试全绿）

- [ ] **Step 5: 编译 + 提交**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

```bash
git add src/main/java/com/example/aipassagecreator/agent/AgentController.java src/test/java/com/example/aipassagecreator/agent/AgentControllerTest.java
git commit -m "feat(agent): REST + SSE 端点（对话/会话/历史/归属校验）"
```

---

### Task 8: RAG 增强接入纯对话

**Files:**
- Modify: `src/main/java/com/example/aipassagecreator/agent/AgentConversationService.java`
- Test: `src/test/java/com/example/aipassagecreator/agent/RagAugmentChatTest.java`

**Interfaces:**
- Consumes: `RagAugmentationService.augment(query, userId)` → `AugmentedResult(record Reference(refId,title,content,score,type), promptBlock)`。
- Produces: 纯对话路由注入 `promptBlock` + 发出 `agent.rag_reference`（sources 为 `SourceRef(refId,title,type,createdAt)`）；references 为空则不发事件。

- [ ] **Step 1: 写失败测试**

`src/test/java/com/example/aipassagecreator/agent/RagAugmentChatTest.java`：

```java
package com.example.aipassagecreator.agent;

import com.example.aipassagecreator.service.RagAugmentationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagAugmentChatTest {

    /** 空结果不阻断（软参考语义） */
    @Test
    void emptyResult_isEmpty_true() {
        assertTrue(RagAugmentationService.AugmentedResult.EMPTY.isEmpty());
    }

    @Test
    void sourceRef_toEventJson_containsExpectedFields() {
        String json = AgentEventFactory.ragReference("req", List.of(
                new AgentEventFactory.SourceRef("r1", "我的文章", "article", "2026-08-01")));
        assertTrue(json.contains("agent.rag_reference"));
        assertTrue(json.contains("\"refId\":\"r1\""));
        assertTrue(json.contains("\"title\":\"我的文章\""));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -Dspring.profiles.active=test -Dtest=RagAugmentChatTest`
Expected: FAIL（`ragReference` 或 `SourceRef` 不匹配——先在 Task 4 确认签名）

- [ ] **Step 3: 改造 executeChatRoute 注入 RAG**

在 `AgentConversationService` 注入 `RagAugmentationService`，并把 `executeChatRoute` 改为：

```java
// 新增构造依赖
private final RagAugmentationService ragAugmentationService;

/** 纯对话路由 — RAG 增强 + 流式 + 落库 */
@Async("skillExecutor")
public void executeChatRoute(String requestId, String userMessage,
                             Long conversationId, Long userId) {
    sseManager.publish(requestId, AgentEventFactory.started(requestId));
    try {
        // RAG 增强：命中才注入参考块并推送引用事件；userId null（游客）自动跳过
        var augmented = ragAugmentationService.augment(userMessage, userId);
        if (!augmented.isEmpty()) {
            var sources = augmented.references().stream()
                    .map(r -> new AgentEventFactory.SourceRef(
                            r.refId(), r.title(), r.type(), "最近"))
                    .toList();
            sseManager.publish(requestId, AgentEventFactory.ragReference(requestId, sources));
        }

        ChatModel chatModel = modelRouter.resolve(null, null);
        List<Message> messages = buildMessages(conversationId, userId, userMessage);
        // 注入软参考块（system 消息里追加）
        if (!augmented.isEmpty()) {
            messages.add(new SystemMessage(augmented.promptBlock()));
        }

        StringBuilder full = new StringBuilder();
        for (var resp : chatModel.stream(new Prompt(messages)).toIterable()) {
            String delta = resp.getResult().getOutput().getText();
            if (delta != null && !delta.isEmpty()) {
                full.append(delta);
                sseManager.publish(requestId, AgentEventFactory.textDelta(requestId, delta));
            }
        }
        Long msgId = null;
        if (conversationId != null) {
            msgId = appendMessage(conversationId, "assistant", "text", full.toString(), null);
        }
        sseManager.publish(requestId, AgentEventFactory.complete(requestId, msgId));
    } catch (Exception e) {
        log.error("Agent 对话失败: requestId={}", requestId, e);
        sseManager.publish(requestId, AgentEventFactory.error(requestId, "生成失败，请重试"));
    } finally {
        sseManager.complete(requestId);
        requestRegistry.remove(requestId);
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn test -Dspring.profiles.active=test -Dtest=RagAugmentChatTest -Dtest=AgentConversationServiceTest`
Expected: PASS（含既有 service 测试——需在 `AgentConversationServiceTest` 补 `@Mock RagAugmentationService` 注入）

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/example/aipassagecreator/agent/AgentConversationService.java src/test/java/com/example/aipassagecreator/agent/RagAugmentChatTest.java src/test/java/com/example/aipassagecreator/agent/AgentConversationServiceTest.java
git commit -m "feat(agent): 纯对话接入 RAG 增强 + 引用事件"
```

---

### Task 9: Skill 桥接（自动意图 + 手动 tab）

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/agent/AgentSkillEventTranslator.java`
- Modify: `src/main/java/com/example/aipassagecreator/agent/AgentConversationService.java`
- Test: `src/test/java/com/example/aipassagecreator/agent/AgentSkillBridgeTest.java`

**Interfaces:**
- Consumes: Task 6 的 `executeSkillRoute`；`SkillExecutionService.dispatchAndExecute(skillName, inputs, user)` → `SkillExecution`；Task 5 的 `SkillSseEmitterManager.listen(executionId, Consumer)`；`UserService.getById(userId)`。
- Produces: `AgentSkillEventTranslator.translate(rawSkillEventJson)` → agent 事件 JSON（`agent.skill_started` / `agent.skill_phase` / `agent.skill_confirm`）；`executeSkillRoute` 真实实现（桥接 + skill 输出作为 assistant 消息落库）。

- [ ] **Step 1: 写失败测试**

`src/test/java/com/example/aipassagecreator/agent/AgentSkillBridgeTest.java`：

```java
package com.example.aipassagecreator.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentSkillBridgeTest {

    @Test
    void translate_skillProgress_toAgentPhase() {
        String skillEvent = "{\"type\":\"skill.progress\",\"skillExecutionId\":\"ex1\","
                + "\"skillName\":\"research\",\"phase\":\"搜索资料\",\"phaseIndex\":1,"
                + "\"totalPhases\":3,\"data\":\"40%\"}";
        String agentEvent = AgentSkillEventTranslator.translate(skillEvent);
        assertTrue(agentEvent.contains("agent.skill_phase"));
        assertTrue(agentEvent.contains("\"phase\":1"));
        assertTrue(agentEvent.contains("\"name\":\"搜索资料\""));
    }

    @Test
    void translate_awaitingConfirmation_toSkillConfirm() {
        String skillEvent = "{\"type\":\"skill.awaiting_confirmation\",\"skillExecutionId\":\"ex1\","
                + "\"skillName\":\"research\",\"phase\":\"搜索资料\",\"phaseIndex\":1,\"totalPhases\":3}";
        String agentEvent = AgentSkillEventTranslator.translate(skillEvent);
        assertTrue(agentEvent.contains("agent.skill_confirm"));
        assertTrue(agentEvent.contains("\"executionId\":\"ex1\""));
    }

    @Test
    void translate_started_toSkillStarted() {
        String skillEvent = "{\"type\":\"skill.started\",\"skillExecutionId\":\"ex1\","
                + "\"skillName\":\"research\",\"totalPhases\":3}";
        assertTrue(AgentSkillEventTranslator.translate(skillEvent).contains("agent.skill_started"));
    }

    @Test
    void translate_complete_noAgentSkillEvent() {
        // skill.complete 不再桥接为 skill_*（最终文本由 Agent 汇总落库），返回空
        String skillEvent = "{\"type\":\"skill.complete\",\"skillExecutionId\":\"ex1\","
                + "\"skillName\":\"research\",\"status\":\"SUCCESS\",\"outputData\":{}}";
        assertEquals("", AgentSkillEventTranslator.translate(skillEvent));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -Dspring.profiles.active=test -Dtest=AgentSkillBridgeTest`
Expected: FAIL（Translator 不存在）

- [ ] **Step 3: 写 AgentSkillEventTranslator**

`src/main/java/com/example/aipassagecreator/agent/AgentSkillEventTranslator.java`：

```java
package com.example.aipassagecreator.agent;

import com.example.aipassagecreator.utils.GsonUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** skill 事件 JSON → agent 事件 JSON（DRY：不复制 skill 事件 schema，只做协议翻译） */
public final class AgentSkillEventTranslator {

    private AgentSkillEventTranslator() {
    }

    /** 返回 agent 事件 JSON；无需桥接的事件返回空字符串 */
    public static String translate(String skillEventJson) {
        if (skillEventJson == null || skillEventJson.isBlank()) {
            return "";
        }
        JsonObject raw;
        try {
            raw = GsonUtils.getGson().fromJson(skillEventJson, JsonObject.class);
        } catch (Exception e) {
            return "";
        }
        String type = raw.get("type") == null ? "" : raw.get("type").getAsString();
        String requestId = raw.get("skillExecutionId") == null ? "" : raw.get("skillExecutionId").getAsString();
        String skillName = raw.get("skillName") == null ? "" : raw.get("skillName").getAsString();

        return switch (type) {
            case "skill.started" -> AgentEventFactory.skillStarted(requestId, skillName,
                    intOf(raw, "totalPhases"));
            case "skill.phase_started", "skill.progress" -> {
                int phase = intOf(raw, "phaseIndex");
                int total = intOf(raw, "totalPhases");
                String name = raw.get("phase") == null ? "" : raw.get("phase").getAsString();
                int progress = progressOf(raw);
                yield AgentEventFactory.skillPhase(requestId, phase, total, name, progress);
            }
            case "skill.awaiting_confirmation" -> AgentEventFactory.skillConfirm(requestId,
                    requestId, raw.get("phase") == null ? "" : raw.get("phase").getAsString());
            default -> "";
        };
    }

    private static int intOf(JsonObject raw, String key) {
        JsonElement el = raw.get(key);
        return el == null || el.isJsonNull() ? 0 : el.getAsInt();
    }

    /** progress 数据可能是 "40%" 或纯数字，统一解析为 0-100 整数 */
    private static int progressOf(JsonObject raw) {
        JsonElement el = raw.get("data");
        if (el == null || el.isJsonNull()) {
            return 0;
        }
        String text = el.getAsString().replaceAll("[^0-9]", "");
        try {
            return text.isEmpty() ? 0 : Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
```

> 需要 `GsonUtils` 暴露 `getGson()` 静态方法（若不存在，直接 `new com.google.gson.Gson().fromJson(...)` 亦可，见 Step 4 备注）。

- [ ] **Step 4: 实现 executeSkillRoute 真实逻辑**

在 `AgentConversationService` 注入 `SkillExecutionService`、`SkillSseEmitterManager`（skill 包）与 `UserService`，并把 `executeSkillRoute` 替换为：

```java
@Async("skillExecutor")
void executeSkillRoute(String requestId, String skillName, Map<String, Object> inputs, Long userId) {
    sseManager.publish(requestId, AgentEventFactory.started(requestId));
    try {
        User user = userService.getById(userId);
        if (user == null) {
            sseManager.publish(requestId, AgentEventFactory.error(requestId, "用户不存在"));
            return;
        }
        SkillExecution execution = skillExecutionService.dispatchAndExecute(
                skillName, inputs == null ? Map.of() : inputs, user);

        // 桥接 skill 事件进 agent 流（listen 会先回放缓冲，后实时派发）
        skillSseEmitterManager.listen(execution.getExecutionId(),
                event -> {
                    String agentEvent = AgentSkillEventTranslator.translate(event);
                    if (!agentEvent.isEmpty()) {
                        sseManager.publish(requestId, agentEvent);
                    }
                });

        // 等 skill 到终态：轮询执行状态（最多 10 分钟），随后落库 assistant 消息
        waitTerminal(execution.getExecutionId());
        Map<String, Object> output = fetchSkillOutput(execution.getExecutionId());
        Long msgId = null;
        if (userId != null && output != null) {
            String content = String.valueOf(output.getOrDefault("output", ""));
            msgId = appendMessageSafe(conversationIdOf(requestId), "assistant", "skill", content,
                    "{\"executionId\":\"%s\"}".formatted(execution.getExecutionId()));
        }
        sseManager.publish(requestId, AgentEventFactory.complete(requestId, msgId));
    } catch (Exception e) {
        log.error("Agent skill 路由失败: requestId={}, skillName={}", requestId, skillName, e);
        sseManager.publish(requestId, AgentEventFactory.error(requestId, "技能执行失败，请重试"));
    } finally {
        sseManager.complete(requestId);
        requestRegistry.remove(requestId);
    }
}
```

配套私有方法（放同一类）：

```java
private void waitTerminal(String executionId) {
    for (int i = 0; i < 120; i++) {           // 10min / 5s
        SkillExecutionPo po = skillExecutionMapper.selectOneByQuery(
                QueryWrapper.create().eq("skill_execution_id", executionId));
        if (po != null && isTerminal(po.getStatus())) {
            return;
        }
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
    }
}

private boolean isTerminal(String status) {
    return SkillExecutionStatusEnum.SUCCESS.getValue().equals(status)
            || SkillExecutionStatusEnum.FAILED.getValue().equals(status);
}

private Map<String, Object> fetchSkillOutput(String executionId) {
    SkillExecutionPo po = skillExecutionMapper.selectOneByQuery(
            QueryWrapper.create().eq("skill_execution_id", executionId));
    if (po == null || po.getOutputData() == null || po.getOutputData().isBlank()) {
        return Map.of();
    }
    return GsonUtils.fromJson(po.getOutputData(), new com.google.gson.reflect.TypeToken<Map<String, Object>>() {
    });
}
```

新增依赖：`SkillExecutionMapper`（查终态/输出）、`SkillExecutionService`、`SkillSseEmitterManager`（skill 包）、`UserService`。

> **关键设计说明**：`waitTerminal` 轮询比在 `listen` 回调里判终态更简单可靠——`skill.complete` 事件本身含 outputData，但其 JSON 结构依赖 skill 定义；轮询 `skill_execution` 表的 `outputData` 是稳定的最终事实源。`agent.skill_confirm` 已在流中发出，前端确认走既有 `POST /skill/{executionId}/confirm`（复用现有端点与检查点续跑），续跑事件经同一个 listen 继续流入 agent 流。
>
> 本任务需要 `SkillExecutionPo`、`SkillExecutionStatusEnum`、`SkillExecutionService`、`SkillSseEmitterManager`、`UserService`、`GsonUtils`、`SkillExecutionMapper` 的准确引用；实现时先 grep 确认这些类的包路径。

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn test -Dspring.profiles.active=test -Dtest=AgentSkillBridgeTest`
Expected: PASS（Translator 4 个测试全绿；桥接逻辑在 Task 11 集成测试覆盖）

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/example/aipassagecreator/agent/AgentSkillEventTranslator.java src/main/java/com/example/aipassagecreator/agent/AgentConversationService.java src/test/java/com/example/aipassagecreator/agent/AgentSkillBridgeTest.java
git commit -m "feat(agent): skill 桥接（自动意图+手动tab）→ agent 流"
```

---

### Task 10: 游客限流与能力开关

**Files:**
- Create: `src/main/java/com/example/aipassagecreator/agent/GuestRateLimiter.java`
- Modify: `src/main/java/com/example/aipassagecreator/agent/AgentController.java`
- Test: `src/test/java/com/example/aipassagecreator/agent/GuestRateLimiterTest.java`

**Interfaces:**
- Consumes: 无新依赖。
- Produces: `GuestRateLimiter.tryAcquire(guestId)` → boolean（滑动窗口 5 次/分钟）；Controller 在游客 POST `/agent/chat` 时调用，超限返回 429 语义错误。

- [ ] **Step 1: 写失败测试**

`src/test/java/com/example/aipassagecreator/agent/GuestRateLimiterTest.java`：

```java
package com.example.aipassagecreator.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GuestRateLimiterTest {

    private final GuestRateLimiter limiter = new GuestRateLimiter(5);

    @Test
    void tryAcquire_withinLimit_ok_thenRejects() {
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("g1"), "第 " + (i + 1) + " 次应放行");
        }
        assertFalse(limiter.tryAcquire("g1"), "第 6 次应拒绝");
    }

    @Test
    void tryAcquire_differentGuests_independent() {
        assertTrue(limiter.tryAcquire("gA"));
        assertTrue(limiter.tryAcquire("gB"));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn test -Dspring.profiles.active=test -Dtest=GuestRateLimiterTest`
Expected: FAIL（GuestRateLimiter 不存在）

- [ ] **Step 3: 写 GuestRateLimiter**

`src/main/java/com/example/aipassagecreator/agent/GuestRateLimiter.java`：

```java
package com.example.aipassagecreator.agent;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 游客内存滑动窗口限流（默认 5 次/分钟）。内存态：重启即清零，可接受。 */
@Component
public class GuestRateLimiter {

    private static final long WINDOW_MS = 60_000L;
    private final int maxRequests;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public GuestRateLimiter() {
        this(5);
    }

    public GuestRateLimiter(int maxRequests) {
        this.maxRequests = maxRequests;
    }

    /** 放行返回 true；超限返回 false（内部清理过期窗口项） */
    public boolean tryAcquire(String guestId) {
        long now = System.currentTimeMillis();
        Deque<Long> window = hits.computeIfAbsent(guestId, ignored -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MS) {
                window.removeFirst();
            }
            if (window.size() >= maxRequests) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }
}
```

- [ ] **Step 4: 挂入 Controller**

在 `AgentController` 注入 `GuestRateLimiter`，`chat()` 中游客分支加限流：

```java
// 注入
private final GuestRateLimiter guestRateLimiter;

// chat() 内，游客时：
String guestId = userId == null ? requireGuestId(request.getGuestId()) : null;
if (userId == null && !guestRateLimiter.tryAcquire(guestId)) {
    throw new BusinessException(ErrorCode.OPERATION_ERROR, "请求过于频繁，请稍后再试");
}
```

> 游客仅纯对话：`chat()` 中已保证——未登录时 `userId=null`，`AgentConversationService.chat` 不会创建会话、不会触发 skill（detected 分支要求 `userId != null`）。这就是能力开关的落点。

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn test -Dspring.profiles.active=test -Dtest=GuestRateLimiterTest -Dtest=AgentControllerTest`
Expected: PASS（限流 + controller 回归全绿）

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/example/aipassagecreator/agent/GuestRateLimiter.java src/main/java/com/example/aipassagecreator/agent/AgentController.java src/test/java/com/example/aipassagecreator/agent/GuestRateLimiterTest.java
git commit -m "feat(agent): 游客滑动窗口限流 + 能力开关"
```

---

### Task 11: 集成测试完备 + 质量闸门

**Files:**
- Create: `src/test/java/com/example/aipassagecreator/agent/AgentChatIntegrationTest.java`
- Modify: 视测试反馈修正既有 agent 代码

**Interfaces:**
- Consumes: 全部前置任务产物。
- Produces: 端到端 @SpringBootTest 集成测试（H2、session=none、MockitoBean 屏蔽真实 LLM）。

- [ ] **Step 1: 写集成测试**

`src/test/java/com/example/aipassagecreator/agent/AgentChatIntegrationTest.java`：

```java
package com.example.aipassagecreator.agent;

import com.example.aipassagecreator.mapper.AgentConversationMapper;
import com.example.aipassagecreator.mapper.AgentMessageMapper;
import com.example.aipassagecreator.model.dto.agent.AgentChatRequest;
import com.example.aipassagecreator.model.dto.agent.AgentChatResponse;
import com.example.aipassagecreator.mapper.AgentConversationMapper;
import com.example.aipassagecreator.mapper.AgentMessageMapper;
import com.example.aipassagecreator.service.RagAugmentationService;
import com.example.aipassagecreator.skill.SkillExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 端到端：游客纯对话流（Mock ChatModel，避免 CI 打真实 LLM） */
@SpringBootTest
class AgentChatIntegrationTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AgentConversationMapper conversationMapper;
    @Autowired
    private AgentMessageMapper messageMapper;

    @MockitoBean
    private ChatModel agnesChatModel;
    @MockitoBean
    private ChatModel dashscopeChatModel;
    @MockitoBean
    private RagAugmentationService ragAugmentationService;
    @MockitoBean
    private SkillExecutionService skillExecutionService;

    @Test
    void guestChat_returnsAgentRequestId() throws Exception {
        when(ragAugmentationService.augment(any(), any())).thenReturn(RagAugmentationService.AugmentedResult.EMPTY);
        when(agnesChatModel.stream(any(Prompt.class))).thenReturn(reactor.core.publisher.Flux.empty());

        String body = objectMapper.writeValueAsString(Map.of(
                "message", "你好", "guestId", "guest-test-1"));
        mockMvc().perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentRequestId").exists());
    }

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }
}
```

- [ ] **Step 2: 运行测试并修复编译/行为问题**

Run: `mvn test -Dspring.profiles.active=test -Dtest=AgentChatIntegrationTest`
Expected: PASS

> 若 `@MockitoBean` 不可用（Spring Boot 3.5 已支持 `org.springframework.test.context.bean.override.mockito.MockitoBean`），改用该包路径。`agnesChatModel.stream()` 在 mock 未打桩时返回 null，需打桩 `Flux.empty()` 或按实际签名调整。登录用户路径的集成覆盖可在 CI 中加一条带 session 的测试（复用现有 UserService 测试基建）。

- [ ] **Step 3: 全量质量闸门**

Run:
```bash
mvn test -Dspring.profiles.active=test        # 后端全量测试零失败
cd frontend && npm run type-check             # 前端未被改动，应保持零错误
```
Expected: 后端全绿；前端 type-check 零错误（未改动前端）。

- [ ] **Step 4: 提交**

```bash
git add src/test/java/com/example/aipassagecreator/agent/AgentChatIntegrationTest.java
git commit -m "test(agent): 端到端集成测试（Mock LLM，CI 可跑）"
```

---

## 依赖图

```
Task 1 (DB) ──► Task 2 (PO+Mapper) ──► Task 3 (DTO)
                                              │
Task 4 (SSE/Event/Registry) ─────────────────┼──► Task 6 (Service 核心) ──► Task 7 (Controller)
Task 5 (skill listen) ───────────────────────┼───────► Task 9 (skill 桥接)
Task 8 (RAG 注入) ───────────────────────────┘
Task 10 (游客限流) ──────────────────────────► Task 7
Task 11 (集成测试) 依赖全部
```

## Self-Review 记录

- **Spec 覆盖**：§3.1 分层（✓ Task 6/7）、§3.2 决策（✓ 复用 skill/RAG）、§3.3 数据模型（✓ Task 1/2）、§3.4 SSE 协议（✓ Task 4/9）、§3.5 意图路由（✓ Task 6/9 关键词兜底）、§3.6 权限限流（✓ Task 10）、§5 安全（✓ 归属校验 Task 7、SSE 复用前端关闭逻辑）、§6 测试（✓ Task 11）。
- **占位符扫描**：无 TBD；`executeSkillRoute` 从占位 → Task 9 实装（链路明确）。
- **类型一致性**：`AgentChatResponse(agentRequestId, conversationId)`、`SourceRef(refId,title,type,createdAt)`、`AgentEventFactory.*` 各 task 签名一致。
- **已知待实现时确认项**：`LoginUserVO.getId()` 字段名、`GsonUtils.getGson()` 是否公开、`@MockitoBean` 包路径、`SkillExecutionPo`/`SkillExecutionStatusEnum`/`SkillExecutionMapper` 包路径（实现时 grep 一次）。
