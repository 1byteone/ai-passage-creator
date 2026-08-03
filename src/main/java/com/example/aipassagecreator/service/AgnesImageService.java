package com.example.aipassagecreator.service;

import com.example.aipassagecreator.enums.ImageMethodEnum;
import com.example.aipassagecreator.model.dto.image.ImageData;
import com.example.aipassagecreator.model.dto.image.ImageRequest;
import com.example.aipassagecreator.utils.GsonUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/**
 * Agnes AI 图片生成服务（备用 AI 生图源）。
 * <p>
 * 通过 OpenAI 兼容的 Images API 生成图片，作为 NANO_BANANA 的降级选择。
 * 模型：agnes-image-2.1-flash（通过 ModelRouterConfig 注入），同步返回 URL 或 b64_json。
 */
@Slf4j
@Service
public class AgnesImageService implements ImageSearchService {

    private static final String IMAGE_MODEL = "agnes-image-2.1-flash";
    private static final String DEFAULT_SIZE = "1024x1024";
    private static final String DEFAULT_QUALITY = "standard";
    private static final int DEFAULT_N = 1;
    private static final String FALLBACK_URL = "https://picsum.photos/seed/agnes-%d/1024/1024";

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://api.agnes-ai.cn}")
    private String baseUrl;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build();

    @Override
    public String searchImage(String prompt) {
        if (!isAvailable()) {
            log.warn("Agnes API key 未配置，AGNES 不可用");
            return null;
        }
        if (prompt == null || prompt.isBlank()) {
            log.warn("AGNES prompt 为空");
            return null;
        }

        try {
            String url = callImageApi(prompt);
            if (url != null) {
                log.info("AGNES 生图完成: model={}, prompt={}",
                        IMAGE_MODEL, prompt.substring(0, Math.min(80, prompt.length())));
                return url;
            }
        } catch (Exception e) {
            log.error("AGNES 生图失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 调用 OpenAI 兼容 Images API：POST {baseUrl}/v1/images/generations
     * <p>响应格式: {"data":[{"url":"..."}]} 或 {"data":[{"b64_json":"..."}]}</p>
     */
    private String callImageApi(String prompt) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("model", IMAGE_MODEL);
        body.addProperty("prompt", prompt);
        body.addProperty("n", DEFAULT_N);
        body.addProperty("size", DEFAULT_SIZE);
        body.addProperty("quality", DEFAULT_QUALITY);
        body.addProperty("response_format", "url");

        String url = baseUrl.endsWith("/") ? baseUrl + "v1/images/generations"
                : baseUrl + "/v1/images/generations";

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(
                        GsonUtils.toJson(body),
                        MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("HTTP " + response.code() + ": " + errorBody);
            }
            String respBody = response.body().string();
            return extractUrl(respBody);
        }
    }

    /**
     * 从响应中提取图片 URL：优先 data[0].url，其次 data[0].b64_json。
     */
    private String extractUrl(String respBody) {
        JsonObject result = GsonUtils.fromJson(respBody, JsonObject.class);
        JsonArray data = result.getAsJsonArray("data");
        if (data == null || data.isEmpty()) {
            log.warn("AGNES 响应无 data 数组: {}", respBody);
            return null;
        }
        JsonObject item = data.get(0).getAsJsonObject();
        if (item.has("url") && !item.get("url").isJsonNull()) {
            return item.get("url").getAsString();
        }
        if (item.has("b64_json") && !item.get("b64_json").isJsonNull()) {
            String b64 = item.get("b64_json").getAsString();
            byte[] bytes = Base64.getDecoder().decode(b64);
            // 转换为 data URL，下游 ImageData.fromUrl 或 CosService 消费
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        }
        log.warn("AGNES 响应无 url/b64_json: {}", respBody);
        return null;
    }

    @Override
    public ImageData getImageData(ImageRequest request) {
        String prompt = request.getEffectiveParam(true);
        String url = searchImage(prompt);
        if (url != null) {
            return ImageData.fromUrl(url);
        }
        int pos = request.getPosition() != null ? request.getPosition() : 1;
        return ImageData.fromUrl(String.format(FALLBACK_URL, pos));
    }

    @Override
    public ImageMethodEnum getMethod() {
        return ImageMethodEnum.AGNES;
    }

    @Override
    public String getFallbackImage(int position) {
        return String.format(FALLBACK_URL, position);
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }
}
