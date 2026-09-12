package com.example.aipassagecreator.handwriting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HandwritingPaperServiceTest {

    private final HandwritingPaperService service = new HandwritingPaperService();

    @Test
    void linePaperUsesTheSameBaselineGridAsHandwritingText() {
        String css = service.lineCss();

        assertTrue(css.contains("transparent 0, transparent 55px"));
        assertTrue(css.contains("#B8C6DB 55px, #B8C6DB 56px"));
        assertTrue(css.contains("background-size: 100% 56px"));
        assertTrue(css.contains("background-position: 0 48px"));
    }
}
