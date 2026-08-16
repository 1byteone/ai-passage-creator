package com.example.aipassagecreator.comic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComicStyleTest {

    @Test
    void from_knownName_returnsEnum() {
        assertEquals(ComicStyle.POWDER, ComicStyle.from("powder"));
        assertEquals(ComicStyle.INKWASH, ComicStyle.from("inkwash"));
    }

    @Test
    void from_unknownOrNull_fallsBackToPowder() {
        assertEquals(ComicStyle.POWDER, ComicStyle.from("bogus"));
        assertEquals(ComicStyle.POWDER, ComicStyle.from(null));
        assertEquals(ComicStyle.POWDER, ComicStyle.from(""));
    }

    @Test
    void eachStyle_hasPromptAndCss() {
        for (ComicStyle style : ComicStyle.values()) {
            assertFalse(style.getPromptConstraint().isBlank(), style.name() + " 缺提示词约束");
            assertTrue(style.getCssFile().endsWith("/" + style.getName() + "/style.css"));
        }
    }
}
