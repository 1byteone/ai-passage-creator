package com.example.aipassagecreator.exception;

import com.example.aipassagecreator.common.BaseResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 全局异常处理器测试
 * <p>
 * 重点验证 SSE 与普通 REST 两条路径的差异化行为：
 * SSE 需要真实 HTTP 状态码（否则 EventSource 会无限重连），
 * 普通 REST 必须保持 200 + body 内业务码的既有契约。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MockHttpServletRequest sseRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept", "text/event-stream");
        return request;
    }

    private MockHttpServletRequest restRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept", "application/json");
        return request;
    }

    @DisplayName("SSE 请求：业务错误码映射为真实 HTTP 状态码")
    @ParameterizedTest(name = "code={0} -> HTTP {1}")
    @CsvSource({
            "40000, 400",
            "40100, 401",
            "40101, 403",
            "40300, 403",
            "40400, 404",
            "50000, 500",
            "50001, 500",
    })
    void sseRequestMapsBusinessCodeToHttpStatus(int businessCode, int expectedStatus) {
        BusinessException e = new BusinessException(businessCode, "boom");

        ResponseEntity<BaseResponse<?>> response = handler.businessExceptionHandler(e, sseRequest());

        assertEquals(expectedStatus, response.getStatusCode().value());
        // SSE 场景不返回 JSON body，避免 EventSource 解析异常
        assertNull(response.getBody());
    }

    @Test
    @DisplayName("回归：未登录访问 SSE 端点返回 401 而非 200")
    void sseUnauthorizedReturns401() {
        BusinessException e = new BusinessException(ErrorCode.NOT_LOGIN_ERROR);

        ResponseEntity<BaseResponse<?>> response = handler.businessExceptionHandler(e, sseRequest());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("普通 REST 请求：维持 HTTP 200 + body 内业务错误码的既有契约")
    void restRequestKeepsHttp200Contract() {
        BusinessException e = new BusinessException(ErrorCode.NOT_LOGIN_ERROR);

        ResponseEntity<BaseResponse<?>> response = handler.businessExceptionHandler(e, restRequest());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        BaseResponse<?> body = response.getBody();
        assertNotNull(body);
        assertEquals(ErrorCode.NOT_LOGIN_ERROR.getCode(), body.getCode());
    }

    @Test
    @DisplayName("无 Accept 头时按普通 REST 处理")
    void missingAcceptHeaderTreatedAsRest() {
        BusinessException e = new BusinessException(ErrorCode.NO_AUTH_ERROR);

        ResponseEntity<BaseResponse<?>> response =
                handler.businessExceptionHandler(e, new MockHttpServletRequest());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("RuntimeException 同样区分 SSE 与 REST")
    void runtimeExceptionDistinguishesSse() {
        RuntimeException e = new RuntimeException("unexpected");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                handler.runtimeExceptionHandler(e, sseRequest()).getStatusCode());

        ResponseEntity<BaseResponse<?>> restResponse = handler.runtimeExceptionHandler(e, restRequest());
        assertEquals(HttpStatus.OK, restResponse.getStatusCode());
        assertNotNull(restResponse.getBody());
    }
}
