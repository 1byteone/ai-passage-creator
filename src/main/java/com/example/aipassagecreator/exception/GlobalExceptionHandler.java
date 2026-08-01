package com.example.aipassagecreator.exception;

import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
@Hidden
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<BaseResponse<?>> businessExceptionHandler(BusinessException e,
                                                                    HttpServletRequest request) {
        log.error("BusinessException", e);
        // SSE 端点必须返回真实 HTTP 状态码：
        // 若返回 200 + JSON，浏览器 EventSource 会因 Content-Type 不匹配而报错并「无限重连」；
        // 返回非 2xx 才会让连接直接置为 CLOSED，前端 onerror 也能区分鉴权失败与网络故障。
        if (isSseRequest(request)) {
            return ResponseEntity.status(toHttpStatus(e.getCode())).build();
        }
        // 普通 REST 请求保持原有契约：HTTP 200 + body 内业务错误码
        return ResponseEntity.ok(ResultUtils.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<BaseResponse<?>> runtimeExceptionHandler(RuntimeException e,
                                                                   HttpServletRequest request) {
        log.error("RuntimeException", e);
        if (isSseRequest(request)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return ResponseEntity.ok(ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误"));
    }

    /**
     * 判断是否为 SSE 订阅请求（EventSource 固定携带 Accept: text/event-stream）
     */
    private boolean isSseRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    /**
     * 业务错误码映射为 HTTP 状态码（仅用于 SSE 场景）
     */
    private HttpStatus toHttpStatus(int businessCode) {
        return switch (businessCode) {
            case 40000 -> HttpStatus.BAD_REQUEST;
            case 40100 -> HttpStatus.UNAUTHORIZED;
            case 40101, 40300 -> HttpStatus.FORBIDDEN;
            case 40400 -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
