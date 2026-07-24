package com.example.aipassagecreator.skill;

import lombok.Data;

import java.util.List;

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
    /** 前端控件类型：input / textarea / select / radio */
    private String uiType = "input";
    /** 可选项 */
    private List<OptionDef> options;
    /** 默认值 */
    private Object defaultValue;
    /** 输入提示 */
    private String placeholder;
    /** 最大长度 */
    private Integer maxLength;

    @Data
    public static class OptionDef {
        private String label;
        private Object value;
    }
}
