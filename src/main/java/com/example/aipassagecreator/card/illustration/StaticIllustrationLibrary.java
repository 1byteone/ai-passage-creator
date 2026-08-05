package com.example.aipassagecreator.card.illustration;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * 静态插画素材库 — AI 生图失败时的熔断兜底。
 * <p>素材内嵌于 classpath:illustration/{style}/，授权记录见同目录 LICENSES.md。
 * 每个子风格预置 2-3 张，AI 失败时按风格取第一张。</p>
 */
@Component
public class StaticIllustrationLibrary {

    private static final Map<IllustrationCharacterStyle, String> RESOURCES = new EnumMap<>(IllustrationCharacterStyle.class);

    static {
        RESOURCES.put(IllustrationCharacterStyle.HEALING, "classpath:illustration/healing/healing-1.png");
        RESOURCES.put(IllustrationCharacterStyle.CUTE, "classpath:illustration/cute/cute-1.png");
        RESOURCES.put(IllustrationCharacterStyle.DOODLE, "classpath:illustration/doodle/doodle-1.png");
        RESOURCES.put(IllustrationCharacterStyle.WATERCOLOR, "classpath:illustration/watercolor/watercolor-1.png");
    }

    /**
     * 返回指定子风格的静态素材 classpath URL。
     */
    public String getUrl(IllustrationCharacterStyle style) {
        return RESOURCES.get(style);
    }
}
