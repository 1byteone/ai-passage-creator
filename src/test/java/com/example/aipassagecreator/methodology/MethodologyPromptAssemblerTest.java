package com.example.aipassagecreator.methodology;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MethodologyPromptAssemblerTest {

    @Autowired
    private MethodologyPromptAssembler assembler;

    @Test
    void buildTitleGuidance_containsStrategies() {
        String guidance = assembler.buildTitleGuidance("default");
        assertTrue(guidance.contains("curiosityGap"));
        assertTrue(guidance.contains("标题策略"));
    }

    @Test
    void buildContentGuidance_containsDimensions() {
        String guidance = assembler.buildContentGuidance("default");
        assertTrue(guidance.contains("核心观点"));
        assertTrue(guidance.contains("情绪触发点"));
    }

    @Test
    void blankName_returnsEmpty() {
        assertEquals("", assembler.buildTitleGuidance(null));
        assertEquals("", assembler.buildContentGuidance("  "));
    }

    @Test
    void unknownName_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> assembler.buildTitleGuidance("not-exist"));
    }
}
