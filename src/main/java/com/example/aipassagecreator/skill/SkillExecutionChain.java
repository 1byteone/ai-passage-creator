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
    private final SkillExecutionService executionService;

    public SkillExecutionChain(String[] skillNames, SkillRegistry registry, SkillExecutionService executionService) {
        this.skillNames = Arrays.asList(skillNames);
        this.registry = registry;
        this.executionService = executionService;
    }

    public void executeAsync(Consumer<String> streamHandler, Map<String, Object> initialInputs, Long userId) {
        Map<String, Object> accumulatedInputs = new HashMap<>(initialInputs);

        for (String skillName : skillNames) {
            log.info("链式执行 Skill: {}", skillName);
            SkillDefinition def = registry.getSkill(skillName);

            // 将累积的输出作为当前 skill 的输入
            Map<String, Object> currentInputs = new HashMap<>(accumulatedInputs);

            // 收集前一个 skill 的输出（从 sharedData 中获取）
            String lastOutputKey = null;
            if (!skillNames.isEmpty() && !skillNames.get(0).equals(skillName)) {
                // 查找前一个 skill 的最后一个 phase 的 outputKey
                int prevIndex = skillNames.indexOf(skillName) - 1;
                if (prevIndex >= 0) {
                    SkillDefinition prevDef = registry.getSkill(skillNames.get(prevIndex));
                    PhaseDefinition lastPhase = prevDef.getPhases().get(prevDef.getPhases().size() - 1);
                    lastOutputKey = lastPhase.getOutputKey();
                }
            }

            // 执行当前 skill
            SkillExecution execution = registry.createExecution(skillName, currentInputs);
            execution.execute(streamHandler, userId);

            // 收集输出到累积上下文
            var ctx = SkillContext.get(execution.getExecutionId());
            if (ctx != null && ctx.getSharedData().containsKey("output")) {
                accumulatedInputs.put(lastOutputKey != null ? lastOutputKey : skillName + "_output",
                        ctx.getSharedData().get("output"));
            }
        }

        streamHandler.accept("{\"type\":\"skill.chain_complete\",\"skills\":" + skillNames + "}");
    }
}