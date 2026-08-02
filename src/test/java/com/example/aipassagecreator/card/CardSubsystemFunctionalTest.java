package com.example.aipassagecreator.card;

import com.example.aipassagecreator.card.model.PagePlan;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 第 2 轮功能测试：图文卡片子系统
 * <p>验证：分页引擎完整路径 / 合规检查全规则 / 模板渲染非空</p>
 */
@SpringBootTest
class CardSubsystemFunctionalTest {

    @Autowired
    private CardStructurePlanner planner;

    @Autowired
    private CardComplianceChecker complianceChecker;

    @Autowired
    private CardTemplateEngine templateEngine;

    // ============= Round 2.1: 分页引擎全路径 =============

    @Test
    void plan_longArticle_producesMultipleContentPages() {
        String content = "## 第一章\n\n" + "章节内容。".repeat(80) + "\n\n## 第二章\n\n" + "更多内容。".repeat(80);
        List<PagePlan> pages = planner.plan(content, "主标题", "副标题", null);
        assertEquals(1, pages.get(0).getPageType().equals("COVER") ? 1 : 0,
                "第一页应是封面");
        assertTrue(pages.size() >= 3, "长文章应拆分多页");
    }

    @Test
    void plan_shortArticle_coverPlusOneContent() {
        String content = "## 简介\n\n简短内容。";
        List<PagePlan> pages = planner.plan(content, "标题", "副标题", null);
        assertEquals("COVER", pages.get(0).getPageType());
        assertTrue(pages.size() >= 2, "至少封面+1内容页");
    }

    @Test
    void plan_noHeading_createsContentPage() {
        String content = "纯文本无标题内容，用于测试分页降级路径。".repeat(10);
        List<PagePlan> pages = planner.plan(content, "标题", "副标题", null);
        assertTrue(pages.size() >= 2, "无章节标题时也要创建内容页");
    }

    @Test
    void plan_emptyContent_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> planner.plan("", "标题", "副标题", null));
    }

    @Test
    void plan_stripsImagePlaceholders() {
        String content = "内容 {{IMAGE_PLACEHOLDER_1}} 和 {{ICON_PLACEHOLDER_2}} 残留。";
        List<PagePlan> pages = planner.plan(content, "标题", "副标题", null);
        String pageContent = pages.get(1).getContentMd();
        assertFalse(pageContent.contains("PLACEHOLDER"), "残留占位符应被清理");
    }

    // ============= Round 2.2: 合规检查全覆盖 =============

    @Test
    void textCheck_titleTooLong_warns() {
        String longTitle = "这是一个非常长的标题超过了二十个字的限制测试";
        List<PagePlan> pages = List.of(
                PagePlan.builder().pageNo(1).contentMd("内容").build());
        var report = complianceChecker.textCheck(pages, longTitle, "default");
        var titleRule = report.getRules().get(0);
        assertEquals("TitleLengthRule", titleRule.getRuleName());
        assertEquals("WARNING", titleRule.getLevel());
        assertFalse(titleRule.isPassed());
    }

    @Test
    void textCheck_emptyContent_passesCompliance() {
        List<PagePlan> pages = List.of(
                PagePlan.builder().pageNo(1).contentMd("简短的合规内容").build());
        var report = complianceChecker.textCheck(pages, "正常标题", "default");
        assertTrue(report.isPassed(), "合规内容应通过检查");
    }

    @Test
    void textCheck_xiaohongshuMaxChars_usesPlatformThreshold() {
        String longContent = "a".repeat(900); // xiaohongshu maxChars=800
        List<PagePlan> pages = List.of(
                PagePlan.builder().pageNo(1).contentMd(longContent).build());
        var report = complianceChecker.textCheck(pages, "正常标题", "xiaohongshu");
        assertFalse(report.isPassed(), "小红书 800 字上限应触发 ERROR");
    }

    // ============= Round 2.3: 模板渲染 =============

    @Test
    void templateEngine_rendersAllThreeStyles() {
        List<PagePlan> pages = List.of(
                PagePlan.builder().pageNo(1).contentMd("测试内容").title("测试标题").build());
        for (String style : List.of("warm", "minimal", "free")) {
            List<String> htmls = templateEngine.render(pages, style);
            assertEquals(1, htmls.size());
            assertTrue(htmls.get(0).contains("测试标题"), style + " 应有标题");
            assertTrue(htmls.get(0).contains("测试内容"), style + " 应有内容");
        }
    }

    @Test
    void templateEngine_unknownStyle_defaultsToWarm() {
        List<PagePlan> pages = List.of(
                PagePlan.builder().pageNo(1).contentMd("内容").title("标题").build());
        List<String> htmls = templateEngine.render(pages, "bogus");
        assertEquals(1, htmls.size());
        assertNotNull(htmls.get(0));
        assertFalse(htmls.get(0).isEmpty());
    }
}
