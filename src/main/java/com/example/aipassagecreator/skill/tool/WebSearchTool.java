package com.example.aipassagecreator.skill.tool;

import com.example.aipassagecreator.utils.GsonUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 网络搜索工具 — 供 Skill 引擎的 LLM 调用真实搜索 API
 * <p>
 * 使用 LangSearch API（兼容 OpenAI 格式），在 skill.yaml 中声明
 * {@code tools: [webSearch]} 即可注入到对应阶段的 LLM 调用上下文中。
 * LLM 自主决定何时调用此工具。
 */
@Slf4j
@Component
public class WebSearchTool {

    private static final String SEARCH_API_URL = "https://api.langsearch.com/v1/web-search";

    private final HttpClient httpClient;
    private final String apiKey;

    public WebSearchTool(@Value("${langsearch.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        log.info("WebSearchTool 初始化完成，apiKey={}", apiKey.isEmpty() ? "未配置" : "已配置");
    }

    /**
     * 执行网络搜索，返回结构化搜索结果
     * <p>
     * 此方法通过 FunctionToolCallback 注册为 LLM 工具，
     * LLM 自主决定何时调用。泛型签名为 (WebSearchRequest) → String。
     */
    public String webSearch(WebSearchRequest request) {
        if (request == null || request.getQuery() == null || request.getQuery().isBlank()) {
            return "{\"error\": \"搜索关键词不能为空\"}";
        }
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("WebSearchTool 未配置 api-key，返回模拟结果");
            return "{\"error\": \"搜索服务未配置，请配置 langsearch.api-key\"}";
        }

        try {
            Map<String, Object> requestBody = Map.of(
                    "query", request.getQuery(),
                    "freshness", request.getFreshness() != null ? request.getFreshness() : "noLimit",
                    "summary", true,
                    "count", request.getCount() != null && request.getCount() > 0 && request.getCount() <= 20
                            ? request.getCount() : 10
            );

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(SEARCH_API_URL))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GsonUtils.toJson(requestBody)))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("搜索 API 返回非 200: status={}, body={}", response.statusCode(), response.body());
                return "{\"error\": \"搜索服务暂时不可用 (HTTP " + response.statusCode() + ")\"}";
            }

            log.info("搜索成功: query={}, responseLength={}", request.getQuery(), response.body().length());
            return response.body();

        } catch (Exception e) {
            log.error("搜索请求异常: query={}", request.getQuery(), e);
            return "{\"error\": \"搜索请求异常: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 搜索请求参数
     * <p>
     * 作为 FunctionToolCallback 的输入类型，Spring AI 自动生成 JSON Schema。
     */
    @Data
    public static class WebSearchRequest {
        private String query;
        private Integer count;
        private String freshness;
    }
}