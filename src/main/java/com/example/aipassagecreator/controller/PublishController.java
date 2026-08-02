package com.example.aipassagecreator.controller;

import com.example.aipassagecreator.aop.AuthCheck;
import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.exception.ThrowUtils;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.service.PublishService;
import com.example.aipassagecreator.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 文章发布排期 API
 */
@Slf4j
@RestController
@RequestMapping("/publish")
@Tag(name = "PublishController", description = "发布排期")
public class PublishController {

    @Resource
    private PublishService publishService;

    @Resource
    private UserService userService;

    /**
     * 创建发布排期
     */
    @PostMapping("/schedule")
    @Operation(summary = "创建发布排期")
    @AuthCheck(mustRole = "user")
    public BaseResponse<?> schedule(@RequestBody Map<String, Object> body,
                                    HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.get("taskId") == null || body.get("publishAt") == null,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        String taskId = (String) body.get("taskId");
        String platform = body.get("platform") instanceof String s ? s : "wechat";
        String methodologyName = body.get("methodologyName") instanceof String s ? s : null;
        LocalDateTime publishAt;
        try {
            publishAt = LocalDateTime.parse((String) body.get("publishAt"));
        } catch (Exception e) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "发布时间格式错误，请使用 ISO 格式如 2026-12-31T10:00:00");
        }
        return ResultUtils.success(publishService.schedule(taskId, publishAt, platform, methodologyName, loginUser.getId()));
    }

    /**
     * 取消排期
     */
    @PostMapping("/cancel/{scheduleId}")
    @Operation(summary = "取消发布排期")
    @AuthCheck(mustRole = "user")
    public BaseResponse<Boolean> cancel(@PathVariable Long scheduleId,
                                        HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        publishService.cancel(scheduleId, loginUser.getId());
        return ResultUtils.success(true);
    }

    /**
     * 文章排期列表
     */
    @GetMapping("/article/{taskId}")
    @Operation(summary = "文章排期列表")
    @AuthCheck(mustRole = "user")
    public BaseResponse<?> listByArticle(@PathVariable String taskId,
                                         HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(publishService.listByArticle(taskId, loginUser.getId()));
    }
}
