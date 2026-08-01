package com.example.aipassagecreator.model.dto.article;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 爆款维度评测请求
 */
@Data
public class ArticleEvaluateViralRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 文章任务 ID */
    private String taskId;

    /** 方法论模板名称（默认 default） */
    private String methodologyName;
}
