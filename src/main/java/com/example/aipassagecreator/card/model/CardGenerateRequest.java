package com.example.aipassagecreator.card.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 卡片生成请求 DTO。
 * <ul>
 *   <li>{@code taskId}：文章任务 ID（必填）；</li>
 *   <li>{@code cardStyle}：卡片风格（warm/minimal/free），为空则从 methodology 读取；</li>
 *   <li>{@code methodologyName}：方法论模板名称（默认 default）。</li>
 * </ul>
 */
@Data
public class CardGenerateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "任务ID不能为空")
    private String taskId;

    /** 卡片风格（warm/minimal/free），为空则从 methodology 读取 */
    private String cardStyle;

    /** 方法论模板名称（默认 default） */
    private String methodologyName;
}
