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
        assertTrue(html.contains("inkwash/style.css"));
    }

    @Test
    void renderMonthly_listsEpisodes() {
        String html = engine.renderMonthly(ComicStyle.POWDER, "2026-08",
                List.of(Map.of("title", "第一页", "episodeNo", 1)));
        assertTrue(html.contains("第一页"));
        assertTrue(html.contains("2026-08"));
    }
}
