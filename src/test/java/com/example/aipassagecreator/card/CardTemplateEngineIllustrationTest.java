package com.example.aipassagecreator.card;

import com.example.aipassagecreator.card.model.PagePlan;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * illustration 风格渲染测试 — 封面页（大图 + 标题）与内容页（角标 + 正文）。
 * <p>模板只消费 {@link CardTemplateEngine} 注入的通用变量（title/content/contentHtml/
 * imageDataUrl/pageNo/pageType），不依赖 illustration 专属注入。CardImageResolver
 * 以 {@link MockitoBean} 替换（真实实现会发起 HTTP 下载）。</p>
 */
@SpringBootTest
class CardTemplateEngineIllustrationTest {

    @Autowired
    private CardTemplateEngine engine;

    @MockitoBean
    private CardImageResolver imageResolver;

    // 模板渲染不依赖截图管线；替换真实 Bean 以避免启动时拉起 Playwright 浏览器（提速）
    @MockitoBean
    private CardRenderPipeline renderPipeline;

    @Test
    void render_coverAndContentPages_usesIllustrationTemplate() {
        // 模拟配图解析：模板经 imageDataUrl 变量拿到 base64 data URL
        when(imageResolver.toDataUrl(any())).thenReturn("data:image/png;base64,xxx");

        PagePlan cover = PagePlan.builder()
                .pageNo(1).pageType("COVER")
                .title("标题").contentMd("副标题")
                .imageUrl("http://example.com/cover.png").build();
        PagePlan content = PagePlan.builder()
                .pageNo(2).pageType("CONTENT")
                .title("章节标题")
                .contentMd("正文")
                .contentHtml("<p>正文</p>")
                .imageUrl("http://example.com/icon.png").build();

        List<String> html = engine.render(List.of(cover, content), "illustration");

        // 封面页：大图 + 标题
        assertTrue(html.get(0).contains("cover-title"));
        assertTrue(html.get(0).contains("data:image/png;base64,xxx"));
        assertTrue(html.get(0).contains("标题"));
        // 无指定子风格 → 默认 healing 色板
        assertTrue(html.get(0).contains("palette-healing"));
        // 内容页：角标 + 章节标题 + 正文 HTML + 页码
        assertTrue(html.get(1).contains("content-icon"));
        assertTrue(html.get(1).contains("data:image/png;base64,xxx"));
        assertTrue(html.get(1).contains("章节标题"));
        assertTrue(html.get(1).contains("<p>正文</p>"));
        assertTrue(html.get(1).contains("2"));
    }

    @Test
    void render_withCharacterStyle_appliesPaletteClass() {
        when(imageResolver.toDataUrl(any())).thenReturn("data:image/png;base64,xxx");

        PagePlan cover = PagePlan.builder()
                .pageNo(1).pageType("COVER")
                .title("标题").contentMd("副标题")
                .imageUrl("http://example.com/cover.png").build();

        List<String> html = engine.render(List.of(cover), "illustration", "cute");

        // 3 参重载注入 characterStyle → 色板类出现在 body 与卡片容器上（S2）
        assertTrue(html.get(0).contains("palette-cute"));
        assertTrue(html.get(0).contains("palette-healing") == false,
                "指定 cute 时不应带默认 healing 色板类");
    }

    @Test
    void render_nullCharacterStyle_normalizesToHealingPalette() {
        when(imageResolver.toDataUrl(any())).thenReturn(null);

        PagePlan cover = PagePlan.builder()
                .pageNo(1).pageType("COVER")
                .title("标题").contentMd("内容").build();

        List<String> html = engine.render(List.of(cover), "illustration", null);

        assertTrue(html.get(0).contains("palette-healing"),
                "null 子风格应归一化为默认 healing 色板");
    }

    @Test
    void render_pageTypeNull_rendersAsContentPage() {
        when(imageResolver.toDataUrl(any())).thenReturn(null);

        PagePlan cover = PagePlan.builder()
                .pageNo(1)
                .title("标题").contentMd("内容").build();

        List<String> html = engine.render(List.of(cover), "illustration");

        assertTrue(html.get(0).contains("content-icon"));
        // 封面分支的 div 仅在 pageType == 'COVER' 时渲染（CSS 中的同名类恒在，须按完整标签判别）
        assertFalse(html.get(0).contains("class=\"card-page cover-layout\""));
    }
}
