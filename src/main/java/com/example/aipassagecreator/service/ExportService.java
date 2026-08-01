package com.example.aipassagecreator.service;

import java.io.ByteArrayOutputStream;

/**
 * 文章多格式导出服务
 */
public interface ExportService {

    enum Format { PDF, DOCX, PPTX }

    /**
     * 将 Markdown 内容导出为指定格式
     *
     * @param markdown Markdown 内容
     * @param title    文档标题
     * @param format   目标格式
     * @return 文件字节数组
     */
    ByteArrayOutputStream export(String markdown, String title, Format format);

    /**
     * 为文章 taskId 执行导出
     */
    byte[] exportArticle(String taskId, Format format);
}
