package com.example.aipassagecreator.model.dto.skill;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Skill 链式编排请求
 * <p>
 * 按顺序串行执行多个 skill，前一个 skill 的最终输出作为后一个的输入。
 */
@Data
public class SkillChainExecuteRequest {

    /** 按顺序执行的 skill 名称列表（至少 2 个） */
    private List<String> skillNames;

    /** 初始输入，作为第一个 skill 的输入 */
    private Map<String, Object> inputs;
}
