package com.example.aipassagecreator.skill;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Skill 链式组合执行器
 * 支持按顺序串行执行多个 Skill
 */
public class SkillExecutionChain {

    private final List<String> skillNames;
    private final SkillRegistry registry;

    public SkillExecutionChain(String[] skillNames, SkillRegistry registry) {
        this.skillNames = new ArrayList<>(Arrays.asList(skillNames));
        this.registry = registry;
    }

    public List<SkillExecution> createExecutions() {
        return skillNames.stream()
                .map(name -> registry.createExecution(name, java.util.Map.of()))
                .toList();
    }

    public List<String> getSkillNames() {
        return List.copyOf(skillNames);
    }
}