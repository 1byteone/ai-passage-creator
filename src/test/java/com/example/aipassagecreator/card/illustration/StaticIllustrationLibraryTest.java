package com.example.aipassagecreator.card.illustration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StaticIllustrationLibraryTest {

    private final StaticIllustrationLibrary library = new StaticIllustrationLibrary();

    @Test
    void getUrl_allStyles_haveResource() {
        for (IllustrationCharacterStyle style : IllustrationCharacterStyle.values()) {
            String url = library.getUrl(style);
            assertNotNull(url, style.getName() + " 应有素材");
            assertFalse(url.isBlank(), style.getName() + " 素材 URL 非空");
        }
    }

    @Test
    void getUrl_staticResourceExists() {
        String url = library.getUrl(IllustrationCharacterStyle.HEALING);
        assertTrue(url.startsWith("classpath:illustration/healing/"), url);
    }
}
