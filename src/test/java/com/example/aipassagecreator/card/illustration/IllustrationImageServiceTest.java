package com.example.aipassagecreator.card.illustration;

import com.example.aipassagecreator.service.AgnesImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IllustrationImageServiceTest {

    private IllustrationImageService service;
    private AgnesImageService agnes;
    private StaticIllustrationLibrary staticLib;

    @BeforeEach
    void setUp() {
        agnes = org.mockito.Mockito.mock(AgnesImageService.class);
        staticLib = org.mockito.Mockito.mock(StaticIllustrationLibrary.class);
        service = new IllustrationImageService(
                agnes,
                new IllustrationPromptBuilder(),
                staticLib);
    }

    @Test
    void generateCoverImage_aiSuccess_returnsAiUrl() {
        org.mockito.Mockito.when(agnes.searchImage(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("https://cos.example.com/cover.png");
        String url = service.generateCoverImage("标题", IllustrationCharacterStyle.HEALING);
        assertEquals("https://cos.example.com/cover.png", url);
    }

    @Test
    void generateCoverImage_aiFail_fallsBackToStatic() {
        org.mockito.Mockito.when(agnes.searchImage(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(null);
        org.mockito.Mockito.when(staticLib.getUrl(IllustrationCharacterStyle.HEALING))
                .thenReturn("classpath:illustration/healing/healing-1.png");
        String url = service.generateCoverImage("标题", IllustrationCharacterStyle.HEALING);
        assertEquals("classpath:illustration/healing/healing-1.png", url);
    }

    @Test
    void generateCoverImage_aiThrows_fallsBackToStatic() {
        org.mockito.Mockito.when(agnes.searchImage(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new RuntimeException("AI 服务异常"));
        org.mockito.Mockito.when(staticLib.getUrl(IllustrationCharacterStyle.HEALING))
                .thenReturn("classpath:illustration/healing/healing-1.png");
        String url = service.generateCoverImage("标题", IllustrationCharacterStyle.HEALING);
        assertEquals("classpath:illustration/healing/healing-1.png", url);
    }
}
