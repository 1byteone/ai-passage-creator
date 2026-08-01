package com.example.aipassagecreator.controller;

import com.example.aipassagecreator.aop.AuthCheck;
import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.exception.ThrowUtils;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.service.ApprovalService;
import com.example.aipassagecreator.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 内容审批流 API
 */
@Slf4j
@RestController
@RequestMapping("/approval")
@Tag(name = "ApprovalController", description = "内容审批")
public class ApprovalController {

    @Resource
    private ApprovalService approvalService;

    @Resource
    private UserService userService;

    /**
     * 提交文章进入审批
     */
    @PostMapping("/submit")
    @Operation(summary = "提交文章进入审批")
    @AuthCheck(mustRole = "user")
    public BaseResponse<?> submit(@RequestBody Map<String, String> body,
                                  HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.get("taskId") == null,
                ErrorCode.PARAMS_ERROR, "taskId 不能为空");
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(approvalService.submit(body.get("taskId"), loginUser.getId()));
    }

    /**
     * 审批通过（仅 admin）
     */
    @PostMapping("/approve")
    @Operation(summary = "审批通过")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<?> approve(@RequestBody Map<String, String> body,
                                   HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.get("taskId") == null,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(approvalService.approve(
                body.get("taskId"), loginUser.getId(), body.get("comment")));
    }

    /**
     * 审批驳回（仅 admin）
     */
    @PostMapping("/reject")
    @Operation(summary = "审批驳回")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<?> reject(@RequestBody Map<String, String> body,
                                  HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.get("taskId") == null,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(approvalService.reject(
                body.get("taskId"), loginUser.getId(), body.get("comment")));
    }

    /**
     * 审批历史（作者或 admin）
     */
    @GetMapping("/history/{taskId}")
    @Operation(summary = "审批历史")
    @AuthCheck(mustRole = "user")
    public BaseResponse<?> history(@PathVariable String taskId,
                                   HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(approvalService.getHistory(taskId, loginUser.getId()));
    }
}
