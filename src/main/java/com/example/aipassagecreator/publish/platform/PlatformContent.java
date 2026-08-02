package com.example.aipassagecreator.publish.platform;

import java.util.List;
import java.util.Map;

/**
 * 平台转换后的内容。
 */
public record PlatformContent(
        String title,
        String body,
        List<String> topics,
        Map<String, Object> metadata
) {}
