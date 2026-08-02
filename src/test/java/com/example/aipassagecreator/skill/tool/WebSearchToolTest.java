package com.example.aipassagecreator.skill.tool;

import com.example.aipassagecreator.config.CircuitBreakerConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网络搜索工具单元测试 — 熔断集成 + 入参校验
 */
class WebSearchToolTest {

    private static final String TEST_KEY = "test-api-key";

    @Test
    void webSearch_blankQuery_returnsError() {
        WebSearchTool tool = new WebSearchTool(TEST_KEY, new CircuitBreakerConfig());

        String result = tool.webSearch(new WebSearchTool.WebSearchRequest());

        assertTrue(result.contains("关键词不能为空"));
    }

    @Test
    void webSearch_apiKeyMissing_returnsErrorWithoutBreaker() {
        WebSearchTool tool = new WebSearchTool("", new CircuitBreakerConfig());
        WebSearchTool.WebSearchRequest req = new WebSearchTool.WebSearchRequest();
        req.setQuery("测试");

        String result = tool.webSearch(req);

        assertTrue(result.contains("未配置"));
    }

    @Test
    void webSearch_breakerOpen_returnsFallbackWithoutHttpCall() {
        CircuitBreakerConfig breaker = new CircuitBreakerConfig();
        for (int i = 0; i < 5; i++) {
            breaker.recordFailure("websearch");
        }
        WebSearchTool tool = new WebSearchTool(TEST_KEY, breaker);
        WebSearchTool.WebSearchRequest req = new WebSearchTool.WebSearchRequest();
        req.setQuery("测试");

        String result = tool.webSearch(req);

        // 熔断中直接返回降级错误，不再发起真实 HTTP 搜索请求
        assertTrue(result.contains("暂时不可用"));
        assertEquals("OPEN", breaker.getStatus("websearch"));
    }
}
