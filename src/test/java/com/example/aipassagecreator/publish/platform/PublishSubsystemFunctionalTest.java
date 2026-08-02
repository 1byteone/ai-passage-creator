package com.example.aipassagecreator.publish.platform;

import com.example.aipassagecreator.methodology.MethodologyDefinition;
import com.example.aipassagecreator.methodology.MethodologyRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 第 3 轮功能测试：多平台分发子系统
 * <p>验证：三个平台适配器注册/转换/校验/合规报告</p>
 */
@SpringBootTest
class PublishSubsystemFunctionalTest {

    @Autowired
    private PlatformAdapterRegistry adapterRegistry;

    @Autowired
    private MethodologyRegistry methodologyRegistry;

    // ============= Round 3.1: 适配器注册 =============

    @Test
    void adapterCount_isThree() {
        assertEquals(3, adapterRegistry.platforms().size());
    }

    @Test
    void eachAdapterRegistered_individually() {
        assertTrue(adapterRegistry.exists("wechat"));
        assertTrue(adapterRegistry.exists("xiaohongshu"));
        assertTrue(adapterRegistry.exists("douyin"));
    }

    @Test
    void unknownPlatform_throwsFast() {
        assertThrows(IllegalArgumentException.class,
                () -> adapterRegistry.get("unknown"));
    }

    // ============= Round 3.2: 微信适配 =============

    @Test
    void wechat_convertsToHtml() {
        PlatformAdapter adapter = adapterRegistry.get("wechat");
        MethodologyDefinition def = methodologyRegistry.get("wechat");
        PlatformContent content = adapter.convert("公众号文章",
                "## 第一节\n\n正文内容第一段。\n\n## 第二节\n\n正文内容第二段。", def);

        assertEquals("公众号文章", content.title());
        assertTrue(content.body().contains("<h2>") || content.body().contains("<p>"),
                "应输出 HTML 标签");
        assertTrue(content.body().contains("第一段"), "应包含原文内容");
    }

    @Test
    void wechat_validate_withinRange_passes() {
        PlatformAdapter adapter = adapterRegistry.get("wechat");
        MethodologyDefinition def = methodologyRegistry.get("wechat");
        String body = "正文内容。".repeat(200); // ~800 chars, 在 1500-3000 之间偏低
        List<ComplianceIssue> issues = adapter.validate(
                new PlatformContent("标题", body, List.of(), java.util.Map.of()), def);
        assertTrue(issues.stream().anyMatch(i -> "LengthRule".equals(i.rule())),
                "短于 1500 字的公众号应触发 LengthRule");
    }

    // ============= Round 3.3: 小红书适配 =============

    @Test
    void xiaohongshu_stripsMarkdown() {
        PlatformAdapter adapter = adapterRegistry.get("xiaohongshu");
        MethodologyDefinition def = methodologyRegistry.get("xiaohongshu");
        PlatformContent content = adapter.convert("测试",
                "## 第一节\n\n这是**加粗**测试*斜体*正文。", def);

        assertFalse(content.body().contains("**"), "不应有加粗标记");
        assertFalse(content.body().contains("##"), "不应有标题标记");
        assertTrue(content.body().contains("加粗"), "应保留文字内容");
    }

    @Test
    void xiaohongshu_hasTopics() {
        PlatformAdapter adapter = adapterRegistry.get("xiaohongshu");
        MethodologyDefinition def = methodologyRegistry.get("xiaohongshu");
        PlatformContent content = adapter.convert("测试",
                "## 护肤心得\n\n## 美妆技巧\n\n## 穿搭推荐\n\n正文内容。", def);

        assertNotNull(content.topics());
        assertFalse(content.topics().isEmpty(), "应有话题标签");
    }

    // ============= Round 3.4: 抖音适配 =============

    @Test
    void douyin_containsHook() {
        PlatformAdapter adapter = adapterRegistry.get("douyin");
        MethodologyDefinition def = methodologyRegistry.get("douyin");
        PlatformContent content = adapter.convert("短视频",
                "这是钩子文案。\n\n## 展开\n\n正文内容展开部分。", def);

        assertTrue(content.body().contains("#创作灵感"), "应含 CTA 话题");
        assertTrue(content.body().length() <= 600, "抖音文案应控制在 600 字内");
    }

    @Test
    void douyin_shortContent_warningOnLength() {
        PlatformAdapter adapter = adapterRegistry.get("douyin");
        MethodologyDefinition def = methodologyRegistry.get("douyin");
        List<ComplianceIssue> issues = adapter.validate(
                new PlatformContent("标题", "很短", List.of(), java.util.Map.of()), def);
        assertTrue(issues.stream().anyMatch(i -> "LengthRule".equals(i.rule())),
                "超短内容应触发 LengthRule");
    }

    // ============= Round 3.5: ContentPublisher 全链路 =============

    @Autowired
    private ContentPublisher contentPublisher;

    @Test
    void contentPublisher_producesValidJson() {
        // 构造最小 article（不真实入 DB，直接测编排逻辑）
        com.example.aipassagecreator.model.po.Article article =
                new com.example.aipassagecreator.model.po.Article();
        article.setMainTitle("发布测试");
        article.setContent("## 第一节\n\n这是测试内容。\n\n## 第二节\n\n更多内容。");
        article.setUserId(1L);

        String json = contentPublisher.convertAndValidate(article, "wechat", "wechat");
        assertNotNull(json);
        assertTrue(json.contains("\"title\""), "JSON 应含 title");
        assertTrue(json.contains("\"body\""), "JSON 应含 body");
        assertTrue(json.contains("\"issues\""), "JSON 应含 issues");
        assertTrue(json.contains("\"metadata\""), "JSON 应含 metadata");
    }
}
