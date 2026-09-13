package com.example.aipassagecreator.service;

import com.example.aipassagecreator.constant.UserConstant;
import com.example.aipassagecreator.mapper.ArticleQualityMapper;
import com.example.aipassagecreator.methodology.antiai.AntiAiFlavorChecker;
import com.example.aipassagecreator.methodology.antiai.AntiAiFlavorRules;
import com.example.aipassagecreator.model.dto.article.ArticleState;
import com.example.aipassagecreator.model.po.ArticleQuality;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.skill.SkillExecution;
import com.example.aipassagecreator.skill.SkillRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 创作质量门 — 生成完成后自动进行表达质量审计，未通过则触发内容质量 Skill 自动优化。
 * <p>
 * 质量门报告经 QUALITY_CHECKED SSE 推送至前端，ALL_COMPLETE 之前。
 * 爆款评分仅 VIP/管理员在 COMPLETED 状态后才能调用，由调用方 {@link ArticleAsyncService} 编排。
 */
@Service
@Slf4j
public class ArticleQualityGateService {

    @Resource
    private SkillRegistry skillRegistry;

    @Resource
    private ArticleQualityMapper articleQualityMapper;

    @Resource
    private UserService userService;

    @Value("${article.quality-gate.enabled:true}")
    private boolean enabled;

    @Value("${article.quality-gate.auto-detox:true}")
    private boolean autoDetoxEnabled;

    @Value("${article.quality-gate.detox-intensity:medium}")
    private String detoxIntensity;

    @Value("${article.quality-gate.pass-threshold:50}")
    private int passThreshold;

    /**
     * 表达质量门检测结果
     */
    public record GateResult(int score, boolean passed, boolean detoxed, List<String> violations) {
    }

    /**
     * 审计文章表达质量，未通过且自动优化开启时同步调用内容质量 Skill。
     * <p>改写后的内容直接写入 state，调用方在最终 saveArticleContent 时保存。</p>
     *
     * @param state  当前文章状态（内容可能被改写）
     * @param taskId 文章任务 ID
     * @param userId 文章作者 ID
     * @return 最终检测报告（可能是改写后的二次报告）
     */
    public GateResult checkAndDetox(ArticleState state, String taskId, Long userId) {
        String text = resolveContent(state);
        if (text == null || text.isBlank()) {
            log.warn("文章内容为空，跳过质量门: taskId={}", taskId);
            return new GateResult(100, true, false, List.of());
        }

        if (!enabled) {
            log.info("质量门已禁用，跳过: taskId={}", taskId);
            return new GateResult(100, true, false, List.of());
        }

        AntiAiFlavorChecker.AiFlavorReport report = AntiAiFlavorChecker.check(text, passThreshold);
        boolean detoxed = false;
        String finalText = text;

        if (!report.passed() && autoDetoxEnabled) {
            log.info("表达质量未通过 (score={}, violations={}), 触发表达优化: taskId={}",
                    report.score(), report.violations().size(), taskId);
            try {
                String detoxedContent = invokeDetox(text, userId);
                if (detoxedContent != null && !detoxedContent.isBlank() && !detoxedContent.equals(text)) {
                    state.setFullContent(detoxedContent);
                    state.setContent(detoxedContent);
                    detoxed = true;
                    finalText = detoxedContent;
                    report = AntiAiFlavorChecker.check(detoxedContent, passThreshold);
                    log.info("表达优化后复检: score={}, passed={}: taskId={}",
                            report.score(), report.passed(), taskId);
                } else {
                    log.info("改写无变化或产出为空，保留原文: taskId={}", taskId);
                }
            } catch (Exception e) {
                log.error("内容质量优化异常，保留原文: taskId={}", taskId, e);
            }
        }

        persistQuality(taskId, userId, finalText, report, detoxed);
        return new GateResult(report.score(), report.passed(), detoxed, report.violations());
    }

    /**
     * 判断用户是否为 VIP 或管理员，用于控制爆款评分等高级功能。
     */
    public boolean isVipOrAdmin(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return false;
        }
        String role = user.getUserRole();
        return UserConstant.ADMIN_ROLE.equals(role) || UserConstant.VIP_ROLE.equals(role);
    }

    // ──────────────────────────── private ────────────────────────────

    /**
     * 从 state 取完整正文：优先 fullContent，为空则取 content。
     */
    private String resolveContent(ArticleState state) {
        String full = state.getFullContent();
        if (full != null && !full.isBlank()) {
            return full;
        }
        return state.getContent();
    }

    /**
     * 同步调用 ai-detox Skill 优化文章表达。
     * <p>镜像 {@code SkillExecutionChain.executeSync} 的用法：createExecution → prepare → execute → getPersistedOutput。</p>
     */
    private String invokeDetox(String text, Long userId) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("articleContent", text);
        inputs.put("intensity", detoxIntensity);

        SkillExecution execution = skillRegistry.createExecution("ai-detox", inputs);
        execution.execute(msg -> {
        }, userId);

        Map<String, Object> output = execution.getPersistedOutput();
        Object detoxed = output.get("detoxedContent");
        return detoxed != null ? detoxed.toString() : null;
    }

    /**
     * 持久化 AI_FLAVOR 评分行到 article_quality 表。
     */
    private void persistQuality(String taskId, Long userId, String snapshot,
                                AntiAiFlavorChecker.AiFlavorReport report, boolean detoxed) {
        // 建议文本以换行分隔违规项
        String suggestions = report.hasViolations()
                ? String.join("\n", report.violations())
                : (detoxed ? "已自动优化表达" : "通过");

        ArticleQuality quality = ArticleQuality.builder()
                .taskId(taskId)
                .userId(userId)
                .scoreType("AI_FLAVOR")
                .overallScore(report.score())
                .suggestions("规则集: " + AntiAiFlavorRules.HUMANIZER_RULE_VERSION + "\n" + suggestions)
                .articleContentSnapshot(snapshot.length() > 4000 ? snapshot.substring(0, 4000) : snapshot)
                .methodologyUsed(AntiAiFlavorRules.HUMANIZER_RULE_VERSION)
                .build();

        articleQualityMapper.insert(quality);
        log.info("表达质量评分已持久化: taskId={}, score={}, passed={}, optimized={}",
                taskId, report.score(), report.passed(), detoxed);
    }
}
