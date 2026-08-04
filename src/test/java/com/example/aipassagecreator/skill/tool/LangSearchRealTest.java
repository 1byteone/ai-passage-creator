package com.example.aipassagecreator.skill.tool;

import com.example.aipassagecreator.config.CircuitBreakerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * LangSearch 真实搜索集成验证 — 手动开关 + 环境变量 LANGSEARCH_API_KEY
 *
 * <p>仅当同时满足以下条件才执行（避免默认测试被沙箱/CI 环境误伤）：
 * <ul>
 *   <li>环境变量 {@code RUN_LANGSEARCH_REAL_TEST=true}</li>
 *   <li>环境变量 {@code LANGSEARCH_API_KEY} 非空</li>
 * </ul>
 * 验证 WebSearchTool 对真实 LangSearch API 的端到端调用返回结构化搜索结果 JSON。
 * 真实环境用法：{@code RUN_LANGSEARCH_REAL_TEST=true mvn test -Dtest=LangSearchRealTest}
 * </p>
 */
class LangSearchRealTest {

    @Test
    @DisplayName("真实搜索 — 配置 key 时返回结构化结果")
    void webSearch_realApi_returnsResults() {
        assumeTrue("true".equals(System.getenv("RUN_LANGSEARCH_REAL_TEST")),
                "RUN_LANGSEARCH_REAL_TEST 未开启，跳过真实搜索验证");
        String apiKey = System.getenv("LANGSEARCH_API_KEY");
        assumeTrue(apiKey != null && !apiKey.isBlank(),
                "LANGSEARCH_API_KEY 未配置，跳过真实搜索验证");

        WebSearchTool tool = new WebSearchTool(apiKey, new CircuitBreakerConfig());
        WebSearchTool.WebSearchRequest req = new WebSearchTool.WebSearchRequest();
        req.setQuery("Spring Boot 3 最新特性");
        req.setCount(3);

        String result = tool.webSearch(req);

        // 真实 API 应返回搜索结果 JSON（含 data / content 等字段），而非错误 JSON
        assertNotNull(result);
        assertTrue(result.contains("data") || result.contains("content")
                        || result.contains("results") || result.contains("answer"),
                "搜索结果应包含结构化字段，实际返回: " + (result.length() > 200 ? result.substring(0, 200) : result));
    }
}
