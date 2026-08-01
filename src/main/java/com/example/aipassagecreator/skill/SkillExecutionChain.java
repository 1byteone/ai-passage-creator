package com.example.aipassagecreator.skill;

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
    private final String chainExecutionId;

    public SkillExecutionChain(String[] skillNames, SkillRegistry registry,
                               SkillExecutionService executionService, String chainExecutionId) {
        this.skillNames = Arrays.asList(skillNames);
        this.registry = registry;
        this.executionService = executionService;
        this.chainExecutionId = chainExecutionId;
    }

    /**
     * 同步链式执行
     *
     * @param streamHandler SSE 推送
     * @param initialInputs 初始输入
     * @param userId        用户
     * @return 每个 skill 的输出映射（skillName → output 值）
     */
    public Map<String, Object> executeSync(Consumer<String> streamHandler,
                                           Map<String, Object> initialInputs, Long userId) {
        Map<String, Object> accumulatedInputs = new HashMap<>(initialInputs);
        Map<String, Object> chainOutputs = new LinkedHashMap<>();

        streamHandler.accept(SkillEventFactory.progress(chainExecutionId, "chain", "started",
                0, skillNames.size(), null));

        for (int i = 0; i < skillNames.size(); i++) {
            String skillName = skillNames.get(i);
            SkillDefinition def = registry.getSkill(skillName);

            // 前一个 skill 的输出（若有）透传为当前输入
            Map<String, Object> currentInputs = new HashMap<>(accumulatedInputs);
            String prevOutputKey = null;
            if (i > 0) {
                SkillDefinition prevDef = registry.getSkill(skillNames.get(i - 1));
                PhaseDefinition lastPhase = prevDef.getPhases().get(prevDef.getPhases().size() - 1);
                prevOutputKey = lastPhase.getOutputKey();
                if (chainOutputs.containsKey(skillNames.get(i - 1))) {
                    currentInputs.put(prevOutputKey, chainOutputs.get(skillNames.get(i - 1)));
                }
            }

            log.info("链式执行 Skill [{}/{}]: {}", i + 1, skillNames.size(), skillName);
            streamHandler.accept(SkillEventFactory.progress(chainExecutionId, "chain", skillName,
                    i + 1, skillNames.size(), null));

            // 执行当前 skill
            SkillExecution execution = registry.createExecution(skillName, currentInputs);
            execution.execute(streamHandler, userId);

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
        }

        streamHandler.accept(SkillEventFactory.progress(chainExecutionId, "chain", "complete",
                skillNames.size(), skillNames.size(), null));
        return chainOutputs;
    }
}
