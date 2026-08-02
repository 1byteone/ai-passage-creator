package com.example.aipassagecreator.security;

import com.example.aipassagecreator.service.ApiKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API Key 认证端到端验证
 *
 * <p>核心主张：改 getLoginUser/getLoginUserVO 读 request attribute 后，既有受保护端点
 * 无需任何 controller 改动即可被 API Key 调用。用最轻量的 {@code /user/get/login}
 * 验证拦截器 → 属性注入 → getLoginUserVO 全链路。
 * 注意：MockMvc 不应用 server.servlet.context-path，URL 不带 /api 前缀。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiKeyAuthFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyService apiKeyService;

    @Test
    void apiKey_callsExistingEndpoint_authenticated() throws Exception {
        // admin 为 user(2) 创建 Key
        String plain = apiKeyService.createKey(2L, true, 2L, "e2e", null).getApiKey();

        mockMvc.perform(get("/user/get/login")
                        .header("Authorization", "Bearer " + plain))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userName").value("普通用户"));
    }

    @Test
    void invalidApiKey_returnsInvalidKeyError() throws Exception {
        mockMvc.perform(get("/user/get/login")
                        .header("Authorization", "Bearer apc_invalid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40102));
    }

    @Test
    void withoutApiKey_returnsNotLoginError() throws Exception {
        mockMvc.perform(get("/user/get/login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));
    }
}
