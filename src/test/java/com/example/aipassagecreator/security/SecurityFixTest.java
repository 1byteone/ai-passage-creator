package com.example.aipassagecreator.security;

import com.example.aipassagecreator.aop.AuthCheck;
import com.example.aipassagecreator.controller.ArticleController;
import com.example.aipassagecreator.service.ArticleRewriteService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prompt 注入安全修复验证
 * <p>
 * 背景：ArticleRewriteService 原先用 REWRITE_PROMPT.formatted(instruction, content)
 * 把用户输入的改写指令和文章内容直接拼进单一 prompt，存在 prompt injection 风险
 * （用户内容可注入"忽略以上指令"等引导文本覆盖系统角色）。
 * <p>
 * 修复：系统指令独立为 SystemMessage，用户输入放入 UserMessage，角色隔离。
 */
@SpringBootTest
class SecurityFixTest {

    @Autowired
    private ArticleRewriteService articleRewriteService;

    @Test
    void rewritePrompt_separatesUserInstructionFromSystem() throws Exception {
        Class<?> clazz = articleRewriteService.getClass();

        // 1. 系统指令独立常量必须存在，且明确标识来源，防止与用户内容混淆
        Field systemField = clazz.getDeclaredField("REWRITE_SYSTEM_PROMPT");
        systemField.setAccessible(true);
        String systemPrompt = (String) systemField.get(articleRewriteService);
        assertTrue(systemPrompt.contains("系统指令"),
                "系统指令必须明确标识，用户内容不能伪装成系统指令");

        // 2. 用户消息模板不应再用 %s 拼接占位（旧实现的安全隐患），改用命名占位符
        Field promptField = clazz.getDeclaredField("REWRITE_PROMPT");
        promptField.setAccessible(true);
        String userPrompt = (String) promptField.get(articleRewriteService);
        assertTrue(userPrompt.contains("{content}"), "用户消息模板必须保留文章内容占位符");
        assertFalse(userPrompt.contains("%s"),
                "用户消息模板不得使用 sprintf %s 拼接，须用命名占位符以便语义隔离");
    }

    /**
     * 修复 IDOR：execution-logs 端点必须携带 @AuthCheck 鉴权注解。
     * 修复前该方法无任何鉴权，任何未登录用户可读取任意任务执行日志（AgentLog 泄漏 prompt/输入/输出）。
     */
    @Test
    void executionLogsEndpoint_isProtectedByAuthCheck() throws Exception {
        Method method = ArticleController.class.getDeclaredMethod(
                "getExecutionLogs", String.class, HttpServletRequest.class);
        AuthCheck authCheck = method.getAnnotation(AuthCheck.class);
        assertNotNull(authCheck, "execution-logs 端点必须携带 @AuthCheck 注解（防止 IDOR）");
        assertEquals("user", authCheck.mustRole(), "必须要求登录角色 user 才可访问执行日志");
    }
}
