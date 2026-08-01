package com.example.aipassagecreator.skill;

/**
 * Skill 输出解析器接口
 */
public interface SkillOutputParser<T> {

    /** 解析 LLM 输出 */
    T parse(String llmOutput, PhaseDefinition phase);

    /** 解析器类型标识：json / markdown / raw / pptx */
    String getType();
}
