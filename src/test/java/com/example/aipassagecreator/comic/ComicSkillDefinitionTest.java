package com.example.aipassagecreator.comic;

import com.example.aipassagecreator.skill.PhaseDefinition;
import com.example.aipassagecreator.skill.SkillDefinition;
import com.example.aipassagecreator.skill.SkillRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ComicSkillDefinitionTest {

    @Autowired private SkillRegistry skillRegistry;

    @Test
    void comicJournalSkill_hasFourPhases_andHITL() {
        SkillDefinition def = skillRegistry.getSkill("comic-journal");
        assertEquals("comic-journal", def.getName());
        assertEquals(4, def.getPhases().size());
        List<String> keys = def.getPhases().stream().map(PhaseDefinition::getOutputKey).toList();
        assertEquals(List.of("routeResult", "storyboardResult", "imagePrompts", "layoutResult"), keys);
        assertTrue(def.getPhases().get(1).isRequireConfirmation()); // storyboard HITL
        assertEquals("upload", def.getVariables().get("photos").getUiType());
    }
}
