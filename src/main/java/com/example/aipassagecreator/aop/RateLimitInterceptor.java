package com.example.aipassagecreator.aop;

import com.example.aipassagecreator.annotation.RateLimit;
import com.example.aipassagecreator.constant.ApiKeyConstant;
import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.model.po.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Aspect
@Component
public class RateLimitInterceptor {

    /** Redis 可选：不可用时降级为进程内内存限流（非放行，避免 fail-open） */
    private final RedisTemplate<String, Object> redisTemplate;

    /** 内存降级限流器：key → (窗口起始, 计数) */
    private final Map<String, InMemoryWindow> localLimit = new ConcurrentHashMap<>();

    public RateLimitInterceptor(ObjectProvider<RedisTemplate<String, Object>> provider) {
        this.redisTemplate = provider.getIfAvailable();
    }

    @Around("@annotation(rateLimit)")
    public Object intercept(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return joinPoint.proceed(); // 非 Web 上下文放行
        }

        HttpServletRequest request = attrs.getRequest();
        String userId = resolveActorKey(request);
        String method = ((MethodSignature) joinPoint.getSignature()).getMethod().getName();
        String key = rateLimit.key() + ":" + method + ":" + userId;

        boolean redisAvailable = redisTemplate != null
                && redisTemplate.getConnectionFactory() != null;

        if (redisAvailable) {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, rateLimit.window(), TimeUnit.SECONDS);
            }
            if (count != null && count > rateLimit.limit()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "请求过于频繁，请稍后重试");
            }
        } else {
            // Redis 故障降级：内存限流，保证限流语义不因依赖中断而失效
            if (checkLocalLimit(key, rateLimit.limit(), rateLimit.window(), rateLimit.unit())) {
                log.warn("内存限流触发(Redis 不可用降级): method={}, userId={}, limit={}",
                        method, userId, rateLimit.limit());
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "请求过于频繁，请稍后重试");
            }
        }

        return joinPoint.proceed();
    }

    /**
     * 限流主体：API Key 请求按用户隔离，会话请求按 sessionId 隔离。
     * 无会话的 API Key 请求若用 getSession() 会每次新建 session，限流失效。
     */
    private String resolveActorKey(HttpServletRequest request) {
        User apiKeyUser = (User) request.getAttribute(ApiKeyConstant.REQUEST_USER_ATTR);
        if (apiKeyUser != null) {
            return "apikey:" + apiKeyUser.getId();
        }
        return request.getSession().getId();
    }

    /**
     * 内存窗口限流检查
     *
     * @return true 表示超限（应拒绝）
     */
    private boolean checkLocalLimit(String key, int limit, int window, TimeUnit unit) {
        long now = Instant.now().toEpochMilli();
        long windowMillis = unit.toMillis(window);

        InMemoryWindow windowEntry = localLimit.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStartMillis >= windowMillis) {
                // 新窗口
                return new InMemoryWindow(now, new AtomicInteger(1));
            }
            existing.counter.incrementAndGet();
            return existing;
        });
        return windowEntry.counter.get() > limit;
    }

    /** 内存限流窗口：窗口起始时间 + 请求计数 */
    private record InMemoryWindow(long windowStartMillis, AtomicInteger counter) {
    }
}
