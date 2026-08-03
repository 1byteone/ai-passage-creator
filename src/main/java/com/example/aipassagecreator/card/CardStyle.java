package com.example.aipassagecreator.card;

/**
 * 卡片风格枚举 — 统一白名单 + 默认值管理。
 * 替代 CardTemplateEngine#SUPPORTED_STYLES 和 CardController 中的硬编码 "warm"。
 */
public enum CardStyle {
    WARM("warm"),
    MINIMAL("minimal"),
    FREE("free"),
    HANDWRITING("handwriting");

    private final String name;

    CardStyle(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /**
     * 从字符串解析风格名，未知或 null 回退默认 WARM。
     */
    public static CardStyle from(String name) {
        if (name == null || name.isBlank()) {
            return WARM;
        }
        for (CardStyle style : values()) {
            if (style.name.equalsIgnoreCase(name)) {
                return style;
            }
        }
        return WARM;
    }
}
