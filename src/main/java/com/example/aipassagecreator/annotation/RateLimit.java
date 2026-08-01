package com.example.aipassagecreator.annotation;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * API 限流注解 — 基于 Redis 令牌桶，叠加在配额系统之上
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** 时间窗口内允许的最大请求数 */
    int limit() default 10;

    /** 时间窗口大小 */
    int window() default 60;

    /** 时间单位 */
    TimeUnit unit() default TimeUnit.SECONDS;

    /** 限流键前缀 */
    String key() default "rate_limit";
}
