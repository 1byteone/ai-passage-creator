package com.example.aipassagecreator.card;

import com.example.aipassagecreator.aop.AuthCheck;
import com.example.aipassagecreator.annotation.RateLimit;
import com.example.aipassagecreator.card.model.CardGenerateRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * CardController 安全闸门测试。
 * <p>验证 preview/generate 端点均挂了 {@link AuthCheck}（登录 + 角色）
 * 与 {@link RateLimit}（防刷）注解，防止新端点绕过既有安全策略。</p>
 */
@SpringBootTest
class CardControllerSecurityTest {

    @Autowired
    private CardController controller;

    @Test
    void previewEndpoint_hasAuthAndRateLimit() throws Exception {
        Method m = CardController.class.getDeclaredMethod("preview",
                CardGenerateRequest.class, HttpServletRequest.class);
        assertNotNull(m.getAnnotation(AuthCheck.class), "preview 必须带 @AuthCheck");
        assertNotNull(m.getAnnotation(RateLimit.class), "preview 必须带 @RateLimit");
    }

    @Test
    void generateEndpoint_hasAuthAndRateLimit() throws Exception {
        Method m = CardController.class.getDeclaredMethod("generate",
                CardGenerateRequest.class, HttpServletRequest.class);
        assertNotNull(m.getAnnotation(AuthCheck.class), "generate 必须带 @AuthCheck");
        assertNotNull(m.getAnnotation(RateLimit.class), "generate 必须带 @RateLimit");
    }
}
