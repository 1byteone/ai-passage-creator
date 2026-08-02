package com.example.aipassagecreator.model.dto.article;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 确认标题请求
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class ArticleConfirmTitleRequest implements Serializable {

    @NotBlank(message = "任务ID不能为空")
    private String taskId;

    @NotBlank(message = "主标题不能为空")
    private String selectedMainTitle;

    @NotBlank(message = "副标题不能为空")
    private String selectedSubTitle;

    /**
     * 用户补充描述（可选）
     */
    private String userDescription;

    /**
     * 选中标题命中的策略 key（可选）
     */
    private String strategyKey;

    private static final long serialVersionUID = 1L;
}
