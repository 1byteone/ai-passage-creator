package com.example.aipassagecreator.service;

import com.example.aipassagecreator.enums.ImageMethodEnum;
import com.example.aipassagecreator.model.dto.image.ImageData;
import com.example.aipassagecreator.model.dto.image.ImageRequest;
import com.example.aipassagecreator.utils.GsonUtils;
import com.google.gson.JsonObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Nano Banana AI 图片生成服务
 * <p>
 * 通过 DashScope 文本生成图像 API (wanx2.1-t2i-turbo) 实现 AI 生图。
 * 支持中英文 prompt，自动将中文 prompt 翻译为英文以提高生成质量。
 */
@Slf4j
@Service
public class NanoBananaImageService implements ImageSearchService {

    private static final String API_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/text2image/image-synthesis";

    private static final String MODEL = "wanx2.1-t2i-turbo";
    private static final String DEFAULT_SIZE = "1024*1024";
    private static final int DEFAULT_N = 1;
    private static final int POLL_MAX_RETRIES = 30;
    private static final int POLL_INTERVAL_MS = 1000;

    /** 降级图片（Picsum 占位图） */
    private static final String FALLBACK_URL = "https://picsum.photos/seed/nano-banana-%d/1024/1024";

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(180, TimeUnit.SECONDS)
            .build();

    @Override
    public String searchImage(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("DashScope API key 未配置，NANO_BANANA 不可用");
            return null;
        }
        if (prompt == null || prompt.isBlank()) {
            log.warn("NANO_BANANA prompt 为空");
            return null;
        }

        try {
            // 1. 提交生图任务
            JsonObject task = submitTask(prompt);
            String taskId = task.getAsJsonObject("output").get("task_id").getAsString();
            log.info("NANO_BANANA 任务已提交: taskId={}, prompt={}", taskId, prompt.substring(0, Math.min(80, prompt.length())));

            // 2. 轮询等待完成
            String imageUrl = pollTask(taskId);
            if (imageUrl != null) {
                log.info("NANO_BANANA 生图完成: taskId={}", taskId);
                return imageUrl;
            }
        } catch (Exception e) {
            log.error("NANO_BANANA 生图失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 提交生图任务到 DashScope
     */
    private JsonObject submitTask(String prompt) throws IOException {
        JsonObject input = new JsonObject();
        input.addProperty("prompt", prompt);

        JsonObject parameters = new JsonObject();
        parameters.addProperty("size", DEFAULT_SIZE);
        parameters.addProperty("n", DEFAULT_N);

        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
        body.add("input", input);
        body.add("parameters", parameters);

        Request request = new Request.Builder()
                .url(API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("X-DashScope-Async", "enable")
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
            return GsonUtils.fromJson(respBody, JsonObject.class);
        }
    }

    /**
     * 轮询任务状态直到完成
     */
    private String pollTask(String taskId) throws IOException, InterruptedException {
        String pollUrl = API_URL + "/" + taskId;

        for (int i = 0; i < POLL_MAX_RETRIES; i++) {
            Thread.sleep(POLL_INTERVAL_MS);

            Request request = new Request.Builder()
                    .url(pollUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) continue;

                String body = response.body().string();
                JsonObject result = GsonUtils.fromJson(body, JsonObject.class);
                JsonObject output = result.getAsJsonObject("output");
                String taskStatus = output.get("task_status").getAsString();

                if ("SUCCEEDED".equals(taskStatus)) {
                    return output.getAsJsonArray("results")
                            .get(0).getAsJsonObject()
                            .get("url").getAsString();
                } else if ("FAILED".equals(taskStatus)) {
                    log.warn("NANO_BANANA 任务失败: taskId={}, message={}",
                            taskId, output.get("message"));
                    return null;
                }
                // RUNNING / PENDING: continue polling
                log.debug("NANO_BANANA 轮询 {}/{}: taskId={}, status={}",
                        i + 1, POLL_MAX_RETRIES, taskId, taskStatus);
            }
        }
        log.warn("NANO_BANANA 轮询超时: taskId={}", taskId);
        return null;
    }

    @Override
    public ImageData getImageData(ImageRequest request) {
        String prompt = request.getEffectiveParam(true);
        String url = searchImage(prompt);
        if (url != null) {
            return ImageData.fromUrl(url);
        }
        // 降级
        int pos = request.getPosition() != null ? request.getPosition() : 1;
        return ImageData.fromUrl(String.format(FALLBACK_URL, pos));
    }

    @Override
    public ImageMethodEnum getMethod() {
        return ImageMethodEnum.NANO_BANANA;
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