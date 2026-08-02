package com.example.aipassagecreator.config;

import com.example.aipassagecreator.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 轻量熔断器单元测试 — 状态转移 + execute/executeOrThrow 语义
 */
class CircuitBreakerConfigTest {

    @Test
    void stateMachine_afterFiveFailures_opens() {
        CircuitBreakerConfig breaker = new CircuitBreakerConfig();

        for (int i = 0; i < 5; i++) {
            breaker.recordFailure("svc");
        }

        assertEquals("OPEN", breaker.getStatus("svc"));
        assertEquals(5, breaker.getFailures("svc"));
        assertFalse(breaker.allowRequest("svc"));
    }

    @Test
    void stateMachine_afterCooldown_halfOpenProbeAllowed() {
        CircuitBreakerConfig breaker = new CircuitBreakerConfig();
        for (int i = 0; i < 5; i++) {
            breaker.recordFailure("svc");
        }
        assertFalse(breaker.allowRequest("svc"));

        // 模拟 30s 冷却结束（把 openedAt 拨到 31s 前），半开放行并重置计数
        Map<String, Object> counters = (Map<String, Object>) ReflectionTestUtils.getField(breaker, "counters");
        Object counter = counters.get("svc");
        ReflectionTestUtils.setField(counter, "openedAt", Instant.now().minusSeconds(31));

        assertTrue(breaker.allowRequest("svc"));
        assertEquals("CLOSED", breaker.getStatus("svc"));
    }

    @Test
    void stateMachine_success_resetsFailures() {
        CircuitBreakerConfig breaker = new CircuitBreakerConfig();
        breaker.recordFailure("svc");
        breaker.recordFailure("svc");

        breaker.recordSuccess("svc");

        assertEquals(0, breaker.getFailures("svc"));
        assertEquals("CLOSED", breaker.getStatus("svc"));
    }

    @Test
    void getStatus_unknownService_returnsClosed() {
        CircuitBreakerConfig breaker = new CircuitBreakerConfig();
        assertEquals("CLOSED", breaker.getStatus("never-used"));
        assertEquals(0, breaker.getFailures("never-used"));
    }

    @Test
    void execute_open_returnsFallbackWithoutInvokingAction() {
        CircuitBreakerConfig breaker = new CircuitBreakerConfig();
        for (int i = 0; i < 5; i++) {
            breaker.recordFailure("svc");
        }
        AtomicBoolean called = new AtomicBoolean(false);

        String result = breaker.execute("svc",
                () -> {
                    called.set(true);
                    return "ok";
                },
                () -> "fallback");

        assertEquals("fallback", result);
        assertFalse(called.get());
    }

    @Test
    void execute_actionThrows_recordsFailureAndReturnsFallback() {
        CircuitBreakerConfig breaker = new CircuitBreakerConfig();

        String result = breaker.execute("svc",
                () -> {
                    throw new IllegalStateException("boom");
                },
                () -> "fallback");

        assertEquals("fallback", result);
        assertEquals(1, breaker.getFailures("svc"));
    }

    @Test
    void execute_actionSucceeds_returnsResultAndResetsFailures() {
        CircuitBreakerConfig breaker = new CircuitBreakerConfig();
        breaker.recordFailure("svc");

        String result = breaker.execute("svc", () -> "ok", () -> "fallback");

        assertEquals("ok", result);
        assertEquals(0, breaker.getFailures("svc"));
    }

    @Test
    void executeOrThrow_open_throwsBusinessException() {
        CircuitBreakerConfig breaker = new CircuitBreakerConfig();
        for (int i = 0; i < 5; i++) {
            breaker.recordFailure("svc");
        }

        assertThrows(BusinessException.class,
                () -> breaker.executeOrThrow("svc", () -> "x"));
    }

    @Test
    void executeOrThrow_actionThrows_rethrowsOriginalAndRecordsFailure() {
        CircuitBreakerConfig breaker = new CircuitBreakerConfig();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> breaker.executeOrThrow("svc", () -> {
                    throw new IllegalStateException("boom");
                }));

        assertEquals("boom", ex.getMessage());
        assertEquals(1, breaker.getFailures("svc"));
    }

    @Test
    void executeOrThrow_actionSucceeds_returnsResult() {
        CircuitBreakerConfig breaker = new CircuitBreakerConfig();

        String result = breaker.executeOrThrow("svc", () -> "ok");

        assertEquals("ok", result);
        assertEquals(0, breaker.getFailures("svc"));
    }
}
