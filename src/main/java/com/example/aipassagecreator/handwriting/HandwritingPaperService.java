package com.example.aipassagecreator.handwriting;

import org.springframework.stereotype.Component;

/**
 * 纸张背景服务 — 纯 CSS 生成 5 种纸张背景。
 * 使用 repeating-linear-gradient，保证 300 DPI 高清渲染。
 */
@Component
public class HandwritingPaperService {

    /**
     * 生成纸张背景 CSS。
     *
     * @param paperType      纸张类型: blank/line/grid/tianzi/dot
     * @param customImageUrl 自定义纸张背景图 URL（可选，非 blank 时优先）
     * @return CSS 样式字符串
     */
    public String generatePaperCss(String paperType, String customImageUrl) {
        if (customImageUrl != null && !customImageUrl.isBlank()) {
            return customImageCss(customImageUrl);
        }
        return switch (paperType != null ? paperType : "blank") {
            case "line"   -> lineCss();
            case "grid"   -> gridCss();
            case "tianzi" -> tianziCss();
            case "dot"    -> dotCss();
            default       -> blankCss();
        };
    }

    String blankCss() {
        return "background: #FFFFFF;";
    }

    /**
     * 横线纸：行距必须与 HandwritingRenderer 的 28px/2.0（56px）一致。
     * 首条线放在正文首行基线附近，避免文字落在两条横线之间。
     */
    String lineCss() {
        return """
            background-color: #FFFFFF;
            background-image: repeating-linear-gradient(
                transparent 0, transparent 55px,
                #B8C6DB 55px, #B8C6DB 56px
            );
            background-size: 100% 56px;
            background-position: 0 48px;
            """;
    }

    /** 方格纸：交叉 repeating-linear-gradient */
    String gridCss() {
        return """
            background-color: #FFFFFF;
            background-image:
                repeating-linear-gradient(
                    #D0DAE8 0px, #D0DAE8 1px,
                    transparent 1px, transparent 40px
                ),
                repeating-linear-gradient(
                    90deg,
                    #D0DAE8 0px, #D0DAE8 1px,
                    transparent 1px, transparent 40px
                );
            background-size: 40px 40px;
            """;
    }

    /** 田字格：实线格 + 虚线十字 */
    String tianziCss() {
        return """
            background-color: #FFFFFF;
            background-image:
                repeating-linear-gradient(
                    transparent, transparent 39px,
                    #FF8A80 39px, #FF8A80 40px
                ),
                repeating-linear-gradient(
                    90deg,
                    transparent, transparent 39px,
                    #FF8A80 39px, #FF8A80 40px
                ),
                repeating-linear-gradient(
                    #E0E0E0 0px, #E0E0E0 1px,
                    transparent 1px, transparent 20px
                ),
                repeating-linear-gradient(
                    90deg,
                    #E0E0E0 0px, #E0E0E0 1px,
                    transparent 1px, transparent 20px
                );
            background-size: 40px 40px, 40px 40px, 20px 20px, 20px 20px;
            """;
    }

    /** 点阵纸：radial-gradient 圆点 */
    String dotCss() {
        return """
            background-color: #FFFFFF;
            background-image: radial-gradient(circle, #C4C4C4 1px, transparent 1px);
            background-size: 20px 20px;
            """;
    }

    private String customImageCss(String imageUrl) {
        return "background-image: url('" + imageUrl + "'); " +
               "background-size: cover; " +
               "background-position: center; " +
               "background-repeat: no-repeat;";
    }
}
