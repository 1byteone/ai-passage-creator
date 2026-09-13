package com.example.aipassagecreator.service.impl;

import com.example.aipassagecreator.config.ObservabilityConfig;
import com.example.aipassagecreator.enums.ArticleStatusEnum;
import com.example.aipassagecreator.mapper.ArticleQualityMapper;
import com.example.aipassagecreator.mapper.ArticleMapper;
import com.example.aipassagecreator.methodology.MethodologyDefinition;
import com.example.aipassagecreator.methodology.MethodologyRegistry;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
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
            - language（语言）：表达是否流畅自然，有无空泛、夸大或模板化表达
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

    private static final String VIRAL_QUALITY_PROMPT = """
            你是一位资深内容评审专家。请对以下文章进行多维度质量评分（每项 0-100 分），
            并给出具体的改进建议和亮点。

            评分维度：
            - structure（结构）：段落划分是否合理，逻辑递进是否清晰
            - logic（逻辑）：论点是否有据可依，论证是否严密
            - language（语言）：表达是否流畅自然，有无空泛、夸大或模板化表达
            - seo（SEO）：标题和关键词布局是否合理
            - readability（可读性）：排版是否舒适，信息密度是否适中

            此外，请对以下爆款维度评分（同样 0-100 分）：
            %s

            标题：%s
            副标题：%s

            请严格按以下 JSON 格式输出：
            {
              "structureScore": 85, "logicScore": 80, "languageScore": 75,
              "seoScore": 70, "readabilityScore": 82, "overallScore": 78,
              "viral": {
                "emotionalTrigger": 85,
                "goldenSentence": 75,
                "interactionHook": 60,
                "persuasion": 90,
                "titleStrategy": 80
              },
              "titleStrategyHit": "curiosityGap",
              "suggestions": ["建议1"],
              "strengths": ["亮点1"]
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

    @Resource
    private MethodologyRegistry methodologyRegistry;

    // v1 以 @RateLimit 限流控制成本；独立评测预算/配额扣减留待后续（与创建配额分离）
    @Resource
    private ObservabilityConfig observabilityConfig;

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
                .scoreType("GENERIC")
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

        // 幂等 upsert: 先删旧评分再插入，避免唯一索引 (task_id, score_type) 冲突
        articleQualityMapper.deleteByQuery(
                QueryWrapper.create()
                        .eq("task_id", taskId)
                        .eq("score_type", "GENERIC"));
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
                        .eq("score_type", "GENERIC")
                        .orderBy("id", false)
                        .limit(1));
    }

    @Override
    public ArticleQuality evaluateViral(String taskId, String methodologyName, Long loginUserId) {
        Article article = articleMapper.selectOneByQuery(
                QueryWrapper.create().eq("taskId", taskId));
        if (article == null) {
            throw new IllegalArgumentException("文章不存在: " + taskId);
        }
        if (!ArticleStatusEnum.COMPLETED.getValue().equals(article.getStatus())) {
            throw new IllegalArgumentException("仅已完成文章可评测: " + taskId);
        }

        // methodology 白名单查名（fail-fast）
        String effective = methodologyName == null || methodologyName.isBlank()
                ? "default" : methodologyName;
        MethodologyDefinition def = methodologyRegistry.get(effective);

        // 组装评测维度说明
        StringBuilder dims = new StringBuilder();
        List<MethodologyDefinition.EvaluationDimension> evalDims = def.getEvaluationDimensions();
        if (evalDims != null) {
            for (MethodologyDefinition.EvaluationDimension d : evalDims) {
                dims.append("- ").append(d.getKey()).append("（").append(d.getName())
                        .append("）：").append(d.getRubric()).append("\n");
            }
        }

        // 内容非空校验（与 evaluate() 一致的守卫，LLM 调用前 fail-fast）
        String content = article.getContent();
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("文章内容为空: " + taskId);
        }

        // 快照：head-tail 策略（前 4000 + 后 4000）
        String snapshot = buildHeadTailSnapshot(content, 4000);

        // 合并单次调用
        ChatModel model = modelRouter.resolveWithFallback(null, null);
        String modelName = modelRouter.resolveModelName(null, null);
        long start = System.currentTimeMillis();
        String prompt = VIRAL_QUALITY_PROMPT.formatted(
                dims, article.getMainTitle() == null ? "" : article.getMainTitle(),
                article.getSubTitle() == null ? "" : article.getSubTitle(), snapshot);
        ChatResponse response = model.call(new Prompt(List.of(new UserMessage(prompt))));
        String output = response.getResult().getOutput().getText();
        long duration = System.currentTimeMillis() - start;
        int tokenUsage = response.getMetadata() != null
                && response.getMetadata().getUsage() != null
                && response.getMetadata().getUsage().getTotalTokens() != null
                ? response.getMetadata().getUsage().getTotalTokens() : 0;

        // 容错解析
        String fixed = GsonUtils.tryFixJson(output);
        Map<String, Object> parsed = GsonUtils.fromJsonSafe(fixed,
                new TypeToken<Map<String, Object>>() {});

        // 维度完备性 + 归一化
        BigDecimal viralScore = computeViralScore(parsed, evalDims);
        String viralScoresJson = GsonUtils.toJson(parsed == null ? null : parsed.get("viral"));
        String strategyHit = parsed != null && parsed.get("titleStrategyHit") instanceof String s ? s : null;

        // 幂等 upsert：同 taskId 的 VIRAL 评测复用行
        ArticleQuality existing = getLatestViral(taskId);
        ArticleQuality quality = ArticleQuality.builder()
                .id(existing != null ? existing.getId() : null)
                .taskId(taskId)
                .userId(loginUserId)
                .scoreType("VIRAL")
                .articleContentSnapshot(snapshot)
                .structureScore(asInt(parsed == null ? null : parsed.get("structureScore")))
                .logicScore(asInt(parsed == null ? null : parsed.get("logicScore")))
                .languageScore(asInt(parsed == null ? null : parsed.get("languageScore")))
                .seoScore(asInt(parsed == null ? null : parsed.get("seoScore")))
                .readabilityScore(asInt(parsed == null ? null : parsed.get("readabilityScore")))
                .overallScore(asInt(parsed == null ? null : parsed.get("overallScore")))
                .viralScore(viralScore)
                .viralScores(viralScoresJson)
                .titleStrategyHit(strategyHit)
                .methodologyUsed(effective)
                .contentHash(Integer.toHexString(content.hashCode()))
                .versionNo(existing != null && existing.getVersionNo() != null ? existing.getVersionNo() + 1 : 1)
                .suggestions(GsonUtils.toJson(parsed == null ? null : parsed.get("suggestions")))
                .strengths(GsonUtils.toJson(parsed == null ? null : parsed.get("strengths")))
                .modelUsed(modelName)
                .tokenUsage(tokenUsage)
                .durationMs((int) duration)
                .build();
        if (existing != null) {
            // 幂等重评：delete + insert 保证每个字段都反映最新评测。
            // 直接 update 因无全局 ignore-strategy 会跳过 null 列，导致 LLM 未返回的
            // titleStrategyHit/viral/suggestions/strengths 残留陈旧值（viral_score 却会被 0 覆盖）。
            articleQualityMapper.deleteById(existing.getId());
            quality.setId(null); // 重新生成主键，避免 insert 复用已删除行 id
            articleQualityMapper.insert(quality);
        } else {
            articleQualityMapper.insert(quality);
        }

        // 可观测
        observabilityConfig.recordLlmCall(modelName, duration, tokenUsage);
        log.info("爆款评测完成: taskId={}, viralScore={}, model={}, methodology={}, duration={}ms",
                taskId, viralScore, modelName, effective, duration);
        return quality;
    }

    @Override
    public ArticleQuality getLatestViral(String taskId) {
        return articleQualityMapper.selectOneByQuery(
                QueryWrapper.create()
                        .eq("task_id", taskId)
                        .eq("score_type", "VIRAL")
                        .orderBy("id", false)
                        .limit(1));
    }

    @Override
    public ArticleQuality restoreViral(String taskId, ArticleQuality quality) {
        // 先删当前 VIRAL 行（其描述的内容已被回退掉），再落库历史评测
        articleQualityMapper.deleteByQuery(
                QueryWrapper.create()
                        .eq("task_id", taskId)
                        .eq("score_type", "VIRAL"));
        if (quality == null) {
            return null;
        }
        quality.setId(null); // 重新生成主键，避免复用已删除行 id
        articleQualityMapper.insert(quality);
        log.info("爆款评测已恢复(反哺回退): taskId={}, viralScore={}, versionNo={}",
                taskId, quality.getViralScore(), quality.getVersionNo());
        return quality;
    }

    /** head-tail 双段快照 */
    private String buildHeadTailSnapshot(String content, int headLen) {
        if (content.length() <= headLen * 2) {
            return content;
        }
        return content.substring(0, headLen) + "\n...[中段省略]...\n"
                + content.substring(content.length() - headLen);
    }

    /** 归一化加权：Σ(score×weight)/Σweight */
    private BigDecimal computeViralScore(Map<String, Object> parsed,
            List<MethodologyDefinition.EvaluationDimension> evalDims) {
        if (parsed == null) {
            return BigDecimal.ZERO;
        }
        Object viralObj = parsed.get("viral");
        if (!(viralObj instanceof Map)) {
            return BigDecimal.ZERO;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> viral = (Map<String, Object>) viralObj;
        int sumWeight = 0;
        int sumScoreWeight = 0;
        boolean anyScored = false;
        if (evalDims != null) {
            for (MethodologyDefinition.EvaluationDimension d : evalDims) {
                int w = d.getWeight() == null ? 0 : d.getWeight();
                Integer score = viral.get(d.getKey()) instanceof Number n ? n.intValue()
                        : (viral.get(d.getKey()) instanceof String s ? parseIntSafe(s) : null);
                if (score == null) {
                    continue; // 维度缺失：跳过不参与（incomplete 语义）
                }
                anyScored = true;
                sumWeight += w;
                sumScoreWeight += score * w;
            }
        }
        if (!anyScored || sumWeight <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(sumScoreWeight)
                .divide(BigDecimal.valueOf(sumWeight), 2, RoundingMode.HALF_UP);
    }

    private static Integer parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int asInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }
}
