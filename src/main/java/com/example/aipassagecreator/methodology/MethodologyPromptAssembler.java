package com.example.aipassagecreator.methodology;

import org.springframework.stereotype.Component;

/**
 * 方法论提示词装配器：将创作维度/标题策略组装为可追加到 Agent prompt 的提示段。
 * <p>注入位置在 getStylePrompt(style) 之后追加，遵循现有 prompt 组装模式。</p>
 */
@Component
public class MethodologyPromptAssembler {

    private final MethodologyRegistry registry;

    public MethodologyPromptAssembler(MethodologyRegistry registry) {
        this.registry = registry;
    }

    /**
     * 组装标题策略引导段。要求每个标题标注命中的策略 key（配合 TitleOption.strategyKey）。
     */
    public String buildTitleGuidance(String methodologyName) {
        if (methodologyName == null || methodologyName.isBlank()) {
            return "";
        }
        MethodologyDefinition def = registry.get(methodologyName);
        if (def.getTitleStrategies() == null || def.getTitleStrategies().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n【标题策略要求】\n"
                + "请为每个标题方案标注命中的策略 key（字段名 strategyKey）。可选策略：\n");
        for (MethodologyDefinition.TitleStrategy s : def.getTitleStrategies()) {
            sb.append("- ").append(s.getKey()).append("：").append(s.getName()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 组装创作维度引导段（正文 Agent 使用）。
     */
    public String buildContentGuidance(String methodologyName) {
        if (methodologyName == null || methodologyName.isBlank()) {
            return "";
        }
        MethodologyDefinition def = registry.get(methodologyName);
        if (def.getCreationDimensions() == null || def.getCreationDimensions().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n【创作维度要求】\n"
                + "请在创作中遵循以下爆款创作维度：\n");
        for (MethodologyDefinition.CreationDimension d : def.getCreationDimensions()) {
            sb.append("- ").append(d.getName()).append("：").append(d.getGuidance()).append("\n");
        }
        return sb.toString();
    }
}
