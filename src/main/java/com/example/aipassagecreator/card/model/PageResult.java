package com.example.aipassagecreator.card.model;

import lombok.Builder;
import lombok.Data;

/**
 * 单页卡片渲染结果。
 * 含 PNG bytes 与布局 compliance 探针结果。
 */
@Data
@Builder
public class PageResult {
    private int pageNo;
    private byte[] pngBytes;
    private boolean layoutPassed;
    private String layoutReport;  // JSON
    private int renderMs;
    private String errorMessage;
}
