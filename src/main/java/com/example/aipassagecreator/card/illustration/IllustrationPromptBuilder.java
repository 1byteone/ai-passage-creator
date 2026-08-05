package com.example.aipassagecreator.card.illustration;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 插画人物提示词母版组装器。
 * <p>母版固定框架 + 子风格变量 + 主题变量。主题变量仅取 mainTitle 纯文本，
 * 经关键词表映射为人物形象，不拼接用户自由文本（防 prompt injection）。</p>
 */
@Component
public class IllustrationPromptBuilder {

    /** 主题关键词 → 人物形象描述 */
    private static final Map<String, String> TOPIC_MAP = new LinkedHashMap<>();
    static {
        TOPIC_MAP.put("学习", "书桌前的学生");
        TOPIC_MAP.put("效率", "书桌前的学生");
        TOPIC_MAP.put("知识", "书桌前的学生");
        TOPIC_MAP.put("读书", "书桌前的学生");
        TOPIC_MAP.put("养生", "品茶的女性");
        TOPIC_MAP.put("健康", "晨练的年轻人");
        TOPIC_MAP.put("饮食", "做饭的厨师");
        TOPIC_MAP.put("美食", "做饭的厨师");
        TOPIC_MAP.put("科技", "用电脑的职场青年");
        TOPIC_MAP.put("互联网", "用电脑的职场青年");
        TOPIC_MAP.put("城市", "漫步城市的路人");
        TOPIC_MAP.put("旅行", "背包旅行者");
        TOPIC_MAP.put("情感", "温柔微笑的人物");
        TOPIC_MAP.put("成长", "微笑的年轻人");
        TOPIC_MAP.put("职场", "穿职业装的上班族");
        TOPIC_MAP.put("理财", "认真记账的青年");
        TOPIC_MAP.put("心理", "安静思考的人");
    }

    private static final Map<IllustrationCharacterStyle, String> STYLE_VISUAL = new LinkedHashMap<>();
    static {
        STYLE_VISUAL.put(IllustrationCharacterStyle.HEALING,
                "柔和线条，温暖光晕，暖米黄+焦糖橙+奶油白+抹茶绿配色");
        STYLE_VISUAL.put(IllustrationCharacterStyle.CUTE,
                "圆润可爱几何，柔和色块，奶油白+珊瑚粉+薄荷绿配色");
        STYLE_VISUAL.put(IllustrationCharacterStyle.DOODLE,
                "马克笔笔触，粗黑描边，米白纸纹+深棕+薄荷绿配色");
        STYLE_VISUAL.put(IllustrationCharacterStyle.WATERCOLOR,
                "毛笔线条，淡彩晕染，浅米纸纹+朱红+墨黑配色");
    }

    /**
     * 组装完整生图提示词。
     */
    public String build(String mainTitle, IllustrationCharacterStyle style) {
        String character = resolveCharacterDescription(mainTitle);
        String visual = STYLE_VISUAL.get(style);
        return "竖版卡片封面插画人物，" + character + "，" + visual +
                "，主体突出，背景干净留白，预留标题区（下方 30% 区域留白放标题），" +
                "无文字，竖版 9:16，治愈系插画，高清细腻";
    }

    /**
     * 主题 → 人物形象映射。命中关键词返回对应描述，否则返回默认「笑脸人物」。
     */
    public String resolveCharacterDescription(String mainTitle) {
        if (mainTitle == null || mainTitle.isBlank()) {
            return "笑脸人物";
        }
        for (Map.Entry<String, String> e : TOPIC_MAP.entrySet()) {
            if (mainTitle.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return "笑脸人物";
    }
}
