package com.example.aipassagecreator.model.po;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "skill_execution")
public class SkillExecutionPo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /** 唯一执行 ID */
    private String skillExecutionId;

    /** Skill 名称 */
    private String skillName;

    /** 关联文章 taskId（可选） */
    private String taskId;

    /** 执行用户 */
    private Long userId;

    /** PENDING/RUNNING/SUCCESS/FAILED */
    private String status;

    /** 当前阶段 */
    private String phase;

    /** 输入数据（JSON） */
    private String inputData;

    /** 输出数据（JSON） */
    private String outputData;

    /** 结果文件 URL */
    private String resultUrl;

    /** Token 消耗 */
    private Integer tokenUsage;

    /** 使用的模型 */
    private String modelUsed;

    /** 总耗时（毫秒） */
    private Integer durationMs;

    /** 错误信息 */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 逻辑删除 */
    @Column(isLogicDelete = true)
    private Integer isDelete;
}