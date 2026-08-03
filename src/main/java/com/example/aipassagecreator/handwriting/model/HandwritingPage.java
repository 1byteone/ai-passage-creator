package com.example.aipassagecreator.handwriting.model;

/**
 * 手写分页结果 — 单页内容结构。
 */
public record HandwritingPage(
    int pageNo,
    String title,        // 页面标题（可选）
    String contentMd     // Markdown 内容（已按段落边界分页）
) {}
