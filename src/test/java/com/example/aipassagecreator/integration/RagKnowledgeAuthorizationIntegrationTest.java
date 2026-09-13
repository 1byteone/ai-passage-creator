package com.example.aipassagecreator.integration;

import com.example.aipassagecreator.constant.UserConstant;
import com.example.aipassagecreator.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RAG 真实 Spring AOP + Session 鉴权和 Bean Validation 验收。 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class RagKnowledgeAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Test
    void knowledgeSearch_requiresLogin() throws Exception {
        mockMvc.perform(post("/rag/knowledge/search")
                        .contentType("application/json")
                        .content("{\"query\":\"项目规范\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void normalUser_cannotStartGitSyncOrApproveDocument() throws Exception {
        MockHttpSession session = loginSession();

        mockMvc.perform(post("/rag/knowledge/sync").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40101));
        mockMvc.perform(post("/rag/document/1/approve").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void knowledgeSearch_rejectsInvalidQueryAndTopK() throws Exception {
        MockHttpSession session = loginSession();

                mockMvc.perform(post("/rag/knowledge/search").session(session)
                        .contentType("application/json")
                        .content("{\"query\":\"\",\"topK\":1}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/rag/knowledge/search").session(session)
                        .contentType("application/json")
                        .content("{\"query\":\"规范\",\"topK\":21}"))
                .andExpect(status().isBadRequest());
    }

    private MockHttpSession loginSession() {
        String account = "rag_auth_" + System.nanoTime();
        long userId = userService.userRegister(account, "Rag@2026", "Rag@2026");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(UserConstant.USER_LOGIN_STATE, userId);
        return session;
    }
}
