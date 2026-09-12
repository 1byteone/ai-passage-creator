package com.example.aipassagecreator.handwriting;

import com.example.aipassagecreator.handwriting.model.HandwritingParams;
import com.example.aipassagecreator.handwriting.model.HandwritingRequest;
import com.example.aipassagecreator.service.CosService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class HandwritingRendererTest {

    @Test
    void generatedContentPreservesLineBreaksAndLongTokens() {
        var renderer = new HandwritingRenderer(
                new HandwritingFontManager(mock(CosService.class)), new HandwritingPaperService());
        var request = new HandwritingRequest(
                "第一段\n第二段 https://example.com/a-very-long-token",
                "maoken-yingbi", "line", HandwritingParams.defaults(), null);

        String html = renderer.renderToHtml(request);

        assertTrue(html.contains("white-space: pre-wrap"));
        assertTrue(html.contains("overflow-wrap: anywhere"));
        assertTrue(html.contains("第一段<br>第二段"));
    }
}
