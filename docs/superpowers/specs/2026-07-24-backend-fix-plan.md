# Skill 引擎后端修复与扩展实施计划

> **Goal:** 修复 12 个审计问题中的后端缺陷，补全 Skill 引擎核心能力，为前端对接提供稳定 API
> **日期：** 2026-07-24
> **项目：** AI Passage Creator（灵犀写作）

---

## 目录

1. [设计目标与原则](#1-设计目标与原则)
2. [整体架构](#2-整体架构)
3. [核心修复方案](#3-核心修复方案)
4. [新增功能设计](#4-新增功能设计)
5. [API 变更清单](#5-api-变更清单)
6. [数据库变更](#6-数据库变更)
7. [分阶段实施计划](#7-分阶段实施计划)
8. [测试策略](#8-测试策略)

---

## 1. 设计目标与原则

### 核心目标

修复前端审计发现的 4 个 🔴 后端缺陷，补全 6 个 🟡 改进项，确保 API 契约稳定、SSE 消息格式统一、执行生命周期完整。

### 设计原则

| 原则 | 说明 |
|------|------|
| **API 契约优先** | 前后端共享类型定义，后端 API 返回格式即为前端类型定义 |
| **SSE 消息统一** | 所有 SSE 消息使用 JSON 格式，不再混用纯文本 |
| **执行生命周期完整** | 从创建 → 执行 → 完成/失败，每个阶段有明确的状态和消息 |
| **向后兼容** | 现有文章写作流程不受影响，SseMessageTypeEnum 保持不动 |
| **渐进修复** | 从最影响前端集成的缺陷开始修复 |

---

## 2. 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│  API 层 (SkillController)                                       │
│  POST /skill/{name}/execute  → 返回正确的 executionId          │
│  GET  /skill/{executionId}/progress → 401 而非 null             │
│  POST /skill/{executionId}/confirm → 完整多轮逻辑               │
│  GET  /skill/{executionId}/result → 从 DB 查询                  │
│  GET  /skill/list → 返回完整字段 (含 fieldType)                 │
│  GET  /skill/{name}/definition → 返回完整定义                   │
├─────────────────────────────────────────────────────────────────┤
│  编排调度层                                                      │
│  SkillExecutionService.executeAsync(execution)                  │
│    → 接受 SkillExecution 对象，避免 executionId 不一致           │
│  SkillEventFactory                                              │
│    → 统一 SSE 消息构造，确保 JSON 格式                          │
├─────────────────────────────────────────────────────────────────┤
│  Skill 定义层                                                    │
│  VariableDef.java                                               │
│    → 新增 fieldType + options 字段                              │
│  PhaseDefinition.java                                           │
│    → 新增 phaseIndex 字段                                       │
├─────────────────────────────────────────────────────────────────┤
│  SSE 消息层                                                      │
│  SkillEventFactory.java (新增)                                   │
│    → 统一构造所有 SSE 消息类型                                   │
│    → skill.started / skill.progress / skill.phase_complete       │
│    → skill.complete / skill.error / skill.awaiting_confirmation  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. 核心修复方案

### 3.1 修复 1：VariableDef 增加 fieldType + options

**文件：** `src/main/java/com/example/aipassagecreator/skill/VariableDef.java`

**变更：** 新增 `fieldType` 和 `options` 字段

```java
@Data
public class VariableDef {
    private String name;
    private String description;
    private boolean required;
    private String source = "INPUT";
    private String phaseRef;
    /** 字段类型：input / textarea / radio / select */
    private String fieldType = "input";
    /** radio/select 的选项：{value: label} */
    private Map<String, String> options;
}
```

**影响：** `GET /api/skill/{name}/definition` 返回的 JSON 中会包含 `fieldType` 和 `options`，前端可直接用于动态表单渲染。

### 3.2 修复 2：SSE 消息统一 JSON 格式 — SkillEventFactory

**新增文件：** `src/main/java/com/example/aipassagecreator/skill/SkillEventFactory.java`

**职责：** 统一构造所有 SSE 消息的 JSON 字符串，确保格式一致

```java
package com.example.aipassagecreator.skill;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;

/**
 * Skill 事件工厂 — 统一 SSE 消息格式
 */
public class SkillEventFactory {

    private static final Gson gson = new Gson();

    public static String started(String executionId, String skillName, int totalPhases) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "skill.started");
        event.put("skillExecutionId", executionId);
        event.put("skillName", skillName);
        event.put("phaseIndex", 0);
        event.put("totalPhases", totalPhases);
        event.put("status", "RUNNING");
        return gson.toJson(event);
    }

    public static String progress(String executionId, String phase, int phaseIndex, int totalPhases, String content) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "skill.progress");
        event.put("skillExecutionId", executionId);
        event.put("phase", phase);
        event.put("phaseIndex", phaseIndex);
        event.put("totalPhases", totalPhases);
        event.put("content", content);
        return gson.toJson(event);
    }

    public static String phaseComplete(String executionId, String phase, int phaseIndex, int totalPhases) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "skill.phase_complete");
        event.put("skillExecutionId", executionId);
        event.put("phase", phase);
        event.put("phaseIndex", phaseIndex);
        event.put("totalPhases", totalPhases);
        return gson.toJson(event);
    }

    public static String complete(String executionId, String skillName, int totalPhases, Object resultData) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "skill.complete");
        event.put("skillExecutionId", executionId);
        event.put("skillName", skillName);
        event.put("phaseIndex", totalPhases);
        event.put("totalPhases", totalPhases);
        event.put("status", "SUCCESS");
        event.put("data", resultData);
        return gson.toJson(event);
    }

    public static String error(String executionId, String skillName, String phase, String errorMessage) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "skill.error");
        event.put("skillExecutionId", executionId);
        event.put("skillName", skillName);
        event.put("phase", phase);
        event.put("errorMessage", errorMessage);
        return gson.toJson(event);
    }

    public static String awaitingConfirmation(String executionId, String phase, int phaseIndex, int totalPhases) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "skill.awaiting_confirmation");
        event.put("skillExecutionId", executionId);
        event.put("phase", phase);
        event.put("phaseIndex", phaseIndex);
        event.put("totalPhases", totalPhases);
        return gson.toJson(event);
    }
}
```

### 3.3 修复 3：SkillNodeAction 流式消息改为 JSON

**文件：** `src/main/java/com/example/aipassagecreator/skill/SkillNodeAction.java`

**变更：** 流式推送改为 JSON 格式，而非 `STREAMING:xxx` 纯文本

```java
// 修改前
ctx.getStreamHandler().accept("STREAMING:" + chunk);

// 修改后
ctx.getStreamHandler().accept(SkillEventFactory.progress(
    executionId, phase.getName(), getPhaseIndex(), getTotalPhases(), chunk));
```

同时在 `SkillNodeAction` 中先推送 `phase_started` 事件，完成后推送 `phase_complete` 事件：

```java
// 阶段开始
ctx.getStreamHandler().accept(SkillEventFactory.progress(executionId, phase.getName(), 
    getPhaseIndex(), getTotalPhases(), ""));

// ... LLM 调用 ...

// 阶段完成
ctx.getStreamHandler().accept(SkillEventFactory.phaseComplete(executionId, phase.getName(),
    getPhaseIndex(), getTotalPhases()));
```

### 3.4 修复 4：SkillController.executeSkill 传递已创建的 executionId

**文件：** `src/main/java/com/example/aipassagecreator/skill/SkillController.java`
**文件：** `src/main/java/com/example/aipassagecreator/skill/SkillExecutionService.java`

**SkillController 变更：**

```java
// 先创建 execution，确保 executionId 一致
SkillExecution execution = skillRegistry.createExecution(skillName, request.getInputs());
sseEmitterManager.createEmitter(execution.getExecutionId());
// 传递已创建的 execution 对象
skillExecutionService.executeAsync(execution, msg -> sseEmitterManager.send(execution.getExecutionId(), msg), loginUser.getId());
```

**SkillExecutionService 变更：**

```java
@Service
public class SkillExecutionService {

    @Async("skillExecutor")
    public void executeAsync(SkillExecution execution, Consumer<String> streamHandler, Long userId) {
        execution.execute(streamHandler, userId);
    }
}
```

### 3.5 修复 5：SSE 端点返回 401 而非 null

**文件：** `src/main/java/com/example/aipassagecreator/skill/SkillController.java`

```java
@GetMapping("/{executionId}/progress")
public SseEmitter progress(@PathVariable String executionId, HttpServletRequest servletRequest,
                           HttpServletResponse response) {
    LoginUserVO loginUser = userService.getLoginUserVO(servletRequest);
    if (loginUser == null) {
        response.setStatus(401);
        return null;
    }
    // ... ownership check ...
    return sseEmitterManager.createEmitter(executionId);
}
```

### 3.6 修复 6：SkillExecution 使用 SkillEventFactory 推送消息

**文件：** `src/main/java/com/example/aipassagecreator/skill/SkillExecution.java`

**变更：** 所有 `streamHandler.accept(...)` 调用改为 `SkillEventFactory` 构造

```java
// 推送开始事件
streamHandler.accept(SkillEventFactory.started(executionId, definition.getName(), definition.getPhases().size()));

// 执行 StateGraph
Optional<OverAllState> result = graph.invoke(stateInputs);

// 提取结果
Map<String, Object> resultData = new LinkedHashMap<>();
if (result.isPresent()) {
    for (PhaseDefinition phase : definition.getPhases()) {
        result.get().value(phase.getOutputKey())
                .ifPresent(value -> resultData.put(phase.getOutputKey(), value));
    }
}

// 持久化成功
this.status = "SUCCESS";
po.setStatus("SUCCESS");
po.setOutputData(gson.toJson(resultData));
po.setDurationMs((int) (System.currentTimeMillis() - context.getStartTime()));
mapper.update(po);

// 推送完成事件
streamHandler.accept(SkillEventFactory.complete(executionId, definition.getName(), 
    definition.getPhases().size(), resultData));

// 异常处理
} catch (Exception e) {
    this.status = "FAILED";
    po.setStatus("FAILED");
    po.setErrorMessage(e.getMessage());
    po.setDurationMs((int) (System.currentTimeMillis() - context.getStartTime()));
    mapper.update(po);
    streamHandler.accept(SkillEventFactory.error(executionId, definition.getName(), 
        context != null ? context.getCurrentPhase() : null, e.getMessage()));
}
```

---

## 4. 新增功能设计

### 4.1 SkillEventFactory — 统一事件工厂（详见 3.2）

### 4.2 confirm 端点完整逻辑

**文件：** `src/main/java/com/example/aipassagecreator/skill/SkillController.java`

**功能：** 多轮交互确认，支持 approve / retry / modify 三种操作

```java
@PostMapping("/{executionId}/confirm")
public BaseResponse<?> confirm(
        @PathVariable String executionId,
        @RequestBody SkillConfirmRequest request,
        HttpServletRequest servletRequest) {
    LoginUserVO loginUser = userService.getLoginUserVO(servletRequest);
    if (loginUser == null) {
        return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR);
    }
    SkillExecutionPo po = skillExecutionMapper.selectOneByQuery(
            QueryWrapper.create().eq("skill_execution_id", executionId));
    if (po == null || !po.getUserId().equals(loginUser.getId())) {
        return ResultUtils.error(ErrorCode.NO_AUTH_ERROR, "无权操作此执行记录");
    }
    // 只允许在 AWAITING_CONFIRMATION 状态时确认
    if (!"AWAITING_CONFIRMATION".equals(po.getStatus())) {
        return ResultUtils.error(ErrorCode.OPERATION_ERROR, "当前状态不允许确认");
    }
    log.info("Skill 确认: executionId={}, phase={}, action={}", executionId, request.getPhase(), request.getAction());
    // 更新状态为 CONFIRMED，等待后续处理
    po.setStatus("CONFIRMED");
    po.setPhase(request.getPhase());
    skillExecutionMapper.update(po);
    return ResultUtils.success("确认已接收");
}
```

### 4.3 SkillExecution 状态扩展

**文件：** `src/main/java/com/example/aipassagecreator/skill/SkillExecution.java`

**状态机扩展：** 新增 `AWAITING_CONFIRMATION` 和 `CONFIRMED` 状态

```
PENDING → RUNNING → AWAITING_CONFIRMATION → CONFIRMED → SUCCESS
                      ↓ (retry)
                      RUNNING
```

---

## 5. API 变更清单

| 端点 | 变更 | 向后兼容 |
|------|------|---------|
| `POST /skill/{name}/execute` | 返回的 executionId 与执行的实际 ID 一致 | ✅ 修复，无格式变化 |
| `GET /skill/{executionId}/progress` | 未登录返回 401（原为 null） | ⚠️ 行为变更，需前端适配 |
| `POST /skill/{executionId}/confirm` | 增加状态校验（AWAITING_CONFIRMATION） | ✅ 新增行为 |
| `GET /skill/{executionId}/result` | 不变 | ✅ |
| `GET /skill/list` | 不变 | ✅ |
| `GET /skill/{name}/definition` | 返回新增 `variables[*].fieldType` 和 `options` | ✅ 新增字段 |

### SSE 消息格式变更

| 旧格式 | 新格式 | 说明 |
|-------|-------|------|
| `{"type":"skill.started",...}` | `{"type":"skill.started","phaseIndex":0,"totalPhases":3,"status":"RUNNING",...}` | 新增字段 |
| `STREAMING:xxx` (纯文本) | `{"type":"skill.progress","phase":"ai_tone_fix","phaseIndex":2,"totalPhases":3,"content":"xxx"}` | 格式变更 |
| 无 | `{"type":"skill.phase_complete","phase":"ai_tone_fix","phaseIndex":2,"totalPhases":3}` | 新增事件 |
| `{"type":"skill.complete",...}` | `{"type":"skill.complete","data":{...},"phaseIndex":3,"totalPhases":3}` | 新增字段 |
| `{"type":"skill.error",...}` | `{"type":"skill.error","phase":"content_review",...}` | 新增字段 |
| 无 | `{"type":"skill.awaiting_confirmation","phase":"ai_tone_fix","phaseIndex":2,"totalPhases":3}` | 新增事件 |

---

## 6. 数据库变更

### 6.1 skill_execution 状态扩展

当前 `status` 字段支持：`PENDING / RUNNING / SUCCESS / FAILED`

新增：`AWAITING_CONFIRMATION`、`CONFIRMED`

仅业务逻辑变更，无需 DDL 变更（VARCHAR 字段已支持）。

---

## 7. 分阶段实施计划

### Phase 1：核心修复（6 个任务）

| 任务 | 文件 | 工作量 |
|------|------|--------|
| 1.1 VariableDef 扩展 | `VariableDef.java` | 0.5h |
| 1.2 SkillEventFactory | `SkillEventFactory.java`（新增） | 1h |
| 1.3 SkillNodeAction 流式 JSON 化 | `SkillNodeAction.java` | 1h |
| 1.4 SkillExecution 消息统一 | `SkillExecution.java` | 1h |
| 1.5 SkillExecutionService 传递 execution 对象 | `SkillExecutionService.java`、`SkillController.java` | 0.5h |
| 1.6 SSE 端点返回 401 | `SkillController.java` | 0.5h |

**Phase 1 里程碑：** SSE 消息格式统一 JSON，executionId 一致，API 契约稳定

### Phase 2：功能增强（3 个任务）

| 任务 | 文件 | 工作量 |
|------|------|--------|
| 2.1 confirm 端点完整逻辑 | `SkillController.java`、`SkillExecution.java` | 1.5h |
| 2.2 SkillExecution 状态机扩展 | `SkillExecution.java` | 1h |
| 2.3 编译验证 + 集成测试 | 全量测试 | 1h |

**Phase 2 里程碑：** 多轮对话可用，状态机完整

---

## 8. 测试策略

### 8.1 单元测试

| 测试 | 验证点 |
|------|--------|
| `SkillEventFactoryTest` | 所有事件类型 JSON 格式正确，包含所有必需字段 |
| `VariableDefTest` | fieldType 和 options 序列化/反序列化正确 |

### 8.2 集成测试

| 测试 | 验证点 |
|------|--------|
| `SkillEngineIntegrationTest` 扩展 | 新增 SSE 消息格式验证、executionId 一致性验证 |

### 8.3 手动验证

| 场景 | 步骤 |
|------|------|
| proofreading 完整流程 | 执行 → SSE 收到 skill.started → skill.progress × N → skill.phase_complete × 3 → skill.complete |
| 未登录调用 progress | 返回 401，前端 EventSource.onerror 触发 |
| confirm 多轮交互 | 执行 topic-gen → 收到 skill.awaiting_confirmation → POST confirm → 继续执行 |

---

## 附录：文件变更清单

### 新建文件

| 文件 | 说明 |
|------|------|
| `src/main/java/.../skill/SkillEventFactory.java` | SSE 统一事件工厂 |

### 修改文件

| 文件 | 变更 |
|------|------|
| `VariableDef.java` | 新增 `fieldType` + `options` 字段 |
| `SkillNodeAction.java` | 流式推送改为 JSON 格式，增加阶段开始/完成事件 |
| `SkillExecution.java` | 使用 SkillEventFactory 推送消息，状态机扩展 |
| `SkillExecutionService.java` | 接受 `SkillExecution` 对象而非 skillName |
| `SkillController.java` | 传递已创建的 execution 对象，SSE 返回 401，confirm 状态校验 |
| `SkillEngineIntegrationTest.java` | 扩展 SSE 消息格式验证 |