package com.example.aipassagecreator.service;

import com.example.aipassagecreator.enums.ImageMethodEnum;
import com.example.aipassagecreator.model.dto.image.ImageData;
import com.example.aipassagecreator.model.dto.image.ImageRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgnesImageService 单元测试
 * <p>
 * 覆盖 isAvailable / getMethod / getFallbackImage / searchImage 空参数守卫。
 * 实际 HTTP 调用在集成环境通过（需 API key）。
 */
class AgnesImageServiceTest {

    private final AgnesImageService service = new AgnesImageService();

    @BeforeEach
    void setUp() {
        // 默认未配置 key，模拟服务不可用
        ReflectionTestUtils.setField(service, "apiKey", "");
        ReflectionTestUtils.setField(service, "baseUrl", "https://api.agnes-ai.cn");
    }

    @Test
    @DisplayName("getMethod → AGNES")
    void getMethod_returnsAgnes() {
        assertEquals(ImageMethodEnum.AGNES, service.getMethod());
    }

    @Test
    @DisplayName("isAvailable=API key 为空 → false")
    void isAvailable_emptyKey_returnsFalse() {
        assertFalse(service.isAvailable());
    }

    @Test
    @DisplayName("isAvailable=API key 已配置 → true")
    void isAvailable_keyPresent_returnsTrue() {
        ReflectionTestUtils.setField(service, "apiKey", "sk-test-key");
        assertTrue(service.isAvailable());
    }

    @Test
    @DisplayName("getFallbackImage → Picsum URL 带 position 参数")
    void getFallbackImage_returnsPicsumUrl() {
        String url = service.getFallbackImage(3);
        assertNotNull(url);
        assertTrue(url.contains("picsum.photos"));
        assertTrue(url.contains("agnes-3"));
    }

    @Test
    @DisplayName("searchImage=空 prompt → 返回 null")
    void searchImage_blankPrompt_returnsNull() {
        ReflectionTestUtils.setField(service, "apiKey", "sk-test-key");
        assertNull(service.searchImage(""));
        assertNull(service.searchImage(null));
    }

    @Test
    @DisplayName("searchImage=无 API key → 返回 null")
    void searchImage_noKey_returnsNull() {
        assertNull(service.searchImage("a cat"));
    }

    @Test
    @DisplayName("getImageData=获取失败 → 返回 Picsum 降级 ImageData")
    void getImageData_fallback_returnsPicsumImageData() {
        ImageRequest request = ImageRequest.builder()
                .prompt("a cat")
                .position(5)
                .build();
        ImageData data = service.getImageData(request);
        assertNotNull(data);
        assertNotNull(data.getUrl());
        assertTrue(data.getUrl().contains("picsum.photos"));
        assertTrue(data.getUrl().contains("agnes-5"));
    }
}
