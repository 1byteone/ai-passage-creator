package com.example.aipassagecreator.model.dto.skill;

import com.example.aipassagecreator.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 查询 Skill 执行历史请求
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SkillExecutionQueryRequest extends PageRequest implements Serializable {

    /**
     * Skill 名称（可选，按技能筛选）
     */
    private String skillName;

    /**
     * 执行状态（可选）：PENDING/RUNNING/SUCCESS/FAILED
     */
    private String status;

    private static final long serialVersionUID = 1L;
}
