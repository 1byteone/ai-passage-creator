package com.example.aipassagecreator.skill;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkillSseEmitterManagerTest {

    @Test
    void buffersEventsBeforeClientSubscribes() {
        SkillSseEmitterManager manager = new SkillSseEmitterManager();
        manager.publish("execution-1", "{\"type\":\"skill.started\"}");
        manager.publish("execution-1", "{\"type\":\"skill.complete\"}");

        assertEquals(
                List.of("{\"type\":\"skill.started\"}", "{\"type\":\"skill.complete\"}"),
                manager.snapshot("execution-1")
        );
    }
}
