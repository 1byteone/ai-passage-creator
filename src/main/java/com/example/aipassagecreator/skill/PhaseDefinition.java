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
    /**
     * 注入的工具名称列表（对应 Spring Bean 名称）
     * <p>
     * 例如：["webSearch"] 表示该阶段 LLM 调用时可使用 WebSearchTool。
     * 工具通过 @Tool 注解声明，运行时由 LLM 自主决定何时调用。
     */
    private List<String> tools;
    /** 输出解析器：json / markdown / raw / pptx */
    private String outputParser = "json";
    /** 输出在 OverAllState 中的键名 */
    private String outputKey;
    /** 变量映射 */
    private List<VariableRef> variables;
    /**
     * 是否需要用户确认
     * <p>
     * 为 true 时，该阶段执行 <b>之前</b> 会暂停（interruptBefore），
     * 让用户先审阅上一阶段产出，确认后才继续。
     */
    private boolean requireConfirmation;
    /** 阶段序号（从 1 开始，由 SkillRegistry 注册时回填，供前端定位进度） */
    private Integer phaseIndex;

    @Data
    public static class VariableRef {
        /** 变量名 */
        private String name;
        /** 来源：INPUT / 引用 outputKey */
        private String ref;
    }
}