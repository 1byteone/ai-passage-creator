package com.example.aipassagecreator.skill;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Skill 定义 — 对应 skill.yaml 的结构
 */
@Data
public class SkillDefinition {
    /** Skill 名称：proofreading, topic-gen, slides */
    private String name;
    /** Skill 描述 */
    private String description;
    /** 分类：writing/design/research/image */
    private String category;
    /** 所需角色：user/vip/admin */
    private List<String> requiredRoles;
    /** 是否多轮交互 */
    private boolean isMultiRound;
    /** 全局变量声明 */
    private Map<String, VariableDef> variables;
    /** 阶段定义列表 */
    private List<PhaseDefinition> phases;
}