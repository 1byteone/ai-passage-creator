package com.example.aipassagecreator.skill.tool;

import com.example.aipassagecreator.config.CircuitBreakerConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 网络搜索工具真实 HTTP 测试 — 用 JDK 内嵌 HttpServer 模拟 LangSearch API
 *
 * <p>验证请求组装正确：Authorization 头、Content-Type、请求体含 query/count，
 * 以及非 200 响应归一为异常。</p>
 */
class WebSearchToolHttpTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> capturedAuth = new AtomicReference<>();
    private final AtomicReference<String> capturedBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://localhost:" + server.getAddress().getPort();
        server.createContext("/v1/web-search", exchange -> {
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] resp = "{\"results\":[{\"title\":\"测试结果\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private WebSearchTool tool(String key) {
        return new WebSearchTool(key, new CircuitBreakerConfig(),
                java.net.http.HttpClient.newHttpClient(), baseUrl + "/v1/web-search");
    }

    @Test
    @DisplayName("webSearch — 真实请求带 Bearer 头与 query/count 请求体")
    void webSearch_sendsAuthAndBody() {
        WebSearchTool.WebSearchRequest req = new WebSearchTool.WebSearchRequest();
        req.setQuery("Spring Boot");
        req.setCount(5);

        String result = tool("test-key-123").webSearch(req);

        assertEquals("Bearer test-key-123", capturedAuth.get());
        assertTrue(capturedBody.get().contains("\"query\":\"Spring Boot\""));
        assertTrue(capturedBody.get().contains("\"count\":5"));
        assertTrue(result.contains("测试结果"));
    }

    @Test
    @DisplayName("webSearch — 默认 count 落为 10")
    void webSearch_defaultCountIs10() {
        WebSearchTool.WebSearchRequest req = new WebSearchTool.WebSearchRequest();
        req.setQuery("测试");

        tool("k").webSearch(req);

        assertTrue(capturedBody.get().contains("\"count\":10"));
    }

    @Test
    @DisplayName("webSearch — 非 200 响应触发熔断并返回降级错误")
    void webSearch_non200_returnsFallbackError() throws IOException {
        HttpServer errServer = HttpServer.create(new InetSocketAddress(0), 0);
        String errBase = "http://localhost:" + errServer.getAddress().getPort();
        errServer.createContext("/v1/web-search", exchange -> {
            exchange.sendResponseHeaders(500, 0);
            exchange.close();
        });
        errServer.start();
        try {
            WebSearchTool.WebSearchRequest req = new WebSearchTool.WebSearchRequest();
            req.setQuery("测试");
            String result = new WebSearchTool("k", new CircuitBreakerConfig(),
                    java.net.http.HttpClient.newHttpClient(), errBase + "/v1/web-search")
                    .webSearch(req);

            // 5 次失败触发熔断前，前几次返回真实错误（非 200 → IllegalStateException → 熔断记录 → 返回降级）
            assertTrue(result.contains("暂时不可用") || result.startsWith("{\"error\""));
        } finally {
            errServer.stop(0);
        }
    }
}
