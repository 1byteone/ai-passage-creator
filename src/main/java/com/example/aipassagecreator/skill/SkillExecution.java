package com.example.aipassagecreator.skill;

import com.alibaba.cloud.ai.graph.CompiledGraph;

import java.util.Map;

/**
 * Skill 执行器
 * 封装一次 Skill 执行的完整上下文，包含定义、输入、StateGraph 及模型路由
 */
public class SkillExecution {

    private final String executionId;
    private final SkillDefinition definition;
    private final Map<String, Object> inputs;
    private final CompiledGraph graph;
    private final ModelRouter modelRouter;

    public SkillExecution(String executionId,
                          SkillDefinition definition,
                          Map<String, Object> inputs,
                          CompiledGraph graph,
                          ModelRouter modelRouter) {
        this.executionId = executionId;
        this.definition = definition;
        this.inputs = inputs;
        this.graph = graph;
        this.modelRouter = modelRouter;
    }

    public String getExecutionId() {
        return executionId;
    }

    public SkillDefinition getDefinition() {
        return definition;
    }

    public Map<String, Object> getInputs() {
        return inputs;
    }

    public CompiledGraph getGraph() {
        return graph;
    }

    public ModelRouter getModelRouter() {
        return modelRouter;
    }
}