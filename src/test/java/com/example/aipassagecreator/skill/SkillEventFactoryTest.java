package com.example.aipassagecreator.skill;

import com.example.aipassagecreator.utils.GsonUtils;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillEventFactoryTest {

    @Test
    void progressEventEscapesModelContent() {
        String chunk = "中文 \"引用\"\n下一行";
        String json = SkillEventFactory.progress(
                "execution-1", "proofreading", "ai_tone_fix", 2, 3, chunk);

        Map<String, Object> payload = GsonUtils.fromJson(
                json,
                new TypeToken<Map<String, Object>>() {
                }
        );

        assertEquals("skill.progress", payload.get("type"));
        assertEquals("execution-1", payload.get("skillExecutionId"));
        assertEquals(chunk, payload.get("data"));
        assertEquals(2.0, payload.get("phaseIndex"));
        assertEquals(3.0, payload.get("totalPhases"));
    }
}
