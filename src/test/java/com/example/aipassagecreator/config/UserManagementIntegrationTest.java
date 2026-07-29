package com.example.aipassagecreator.config;

import com.example.aipassagecreator.constant.UserConstant;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:user_management;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:test-vip-schema.sql",
        "spring.session.store-type=none",
        "app.test-admin-account.enabled=true",
        "app.test-admin-account.account=admin_test",
        "app.test-admin-account.password=AdminTest@2026",
        "app.test-admin-account.display-name=管理员测试账号"
})
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
@DirtiesContext
class UserManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Test
    void shouldListUpdateAndDeleteUsersWithoutAllowingSelfDeletion() throws Exception {
        long managedUserId = userService.userRegister(
                "managed_user",
                "Managed@2026",
                "Managed@2026");

        MvcResult loginResult = mockMvc.perform(post("/user/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "userAccount": "admin_test",
                                  "userPassword": "AdminTest@2026"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userRole").value(UserConstant.ADMIN_ROLE))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertNotNull(session);

        mockMvc.perform(post("/article/list")
                        .session(session)
                        .contentType("application/json")
                        .content("""
                                {
                                  "current": 1,
                                  "pageSize": 10
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalRow").value(0))
                .andExpect(jsonPath("$.data.records").isEmpty());

        mockMvc.perform(post("/user/list/page/vo")
                        .session(session)
                        .contentType("application/json")
                        .content("""
                                {
                                  "current": 1,
                                  "pageSize": 10,
                                  "userAccount": "managed"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalRow").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(String.valueOf(managedUserId)))
                .andExpect(jsonPath("$.data.records[0].userAccount").value("managed_user"))
                .andExpect(jsonPath("$.data.records[0].userPassword").doesNotExist());

        mockMvc.perform(post("/user/update")
                        .session(session)
                        .contentType("application/json")
                        .content("""
                                {
                                  "id": %d,
                                  "userName": "受管测试用户",
                                  "userRole": "vip"
                                }
                                """.formatted(managedUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        User updatedUser = userService.getById(managedUserId);
        assertEquals("受管测试用户", updatedUser.getUserName());
        assertEquals(UserConstant.VIP_ROLE, updatedUser.getUserRole());

        User adminUser = userService.getOne(QueryWrapper.create()
                .eq(User::getUserAccount, "admin_test"));
        assertNotNull(adminUser);

        mockMvc.perform(post("/user/delete")
                        .session(session)
                        .contentType("application/json")
                        .content("""
                                {
                                  "id": %d
                                }
                                """.formatted(adminUser.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40300));

        mockMvc.perform(post("/user/delete")
                        .session(session)
                        .contentType("application/json")
                        .content("""
                                {
                                  "id": %d
                                }
                                """.formatted(managedUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));

        assertNull(userService.getById(managedUserId));
    }
}
