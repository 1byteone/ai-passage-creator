package com.example.aipassagecreator.skill;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SkillEngineIntegrationTest {

    @Autowired(required = false)
    private SkillRegistry skillRegistry;

    @Autowired(required = false)
    private PromptTemplateEngine promptTemplateEngine;

    @Autowired
    private OutputParserRegistry parserRegistry;

    @Test
    void testSkillRegistryLoaded() {
        assertNotNull(skillRegistry);
        assertTrue(skillRegistry.getAllSkills().size() > 0,
                "至少注册一个 Skill");
    }

    @Test
    void testProofreadingSkillExists() {
        SkillDefinition def = skillRegistry.getSkill("proofreading");
        assertNotNull(def);
        assertEquals("proofreading", def.getName());
        assertEquals(3, def.getPhases().size());
    }

    @Test
    void testTopicGenSkillExists() {
        SkillDefinition def = skillRegistry.getSkill("topic-gen");
        assertNotNull(def);
        assertEquals("topic-gen", def.getName());
    }

    @Test
    void testPromptTemplateRendering() {
        String result = promptTemplateEngine.render(
                "skills/proofreading/prompts/phase1_content_review.md",
                Map.of("articleContent", "测试内容", "style", "tech"));
        assertNotNull(result);
        assertTrue(result.contains("测试内容"));
    }

    @Test
    void testJsonOutputParser() {
        Map<String, Object> parser = parserRegistry.parse("json",
                "{\"score\": 85, \"valid\": true}",
                new PhaseDefinition());
        assertNotNull(parser);
        assertEquals(85.0, parser.get("score"));
        assertEquals(true, parser.get("valid"));
    }
}