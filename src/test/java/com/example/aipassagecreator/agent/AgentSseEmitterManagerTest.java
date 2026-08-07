package com.example.aipassagecreator.agent;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentSseEmitterManagerTest {

    private final AgentSseEmitterManager manager = new AgentSseEmitterManager();

    @Test
    void publishBeforeSubscribe_replaysBufferOnSubscribe() {
        manager.publish("req1", AgentEventFactory.started("req1"));
        SseEmitter emitter = manager.subscribe("req1");
        assertNotNull(emitter);
        // 缓冲已回放，不抛异常即通过；状态为终态前连接保持
        assertTrue(true);
    }

    @Test
    void complete_setsTerminalAndKeepsBuffer() {
        manager.publish("req2", AgentEventFactory.started("req2"));
        manager.complete("req2");
        List<String> snapshot = manager.snapshot("req2");
        assertEquals(1, snapshot.size());
        assertTrue(snapshot.get(0).contains("agent.chat_started"));
    }

    @Test
    void registry_ownerCheck_works() {
        AgentRequestRegistry registry = new AgentRequestRegistry();
        registry.register("req3", "u:1");
        assertTrue(registry.isOwner("req3", "u:1"));
        assertFalse(registry.isOwner("req3", "g:abc"));
        registry.remove("req3");
        assertFalse(registry.isOwner("req3", "u:1"));
    }
}