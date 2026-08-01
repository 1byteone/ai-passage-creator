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
}
