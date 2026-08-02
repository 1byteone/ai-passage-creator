package com.example.aipassagecreator.skill;

import com.example.aipassagecreator.service.ApiKeyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 链式编排 SSE 异步化集成测试
 * <p>
 * 核心主张：POST /skill/chain/execute 不再同步跑完整条链，而是立即返回 chainId +
 * RUNNING 状态（异步派发），进度经 GET /skill/chain/{chainId}/progress SSE 推送。
 * 用 @MockitoBean 替换 SkillExecutionService 避免触发真实 LLM 执行。
 * 注意：MockMvc 不应用 context-path，URL 不带 /api 前缀。</p>
 */
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SkillChainAsyncIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private SkillSseEmitterManager sseEmitterManager;

    /** 替换真实执行服务，避免 @Async 触发真实 skill 执行 */
    @MockitoBean
    private SkillExecutionService skillExecutionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("POST 链 → 立即返回 chainId + RUNNING，异步派发到 SkillExecutionService")
    void chainExecute_returnsChainIdImmediately() throws Exception {
        String plain = apiKeyService.createKey(2L, true, 2L, "e2e", null).getApiKey();

        mockMvc.perform(post("/skill/chain/execute")
                        .header("Authorization", "Bearer " + plain)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skillNames\":[\"topic-gen\",\"proofreading\"],\"inputs\":{\"topic\":\"AI\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.chainId").exists())
                .andExpect(jsonPath("$.data.skills[0]").value("topic-gen"))
                .andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.progressUrl").value(startsWith("/skill/chain/")));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<SkillExecutionChain> captor = ArgumentCaptor.forClass(SkillExecutionChain.class);
        verify(skillExecutionService).executeChainAsync(captor.capture(), any(), eq(2L), eq(2));
        SkillExecutionChain chain = captor.getValue();
        assertEquals(2, chain.getSkillNames().size());
    }

    @Test
    @DisplayName("GET 链进度 → 归属用户收到 chain.complete 事件（SSE 终态带输出）")
    void chainProgress_owner_receivesChainCompleteEvent() throws Exception {
        String plain = apiKeyService.createKey(2L, true, 2L, "e2e", null).getApiKey();
        String chainId = startChain(plain);

        MvcResult asyncResult = mockMvc.perform(get("/skill/chain/" + chainId + "/progress")
                        .header("Authorization", "Bearer " + plain))
                .andExpect(request().asyncStarted())
                .andReturn();

        // 模拟异步执行器推送终态事件并关闭流（与 SkillExecutionService.executeChainAsync 收尾一致）
        sseEmitterManager.publish(chainId,
                SkillEventFactory.chainComplete(chainId, Map.of("topic-gen", "选题A")));
        sseEmitterManager.complete(chainId);

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("chain.complete")));
    }

    @Test
    @DisplayName("GET 链进度 → 他人访问返回 NOT_FOUND")
    void chainProgress_otherUser_returnsNotFound() throws Exception {
        String user2Key = apiKeyService.createKey(2L, true, 2L, "e2e", null).getApiKey();
        String user1Key = apiKeyService.createKey(1L, true, 1L, "e2e", null).getApiKey();
        String chainId = startChain(user2Key);

        mockMvc.perform(get("/skill/chain/" + chainId + "/progress")
                        .header("Authorization", "Bearer " + user1Key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.message").value("链式执行不存在"));
    }

    @Test
    @DisplayName("GET 链进度 → 未登记的 chainId 返回 NOT_FOUND")
    void chainProgress_unregisteredChain_returnsNotFound() throws Exception {
        String plain = apiKeyService.createKey(2L, true, 2L, "e2e", null).getApiKey();

        mockMvc.perform(get("/skill/chain/chain-does-not-exist/progress")
                        .header("Authorization", "Bearer " + plain))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40400));
    }

    /** POST 发起链式执行，返回响应中的 chainId */
    @SuppressWarnings("unchecked")
    private String startChain(String plain) throws Exception {
        MvcResult result = mockMvc.perform(post("/skill/chain/execute")
                        .header("Authorization", "Bearer " + plain)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skillNames\":[\"topic-gen\",\"proofreading\"],\"inputs\":{\"topic\":\"AI\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Map<String, Object> body = objectMapper.readValue(
                result.getResponse().getContentAsString(), Map.class);
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        return (String) data.get("chainId");
    }
}
