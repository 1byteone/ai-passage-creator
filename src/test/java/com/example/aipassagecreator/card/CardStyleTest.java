package com.example.aipassagecreator.card;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CardStyleTest {

    @Test
    void from_validName_returnsCorrectStyle() {
        assertEquals(CardStyle.WARM, CardStyle.from("warm"));
        assertEquals(CardStyle.MINIMAL, CardStyle.from("minimal"));
        assertEquals(CardStyle.FREE, CardStyle.from("free"));
        assertEquals(CardStyle.HANDWRITING, CardStyle.from("handwriting"));
    }

    @Test
    void from_null_returnsWarm() {
        assertEquals(CardStyle.WARM, CardStyle.from(null));
    }

    @Test
    void from_blank_returnsWarm() {
        assertEquals(CardStyle.WARM, CardStyle.from("  "));
    }

    @Test
    void from_unknown_returnsWarm() {
        assertEquals(CardStyle.WARM, CardStyle.from("nonexistent"));
    }

    @Test
    void from_caseInsensitive_matchesCorrectly() {
        assertEquals(CardStyle.HANDWRITING, CardStyle.from("HANDWRITING"));
        assertEquals(CardStyle.HANDWRITING, CardStyle.from("Handwriting"));
    }
}
