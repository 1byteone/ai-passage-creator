package com.example.aipassagecreator.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class PromptTemplateEngine {

    private final ResourceLoader resourceLoader;
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    public PromptTemplateEngine(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * 渲染 Prompt 模板
     * @param promptFilePath classpath 路径，如 skills/proofreading/prompts/phase1_content_review.md
     * @param variables 变量映射
     * @return 渲染后的完整 Prompt
     */
    public String render(String promptFilePath, Map<String, Object> variables) {
        String template = templateCache.computeIfAbsent(promptFilePath, path -> {
            try {
                Resource resource = resourceLoader.getResource("classpath:" + path);
                if (!resource.exists()) {
                    throw new IllegalArgumentException("Prompt 模板不存在: " + path);
                }
                byte[] bytes = StreamUtils.copyToByteArray(resource.getInputStream());
                return new String(bytes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("加载 Prompt 模板失败: " + path, e);
            }
        });

        String result = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace("{" + entry.getKey() + "}", value);
        }

        log.debug("Prompt 模板渲染完成: {} ({} 变量)", promptFilePath, variables.size());
        return result;
    }

    /** 清除模板缓存（用于热加载） */
    public void clearCache() {
        templateCache.clear();
        log.info("Prompt 模板缓存已清除");
    }
}