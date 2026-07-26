package com.example.aipassagecreator.model.vo;

import com.example.aipassagecreator.model.po.SkillExecutionPo;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Skill 执行历史列表项
 * <p>
 * 仅包含列表展示所需的摘要字段，不返回体积较大的
 * inputData / outputData，详情请调用 {@code GET /skill/{executionId}/result}。
 */
@Data
@Builder
public class SkillExecutionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 唯一执行 ID */
    private String skillExecutionId;

    /** Skill 名称 */
    private String skillName;

    /** 执行状态 */
    private String status;

    /** 当前/最终阶段 */
    private String phase;

    /** Token 消耗 */
    private Integer tokenUsage;

    /** 实际使用的模型 */
    private String modelUsed;

    /** 总耗时（毫秒） */
    private Integer durationMs;

    /** 错误信息（失败时） */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * PO 转 VO
     *
     * @param po 执行记录
     * @return VO，入参为 null 时返回 null
     */
    public static SkillExecutionVO objToVo(SkillExecutionPo po) {
        if (po == null) {
            return null;
        }
        return SkillExecutionVO.builder()
                .skillExecutionId(po.getSkillExecutionId())
                .skillName(po.getSkillName())
                .status(po.getStatus())
                .phase(po.getPhase())
                .tokenUsage(po.getTokenUsage())
                .modelUsed(po.getModelUsed())
                .durationMs(po.getDurationMs())
                .errorMessage(po.getErrorMessage())
                .createTime(po.getCreateTime())
                .build();
    }
}
