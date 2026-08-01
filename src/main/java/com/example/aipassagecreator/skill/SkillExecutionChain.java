package com.example.aipassagecreator.skill;

import com.example.aipassagecreator.enums.SkillExecutionStatusEnum;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Consumer;

/**
 * Skill 链式执行器
 * <p>
 * 按顺序串行执行多个 skill，前一个 skill 的最终输出作为后一个的输入。
 * 通过共享 {@link SkillContext} 传递阶段间数据。
 */
@Slf4j
public class SkillExecutionChain {

    private final List<String> skillNames;
    private final SkillRegistry registry;
    private final SkillExecutionService executionService;
    private final SkillExecutionRegistry executionRegistry;
    private final String chainExecutionId;

    public SkillExecutionChain(String[] skillNames, SkillRegistry registry,
                               SkillExecutionService executionService,
                               SkillExecutionRegistry executionRegistry,
                               String chainExecutionId) {
        this.skillNames = Arrays.asList(skillNames);
        this.registry = registry;
        this.executionService = executionService;
        this.executionRegistry = executionRegistry;
        this.chainExecutionId = chainExecutionId;
    }

    /**
     * 同步链式执行
     *
     * @param streamHandler SSE 推送
     * @param initialInputs 初始输入
     * @param userId        用户
     * @return 每个 skill 的输出映射 + 失败 skill 名
     */
    public ChainResult executeSync(Consumer<String> streamHandler,
                                   Map<String, Object> initialInputs, Long userId) {
        Map<String, Object> accumulatedInputs = new HashMap<>(initialInputs);
        Map<String, Object> chainOutputs = new LinkedHashMap<>();
        String failedSkill = null;

        streamHandler.accept(SkillEventFactory.progress(chainExecutionId, "chain", "started",
                0, skillNames.size(), null));

        for (int i = 0; i < skillNames.size(); i++) {
            String skillName = skillNames.get(i);
            SkillDefinition def = registry.getSkill(skillName);

            // 前一个 skill 的输出（若有）透传为当前输入
            Map<String, Object> currentInputs = new HashMap<>(accumulatedInputs);
            if (i > 0) {
                SkillDefinition prevDef = registry.getSkill(skillNames.get(i - 1));
                PhaseDefinition lastPhase = prevDef.getPhases().get(prevDef.getPhases().size() - 1);
                String prevOutputKey = lastPhase.getOutputKey();
                if (chainOutputs.containsKey(skillNames.get(i - 1))) {
                    currentInputs.put(prevOutputKey, chainOutputs.get(skillNames.get(i - 1)));
                }
            }

            log.info("链式执行 Skill [{}/{}]: {}", i + 1, skillNames.size(), skillName);
            streamHandler.accept(SkillEventFactory.progress(chainExecutionId, "chain", skillName,
                    i + 1, skillNames.size(), null));

            // 执行当前 skill
            SkillExecution execution = registry.createExecution(skillName, currentInputs);
            execution.prepare(userId);

            // 含确认阶段的 skill 需注册实例，否则 confirm/reaper 无法找到
            if (registry.hasConfirmationPhase(skillName)) {
                executionRegistry.register(execution, userId);
            }

            execution.execute(streamHandler, userId);

            // 检查执行状态：失败则停止链式，退还后续未执行 skill 的配额
            if (SkillExecutionStatusEnum.FAILED.getValue().equals(execution.getStatus())) {
                log.warn("链式执行中止: {} 失败 (第 {}/{} 个)", skillName, i + 1, skillNames.size());
                failedSkill = skillName;
                // 退还当前失败 skill 的配额
                executionService.refundQuietly(userId, execution.getExecutionId());
                // 退还后续未执行 skill 的配额
                for (int j = i + 1; j < skillNames.size(); j++) {
                    executionService.refundQuietly(userId, chainExecutionId + "-" + j);
                }
                break;
            }

            // 收集输出：终态后 SkillContext 已清理，从持久化的 outputData 读取
            PhaseDefinition lastPhase = def.getPhases().get(def.getPhases().size() - 1);
            Map<String, Object> persisted = execution.getPersistedOutput();
            Object output = persisted.get(lastPhase.getOutputKey());
            if (output == null) {
                output = persisted.get("output");
            }
            chainOutputs.put(skillName, output);
            // 累积输入，供后续 skill 使用
            if (output != null) {
                accumulatedInputs.put(lastPhase.getOutputKey(), output);
            }

            // 清理注册表（终态 skill 无需保留）
            executionRegistry.remove(execution.getExecutionId());
        }

        String eventType = failedSkill != null ? "error" : "complete";
        streamHandler.accept(SkillEventFactory.progress(chainExecutionId, "chain", eventType,
                skillNames.size(), skillNames.size(), null));
        return new ChainResult(chainOutputs, failedSkill);
    }

    /**
     * 链式执行结果
     */
    public record ChainResult(Map<String, Object> outputs, String failedSkill) {
        public boolean isSuccess() {
            return failedSkill == null;
        }
    }
}