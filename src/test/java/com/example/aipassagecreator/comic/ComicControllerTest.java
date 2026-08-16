package com.example.aipassagecreator.comic;

import com.example.aipassagecreator.mapper.ComicBookMapper;
import com.example.aipassagecreator.mapper.ComicEpisodeMapper;
import com.example.aipassagecreator.model.po.ComicBookPo;
import com.example.aipassagecreator.model.po.ComicEpisodePo;
import com.example.aipassagecreator.model.po.User;
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
 * ComicController 集成测试 — /comic 浏览端点（档案/月册/章节）。
 *
 * <p>复用 Task 6 ComicFileUploadTest 的鉴权约定：真实注册→登录（写会话）→带 session 请求，
 * 未登录场景断言 40100（登录检查在 controller 内，非过滤器拦截）。</p>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:comic_controller;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:sql/h2-schema.sql",
        "spring.session.store-type=none"
})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DirtiesContext
class ComicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private ComicBookMapper bookMapper;

    @Autowired
    private ComicEpisodeMapper episodeMapper;

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
    @DisplayName("未登录访问档案列表 → NOT_LOGIN_ERROR(40100)")
    void listBooks_requiresLogin() throws Exception {
        mockMvc.perform(get("/comic/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100)); // NOT_LOGIN_ERROR
    }

    @Test
    @DisplayName("未登录访问月册列表 → NOT_LOGIN_ERROR(40100)")
    void listMonths_requiresLogin() throws Exception {
        mockMvc.perform(get("/comic/books/1/months"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100)); // NOT_LOGIN_ERROR
    }

    @Test
    @DisplayName("未登录访问章节列表 → NOT_LOGIN_ERROR(40100)")
    void listEpisodes_requiresLogin() throws Exception {
        mockMvc.perform(get("/comic/books/1/months/2026-08/episodes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100)); // NOT_LOGIN_ERROR
    }

    @Test
    @DisplayName("未登录访问章节详情 → NOT_LOGIN_ERROR(40100)")
    void getEpisode_requiresLogin() throws Exception {
        mockMvc.perform(get("/comic/episodes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100)); // NOT_LOGIN_ERROR
    }

    @Test
    @DisplayName("登录用户访问他人档案章节 → NO_AUTH_ERROR(40101)")
    void getEpisode_otherUsersBook_denied() throws Exception {
        long userId = registerUser();
        User user = userService.getById(userId);
        MockHttpSession session = loginAs(user.getUserAccount(), "Comic@2026");

        ComicBookPo book = new ComicBookPo();
        book.setUserId(userId + 999); // 他人档案
        book.setBookName("他人手帐");
        book.setDefaultStyle("powder");
        book.setIsDelete(0);
        bookMapper.insert(book);

        ComicEpisodePo ep = new ComicEpisodePo();
        ep.setBookId(book.getId());
        ep.setEpisodeNo(1);
        ep.setTitle("他人章节");
        ep.setInputType("daily");
        ep.setStyle("powder");
        ep.setIsDelete(0);
        episodeMapper.insert(ep);

        mockMvc.perform(get("/comic/episodes/" + ep.getId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40101)); // NO_AUTH_ERROR
    }
}
