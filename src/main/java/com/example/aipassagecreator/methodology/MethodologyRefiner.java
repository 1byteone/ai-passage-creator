package com.example.aipassagecreator.methodology;

import com.example.aipassagecreator.model.po.ArticleQuality;
import com.example.aipassagecreator.model.po.ArticleVersion;
import com.example.aipassagecreator.service.ArticleRewriteService;
import com.example.aipassagecreator.service.ContentQualityService;
import com.example.aipassagecreator.utils.GsonUtils;
import com.google.gson.reflect.TypeToken;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 爆款反哺闭环：低分维度定向改写 → 复评 → 无提升回退。
 * <p>由编排层（Controller）驱动，每任务最多 MAX_ROUNDS 轮；每轮只改最弱的 1-2 个维度。</p>
 */
@Slf4j
@Service
public class MethodologyRefiner {

    private static final int MAX_ROUNDS = 3;
    private static final int WEAK_THRESHOLD = 60;
    private static final int LOW_VIRAL_THRESHOLD = 70;

    private final MethodologyRegistry registry;
    private final ContentQualityService contentQualityService;
    private final ArticleRewriteService articleRewriteService;

    public MethodologyRefiner(MethodologyRegistry registry,
                              ContentQualityService contentQualityService,
                              ArticleRewriteService articleRewriteService) {
        this.registry = registry;
        this.contentQualityService = contentQualityService;
        this.articleRewriteService = articleRewriteService;
    }

    /**
     * 执行一轮反哺闭环。返回本次优化结果。
     */
    public RefineResult refine(String taskId, String methodologyName, Long loginUserId) {
        String effective = methodologyName == null || methodologyName.isBlank()
                ? "default" : methodologyName;
        MethodologyDefinition def = registry.get(effective);

        ArticleQuality initial = contentQualityService.getLatestViral(taskId);
        if (initial == null || initial.getViralScore() == null) {
            log.info("无爆款评测结果，跳过反哺: taskId={}", taskId);
            return RefineResult.builder().rounds(0).skipped(true).build();
        }
        if (initial.getViralScore().compareTo(BigDecimal.valueOf(LOW_VIRAL_THRESHOLD)) >= 0
                && findWeakDimensions(initial, def).isEmpty()) {
            log.info("评测达标，无需反哺: taskId={}, viralScore={}", taskId, initial.getViralScore());
            return RefineResult.builder().rounds(0).skipped(true).build();
        }

        int rounds = 0;
        BigDecimal before = initial.getViralScore();
        BigDecimal after = before;
        List<String> weakDims = new ArrayList<>();
        boolean reverted = false;

        ArticleQuality current = initial;
        while (rounds < MAX_ROUNDS) {
            weakDims = findWeakDimensions(current, def);
            if (weakDims.isEmpty()) {
                break;
            }
            // 取 top-2 最弱维度（findWeakDimensions 已按分数升序，最弱在前）
            List<String> targets = weakDims.size() > 2 ? weakDims.subList(0, 2) : weakDims;

            // 组装定向改写指令（查对应创作维度 guidance）
            String instruction = buildRefineInstruction(def, targets);

            // 改写（v1 整篇；service 内校验归属）。返回新版本号用于无提升时回退到改写前版本。
            ArticleVersion newVersion = articleRewriteService.rewriteSection(
                    taskId, instruction, null, loginUserId);

            // 复评
            ArticleQuality reEval = contentQualityService.evaluateViral(taskId, effective, loginUserId);
            after = reEval.getViralScore();

            log.info("反哺第 {} 轮完成: viralScore {} -> {}", rounds + 1, before, after);
            rounds++;

            if (after.compareTo(before) <= 0) {
                // 无提升：回退到本轮改写前的版本（newVersion.versionNo - 1），终止
                // 版本语义：每个 rewriteSection 创建一个新版本；revertTo 会把回退记录作为下一版本追加。
                // 注意不能取 reEval.getVersionNo() —— 那是 VIRAL 评测自身的版本计数器，与文章版本号不同空间。
                int revertTarget = newVersion.getVersionNo() != null ? newVersion.getVersionNo() - 1 : 0;
                if (revertTarget > 0) {
                    articleRewriteService.revertTo(taskId, revertTarget, loginUserId);
                    // current 即回退目标内容对应的评测（本轮改写前）；恢复它，
                    // 避免 evaluateViral 的 delete+insert 把最新 VIRAL 行留在"已回退内容"的分数上
                    contentQualityService.restoreViral(taskId, current);
                } else {
                    log.warn("无提升但不存在可回退的前驱版本(本轮版本号 {}), 跳过 revertTo: taskId={}",
                            newVersion.getVersionNo(), taskId);
                }
                reverted = true;
                break;
            }
            before = after;
            current = reEval;
        }

        return RefineResult.builder()
                .rounds(rounds)
                .weakDimensions(weakDims)
                .reverted(reverted)
                .beforeScore(initial.getViralScore())
                .afterScore(reverted ? before : after)
                .skipped(false)
                .build();
    }

    /** 找出低于阈值的评测维度（按 viral_scores JSON 中的 key），按分数升序（最弱在前） */
    private List<String> findWeakDimensions(ArticleQuality quality, MethodologyDefinition def) {
        List<String> weak = new ArrayList<>();
        Map<String, Object> viral = GsonUtils.fromJsonSafe(
                quality.getViralScores(), new TypeToken<Map<String, Object>>() {});
        if (viral == null) {
            return weak;
        }
        for (MethodologyDefinition.EvaluationDimension d : def.getEvaluationDimensions()) {
            Object v = viral.get(d.getKey());
            int score = v instanceof Number n ? n.intValue()
                    : (v instanceof String s ? parseIntSafe(s) : -1);
            if (score >= 0 && score < WEAK_THRESHOLD) {
                weak.add(d.getKey());
            }
        }
        // 升序：分数最低（最弱）的维度排在最前，subList(0,2) 即"最弱的 1-2 个维度"
        weak.sort((a, b) -> Integer.compare(
                viralScoreOf(viral, a), viralScoreOf(viral, b)));
        return weak;
    }

    private int viralScoreOf(Map<String, Object> viral, String key) {
        Object v = viral.get(key);
        return v instanceof Number n ? n.intValue()
                : (v instanceof String s ? parseIntSafe(s) : 0);
    }

    /** 组装定向改写指令：低分维度 → 对应创作维度 guidance */
    private String buildRefineInstruction(MethodologyDefinition def, List<String> targets) {
        StringBuilder sb = new StringBuilder("请针对以下薄弱维度定向优化文章：\n");
        for (String key : targets) {
            for (MethodologyDefinition.CreationDimension cd : def.getCreationDimensions()) {
                if (key.equals(cd.getKey())) {
                    sb.append("- ").append(cd.getName()).append("：").append(cd.getGuidance()).append("\n");
                }
            }
        }
        return sb.toString();
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Data
    @Builder
    public static class RefineResult {
        private int rounds;
        private List<String> weakDimensions;
        private boolean reverted;
        private boolean skipped;
        private BigDecimal beforeScore;
        private BigDecimal afterScore;
    }
}
