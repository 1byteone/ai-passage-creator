package com.example.aipassagecreator.model.dto.article;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 确认大纲请求
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class ArticleConfirmOutlineRequest implements Serializable {

    @NotBlank(message = "任务ID不能为空")
    private String taskId;

    @NotEmpty(message = "大纲不能为空")
    private List<ArticleState.OutlineSection> outline;

    private static final long serialVersionUID = 1L;
}
