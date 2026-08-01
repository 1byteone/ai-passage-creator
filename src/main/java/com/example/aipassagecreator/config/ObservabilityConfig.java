package com.example.aipassagecreator.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * 可观测性配置 — 自定义业务指标
 */
@Configuration
public class ObservabilityConfig {

    private final MeterRegistry registry;

    // 业务计数器
    private Counter llmCallCounter;
    private Counter quotaConsumedCounter;
    private Counter skillExecutionCounter;
    private Counter articleGeneratedCounter;
    private Timer llmCallTimer;

    public ObservabilityConfig(MeterRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    void initMetrics() {
        this.llmCallCounter = Counter.builder("apc.llm.calls.total")
                .description("LLM 调用总次数")
                .tag("version", "v1")
                .register(registry);

        this.quotaConsumedCounter = Counter.builder("apc.quota.consumed.total")
                .description("配额消耗总次数")
                .register(registry);

        this.skillExecutionCounter = Counter.builder("apc.skill.executions.total")
                .description("Skill 执行总次数")
                .register(registry);

        this.articleGeneratedCounter = Counter.builder("apc.article.generated.total")
                .description("文章生成总次数")
                .register(registry);

        this.llmCallTimer = Timer.builder("apc.llm.call.duration")
                .description("LLM 调用耗时")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    public void recordLlmCall(String model, long durationMs, int tokens) {
        llmCallCounter.increment();
        llmCallTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void recordQuotaConsumed() {
        quotaConsumedCounter.increment();
    }

    public void recordSkillExecution() {
        skillExecutionCounter.increment();
    }

    public void recordArticleGenerated() {
        articleGeneratedCounter.increment();
    }
}
