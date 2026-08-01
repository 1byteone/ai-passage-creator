package com.example.aipassagecreator.service.impl;

import com.example.aipassagecreator.mapper.ArticleQualityMapper;
import com.example.aipassagecreator.mapper.ArticleMapper;
import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.model.po.ArticleQuality;
import com.example.aipassagecreator.service.ContentQualityService;
import com.example.aipassagecreator.skill.ModelRouter;
import com.example.aipassagecreator.utils.GsonUtils;
import com.google.gson.reflect.TypeToken;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ContentQualityServiceImpl implements ContentQualityService {

    private static final String QUALITY_PROMPT = """
            你是一位资深内容评审专家。请对以下文章进行多维度质量评分（每项 0-100 分），
            并给出具体的改进建议和亮点。

            评分维度：
            - structure（结构）：段落划分是否合理，逻辑递进是否清晰
            - logic（逻辑）：论点是否有据可依，论证是否严密
            - language（语言）：表达是否流畅自然，有无 AI 痕迹过重
            - seo（SEO）：标题和关键词布局是否合理
            - readability（可读性）：排版是否舒适，信息密度是否适中

            请严格按以下 JSON 格式输出：
            {
              "structureScore": 85,
              "logicScore": 80,
              "languageScore": 75,
              "seoScore": 70,
              "readabilityScore": 82,
              "overallScore": 78,
              "suggestions": ["建议1", "建议2"],
              "strengths": ["亮点1", "亮点2"]
            }

            文章内容：
            %s
            """;

    @Resource
    private ArticleMapper articleMapper;

    @Resource
    private ArticleQualityMapper articleQualityMapper;

    @Resource
    private ModelRouter modelRouter;

    @Override
    public ArticleQuality evaluate(String taskId) {
        Article article = articleMapper.selectOneByQuery(
                QueryWrapper.create().eq("taskId", taskId));
        if (article == null) {
            throw new IllegalArgumentException("文章不存在: " + taskId);
        }

        String content = article.getContent();
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("文章内容为空: " + taskId);
        }

        // 截取前 8000 字符避免超长
        String snapshot = content.length() > 8000
                ? content.substring(0, 8000)
                : content;

        // 调用 LLM 评分
        ChatModel model = modelRouter.resolveWithFallback(
                null, null); // 使用默认模型

        long start = System.currentTimeMillis();
        String modelName = modelRouter.resolveModelName(null, null);

        String prompt = QUALITY_PROMPT.formatted(snapshot);
        ChatResponse response = model.call(new Prompt(List.of(new UserMessage(prompt))));
        String output = response.getResult().getOutput().getText();

        long duration = System.currentTimeMillis() - start;

        // 解析 JSON
        Map<String, Object> parsed = GsonUtils.fromJson(
                output, new TypeToken<Map<String, Object>>() {});
        int tokenUsage = response.getMetadata() != null
                && response.getMetadata().getUsage() != null
                && response.getMetadata().getUsage().getTotalTokens() != null
                ? response.getMetadata().getUsage().getTotalTokens()
                : 0;

        ArticleQuality quality = ArticleQuality.builder()
                .taskId(taskId)
                .articleContentSnapshot(snapshot)
                .structureScore(asInt(parsed.get("structureScore")))
                .logicScore(asInt(parsed.get("logicScore")))
                .languageScore(asInt(parsed.get("languageScore")))
                .seoScore(asInt(parsed.get("seoScore")))
                .readabilityScore(asInt(parsed.get("readabilityScore")))
                .overallScore(asInt(parsed.get("overallScore")))
                .suggestions(GsonUtils.toJson(parsed.get("suggestions")))
                .strengths(GsonUtils.toJson(parsed.get("strengths")))
                .modelUsed(modelName)
                .tokenUsage(tokenUsage)
                .durationMs((int) duration)
                .build();

        articleQualityMapper.insert(quality);
        log.info("文章质量评分完成: taskId={}, overallScore={}, model={}, duration={}ms",
                taskId, quality.getOverallScore(), modelName, duration);

        return quality;
    }

    @Override
    public ArticleQuality getLatest(String taskId) {
        return articleQualityMapper.selectOneByQuery(
                QueryWrapper.create()
                        .eq("task_id", taskId)
                        .orderBy("create_time", false)
                        .limit(1));
    }

    private static int asInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }
}
