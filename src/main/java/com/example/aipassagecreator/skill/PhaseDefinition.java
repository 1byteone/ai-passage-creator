package com.example.aipassagecreator.skill;

import lombok.Data;
import java.util.List;

/**
 * Skill 阶段定义
 */
@Data
public class PhaseDefinition {
    /** 阶段名称：content_review, ai_tone_fix 等 */
    private String name;
    /** Prompt 文件路径：skills/proofreading/prompts/phase1_content_review.md */
    private String promptFile;
    /** 使用的模型：agnes / dashscope */
    private String model;
    /** 是否流式输出 */
    private boolean streaming;
    /** 输出解析器：json / markdown / raw / pptx */
    private String outputParser = "json";
    /** 输出在 OverAllState 中的键名 */
    private String outputKey;
    /** 变量映射 */
    private List<VariableRef> variables;
    /** 是否需要用户确认 */
    private boolean requireConfirmation;

    @Data
    public static class VariableRef {
        /** 变量名 */
        private String name;
        /** 来源：INPUT / 引用 outputKey */
        private String ref;
    }
}