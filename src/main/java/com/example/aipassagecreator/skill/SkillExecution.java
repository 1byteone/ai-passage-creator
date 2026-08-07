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

import java.time.LocalDateTime;
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

    /** 暂停时待用户确认的阶段名，供超时收割等场景回报准确阶段 */
    @Getter
    private volatile String pendingPhase;

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
     * 应用重启后从持久化记录重建执行实例（状态置为 AWAITING_CONFIRMATION）。
     * <p>
     * 不恢复 sharedData：续跑所需的阶段输出都在 DB 检查点里，由 {@link #resume} 从图中读取；
     * 仅种子化 token 消耗与模型列表，保证成本核算连续。resume() 本身无需改动。
     */
    public SkillExecution restoreFrom(SkillExecutionPo po) {
        this.persistedExecution = po;
        this.status = SkillExecutionStatusEnum.AWAITING_CONFIRMATION.getValue();
        this.pendingPhase = po.getPhase();

        SkillContext.RuntimeContext ctx = SkillContext.create(executionId, null);
        ctx.setCurrentPhase(po.getPhase());
        ctx.setTotalPhases(definition.getPhases().size());
        ctx.setPhaseHandler(phase -> {
            persistedExecution.setPhase(phase);
            mapper.update(persistedExecution);
        });
        if (po.getTokenUsage() != null && po.getTokenUsage() > 0) {
            ctx.addTokenUsage(po.getTokenUsage());
        }
        if (po.getModelUsed() != null && !po.getModelUsed().isBlank()) {
            for (String modelName : po.getModelUsed().split(",")) {
                ctx.recordModelUsed(modelName.trim());
            }
        }
        this.context = ctx;
        log.info("Skill 执行已从持久化记录重建: executionId={}, pendingPhase={}", executionId, po.getPhase());
        return this;
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
     * 从检查点续跑 — 由用户确认后触发（兼容重载，默认非 retry）
     */
    public synchronized void resume(Map<String, Object> modifiedData, Consumer<String> streamHandler) {
        resume(modifiedData, streamHandler, false);
    }

    /**
     * 从检查点续跑 — 由用户确认后触发
     *
     * @param modifiedData 用户修改后的数据（modify 动作），为空则表示直接 approve
     * @param retry        是否重新生成当前待确认阶段（清除该阶段输出，使其重跑）
     */
    public synchronized void resume(Map<String, Object> modifiedData, Consumer<String> streamHandler,
                                    boolean retry) {
        if (!SkillExecutionStatusEnum.AWAITING_CONFIRMATION.getValue().equals(status)) {
            throw new IllegalStateException("当前状态不允许续跑: " + status);
        }
        // 先校验上下文再改状态：若此处失败后已置为 RUNNING，执行会卡在非终态，
        // 既不会被收割也无法再次确认
        SkillContext.RuntimeContext resumeContext = SkillContext.get(executionId);
        if (resumeContext == null) {
            throw new IllegalStateException("执行上下文已丢失，无法续跑: " + executionId);
        }
        this.context = resumeContext;
        this.status = SkillExecutionStatusEnum.RUNNING.getValue();
        context.setStreamHandler(streamHandler);

        SkillExecutionPo po = persistedExecution;
        po.setStatus(SkillExecutionStatusEnum.RUNNING.getValue());
        mapper.update(po);

        try {
            // 关键：续跑必须使用带 checkPointId 的 config。
            // 直接传原 runnableConfig 会从 START 重跑已完成阶段（见 GraphInterruptMechanismTest）。
            RunnableConfig resumeConfig;
            if (retry) {
                // retry：清除当前待确认阶段的 outputKey，使图重新执行该阶段节点。
                // 上游阶段结果保留在检查点中，不白烧 token。
                Map<String, Object> resetState = new HashMap<>();
                resetState.put(resolvePendingOutputKey(), null);
                resumeConfig = graph.updateState(runnableConfig, resetState, null);
            } else if (modifiedData == null || modifiedData.isEmpty()) {
                resumeConfig = graph.getState(runnableConfig).config();
            } else {
                resumeConfig = graph.updateState(runnableConfig, modifiedData, null);
            }

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
        this.pendingPhase = next;
        SkillExecutionPo po = persistedExecution;
        po.setStatus(SkillExecutionStatusEnum.AWAITING_CONFIRMATION.getValue());
        po.setPhase(next);
        po.setTokenUsage(context.getTokenUsage());
        po.setModelUsed(context.getModelUsedSummary());
        // update_time 不被 MyBatis-Flex 自动回填，需显式写入，
        // 否则 SkillConfirmationReaper 无法按超时时间扫到该行
        po.setUpdateTime(LocalDateTime.now());
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
     * 获取持久化的终态输出（链式编排透传用）
     * <p>
     * SkillContext 在终态会被清理，但 outputData 已持久化到 DB 记录中。
     * 返回解析后的 Map，无输出时返回空 Map。
     */
    public Map<String, Object> getPersistedOutput() {
        if (persistedExecution == null || persistedExecution.getOutputData() == null
                || persistedExecution.getOutputData().isBlank()) {
            return Map.of();
        }
        try {
            return gson.fromJson(persistedExecution.getOutputData(),
                    new com.google.gson.reflect.TypeToken<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
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

    /**
     * P1 索引用：返回持久化执行记录（终态后仍可读）。
     * <p>
     * SkillContext 终态即清理，但 DB 记录始终可读，RAG 索引用其做数据源。
     */
    SkillExecutionPo getPoForIndex() {
        return persistedExecution;
    }

    /**
     * 测试/诊断用：暴露当前运行时共享数据快照。
     * 终态清理后 context 为空，返回不可变空 Map。
     */
    public Map<String, Object> getContextSharedData() {
        if (context == null) {
            return Map.of();
        }
        return Map.copyOf(context.getSharedData());
    }

    private PhaseDefinition findPhase(String phaseName) {
        return definition.getPhases().stream()
                .filter(p -> p.getName().equals(phaseName))
                .findFirst()
                .orElse(null);
    }

    /**
     * 解析待确认阶段对应的 outputKey（retry 时清除该键触发重跑）
     */
    private String resolvePendingOutputKey() {
        if (pendingPhase != null) {
            PhaseDefinition pending = findPhase(pendingPhase);
            if (pending != null) {
                return pending.getOutputKey();
            }
        }
        // 回退：取最后一个阶段的 outputKey
        return definition.getPhases().get(definition.getPhases().size() - 1).getOutputKey();
    }

    private SkillExecutionPo buildPo(String status, String outputData, String errorMessage, Long userId) {
        LocalDateTime now = LocalDateTime.now();
        return SkillExecutionPo.builder()
                .skillExecutionId(executionId)
                .skillName(definition.getName())
                .userId(userId)
                .status(status)
                .inputData(gson.toJson(inputs))
                .outputData(outputData)
                .errorMessage(errorMessage)
                .createTime(now)
                .updateTime(now)
                .durationMs(context != null ? (int) (System.currentTimeMillis() - context.getStartTime()) : 0)
                .build();
    }
}
