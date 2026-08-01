package com.example.aipassagecreator.model.dto.article;

import lombok.Data;

/**
 * 文章回退到指定版本请求
 */
@Data
public class ArticleRevertRequest {

    /** 文章任务 ID */
    private String taskId;

    /** 目标版本号 */
    private Integer versionNo;
}
