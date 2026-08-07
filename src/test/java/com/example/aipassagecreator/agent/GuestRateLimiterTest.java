package com.example.aipassagecreator.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GuestRateLimiterTest {

    private final GuestRateLimiter limiter = new GuestRateLimiter(5);

    @Test
    void tryAcquire_withinLimit_ok_thenRejects() {
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("g1"), "第 " + (i + 1) + " 次应放行");
        }
        assertFalse(limiter.tryAcquire("g1"), "第 6 次应拒绝");
    }

    @Test
    void tryAcquire_differentGuests_independent() {
        assertTrue(limiter.tryAcquire("gA"));
        assertTrue(limiter.tryAcquire("gB"));
    }
}
