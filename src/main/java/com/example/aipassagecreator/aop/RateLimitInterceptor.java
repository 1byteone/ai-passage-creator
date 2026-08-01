package com.example.aipassagecreator.aop;

import com.example.aipassagecreator.annotation.RateLimit;
import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
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

import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
public class RateLimitInterceptor {

    /** Redis 可选：不可用时限流降级为放行（测试环境/Redis 故障） */
    private final RedisTemplate<String, Object> redisTemplate;

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
        String userId = request.getSession().getId();
        String method = ((MethodSignature) joinPoint.getSignature()).getMethod().getName();
        String key = rateLimit.key() + ":" + method + ":" + userId;

        // Redis 不可用时跳过限流（放行），避免限流依赖拖垮主流程
        if (redisTemplate == null || redisTemplate.getConnectionFactory() == null) {
            return joinPoint.proceed();
        }

        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            // 首次访问，设置过期时间
            redisTemplate.expire(key, rateLimit.window(), TimeUnit.SECONDS);
        }

        if (count != null && count > rateLimit.limit()) {
            log.warn("接口限流触发: method={}, userId={}, count={}, limit={}",
                    method, userId, count, rateLimit.limit());
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "请求过于频繁，请稍后重试");
        }

        return joinPoint.proceed();
    }
}
