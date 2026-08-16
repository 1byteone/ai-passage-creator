package com.example.aipassagecreator.comic;

import com.example.aipassagecreator.card.CardImageResolver;
import com.example.aipassagecreator.card.CardRenderPipeline;
import com.example.aipassagecreator.card.model.PageResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 漫画 PNG 渲染服务单测 — 验证远端图在交给渲染管线前被内联为 data URL。
 * Mock CardRenderPipeline/CardImageResolver，不发起真实 Playwright 与 HTTP。
 */
@ExtendWith(MockitoExtension.class)
class ComicRenderServiceTest {

    @Mock private CardRenderPipeline cardRenderPipeline;
    @Mock private CardImageResolver cardImageResolver;

    @InjectMocks private ComicRenderService renderService;

    @Test
    void renderToPngDataUrl_inlinesRemoteImageBeforeRender() {
        String html = "<html><body><img src=\"https://cos/img.png\" alt=\"\"/></body></html>";
        when(cardRenderPipeline.isHealthy()).thenReturn(true);
        when(cardImageResolver.toDataUrl("https://cos/img.png"))
                .thenReturn("data:image/png;base64,AAAA");
        when(cardRenderPipeline.render(any(), any()))
                .thenReturn(List.of(PageResult.builder().pngBytes(new byte[]{1, 2, 3}).build()));

        String result = renderService.renderToPngDataUrl(html, "task-1");

        // 交给渲染管线的 HTML 里远端图已被内联为 data URL，不再含远端链接
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(cardRenderPipeline).render(captor.capture(), eq("task-1"));
        String renderedHtml = captor.getValue().get(0);
        assertTrue(renderedHtml.contains("data:image/png;base64,AAAA"), "远端图应内联为 data URL");
        assertFalse(renderedHtml.contains("https://cos/img.png"), "原远端 URL 不应再出现");
        assertEquals("data:image/png;base64,AQID", result, "返回契约不变：base64 PNG data URL");
    }

    @Test
    void renderToPngDataUrl_inlineFails_keepsOriginalUrl() {
        String html = "<img src=\"https://cos/img.png\"/>";
        when(cardRenderPipeline.isHealthy()).thenReturn(true);
        when(cardImageResolver.toDataUrl("https://cos/img.png")).thenReturn(null); // 下载失败
        when(cardRenderPipeline.render(any(), any()))
                .thenReturn(List.of(PageResult.builder().pngBytes(new byte[]{1}).build()));

        renderService.renderToPngDataUrl(html, "task-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(cardRenderPipeline).render(captor.capture(), eq("task-1"));
        assertTrue(captor.getValue().get(0).contains("https://cos/img.png"),
                "内联失败应保留原 URL，不阻断整页渲染");
    }

    @Test
    void renderToPngDataUrl_engineUnhealthy_returnsNullWithoutInlining() {
        when(cardRenderPipeline.isHealthy()).thenReturn(false);

        String result = renderService.renderToPngDataUrl(
                "<img src=\"https://cos/img.png\"/>", "task-1");

        assertNull(result, "渲染引擎不可用应返回 null（pngUrl 降级为生图 URL）");
        verify(cardRenderPipeline, never()).render(any(), any());
        verify(cardImageResolver, never()).toDataUrl(any());
    }
}
