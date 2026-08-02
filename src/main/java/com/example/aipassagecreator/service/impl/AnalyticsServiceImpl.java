package com.example.aipassagecreator.service.impl;

import com.example.aipassagecreator.mapper.ArticleMapper;
import com.example.aipassagecreator.mapper.ArticleQualityMapper;
import com.example.aipassagecreator.mapper.SkillExecutionMapper;
import com.example.aipassagecreator.model.po.Article;
import com.example.aipassagecreator.model.po.ArticleQuality;
import com.example.aipassagecreator.model.vo.AnalyticsVO;
import com.example.aipassagecreator.service.AnalyticsService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    @Resource
    private ArticleMapper articleMapper;

    @Resource
    private ArticleQualityMapper qualityMapper;

    @Resource
    private SkillExecutionMapper skillExecutionMapper;

    @Override
    public AnalyticsVO getContentAnalytics() {
        List<Article> allArticles = articleMapper.selectListByQuery(
                QueryWrapper.create().eq("isDelete", 0));
        return buildAnalytics(allArticles, null);
    }

    @Override
    public AnalyticsVO getUserAnalytics(Long userId) {
        List<Article> userArticles = articleMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("userId", userId)
                        .eq("isDelete", 0));
        return buildAnalytics(userArticles, userId);
    }

    private AnalyticsVO buildAnalytics(List<Article> articles, Long userId) {
        // 风格分布
        Map<String, Long> styleDistribution = articles.stream()
                .filter(a -> a.getStyle() != null)
                .collect(Collectors.groupingBy(Article::getStyle, Collectors.counting()));

        // 配图方式分布（从 images JSON 提取 method 字段的简化统计）
        Map<String, Long> imageMethodDistribution = new LinkedHashMap<>();
        articles.stream()
                .filter(a -> a.getEnabledImageMethods() != null)
                .forEach(a -> {
                    try {
                        List<String> methods = com.example.aipassagecreator.utils.GsonUtils.fromJson(
                                a.getEnabledImageMethods(),
                                new com.google.gson.reflect.TypeToken<List<String>>() {});
                        if (methods != null) {
                            for (String m : methods) {
                                imageMethodDistribution.merge(m, 1L, Long::sum);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("解析图片方法分布失败: {}", e.getMessage());
                    }
                });

        // 质量评分趋势
        List<Double> qualityTrend = new ArrayList<>();
        List<ArticleQuality> qualityList = qualityMapper.selectListByQuery(
                QueryWrapper.create()
                        .orderBy("create_time", true)
                        .limit(20));
        double avgQuality = 0;
        if (!qualityList.isEmpty()) {
            avgQuality = qualityList.stream()
                    .filter(q -> q.getOverallScore() != null)
                    .mapToInt(ArticleQuality::getOverallScore)
                    .average()
                    .orElse(0);
            for (ArticleQuality q : qualityList) {
                if (q.getOverallScore() != null) {
                    qualityTrend.add(q.getOverallScore().doubleValue());
                }
            }
        }

        // 技能使用排行
        Map<String, Long> skillUsageTop = new LinkedHashMap<>();
        List<com.example.aipassagecreator.model.po.SkillExecutionPo> executions =
                skillExecutionMapper.selectListByQuery(
                        QueryWrapper.create()
                                .eq("user_id", userId, userId != null)
                                .limit(10));
        Map<String, Long> modelUsage = executions.stream()
                .filter(e -> e.getModelUsed() != null)
                .collect(Collectors.groupingBy(
                        com.example.aipassagecreator.model.po.SkillExecutionPo::getModelUsed,
                        Collectors.counting()));
        executions.stream()
                .collect(Collectors.groupingBy(
                        com.example.aipassagecreator.model.po.SkillExecutionPo::getSkillName,
                        Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .forEach(e -> skillUsageTop.put(e.getKey(), e.getValue()));

        // 近 7 天活跃度
        Map<String, Long> dailyActiveUsers = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            dailyActiveUsers.put(day.toString(), 0L);
        }
        articles.stream()
                .filter(a -> a.getCreateTime() != null)
                .forEach(a -> {
                    LocalDate d = a.getCreateTime().toLocalDate();
                    if (dailyActiveUsers.containsKey(d.toString())) {
                        dailyActiveUsers.merge(d.toString(), 1L, Long::sum);
                    }
                });

        // 成功率
        long total = articles.size();
        long completed = articles.stream()
                .filter(a -> "COMPLETED".equals(a.getStatus()))
                .count();
        double successRate = total == 0 ? 0 : (completed * 100.0 / total);

        // Token 总消耗
        long totalTokens = executions.stream()
                .filter(e -> e.getTokenUsage() != null)
                .mapToLong(com.example.aipassagecreator.model.po.SkillExecutionPo::getTokenUsage)
                .sum();

        return AnalyticsVO.builder()
                .totalArticles((long) articles.size())
                .styleDistribution(styleDistribution)
                .imageMethodDistribution(imageMethodDistribution)
                .qualityTrend(qualityTrend)
                .avgQualityScore(avgQuality)
                .skillUsageTop(skillUsageTop)
                .modelUsage(modelUsage)
                .dailyActiveUsers(dailyActiveUsers)
                .successRate(successRate)
                .totalTokenUsage(totalTokens)
                .build();
    }
}
