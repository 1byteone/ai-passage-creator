package com.example.aipassagecreator.publish.platform;

import com.example.aipassagecreator.methodology.MethodologyDefinition;
import com.example.aipassagecreator.methodology.MethodologyRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 平台适配器集成测试：验证三平台适配器注册、转换、校验。
 */
@SpringBootTest
class PlatformAdapterIntegrationTest {

    @Autowired
    private PlatformAdapterRegistry adapterRegistry;

    @Autowired
    private MethodologyRegistry methodologyRegistry;

    @Autowired
    private List<PlatformAdapter> adapters;

    @Test
    void allThreeAdaptersRegistered() {
        assertEquals(3, adapters.size(), "应注册 3 个平台适配器");
        assertTrue(adapterRegistry.exists("wechat"));
        assertTrue(adapterRegistry.exists("xiaohongshu"));
        assertTrue(adapterRegistry.exists("douyin"));
        assertFalse(adapterRegistry.exists("unknown"));
    }

    @Test
    void wechat_convertsMarkdownToHtml() {
        PlatformAdapter adapter = adapterRegistry.get("wechat");
        MethodologyDefinition def = methodologyRegistry.get("wechat");
        PlatformContent content = adapter.convert("测试标题",
                "## 第一章\n\n这是测试正文内容。\n\n## 第二章\n\n更多内容。", def);

        assertEquals("测试标题", content.title());
        assertTrue(content.body().contains("<h2>") || content.body().contains("<p>"),
                "公众号应输出 HTML 富文本");
    }

    @Test
    void xiaohongshu_convertsMarkdownToPlaintext() {
        PlatformAdapter adapter = adapterRegistry.get("xiaohongshu");
        MethodologyDefinition def = methodologyRegistry.get("xiaohongshu");
        PlatformContent content = adapter.convert("测试",
                "## 第一章\n\n这是测试正文。\n\n## 第二章\n\n更多内容。", def);

        assertFalse(content.body().contains("##"), "小红书不应包含 Markdown 标题标记");
        assertFalse(content.body().contains("**"), "小红书不应包含加粗标记");
        assertEquals(5, content.topics().size(), "小红书应生成 5 个话题标签");
    }

    @Test
    void douyin_convertsToShortScript() {
        PlatformAdapter adapter = adapterRegistry.get("douyin");
        MethodologyDefinition def = methodologyRegistry.get("douyin");
        PlatformContent content = adapter.convert("短视频标题",
                "这是一段钩子文案，用来抓住前3秒的注意力。\n\n" +
                        "## 核心观点\n\n这是正文展开部分，用来说明主要观点。", def);

        assertTrue(content.body().contains("#创作灵感") || content.body().contains("钩子"),
                "抖音应包含口播脚本元素");
        assertTrue(content.body().length() <= 600, "抖音不应超过 600 字");
    }

    @Test
    void validate_lengthRule_triggered() {
        PlatformAdapter adapter = adapterRegistry.get("xiaohongshu");
        MethodologyDefinition def = methodologyRegistry.get("xiaohongshu");

        // 空内容应触发 minChars WARNING
        PlatformContent empty = new PlatformContent("t", "", List.of(), java.util.Map.of());
        List<ComplianceIssue> issues = adapter.validate(empty, def);
        assertTrue(issues.stream().anyMatch(i -> "LengthRule".equals(i.rule())),
                "空内容应触发 LengthRule");
    }

    @Test
    void unknownPlatform_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> adapterRegistry.get("unknown"));
    }

    @Test
    void adapterRegistry_platforms_returnsAll() {
        List<String> platforms = adapterRegistry.platforms();
        assertTrue(platforms.contains("wechat"));
        assertTrue(platforms.contains("xiaohongshu"));
        assertTrue(platforms.contains("douyin"));
    }
}
