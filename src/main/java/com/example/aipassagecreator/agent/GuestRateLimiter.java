package com.example.aipassagecreator.agent;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 游客内存滑动窗口限流（默认 5 次/分钟）。内存态：重启即清零，可接受。 */
@Component
public class GuestRateLimiter {

    private static final long WINDOW_MS = 60_000L;
    private final int maxRequests;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public GuestRateLimiter() {
        this(5);
    }

    public GuestRateLimiter(int maxRequests) {
        this.maxRequests = maxRequests;
    }

    /** 放行返回 true；超限返回 false（内部清理过期窗口项） */
    public boolean tryAcquire(String guestId) {
        long now = System.currentTimeMillis();
        Deque<Long> window = hits.computeIfAbsent(guestId, ignored -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MS) {
                window.removeFirst();
            }
            if (window.size() >= maxRequests) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }
}
