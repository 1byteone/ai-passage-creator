package com.example.aipassagecreator.integration;

import com.example.aipassagecreator.card.CardComplianceChecker;
import com.example.aipassagecreator.card.CardStructurePlanner;
import com.example.aipassagecreator.card.CardTemplateEngine;
import com.example.aipassagecreator.card.model.ComplianceReport;
import com.example.aipassagecreator.card.model.PagePlan;
import com.example.aipassagecreator.methodology.MethodologyDefinition;
import com.example.aipassagecreator.methodology.MethodologyRefiner;
import com.example.aipassagecreator.methodology.MethodologyRegistry;
import com.example.aipassagecreator.publish.platform.ContentPublisher;
import com.example.aipassagecreator.publish.platform.PlatformAdapter;
import com.example.aipassagecreator.publish.platform.PlatformAdapterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 第 5 轮功能测试：跨系统集成
 * <p>验证全过程链条：方法论 → 分页 → 合规 → 模板 → 适配 → 发布输出</p>
 */
@SpringBootTest
class CrossSubsystemIntegrationTest {

    @Autowired
    private MethodologyRegistry methodologyRegistry;

    @Autowired
    private CardStructurePlanner planner;

    @Autowired
    private CardComplianceChecker complianceChecker;

    @Autowired
    private CardTemplateEngine templateEngine;

    @Autowired
    private PlatformAdapterRegistry adapterRegistry;

    @Autowired
    private ContentPublisher contentPublisher;

    @Autowired
    private MethodologyRefiner refiner;

    private static final String SAMPLE_CONTENT =
            "## 情绪触发点\n\n击中读者的情绪，引起共鸣或向往，是自媒体创作的核心法则。\n\n" +
            "## 金句\n\n金句需要从内容中自然生长，结尾提炼，成为可独立转发的高价值内容。\n\n" +
            "## 互动钩子\n\n开放提问、争议观点、投票、晒图——让读者有话想说。";

    // ============= Round 5.1: 方法论→卡片 链路 =============

    @Test
    void methodology_cascadesToCardPlan() {
        // xiaohongshu: maxChars=800, cardStyle=warm
        MethodologyDefinition xhs = methodologyRegistry.get("xiaohongshu");
        assertEquals(Integer.valueOf(800), xhs.getPlatform().getMaxChars());

        // 分页
        List<PagePlan> pages = planner.plan(SAMPLE_CONTENT, "标题", "副标题", null);
        assertFalse(pages.isEmpty());

        // 合规（用 xiaohongshu 阈值校验）
        ComplianceReport report = complianceChecker.textCheck(pages, "标题", "xiaohongshu");
        assertNotNull(report);
        assertNotNull(report.getRules());

        // 模板渲染（取内容页，非封面页）
        String cardStyle = xhs.getPlatform().getCardStyle();
        assertEquals("warm", cardStyle);
        List<PagePlan> contentPages = pages.stream()
                .filter(p -> "CONTENT".equals(p.getPageType()))
                .limit(2).toList();
        List<String> htmls = templateEngine.render(contentPages, cardStyle);
        assertFalse(htmls.isEmpty());
        assertTrue(htmls.get(0).contains("情绪触发点"), "卡片 HTML 应含原文字（非封面页）");
    }

    // ============= Round 5.2: 方法论→发布 链路 =============

    @Test
    void methodology_cascadesToPublishContent() {
        // 模拟文章 → 发布转换 (xiaohongshu)
        com.example.aipassagecreator.model.po.Article article =
                new com.example.aipassagecreator.model.po.Article();
        article.setMainTitle("自媒体创作指南");
        article.setContent(SAMPLE_CONTENT);
        article.setMethodology("xiaohongshu");

        String json = contentPublisher.convertAndValidate(article, "xiaohongshu", "xiaohongshu");
        assertNotNull(json);
        assertTrue(json.contains("自媒体创作指南"), "应含标题");
        assertTrue(json.contains("情绪"), "应含正文片段");
    }

    // ============= Round 5.3: 反哺闭环 状态完整性 =============

    @Test
    void refine_skipsWhenNoEval() {
        MethodologyRefiner.RefineResult result = refiner.refine("nonexistent-task", "default", 1L);
        assertTrue(result.isSkipped());
        assertEquals(0, result.getRounds());
    }

    // ============= Round 5.4: 跨平台适配覆盖率 =============

    @Test
    void allPlatforms_haveCorrespondingAdapter() {
        for (String platform : List.of("wechat", "xiaohongshu", "douyin")) {
            PlatformAdapter adapter = adapterRegistry.get(platform);
            assertNotNull(adapter, platform + " 应有适配器");
            assertTrue(methodologyRegistry.exists(platform), platform + " 应有方法论");
        }
    }

    @Test
    void allPlatforms_havePlatformConfig() {
        for (String name : List.of("wechat", "xiaohongshu", "douyin")) {
            MethodologyDefinition def = methodologyRegistry.get(name);
            assertNotNull(def.getPlatform(), name + " 应有 platform 配置");
            assertNotNull(def.getPlatform().getMinChars(), name + " 应有 minChars");
            assertNotNull(def.getPlatform().getMaxChars(), name + " 应有 maxChars");
        }
    }

    // ============= Round 5.5: 模板白名单安全 =============

    @Test
    void templateStyle_whitelist_enforced() {
        // 合法风格
        for (String style : List.of("warm", "minimal", "free")) {
            List<PagePlan> pages = List.of(
                    PagePlan.builder().pageNo(1).contentMd("内容").title("标题").build());
            List<String> htmls = templateEngine.render(pages, style);
            assertNotNull(htmls);
            assertFalse(htmls.isEmpty());
        }
        // 非法风格回退 warm（不抛异常）
        List<PagePlan> pages = List.of(
                PagePlan.builder().pageNo(1).contentMd("内容").title("标题").build());
        List<String> htmls = templateEngine.render(pages, "bogus");
        assertEquals(1, htmls.size());
        assertNotNull(htmls.get(0));
    }
}
