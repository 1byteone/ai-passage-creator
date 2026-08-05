package com.example.aipassagecreator.card.model;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class PagePlan {
    private int pageNo;
    private String pageType;    // COVER / CONTENT
    private String title;       // 章节标题（封面页为 mainTitle）
    private String contentMd;   // Markdown 原文
    private String contentHtml; // flexmark 转 HTML
    /** 该页配图 URL（内容页挂对应 position 的图片；封面页为 coverImage） */
    private String imageUrl;
    /** 该页配图 base64 data URL（渲染时内联，避免外网加载） */
    private String imageBase64;
}
