package com.example.aipassagecreator.publish.platform;

import com.example.aipassagecreator.methodology.MethodologyDefinition;
import com.example.aipassagecreator.methodology.MethodologyRegistry;
import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.utils.GsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内容发布编排器：读取文章 → 平台适配转换 → 校验 → 输出平台文案 JSON。
 */
@Slf4j
@Component
public class ContentPublisher {

    private final PlatformAdapterRegistry adapterRegistry;
    private final MethodologyRegistry methodologyRegistry;

    public ContentPublisher(PlatformAdapterRegistry adapterRegistry,
                            MethodologyRegistry methodologyRegistry) {
        this.adapterRegistry = adapterRegistry;
        this.methodologyRegistry = methodologyRegistry;
    }

    /**
     * 执行内容适配转换 + 校验。
     * @return JSON 格式的 adapterOutput（含 title/body/topics/issues/metadata）
     */
    public String convertAndValidate(Article article, String platform,
                                     String methodologyName) {
        PlatformAdapter adapter = adapterRegistry.get(platform);
        MethodologyDefinition def = methodologyRegistry.get(
                methodologyName != null && !methodologyName.isBlank() ? methodologyName : platform);

        String title = article.getMainTitle() != null ? article.getMainTitle()
                : article.getTopic() != null ? article.getTopic() : "";
        String markdown = article.getContent() != null ? article.getContent() : "";

        PlatformContent content = adapter.convert(title, markdown, def);
        List<ComplianceIssue> issues = adapter.validate(content, def);

        // 构建输出 JSON
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("title", content.title());
        output.put("body", content.body());
        output.put("topics", content.topics());
        output.put("issues", issues.stream()
                .map(i -> Map.of("rule", i.rule(), "level", i.level(), "message", i.message()))
                .toList());
        output.put("metadata", content.metadata());

        String json = GsonUtils.toJson(output);
        log.info("平台适配完成: platform={}, title={}, charCount={}",
                platform, title,
                content.metadata() != null ? content.metadata().get("charCount") : "?");
        return json;
    }
}
