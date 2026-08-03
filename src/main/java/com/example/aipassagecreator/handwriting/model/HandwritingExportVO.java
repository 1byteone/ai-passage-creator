package com.example.aipassagecreator.handwriting.model;

/**
 * 手写导出响应 VO。
 */
public record HandwritingExportVO(
    String taskId,
    String progressUrl
) {}
