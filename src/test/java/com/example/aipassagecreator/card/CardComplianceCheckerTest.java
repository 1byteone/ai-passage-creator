package com.example.aipassagecreator.card;

import com.example.aipassagecreator.card.model.ComplianceReport;
import com.example.aipassagecreator.card.model.PagePlan;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class CardComplianceCheckerTest {

    @Autowired
    private CardComplianceChecker checker;

    @Test
    void textCheck_titleTooLong_warns() {
        // 标题 25 字 > 20，触发 TitleLengthRule 警告
        String longTitle = "这个标题实在是太长了已经远远超过二十个字的显示限制";
        assertEquals(25, longTitle.length(), "测试标题应真实超过 20 字上限");
        List<PagePlan> pages = List.of(PagePlan.builder().pageNo(1).contentMd("内容").build());
        ComplianceReport report = checker.textCheck(pages, longTitle, "default");
        assertFalse(report.getRules().get(0).isPassed());
        assertEquals("WARNING", report.getRules().get(0).getLevel());
    }

    @Test
    void textCheck_contentTooLong_errors() {
        String longContent = "a".repeat(1500);
        List<PagePlan> pages = List.of(PagePlan.builder().pageNo(1).contentMd(longContent).build());
        ComplianceReport report = checker.textCheck(pages, "标题", "default");
        assertFalse(report.isPassed());
    }

    @Test
    void textCheck_placeholder_fails() {
        String content = "正文 {{IMAGE_PLACEHOLDER_1}}";
        List<PagePlan> pages = List.of(PagePlan.builder().pageNo(1).contentMd(content).build());
        ComplianceReport report = checker.textCheck(pages, "标题", "default");
        assertFalse(report.isPassed());
    }
}
