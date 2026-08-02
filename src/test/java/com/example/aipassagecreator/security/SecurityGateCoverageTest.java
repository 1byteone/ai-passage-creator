package com.example.aipassagecreator.security;

import com.example.aipassagecreator.aop.AuthCheck;
import com.example.aipassagecreator.annotation.RateLimit;
import com.example.aipassagecreator.card.CardController;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 第 4 轮功能测试：安全门禁全覆盖
 * <p>验证所有关键端点均有 @AuthCheck + @RateLimit（合适层级）</p>
 */
class SecurityGateCoverageTest {

    // ============= Card 端点 =============

    @Test
    void cardPreview_isProtected() throws Exception {
        Method m = CardController.class.getDeclaredMethod("preview",
                com.example.aipassagecreator.card.model.CardGenerateRequest.class,
                HttpServletRequest.class);
        assertNotNull(m.getAnnotation(AuthCheck.class), "preview 需 @AuthCheck");
        assertNotNull(m.getAnnotation(RateLimit.class), "preview 需 @RateLimit");
    }

    @Test
    void cardGenerate_isProtected() throws Exception {
        Method m = CardController.class.getDeclaredMethod("generate",
                com.example.aipassagecreator.card.model.CardGenerateRequest.class,
                HttpServletRequest.class);
        assertNotNull(m.getAnnotation(AuthCheck.class));
        assertNotNull(m.getAnnotation(RateLimit.class));
    }

    @Test
    void cardGetCards_isProtected() throws Exception {
        Method m = CardController.class.getDeclaredMethod("getCards",
                String.class, HttpServletRequest.class);
        assertNotNull(m.getAnnotation(AuthCheck.class), "getCards 需 @AuthCheck");
    }

    // ============= Article 端点 =============

    @Test
    void evaluateViral_isProtected() throws Exception {
        Method m = com.example.aipassagecreator.controller.ArticleController.class
                .getDeclaredMethod("evaluateViral",
                        com.example.aipassagecreator.model.dto.article.ArticleEvaluateViralRequest.class,
                        HttpServletRequest.class);
        assertNotNull(m.getAnnotation(AuthCheck.class));
        assertNotNull(m.getAnnotation(RateLimit.class));
    }

    @Test
    void refine_isProtected() throws Exception {
        Method m = com.example.aipassagecreator.controller.ArticleController.class
                .getDeclaredMethod("refine",
                        com.example.aipassagecreator.model.dto.article.ArticleEvaluateViralRequest.class,
                        HttpServletRequest.class);
        assertNotNull(m.getAnnotation(AuthCheck.class));
        assertNotNull(m.getAnnotation(RateLimit.class));
    }

    @Test
    void evaluateQuality_isProtected() throws Exception {
        Method m = com.example.aipassagecreator.controller.ArticleController.class
                .getDeclaredMethod("evaluateQuality",
                        com.example.aipassagecreator.common.DeleteRequest.class,
                        HttpServletRequest.class);
        assertNotNull(m.getAnnotation(AuthCheck.class));
        assertNotNull(m.getAnnotation(RateLimit.class), "evaluateQuality 需 @RateLimit（P0 修复）");
    }

    @Test
    void rewrite_isProtected() throws Exception {
        Method m = com.example.aipassagecreator.controller.ArticleController.class
                .getDeclaredMethod("rewriteArticle",
                        com.example.aipassagecreator.model.dto.article.ArticleAiModifyOutlineRequest.class,
                        HttpServletRequest.class);
        assertNotNull(m.getAnnotation(AuthCheck.class));
        assertNotNull(m.getAnnotation(RateLimit.class), "rewrite 需 @RateLimit（P0 修复）");
    }

    @Test
    void executionLogs_isProtected() throws Exception {
        Method m = com.example.aipassagecreator.controller.ArticleController.class
                .getDeclaredMethod("getExecutionLogs",
                        String.class, HttpServletRequest.class);
        assertNotNull(m.getAnnotation(AuthCheck.class), "execution-logs 需 @AuthCheck（Phase1 修复）");
    }

    // ============= Publish 端点 =============

    @Test
    void publishSchedule_isProtected() throws Exception {
        Method m = com.example.aipassagecreator.controller.PublishController.class
                .getDeclaredMethod("schedule",
                        java.util.Map.class, HttpServletRequest.class);
        assertNotNull(m.getAnnotation(AuthCheck.class));
    }

    @Test
    void publishCancel_isProtected() throws Exception {
        Method m = com.example.aipassagecreator.controller.PublishController.class
                .getDeclaredMethod("cancel", Long.class, HttpServletRequest.class);
        assertNotNull(m.getAnnotation(AuthCheck.class));
    }

    @Test
    void publishList_isProtected() throws Exception {
        Method m = com.example.aipassagecreator.controller.PublishController.class
                .getDeclaredMethod("listByArticle", String.class, HttpServletRequest.class);
        assertNotNull(m.getAnnotation(AuthCheck.class));
    }

    // ============= ErrorCode 完整性 =============

    @Test
    void allErrorCodes_present() {
        com.example.aipassagecreator.exception.ErrorCode[] codes =
                com.example.aipassagecreator.exception.ErrorCode.values();
        assertTrue(codes.length >= 8, "至少 8 个错误码");
        // 关键错误码存在性
        assertNotNull(com.example.aipassagecreator.exception.ErrorCode.PARAMS_ERROR);
        assertNotNull(com.example.aipassagecreator.exception.ErrorCode.NO_AUTH_ERROR);
        assertNotNull(com.example.aipassagecreator.exception.ErrorCode.NOT_FOUND_ERROR);
        assertNotNull(com.example.aipassagecreator.exception.ErrorCode.OPERATION_ERROR);
        assertNotNull(com.example.aipassagecreator.exception.ErrorCode.FORBIDDEN_ERROR);
    }
}
