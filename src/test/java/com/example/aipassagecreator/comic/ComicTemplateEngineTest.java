package com.example.aipassagecreator.comic;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ComicTemplateEngineTest {

    @Autowired private ComicTemplateEngine engine;

    @Test
    void renderEpisode_escapesLlmText() {
        List<Map<String, Object>> panels = List.of(Map.of(
                "panelNo", 1, "captionText", "<script>alert(1)</script>坏文字"));
        String html = engine.renderEpisode(ComicStyle.POWDER, "标题", panels, List.of("data:image/png;base64,x"), List.of(), "副题");
        assertFalse(html.contains("<script>"), "LLM 文本不得以可执行标签进入 HTML");
        assertTrue(html.contains("&lt;script&gt;"), "应当被转义");
    }

    @Test
    void renderEpisode_containsTitleAndStyleCss() {
        String html = engine.renderEpisode(ComicStyle.INKWASH, "雨中散步", List.of(), List.of(), List.of(), "");
        assertTrue(html.contains("雨中散步"));
        // 外链样式表应被内联为 <style>（标准管线禁网，外链加载不到）
        assertTrue(html.contains("<style>"), "画风 CSS 应内联为 <style> 块");
        assertTrue(html.contains("#b23a3a"), "应包含 inkwash 画风色板（朱红点睛）");
        assertFalse(html.contains("<link rel=\"stylesheet\""), "外链样式表应被内联替换");
    }

    @Test
    void renderMonthly_inlinesStyleCss() {
        String html = engine.renderMonthly(ComicStyle.INKWASH, "2026-08",
                List.of(Map.of("title", "第一页", "episodeNo", 1)));
        assertTrue(html.contains("第一页"));
        assertTrue(html.contains("2026-08"));
        assertTrue(html.contains("<style>"), "月册页同样应内联画风 CSS");
        assertTrue(html.contains("#b23a3a"));
        assertFalse(html.contains("<link rel=\"stylesheet\""));
    }

    @Test
    void renderEpisode_nullStyle_fallsBackToDefaultStillValid() {
        // style 缺省回退默认画风，HTML 仍自包含（内联 CSS），不抛 NPE
        String html = engine.renderEpisode(null, "标题", List.of(), List.of(), List.of(), "");
        assertTrue(html.contains("标题"));
        assertTrue(html.contains("<style>"), "空风格回退默认画风仍应内联 CSS");
    }

    @Test
    void renderMonthly_listsEpisodes() {
        String html = engine.renderMonthly(ComicStyle.POWDER, "2026-08",
                List.of(Map.of("title", "第一页", "episodeNo", 1)));
        assertTrue(html.contains("第一页"));
        assertTrue(html.contains("2026-08"));
    }
}
