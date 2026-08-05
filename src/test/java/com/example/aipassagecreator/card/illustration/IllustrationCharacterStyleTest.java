package com.example.aipassagecreator.card.illustration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IllustrationCharacterStyleTest {

    @Test
    void from_knownNames_returnsEnum() {
        assertEquals(IllustrationCharacterStyle.HEALING, IllustrationCharacterStyle.from("healing"));
        assertEquals(IllustrationCharacterStyle.CUTE, IllustrationCharacterStyle.from("cute"));
        assertEquals(IllustrationCharacterStyle.DOODLE, IllustrationCharacterStyle.from("doodle"));
        assertEquals(IllustrationCharacterStyle.WATERCOLOR, IllustrationCharacterStyle.from("watercolor"));
    }

    @Test
    void from_nullOrBlank_returnsDefault() {
        assertEquals(IllustrationCharacterStyle.HEALING, IllustrationCharacterStyle.from(null));
        assertEquals(IllustrationCharacterStyle.HEALING, IllustrationCharacterStyle.from(""));
        assertEquals(IllustrationCharacterStyle.HEALING, IllustrationCharacterStyle.from("  "));
    }

    @Test
    void from_unknownName_returnsDefault() {
        assertEquals(IllustrationCharacterStyle.HEALING, IllustrationCharacterStyle.from("neon"));
    }

    @Test
    void getName_returnsKey() {
        assertEquals("healing", IllustrationCharacterStyle.HEALING.getName());
        assertEquals("watercolor", IllustrationCharacterStyle.WATERCOLOR.getName());
    }
}
