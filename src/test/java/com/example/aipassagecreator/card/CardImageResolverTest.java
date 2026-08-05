package com.example.aipassagecreator.card;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CardImageResolver 单元测试 — 图片下载转 base64（方案 A）。
 */
class CardImageResolverTest {

    private final CardImageResolver resolver = new CardImageResolver();

    @Test
    @DisplayName("null/空白/已有 data URL 直接处理")
    void toDataUrl_edgeInputs() {
        assertNull(resolver.toDataUrl(null));
        assertNull(resolver.toDataUrl(""));
        assertNull(resolver.toDataUrl("   "));
        assertTrue(resolver.toDataUrl("data:image/png;base64,abc").startsWith("data:image/png"),
                "已是 data URL 应原样返回");
    }

    @Test
    @DisplayName("无效 URL 返回 null（不抛异常）")
    void toDataUrl_invalidUrl_returnsNull() {
        // 非法 URL 应被捕获并返回 null，而非抛异常
        assertNull(resolver.toDataUrl("http://not-a-real-host-xyz.example/img.png"));
    }

    @Test
    @DisplayName("不可达域名返回 null（下载失败降级）")
    void toDataUrl_unreachable_returnsNull() {
        String url = "http://localhost:1/nonexistent.png"; // 必然连接失败
        assertNull(resolver.toDataUrl(url), "下载失败应降级为 null");
    }

    @Test
    @DisplayName("classpath 静态素材转 base64 data URL（插图熔断兜底）")
    void toDataUrl_classpath_returnsDataUrl() {
        // 插画静态素材内嵌于 src/main/resources/illustration/，测试 classpath 可见
        String dataUrl = resolver.toDataUrl("classpath:illustration/healing/healing-1.png");
        assertNotNull(dataUrl, "存在的 classpath 素材应返回 data URL");
        assertTrue(dataUrl.startsWith("data:image/png;base64,"),
                "classpath PNG 应转为 data:image/png;base64");
    }

    @Test
    @DisplayName("不存在的 classpath 资源返回 null（不抛异常）")
    void toDataUrl_classpathMissing_returnsNull() {
        assertNull(resolver.toDataUrl("classpath:illustration/nonexistent/xxx.png"));
    }
}
