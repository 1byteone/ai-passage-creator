package com.example.aipassagecreator.skill;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.example.aipassagecreator.mapper.SkillExecutionMapper;
import com.example.aipassagecreator.model.po.SkillExecutionPo;
import com.google.gson.Gson;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Skill 执行器 — 纯 POJO，封装一次 Skill 执行的完整上下文
 * 异步执行由 SkillExecutionService 负责
 */
@Slf4j
public class SkillExecution {

    @Getter
    private final String executionId;
    @Getter
    private final SkillDefinition definition;
    private final Map<String, Object> inputs;
    private final CompiledGraph graph;
    private final ModelRouter modelRouter;
    private final SkillExecutionMapper mapper;
    private final Gson gson = new Gson();

    private SkillContext.RuntimeContext context;
    private volatile String status = "PENDING";

    public SkillExecution(String executionId, SkillDefinition definition,
                          Map<String, Object> inputs, CompiledGraph graph,
                          ModelRouter modelRouter, SkillExecutionMapper mapper) {
        this.executionId = executionId;
        this.definition = definition;
        this.inputs = inputs;
        this.graph = graph;
        this.modelRouter = modelRouter;
        this.mapper = mapper;
    }

    /**
     * 同步执行 — 由 SkillExecutionService 异步调度
     */
    public void execute(Consumer<String> streamHandler, Long userId) {
        this.status = "RUNNING";
        this.context = SkillContext.create(executionId, null);
        context.setStreamHandler(streamHandler);
        context.setTotalPhases(definition.getPhases().size());

        // 持久化初始状态
        SkillExecutionPo po = buildPo("RUNNING", null, null, userId);
        mapper.insert(po);

        try {
            Map<String, Object> stateInputs = new java.util.HashMap<>();
            stateInputs.putAll(inputs);
            stateInputs.put("skillExecutionId", executionId);
            stateInputs.put("skillName", definition.getName());
            stateInputs.put("skillDefaultModel", "agnes");

            streamHandler.accept("{\"type\":\"skill.started\",\"skillExecutionId\":\"" + executionId
                    + "\",\"skillName\":\"" + definition.getName() + "\"}");

            Optional<OverAllState> result = graph.invoke(stateInputs);

            Object resultData = null;
            PhaseDefinition lastPhase = definition.getPhases().get(definition.getPhases().size() - 1);
            if (result.isPresent()) {
                resultData = result.get().value(lastPhase.getOutputKey()).orElse(null);
            }

            // 保存结果到共享上下文，供链式执行使用
            if (resultData != null) {
                context.getSharedData().put("output", resultData);
                context.getSharedData().put(lastPhase.getOutputKey(), resultData);
            }

            this.status = "SUCCESS";
            po.setStatus("SUCCESS");
            po.setOutputData(resultData != null ? gson.toJson(resultData) : null);
            po.setDurationMs((int) (System.currentTimeMillis() - context.getStartTime()));
            mapper.update(po);

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

    private SkillExecutionPo buildPo(String status, String outputData, String errorMessage, Long userId) {
        return SkillExecutionPo.builder()
                .skillExecutionId(executionId)
                .skillName(definition.getName())
                .userId(userId)
                .status(status)
                .inputData(gson.toJson(inputs))
                .outputData(outputData)
                .errorMessage(errorMessage)
                .durationMs(context != null ? (int) (System.currentTimeMillis() - context.getStartTime()) : 0)
                .build();
    }
}