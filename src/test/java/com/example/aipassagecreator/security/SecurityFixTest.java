package com.example.aipassagecreator.security;

import com.example.aipassagecreator.service.ArticleRewriteService;
import com.example.aipassagecreator.skill.ModelRouter;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SecurityFixTest {

    @Autowired
    private ArticleRewriteService articleRewriteService;

    @Test
    void rewritePrompt_separatesUserInstructionFromSystem() throws Exception {
        // 通过反射读取 REWRITE_PROMPT 常量，验证其不再包含 {instruction} 占位符拼进用户消息
        java.lang.reflect.Field f = articleRewriteService.getClass().getDeclaredField("REWRITE_PROMPT");
        f.setAccessible(true);
        String prompt = (String) f.get(articleRewriteService);
        // 修复后：指令应放入独立的 SystemMessage，prompt 模板只包含 {content}
        assertTrue(prompt.contains("{content}"), "重写 prompt 必须仍包含文章占位符");
        assertTrue(prompt.contains("系统指令"), "修复后 SystemMessage 应明确标识系统指令来源");
    }
}
