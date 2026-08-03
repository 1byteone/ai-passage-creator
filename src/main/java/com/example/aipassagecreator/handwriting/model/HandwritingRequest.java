package com.example.aipassagecreator.handwriting.model;

import jakarta.validation.constraints.NotBlank;

/**
 * 手写渲染请求 — 进入 HandwritingRenderer.renderToHtml 的入口参数。
 */
public record HandwritingRequest(
    @NotBlank
    String content,          // 已清洗的文本内容

    @NotBlank
    String fontName,         // 字体 key (shoushu/tegakizatsu/jiayouya)

    @NotBlank
    String paperType,        // 纸张类型: blank/line/grid/tianzi/dot

    HandwritingParams params, // 扰动参数（null 时用默认值）

    String paperImageUrl     // 自定义纸张背景图 URL（可选）
) {
    public HandwritingParams effectiveParams() {
        return params != null ? params : HandwritingParams.defaults();
    }
}
