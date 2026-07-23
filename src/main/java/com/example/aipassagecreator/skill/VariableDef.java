package com.example.aipassagecreator.skill;

import lombok.Data;

/**
 * Skill 变量定义
 */
@Data
public class VariableDef {
    /** 变量名 */
    private String name;
    /** 变量描述 */
    private String description;
    /** 是否必须 */
    private boolean required;
    /** 来源：INPUT / PHASE_OUTPUT */
    private String source = "INPUT";
    /** 来源阶段（source=PHASE_OUTPUT时使用） */
    private String phaseRef;
}