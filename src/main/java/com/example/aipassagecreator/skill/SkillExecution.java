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
import java.util.LinkedHashMap;
import java.util.HashMap;
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
    private final SkillExecutionMapper mapper;
    private final Gson gson = new Gson();

    private SkillContext.RuntimeContext context;
    /** PENDING / RUNNING / SUCCESS / FAILED */
    @Getter
    private volatile String status = "PENDING";
    private SkillExecutionPo persistedExecution;

    public SkillExecution(String executionId, SkillDefinition definition,
                          Map<String, Object> inputs, CompiledGraph graph,
                          ModelRouter modelRouter, SkillExecutionMapper mapper) {
        this.executionId = executionId;
        this.definition = definition;
        this.inputs = inputs == null ? new HashMap<>() : new HashMap<>(inputs);
        this.graph = graph;
        this.mapper = mapper;
    }

    public synchronized void prepare(Long userId) {
        if (persistedExecution != null) {
            return;
        }
        persistedExecution = buildPo("PENDING", null, null, userId);
        mapper.insert(persistedExecution);
    }

    /**
     * 同步执行 — 由 SkillExecutionService 异步调度
     */
    public void execute(Consumer<String> streamHandler, Long userId) {
        prepare(userId);
        this.status = "RUNNING";
        this.context = SkillContext.create(executionId, null);
        context.setStreamHandler(streamHandler);
        context.setTotalPhases(definition.getPhases().size());
        context.setPhaseHandler(phase -> {
            persistedExecution.setPhase(phase);
            mapper.update(persistedExecution);
        });

        SkillExecutionPo po = persistedExecution;
        po.setStatus("RUNNING");
        mapper.update(po);

        try {
            Map<String, Object> stateInputs = new java.util.HashMap<>();
            stateInputs.putAll(inputs);
            stateInputs.put("skillExecutionId", executionId);
            stateInputs.put("skillName", definition.getName());
            stateInputs.put("skillDefaultModel", "agnes");

            streamHandler.accept(SkillEventFactory.started(
                    executionId, definition.getName(), definition.getPhases().size()));

            Optional<OverAllState> result = graph.invoke(stateInputs);

            Map<String, Object> resultData = new LinkedHashMap<>();
            if (result.isPresent()) {
                for (PhaseDefinition phase : definition.getPhases()) {
                    result.get().value(phase.getOutputKey())
                            .ifPresent(value -> resultData.put(phase.getOutputKey(), value));
                }
            }

            if (!resultData.isEmpty()) {
                PhaseDefinition lastPhase = definition.getPhases().get(definition.getPhases().size() - 1);
                context.getSharedData().putAll(resultData);
                context.getSharedData().put("output", resultData.get(lastPhase.getOutputKey()));
            }

            this.status = "SUCCESS";
            po.setStatus("SUCCESS");
            po.setPhase(definition.getPhases().get(definition.getPhases().size() - 1).getName());
            po.setOutputData(gson.toJson(resultData));
            po.setDurationMs((int) (System.currentTimeMillis() - context.getStartTime()));
            po.setTokenUsage(context.getTokenUsage());
            po.setModelUsed(context.getModelUsedSummary());
            mapper.update(po);

            streamHandler.accept(SkillEventFactory.complete(
                    executionId, definition.getName(), definition.getPhases().size(), resultData));

        } catch (Exception e) {
            log.error("Skill 执行失败: executionId={}, skillName={}", executionId, definition.getName(), e);
            this.status = "FAILED";
            String errorMessage = e.getMessage() == null ? "Skill 执行失败" : e.getMessage();
            po.setStatus("FAILED");
            po.setErrorMessage(errorMessage);
            po.setDurationMs((int) (System.currentTimeMillis() - context.getStartTime()));
            // 失败前已完成的阶段同样消耗了 Token，需一并记录用于成本核算
            po.setTokenUsage(context.getTokenUsage());
            po.setModelUsed(context.getModelUsedSummary());
            mapper.update(po);

            streamHandler.accept(SkillEventFactory.error(
                    executionId, definition.getName(), context.getCurrentPhase(), errorMessage));
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
