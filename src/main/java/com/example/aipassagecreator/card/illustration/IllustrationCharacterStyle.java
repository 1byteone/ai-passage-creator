package com.example.aipassagecreator.card.illustration;

/**
 * 插画人物子风格枚举 — 白名单 + 默认值管理。
 * 与 {@link com.example.aipassagecreator.card.CardStyle} 同理，未知回退 HEALING。
 */
public enum IllustrationCharacterStyle {
    HEALING("healing"),
    CUTE("cute"),
    DOODLE("doodle"),
    WATERCOLOR("watercolor");

    private final String name;

    IllustrationCharacterStyle(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static IllustrationCharacterStyle from(String name) {
        if (name == null || name.isBlank()) {
            return HEALING;
        }
        for (IllustrationCharacterStyle style : values()) {
            if (style.name.equalsIgnoreCase(name)) {
                return style;
            }
        }
        return HEALING;
    }
}
