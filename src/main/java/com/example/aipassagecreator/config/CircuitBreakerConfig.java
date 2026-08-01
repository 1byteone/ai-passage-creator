package com.example.aipassagecreator.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轻量熔断器 — 保护外部依赖调用
 * <p>
 * 不依赖 Resilience4j，使用 ConcurrentHashMap 实现简单的故障计数 + 自动恢复。
 * 适用于 LLM / 搜索 / 图片 / COS / Stripe 等外部依赖的保护。
 */
@Slf4j
@Configuration
public class CircuitBreakerConfig {

    private final Map<String, FailureCounter> counters = new ConcurrentHashMap<>();

    /**
     * 检查是否允许调用指定服务
     *
     * @param service 服务名 (llm / websearch / image / cos / stripe)
     * @return true 允许调用，false 熔断中
     */
    public boolean allowRequest(String service) {
        FailureCounter counter = counters.computeIfAbsent(service, k -> new FailureCounter());
        return counter.allow();
    }

    /**
     * 记录成功调用
     */
    public void recordSuccess(String service) {
        FailureCounter counter = counters.get(service);
        if (counter != null) {
            counter.success();
        }
    }

    /**
     * 记录失败调用
     */
    public void recordFailure(String service) {
        FailureCounter counter = counters.computeIfAbsent(service, k -> new FailureCounter());
        counter.failure();
        if (!counter.allow()) {
            log.warn("熔断器触发: service={}, failures={}, window={}", service,
                    counter.failures.get(), counter.windowStart);
        }
    }

    /**
     * 获取当前熔断状态
     */
    public String getStatus(String service) {
        FailureCounter counter = counters.get(service);
        return counter == null ? "UNKNOWN" : counter.allow() ? "CLOSED" : "OPEN";
    }

    /**
     * 故障计数器 — 滑动窗口内失败超过阈值则熔断 30 秒
     */
    private static class FailureCounter {
        private static final int FAILURE_THRESHOLD = 5;
        private static final long OPEN_DURATION_SEC = 30;

        volatile Instant windowStart = Instant.now();
        final AtomicInteger failures = new AtomicInteger(0);
        volatile Instant openedAt;

        boolean allow() {
            if (openedAt != null) {
                if (Instant.now().isAfter(openedAt.plusSeconds(OPEN_DURATION_SEC))) {
                    // 半开：允许一个请求通过
                    openedAt = null;
                    failures.set(0);
                    windowStart = Instant.now();
                    return true;
                }
                return false; // 熔断中
            }
            return true;
        }

        void success() {
            failures.set(0);
            windowStart = Instant.now();
        }

        void failure() {
            failures.incrementAndGet();
            if (failures.get() >= FAILURE_THRESHOLD) {
                openedAt = Instant.now();
            }
        }
    }
}
