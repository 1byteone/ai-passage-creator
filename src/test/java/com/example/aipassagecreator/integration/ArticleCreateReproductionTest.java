package com.example.aipassagecreator.integration;

import com.example.aipassagecreator.agent.ArticleAgentOrchestrator;
import com.example.aipassagecreator.model.dto.article.ArticleState;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 复现「创建文章返回 50000 系统错误」——真实 Controller 路径。
 *
 * <p>复现方式：真实注册普通用户 → 真实登录（写会话）→ POST /article/create。
 * 覆盖：getLoginUser（会话）→ createArticleTaskWithQuotaCheck（扣配额 + 落库）→
 * executePhase1（@Async 提交）。若同步路径抛出 RuntimeException，将在此复现并暴露根因。</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:create_repro;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:test-vip-schema.sql",
        "spring.session.store-type=none"
})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DirtiesContext
class ArticleCreateReproductionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    // 隔离真实 AI：异步阶段1 用 Mock 编排器填充标题方案，避免调用真实 LLM
    @MockBean
    private ArticleAgentOrchestrator articleAgentOrchestrator;

    private MockHttpSession loginAs(String account, String password) throws Exception {
        MvcResult login = mockMvc.perform(post("/user/login")
                        .contentType("application/json")
                        .content("""
                                { "userAccount": "%s", "userPassword": "%s" }
                                """.formatted(account, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session);
        return session;
    }

    private long registerUser() {
        String account = "repro_" + System.nanoTime() % 1000000;
        long userId = userService.userRegister(account, "Repro@2026", "Repro@2026");
        assertNotNull(userId);
        return userId;
    }

    @Test
    @DisplayName("复现：普通用户 POST /article/create 应返回 taskId 而非 50000")
    void createArticle_normalUser_returnsTaskId() throws Exception {
        long userId = registerUser();
        User user = userService.getById(userId);
        MockHttpSession session = loginAs(user.getUserAccount(), "Repro@2026");

        mockMvc.perform(post("/article/create")
                        .session(session)
                        .contentType("application/json")
                        .content("""
                                { "topic": "2026年AI如何改变职场", "style": "tech" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @DisplayName("复现：已注册用户 quota 被正确扣减")
    void createArticle_decrementsQuota() throws Exception {
        long userId = registerUser();
        User user = userService.getById(userId);
        int quotaBefore = user.getQuota();
        MockHttpSession session = loginAs(user.getUserAccount(), "Repro@2026");

        mockMvc.perform(post("/article/create")
                        .session(session)
                        .contentType("application/json")
                        .content("""
                                { "topic": "远程办公的利与弊", "style": "tech" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists());

        User after = userService.getById(userId);
        assertEquals(quotaBefore - 1, after.getQuota(), "创建后配额应扣减 1");
    }

    @Test
    @DisplayName("复现：空 style + 不传配图方式 不应导致 50000（模拟前端默认提交）")
    void createArticle_defaultStyleAndNoMethods_returnsTaskId() throws Exception {
        long userId = registerUser();
        User user = userService.getById(userId);
        MockHttpSession session = loginAs(user.getUserAccount(), "Repro@2026");

        // 模拟前端默认：style 缺省、enabledImageMethods 不传 → 走默认配图方式
        mockMvc.perform(post("/article/create")
                        .session(session)
                        .contentType("application/json")
                        .content("""
                                { "topic": "如何培养深度思考" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").exists());
    }
}
