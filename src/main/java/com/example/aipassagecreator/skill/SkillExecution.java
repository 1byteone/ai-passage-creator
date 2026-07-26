package com.example.aipassagecreator.skill;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.example.aipassagecreator.enums.SkillExecutionStatusEnum;
import com.example.aipassagecreator.mapper.SkillExecutionMapper;
import com.example.aipassagecreator.model.po.SkillExecutionPo;
import com.google.gson.Gson;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Skill 执行器 — 纯 POJO，封装一次 Skill 执行的完整上下文
 * 异步执行由 SkillExecutionService 负责
 * <p>
 * 支持多轮确认：声明 {@code requireConfirmation: true} 的阶段执行前会暂停
 * （interruptBefore），此时 {@link #execute} 正常返回而不占用线程，
 * 待用户确认后由 {@link #resume} 从检查点续跑。
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
    /** PENDING / RUNNING / AWAITING_CONFIRMATION / SUCCESS / FAILED */
    @Getter
    private volatile String status = SkillExecutionStatusEnum.PENDING.getValue();
    private SkillExecutionPo persistedExecution;

    /** 图执行的线程上下文，threadId 固定为 executionId，续跑时复用 */
    private final RunnableConfig runnableConfig;

    public SkillExecution(String executionId, SkillDefinition definition,
                          Map<String, Object> inputs, CompiledGraph graph,
                          ModelRouter modelRouter, SkillExecutionMapper mapper) {
        this.executionId = executionId;
        this.definition = definition;
        this.inputs = inputs == null ? new HashMap<>() : new HashMap<>(inputs);
        this.graph = graph;
        this.mapper = mapper;
        this.runnableConfig = RunnableConfig.builder().threadId(executionId).build();
    }

    public synchronized void prepare(Long userId) {
        if (persistedExecution != null) {
            return;
        }
        persistedExecution = buildPo(SkillExecutionStatusEnum.PENDING.getValue(), null, null, userId);
        mapper.insert(persistedExecution);
    }

    /**
     * 同步执行 — 由 SkillExecutionService 异步调度
     * <p>
     * 遇到需要确认的阶段时会停在该阶段之前，状态置为 AWAITING_CONFIRMATION 并返回。
     */
    public void execute(Consumer<String> streamHandler, Long userId) {
        prepare(userId);
        this.status = SkillExecutionStatusEnum.RUNNING.getValue();
        this.context = SkillContext.create(executionId, null);
        context.setStreamHandler(streamHandler);
        context.setTotalPhases(definition.getPhases().size());
        context.setPhaseHandler(phase -> {
            persistedExecution.setPhase(phase);
            mapper.update(persistedExecution);
        });

        SkillExecutionPo po = persistedExecution;
        po.setStatus(SkillExecutionStatusEnum.RUNNING.getValue());
        mapper.update(po);

        try {
            Map<String, Object> stateInputs = new HashMap<>(inputs);
            stateInputs.put("skillExecutionId", executionId);
            stateInputs.put("skillName", definition.getName());
            stateInputs.put("skillDefaultModel", "agnes");

            streamHandler.accept(SkillEventFactory.started(
                    executionId, definition.getName(), definition.getPhases().size()));

            NodeOutput last = graph.stream(stateInputs, runnableConfig).blockLast();

            if (pauseIfAwaitingConfirmation(streamHandler)) {
                return;
            }
            finishSuccess(extractResultData(last), streamHandler);

        } catch (Exception e) {
            finishFailed(e, streamHandler);
        } finally {
            // 暂停态需保留上下文供续跑使用，仅终态才清理
            if (isTerminal()) {
                SkillContext.remove(executionId);
            }
        }
    }

    /**
     * 从检查点续跑 — 由用户确认后触发
     *
     * @param modifiedData 用户修改后的数据（modify 动作），为空则表示直接 approve
     */
    public void resume(Map<String, Object> modifiedData, Consumer<String> streamHandler) {
        if (!SkillExecutionStatusEnum.AWAITING_CONFIRMATION.getValue().equals(status)) {
            throw new IllegalStateException("当前状态不允许续跑: " + status);
        }
        this.status = SkillExecutionStatusEnum.RUNNING.getValue();
        // 上下文在暂停时被保留；若已丢失（如应用重启）则无法续跑
        this.context = SkillContext.get(executionId);
        if (context == null) {
            throw new IllegalStateException("执行上下文已丢失，无法续跑: " + executionId);
        }
        context.setStreamHandler(streamHandler);

        SkillExecutionPo po = persistedExecution;
        po.setStatus(SkillExecutionStatusEnum.RUNNING.getValue());
        mapper.update(po);

        try {
            // 关键：续跑必须使用带 checkPointId 的 config。
            // 直接传原 runnableConfig 会从 START 重跑已完成阶段（见 GraphInterruptMechanismTest）。
            RunnableConfig resumeConfig = modifiedData == null || modifiedData.isEmpty()
                    ? graph.getState(runnableConfig).config()
                    : graph.updateState(runnableConfig, modifiedData, null);

            NodeOutput last = graph.stream(null, resumeConfig).blockLast();

            // 可能存在多个确认点，续跑后仍可能再次暂停
            if (pauseIfAwaitingConfirmation(streamHandler)) {
                return;
            }
            finishSuccess(extractResultData(last), streamHandler);

        } catch (Exception e) {
            finishFailed(e, streamHandler);
        } finally {
            if (isTerminal()) {
                SkillContext.remove(executionId);
            }
        }
    }

    /**
     * 检查图是否停在中断点；是则置为等待确认并推送事件
     *
     * @return true 表示已暂停，调用方应立即返回
     */
    private boolean pauseIfAwaitingConfirmation(Consumer<String> streamHandler) {
        StateSnapshot snapshot;
        try {
            snapshot = graph.getState(runnableConfig);
        } catch (Exception e) {
            // 无检查点（未启用确认的 Skill）视为未暂停
            return false;
        }
        if (snapshot == null) {
            return false;
        }
        String next = snapshot.next();
        if (next == null || StateGraph.END.equals(next)) {
            return false;
        }

        PhaseDefinition pendingPhase = findPhase(next);
        int phaseIndex = pendingPhase != null && pendingPhase.getPhaseIndex() != null
                ? pendingPhase.getPhaseIndex()
                : 0;

        this.status = SkillExecutionStatusEnum.AWAITING_CONFIRMATION.getValue();
        SkillExecutionPo po = persistedExecution;
        po.setStatus(SkillExecutionStatusEnum.AWAITING_CONFIRMATION.getValue());
        po.setPhase(next);
        po.setTokenUsage(context.getTokenUsage());
        po.setModelUsed(context.getModelUsedSummary());
        mapper.update(po);

        // 待审阅的产出来自上一阶段
        Object pendingOutput = phaseIndex > 1
                ? context.getSharedData().get(definition.getPhases().get(phaseIndex - 2).getOutputKey())
                : null;

        log.info("Skill 暂停等待确认: executionId={}, pendingPhase={}", executionId, next);
        streamHandler.accept(SkillEventFactory.awaitingConfirmation(
                executionId, definition.getName(), next, phaseIndex,
                definition.getPhases().size(), pendingOutput));
        return true;
    }

    private Map<String, Object> extractResultData(NodeOutput last) {
        Map<String, Object> resultData = new LinkedHashMap<>();
        OverAllState finalState = resolveFinalState(last);
        if (finalState != null) {
            for (PhaseDefinition phase : definition.getPhases()) {
                finalState.value(phase.getOutputKey())
                        .ifPresent(value -> resultData.put(phase.getOutputKey(), value));
            }
        }
        return resultData;
    }

    /**
     * 取最终状态：优先用流的末元素，其次回落到检查点快照
     */
    private OverAllState resolveFinalState(NodeOutput last) {
        if (last != null && last.state() != null) {
            return last.state();
        }
        try {
            StateSnapshot snapshot = graph.getState(runnableConfig);
            return snapshot == null ? null : snapshot.state();
        } catch (Exception e) {
            return null;
        }
    }

    private void finishSuccess(Map<String, Object> resultData, Consumer<String> streamHandler) {
        if (!resultData.isEmpty()) {
            PhaseDefinition lastPhase = definition.getPhases().get(definition.getPhases().size() - 1);
            context.getSharedData().putAll(resultData);
            context.getSharedData().put("output", resultData.get(lastPhase.getOutputKey()));
        }

        this.status = SkillExecutionStatusEnum.SUCCESS.getValue();
        SkillExecutionPo po = persistedExecution;
        po.setStatus(SkillExecutionStatusEnum.SUCCESS.getValue());
        po.setPhase(definition.getPhases().get(definition.getPhases().size() - 1).getName());
        po.setOutputData(gson.toJson(resultData));
        po.setDurationMs((int) (System.currentTimeMillis() - context.getStartTime()));
        po.setTokenUsage(context.getTokenUsage());
        po.setModelUsed(context.getModelUsedSummary());
        mapper.update(po);

        streamHandler.accept(SkillEventFactory.complete(
                executionId, definition.getName(), definition.getPhases().size(), resultData));
    }

    private void finishFailed(Exception e, Consumer<String> streamHandler) {
        log.error("Skill 执行失败: executionId={}, skillName={}", executionId, definition.getName(), e);
        this.status = SkillExecutionStatusEnum.FAILED.getValue();
        String errorMessage = e.getMessage() == null ? "Skill 执行失败" : e.getMessage();
        SkillExecutionPo po = persistedExecution;
        po.setStatus(SkillExecutionStatusEnum.FAILED.getValue());
        po.setErrorMessage(errorMessage);
        po.setDurationMs((int) (System.currentTimeMillis() - context.getStartTime()));
        // 失败前已完成的阶段同样消耗了 Token，需一并记录用于成本核算
        po.setTokenUsage(context.getTokenUsage());
        po.setModelUsed(context.getModelUsedSummary());
        mapper.update(po);

        streamHandler.accept(SkillEventFactory.error(
                executionId, definition.getName(), context.getCurrentPhase(), errorMessage));
    }

    /**
     * 是否已到终态（SUCCESS / FAILED）
     */
    public boolean isTerminal() {
        SkillExecutionStatusEnum current = SkillExecutionStatusEnum.getEnumByValue(status);
        return current != null && current.isTerminal();
    }

    /**
     * 是否正在等待用户确认
     */
    public boolean isAwaitingConfirmation() {
        return SkillExecutionStatusEnum.AWAITING_CONFIRMATION.getValue().equals(status);
    }

    private PhaseDefinition findPhase(String phaseName) {
        return definition.getPhases().stream()
                .filter(p -> p.getName().equals(phaseName))
                .findFirst()
                .orElse(null);
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
