package com.example.aipassagecreator.aop;

import com.example.aipassagecreator.constant.ApiKeyConstant;
import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.service.ApiKeyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * API Key 拦截器单元测试 — 头解析 + 用户注入 + 无效 Key 显式报错
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyInterceptorTest {

    @Mock
    private ApiKeyService apiKeyService;

    @InjectMocks
    private ApiKeyInterceptor interceptor;

    @Test
    @DisplayName("无 API Key 头 — 放行且不注入用户，交给 session 认证")
    void noHeader_passesThroughWithoutAttribute() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        boolean passed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(passed);
        assertNull(request.getAttribute(ApiKeyConstant.REQUEST_USER_ATTR));
        verify(apiKeyService, never()).validateAndGetUser(any());
    }

    @Test
    @DisplayName("有效 Bearer — 放行并把归属用户注入 request attribute")
    void validBearer_setsUserAttribute() {
        User user = user(2L);
        when(apiKeyService.validateAndGetUser("apc_valid")).thenReturn(user);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiKeyConstant.HEADER_AUTHORIZATION, "Bearer apc_valid");

        boolean passed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(passed);
        assertEquals(user, request.getAttribute(ApiKeyConstant.REQUEST_USER_ATTR));
    }

    @Test
    @DisplayName("无效 Key — 抛 BusinessException，不静默回退")
    void invalidKey_throwsBusinessException() {
        when(apiKeyService.validateAndGetUser("apc_bad")).thenReturn(null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiKeyConstant.HEADER_AUTHORIZATION, "Bearer apc_bad");

        assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertNull(request.getAttribute(ApiKeyConstant.REQUEST_USER_ATTR));
    }

    @Test
    @DisplayName("X-Api-Key 头 — 同样支持")
    void xApiKeyHeader_supported() {
        User user = user(2L);
        when(apiKeyService.validateAndGetUser("apc_x")).thenReturn(user);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(ApiKeyConstant.HEADER_X_API_KEY, "apc_x");

        boolean passed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(passed);
        assertEquals(user, request.getAttribute(ApiKeyConstant.REQUEST_USER_ATTR));
    }

    private User user(Long id) {
        User u = new User();
        u.setId(id);
        u.setUserRole("user");
        return u;
    }
}
