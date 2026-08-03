package com.example.aipassagecreator.handwriting.model;

/**
 * 手写字体 VO — 返回给前端的字体信息。
 */
public record HandwritingFont(
    String name,       // 中文显示名，如 "手书体"
    String key,        // 内部 key，如 "shoushu"
    String previewText  // 预览样本文本
) {}
