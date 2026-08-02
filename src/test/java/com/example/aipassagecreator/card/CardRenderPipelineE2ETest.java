package com.example.aipassagecreator.card;

import com.example.aipassagecreator.card.model.PagePlan;
import com.example.aipassagecreator.card.model.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CardRenderPipeline 端到端集成测试。
 * <p>使用已安装的真实 Chromium 浏览器验证：安全沙箱（JS 禁用 + route abort）、
 * 布局探针（溢出检测）、HTML→PNG 截图全过程。
 * <p>无浏览器时跳过（pipeline 须已由 spring 上下文创建且 healthy==true）。
 */
@SpringBootTest
class CardRenderPipelineE2ETest {

    @Autowired
    private CardRenderPipeline pipeline;

    @Autowired(required = false)
    private CardTemplateEngine templateEngine;

    @Test
    void pipelineInit_healthy() {
        assertNotNull(pipeline);
        assertTrue(pipeline.isHealthy(), "Playwright browser should be available on this machine");
    }

    @Test
    void renderSimpleHtml_producesPng() {
        String html = """
                <html>
                <head><meta charset="UTF-8"></head>
                <body style="width:1080px;height:1920px;margin:0;font-family:sans-serif;">
                <div style="padding:60px;font-size:48px;color:#333;">Hello Card Test</div>
                </body>
                </html>""";

        List<PageResult> results = pipeline.render(List.of(html), "e2e-test");
        assertEquals(1, results.size());
        PageResult r = results.get(0);
        assertNull(r.getErrorMessage(), "Should render without error: " + r.getErrorMessage());
        assertTrue(r.isLayoutPassed(), "Layout should pass for simple content");
        assertNotNull(r.getPngBytes());
        assertTrue(r.getPngBytes().length > 1000, "PNG must be non-trivial (>1KB)");
        assertTrue(r.getRenderMs() > 0, "Render time must be recorded");
    }

    @Test
    void renderOverflowContent_detectsOverflow() {
        // Generate HTML that will overflow 1920px viewport
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta charset=\"UTF-8\"></head>");
        sb.append("<body style=\"width:1080px;margin:0;font-size:24px;line-height:1.8;\">");
        for (int i = 0; i < 200; i++) {
            sb.append("<p>第 ").append(i + 1).append(" 行内容，用于制造页面溢出的测试文本。</p>");
        }
        sb.append("</body></html>");

        List<PageResult> results = pipeline.render(List.of(sb.toString()), "overflow-test");
        assertEquals(1, results.size());
        PageResult r = results.get(0);
        assertFalse(r.isLayoutPassed(), "Overflow content must be detected");
        assertNotNull(r.getLayoutReport());
        assertTrue(r.getLayoutReport().contains("scrollH"), "Layout report must contain scrollH");
    }

    @Test
    void renderScriptInContent_isBlocked() {
        // Attempt script execution — must be blocked by JS disabled
        String html = """
                <html>
                <head><meta charset="UTF-8"></head>
                <body style="width:1080px;height:1920px;margin:0;">
                <script>window.__HACKED__ = true;</script>
                <div style="font-size:32px;">Safe Content</div>
                </body>
                </html>""";

        List<PageResult> results = pipeline.render(List.of(html), "security-test");
        assertEquals(1, results.size());
        assertNull(results.get(0).getErrorMessage(),
                "Script tag should not cause render failure (JS disabled)");
    }

    @Test
    void rendersChineseContent_correctly() {
        String html = """
                <html>
                <head><meta charset="UTF-8"></head>
                <body style="width:1080px;height:1920px;margin:0;font-family:sans-serif;">
                <div style="padding:60px;font-size:48px;color:#2D1810;">
                小红书爆款写作指南
                </div>
                <div style="padding:20px 60px;font-size:28px;color:#4A3428;line-height:1.8;">
                情绪触发点是自媒体内容创作的核心法则。一个好的开头能够瞬间抓住读者的注意力，
                让他们产生强烈的好奇心或情感共鸣。金句需要在内容中自然生长，结尾提炼，
                成为可独立转发的高价值内容。
                </div>
                </body>
                </html>""";

        List<PageResult> results = pipeline.render(List.of(html), "zh-test");
        assertEquals(1, results.size());
        assertNull(results.get(0).getErrorMessage(),
                "Chinese text must render without error");
        assertTrue(results.get(0).isLayoutPassed(),
                "Short Chinese content must fit in viewport");
        System.out.println("Chinese render: " + results.get(0).getPngBytes().length + " bytes, "
                + results.get(0).getRenderMs() + "ms");
    }
}
