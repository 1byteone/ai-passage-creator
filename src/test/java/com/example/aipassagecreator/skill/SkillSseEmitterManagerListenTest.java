package com.example.aipassagecreator.skill;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class SkillSseEmitterManagerListenTest {

    private final SkillSseEmitterManager manager = new SkillSseEmitterManager();

    @Test
    void listen_replaysBuffer_thenReceivesLiveEvents() {
        List<String> received = new CopyOnWriteArrayList<>();
        manager.publish("ex1", "before");

        manager.listen("ex1", received::add);   // 回放缓冲

        assertEquals(List.of("before"), new ArrayList<>(received));

        manager.publish("ex1", "after");        // 实时派发
        assertEquals(List.of("before", "after"), new ArrayList<>(received));
    }

    @Test
    void listen_registersOnNewExecution_receivesFromStart() {
        List<String> received = new CopyOnWriteArrayList<>();
        manager.listen("ex2", received::add);
        manager.publish("ex2", "started");
        assertEquals(List.of("started"), new ArrayList<>(received));
    }
}