package com.example.aipassagecreator.model.dto.article;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * AI 修改大纲请求
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Data
public class ArticleAiModifyOutlineRequest implements Serializable {

    @NotBlank(message = "任务ID不能为空")
    private String taskId;

    @NotBlank(message = "修改建议不能为空")
    private String modifySuggestion;

    private static final long serialVersionUID = 1L;
}
