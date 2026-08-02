package com.example.aipassagecreator.config;

import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 轻量熔断器 — 保护外部依赖调用
 * <p>
 * 不依赖 Resilience4j，使用 ConcurrentHashMap 实现简单的故障计数 + 自动恢复。
 * 适用于 LLM / 搜索 / 图片 / COS / Stripe 等外部依赖的保护。
 * 外部调用点大多已吞异常转降级值，因此熔断的价值在「预防」：服务已知故障时
 * 不再发起请求，直接经 {@link #execute} / {@link #executeOrThrow} 走降级或抛错路径。
 */
@Slf4j
@Component
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
     * 获取当前熔断状态。未调用过的服务视为 CLOSED（允许请求）。
     */
    public String getStatus(String service) {
        FailureCounter counter = counters.get(service);
        return counter == null ? "CLOSED" : counter.allow() ? "CLOSED" : "OPEN";
    }

    /**
     * 获取当前失败计数（可观测用）
     */
    public int getFailures(String service) {
        FailureCounter counter = counters.get(service);
        return counter == null ? 0 : counter.failures.get();
    }

    /**
     * 熔断保护执行 — 熔断中直接返回降级值，不发起外部请求。
     * 调用成功记录 success；调用抛异常记录 failure 后返回降级值。
     * 用于 websearch / image / cos 等「失败可降级」的边界。
     */
    public <T> T execute(String service, CheckedAction<T> action, Supplier<T> fallback) {
        if (!allowRequest(service)) {
            log.warn("熔断中，跳过调用直接降级: service={}", service);
            return fallback.get();
        }
        try {
            T result = action.run();
            recordSuccess(service);
            return result;
        } catch (Exception e) {
            recordFailure(service);
            log.warn("外部调用失败，走降级: service={}, error={}", service, e.getMessage());
            return fallback.get();
        }
    }

    /**
     * 熔断保护执行（抛错版）— 熔断中抛业务异常；调用抛异常记录 failure 后原样重抛。
     * 用于 stripe 等必须让异常（含受检异常）向上传播的边界。
     * 受检异常经 sneaky throw 穿透泛型守卫，调用方原有的 throws 签名保持不变。
     */
    public <T> T executeOrThrow(String service, CheckedAction<T> action) {
        if (!allowRequest(service)) {
            log.warn("熔断中，直接拒绝: service={}", service);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, service + " 服务暂时不可用");
        }
        try {
            T result = action.run();
            recordSuccess(service);
            return result;
        } catch (Exception e) {
            recordFailure(service);
            sneakyThrow(e);
            return null; // 不可达，仅满足编译器
        }
    }

    /**
     * 允许抛出受检异常的调用动作（lambda 内部可抛受检异常）
     */
    @FunctionalInterface
    public interface CheckedAction<T> {
        T run() throws Exception;
    }

    @SuppressWarnings("unchecked")
    private static <E extends Exception> void sneakyThrow(Exception e) throws E {
        throw (E) e;
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
