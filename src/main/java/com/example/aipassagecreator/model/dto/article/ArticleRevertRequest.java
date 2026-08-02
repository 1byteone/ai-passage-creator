package com.example.aipassagecreator.model.dto.article;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文章回退到指定版本请求
 */
@Data
public class ArticleRevertRequest {

    @NotBlank(message = "任务ID不能为空")
    private String taskId;

    @NotNull(message = "版本号不能为空")
    private Integer versionNo;
}
