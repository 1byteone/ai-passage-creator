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
            execution.executeAsync(streamHandler, userId);

            // 收集输出（简化：实际需要从数据库或上下文获取）
            chainContext.put(skillName + "_executed", true);
        }

        streamHandler.accept("{\"type\":\"skill.chain_complete\",\"skills\":" + skillNames + "}");
    }
}