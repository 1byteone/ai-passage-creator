package com.example.aipassagecreator.skill;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class PromptTemplateEngineTest {

    @Autowired
    private PromptTemplateEngine engine;

    @Test
    void testRenderWithVariables() {
        String result = engine.render("skills/test/test-prompt.md",
                Map.of("name", "AGNES", "project", "Skill Engine"));
        assertNotNull(result);
        assertTrue(result.contains("AGNES"));
        assertTrue(result.contains("Skill Engine"));
        assertTrue(result.contains("Hello"));
    }

    @Test
    void testMissingTemplateThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                engine.render("skills/non-existent/prompt.md", Map.of()));
    }
}