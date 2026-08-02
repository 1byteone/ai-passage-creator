package com.example.aipassagecreator.controller;

import com.example.aipassagecreator.aop.AuthCheck;
import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.config.CircuitBreakerConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 熔断器状态观测端点 — 供运维巡检各外部依赖健康度
 */
@Slf4j
@RestController
@RequestMapping("/breaker")
@Tag(name = "CircuitBreakerController", description = "熔断器状态")
public class CircuitBreakerController {

    private static final List<String> SERVICES = List.of("llm", "websearch", "image", "cos", "stripe");

    @Resource
    private CircuitBreakerConfig circuitBreaker;

    @GetMapping("/status")
    @Operation(summary = "查询各外部依赖熔断状态")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<Map<String, Map<String, Object>>> status() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (String service : SERVICES) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("status", circuitBreaker.getStatus(service));
            entry.put("failures", circuitBreaker.getFailures(service));
            result.put(service, entry);
        }
        return ResultUtils.success(result);
    }
}
