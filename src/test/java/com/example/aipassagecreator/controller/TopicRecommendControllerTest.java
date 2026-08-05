package com.example.aipassagecreator.controller;

import com.example.aipassagecreator.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TopicRecommendController 集成测试 — 登录后拉取推荐选题。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:topic_rec;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:test-vip-schema.sql",
        "spring.session.store-type=none",
        "app.test-admin-account.enabled=true",
        "app.test-admin-account.account=admin_test",
        "app.test-admin-account.password=AdminTest@2026",
        "app.test-admin-account.display-name=管理员测试账号",
        "topic-recommend.ai-enabled=false"
})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DirtiesContext
class TopicRecommendControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    private MockHttpSession loginAsAdmin() throws Exception {
        MvcResult login = mockMvc.perform(post("/user/login")
                        .contentType("application/json")
                        .content("""
                                { "userAccount": "admin_test", "userPassword": "AdminTest@2026" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session);
        return session;
    }

    @Test
    @DisplayName("登录用户可拉取推荐选题（非 refresh，不触发 AI）")
    void recommend_noRefresh_returnsTopics() throws Exception {
        MockHttpSession session = loginAsAdmin();

        mockMvc.perform(get("/topic/recommend").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.hasAi").value(false));
    }

    @Test
    @DisplayName("未登录访问返回业务未登录错误码")
    void recommend_unauthenticated_denied() throws Exception {
        mockMvc.perform(get("/topic/recommend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));
    }
}
