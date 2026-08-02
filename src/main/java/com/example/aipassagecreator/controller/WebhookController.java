package com.example.aipassagecreator.controller;

import com.example.aipassagecreator.aop.AuthCheck;
import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.exception.ThrowUtils;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.service.UserService;
import com.example.aipassagecreator.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 出站 Webhook 配置 API
 */
@Slf4j
@RestController
@RequestMapping("/webhook")
@Tag(name = "WebhookController", description = "Webhook 通知")
public class WebhookController {

    @Resource
    private WebhookService webhookService;

    @Resource
    private UserService userService;

    /**
     * 手动触发一个测试 webhook（admin）
     */
    @PostMapping("/test")
    @Operation(summary = "发送测试 Webhook")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<Boolean> sendTest(@RequestBody Map<String, String> body,
                                          HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.get("url") == null,
                ErrorCode.PARAMS_ERROR, "url 不能为空");
        User loginUser = userService.getLoginUser(request);

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "webhook.test");
        payload.put("triggeredBy", loginUser.getUserAccount());
        payload.put("timestamp", System.currentTimeMillis());
        payload.put("message", "这是一条测试通知");

        webhookService.publish("webhook.test", payload, body.get("url"));
        return ResultUtils.success(true);
    }
}
