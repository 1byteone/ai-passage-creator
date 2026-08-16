package com.example.aipassagecreator.comic;

import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ComicFileController 集成测试 — /file/upload 校验（登录态 + 图片类型白名单）。
 *
 * <p>复用 ArticleCreateReproductionTest 的真实 注册→登录→会话 模式：
 * getLoginUser 走真实会话校验，未登录场景单独断言 40100，确保登录检查不被绕过。</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:comic_upload;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:test-vip-schema.sql",
        "spring.session.store-type=none"
})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DirtiesContext
class ComicFileUploadTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

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
        String account = "comic_" + System.nanoTime() % 1000000;
        long userId = userService.userRegister(account, "Comic@2026", "Comic@2026");
        assertNotNull(userId);
        return userId;
    }

    @Test
    @DisplayName("登录用户上传非图片 → PARAMS_ERROR(40000)")
    void upload_nonImage_returnsError() throws Exception {
        long userId = registerUser();
        User user = userService.getById(userId);
        MockHttpSession session = loginAs(user.getUserAccount(), "Comic@2026");

        MockMultipartFile file = new MockMultipartFile(
                "file", "a.txt", "text/plain", "hello".getBytes());
        mockMvc.perform(MockMvcRequestBuilders.multipart("/file/upload").file(file).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000)); // PARAMS_ERROR
    }

    @Test
    @DisplayName("未登录上传 → NOT_LOGIN_ERROR(40100)，登录校验不可绕过")
    void upload_unauthenticated_denied() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.png", "image/png", "fake-png".getBytes());
        mockMvc.perform(MockMvcRequestBuilders.multipart("/file/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100)); // NOT_LOGIN_ERROR
    }
}
