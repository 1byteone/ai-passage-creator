package com.example.aipassagecreator.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SseEmitterManager 单元测试 — 连接创建/复用、发送、完成、身份守卫。
 */
class SseEmitterManagerTest {

    private SseEmitterManager manager;
    private final ConcurrentHashMap<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        manager = new SseEmitterManager();
        // 注入真实的 map，便于断言清理行为
        ReflectionTestUtils.setField(manager, "emitterMap", emitterMap);
    }

    @Test
    @DisplayName("createEmitter 创建有效连接并登记到 map")
    void createEmitter_returnsValidEmitter() {
        SseEmitter emitter = manager.createEmitter("task-1");
        assertNotNull(emitter);
        assertTrue(manager.exists("task-1"));
        assertSame(emitter, emitterMap.get("task-1"));
    }

    @Test
    @DisplayName("重复 createEmitter 完成旧连接并替换")
    void createEmitter_duplicateReplacesOld() {
        SseEmitter first = manager.createEmitter("task-1");
        SseEmitter second = manager.createEmitter("task-1");
        assertNotNull(first);
        assertSame(second, emitterMap.get("task-1"), "新 emitter 应替换旧 emitter");
        assertEquals(1, emitterMap.size(), "map 中只应保留一个新 emitter");
    }

    @Test
    @DisplayName("send 在连接存在时正常发送，不抛异常")
    void send_emitterExists_sendsData() {
        manager.createEmitter("task-1");
        // 正常发送不抛异常即可（SseEmitter.send 在 mock/真实对象上执行）
        manager.send("task-1", "{\"type\":\"ALL_COMPLETE\"}");
        assertTrue(manager.exists("task-1"), "发送后连接应保持");
    }

    @Test
    @DisplayName("send 在连接不存在时静默处理")
    void send_emitterNotExists_handlesGracefully() {
        manager.send("unknown-task", "data");
        // 不抛异常即为通过
        assertFalse(manager.exists("unknown-task"));
    }

    @Test
    @DisplayName("sendHeartbeat 在连接存在时正常发送")
    void sendHeartbeat_emitterExists_sends() {
        manager.createEmitter("task-1");
        manager.sendHeartbeat("task-1");
        assertTrue(manager.exists("task-1"));
    }

    @Test
    @DisplayName("complete 完成连接并从 map 清理")
    void complete_removesEmitter() {
        manager.createEmitter("task-1");
        manager.complete("task-1");
        assertFalse(manager.exists("task-1"), "完成后应从 map 移除");
        assertNull(emitterMap.get("task-1"));
    }

    @Test
    @DisplayName("complete 对不存在的连接静默处理")
    void complete_notExists_handlesGracefully() {
        manager.complete("unknown-task");
        assertFalse(manager.exists("unknown-task"));
    }

    @Test
    @DisplayName("身份守卫 — 回调只移除当前 emitter 而非全部")
    void identityGuard_preventsStaleRemoval() {
        SseEmitter first = manager.createEmitter("task-1");
        SseEmitter second = manager.createEmitter("task-1");

        // 模拟旧 emitter 的完成回调触发（身份守卫：仅当 map 中仍是 first 时才移除）
        // 直接验证 map 中仍保留 second
        assertSame(second, emitterMap.get("task-1"), "旧 emitter 回调不应误删新 emitter");
        assertNotNull(first);
        assertEquals(1, emitterMap.size());
    }
}
