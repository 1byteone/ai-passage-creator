package com.example.aipassagecreator.comic;

/** 画风枚举 — 生图提示词约束 + Thymeleaf 模板路径双写基线（完全自建，不取自外部仓库） */
public enum ComicStyle {
    POWDER("powder", "雪白底、冰蓝炭灰、粉蜡笔触、大留白、安静不冷淡、手帐排版"),
    GOUACHE("gouache", "奶油纸、低饱和粉彩、松软晕染、空气感、云层水粉"),
    COLORPENCIL("colorpencil", "白底、清晰彩铅线条、认真手记感、明快色点、少量留白"),
    INKWASH("inkwash", "宣纸白、细墨线稿、稳健笔触、适合阅读、朱红点睛");

    private final String name;
    private final String promptConstraint;

    ComicStyle(String name, String promptConstraint) {
        this.name = name;
        this.promptConstraint = promptConstraint;
    }

    public String getName() { return name; }
    public String getPromptConstraint() { return promptConstraint; }
    public String getTemplatePrefix() { return "comic/" + name; }
    public String getCssFile() { return getTemplatePrefix() + "/style.css"; }

    /** 未知或 null 回退默认粉蜡，防模板路径注入 */
    public static ComicStyle from(String style) {
        if (style == null || style.isBlank()) {
            return POWDER;
        }
        for (ComicStyle s : values()) {
            if (s.name.equals(style)) {
                return s;
            }
        }
        return POWDER;
    }
}
