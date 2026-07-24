package com.example.aipassagecreator.service;

import com.example.aipassagecreator.enums.ImageMethodEnum;
import com.example.aipassagecreator.model.dto.image.ImageData;
import com.example.aipassagecreator.model.dto.image.ImageRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AgnesImageService implements ImageSearchService {

    private final ChatModel agnesChatModel;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${model.router.image-model:agnes-image-2.1-flash}")
    private String imageModel;

    public AgnesImageService(@Qualifier("agnesChatModel") ChatModel agnesChatModel) {
        this.agnesChatModel = agnesChatModel;
    }

    @Override
    public ImageMethodEnum getMethod() {
        return ImageMethodEnum.AGNES;
    }

    @Override
    public String searchImage(String keywords) {
        // 通过 getImageData 实现，返回 URL
        ImageRequest request = ImageRequest.builder()
                .prompt(keywords)
                .keywords(keywords)
                .build();
        ImageData data = getImageData(request);
        return data != null ? data.getUrl() : null;
    }

    @Override
    public ImageData getImageData(ImageRequest request) {
        // 1. 翻译 Prompt 为英文
        String englishPrompt = translatePrompt(request.getPrompt());
        log.debug("AGNES 图片生成: prompt={}", englishPrompt);

        // 2. 构建请求体
        Map<String, Object> body = new HashMap<>();
        body.put("model", imageModel);
        body.put("prompt", englishPrompt);
        body.put("size", "1024x768");
        body.put("extra_body", Map.of("response_format", "url"));

        // 3. 调用 API
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://apihub.agnes-ai.com/v1/images/generations",
                    new HttpEntity<>(body, headers),
                    Map.class);

            // 4. 提取 URL
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
            if (data != null && !data.isEmpty()) {
                String imageUrl = (String) data.get(0).get("url");
                log.info("AGNES 图片生成成功: url={}", imageUrl);
                return ImageData.fromUrl(imageUrl);
            }
            throw new RuntimeException("AGNES 图片 API 返回为空");
        } catch (Exception e) {
            log.error("AGNES 图片生成失败: {}", e.getMessage());
            throw new RuntimeException("AGNES 图片生成失败", e);
        }
    }

    private String translatePrompt(String text) {
        if (text == null || text.isBlank()) return "a beautiful landscape";
        // 检查是否包含非 ASCII 字符（中文等）
        if (text.chars().noneMatch(c -> c > 127)) return text;

        String translation = agnesChatModel.call(new Prompt(
                new SystemMessage("Translate the following Chinese text to English. Preserve all visual details, style, lighting, composition information. Return only the English translation, no explanations."),
                new UserMessage(text)
        )).getResult().getOutput().getText();

        return translation != null && !translation.isBlank() ? translation.trim() : text;
    }

    @Override
    public String getFallbackImage(int position) {
        return "https://picsum.photos/seed/agnes" + position + "/1024/768";
    }
}