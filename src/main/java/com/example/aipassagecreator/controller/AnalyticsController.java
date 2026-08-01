package com.example.aipassagecreator.controller;

import com.example.aipassagecreator.aop.AuthCheck;
import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.model.vo.AnalyticsVO;
import com.example.aipassagecreator.service.AnalyticsService;
import com.example.aipassagecreator.service.UserService;
import com.example.aipassagecreator.constant.UserConstant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 增强版数据分析 API
 */
@Slf4j
@RestController
@RequestMapping("/analytics")
@Tag(name = "AnalyticsController", description = "数据分析")
public class AnalyticsController {

    @Resource
    private AnalyticsService analyticsService;

    @Resource
    private UserService userService;

    /**
     * 全站内容分析（admin 专属）
     */
    @GetMapping("/content")
    @Operation(summary = "全站内容分析")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<AnalyticsVO> getContentAnalytics(HttpServletRequest request) {
        return ResultUtils.success(analyticsService.getContentAnalytics());
    }

    /**
     * 我的创作分析
     */
    @GetMapping("/mine")
    @Operation(summary = "我的创作分析")
    @AuthCheck(mustRole = "user")
    public BaseResponse<AnalyticsVO> getMyAnalytics(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(analyticsService.getUserAnalytics(loginUser.getId()));
    }

    /**
     * 指定用户分析（admin）
     */
    @GetMapping("/user")
    @Operation(summary = "指定用户分析")
    @AuthCheck(mustRole = "admin")
    public BaseResponse<AnalyticsVO> getUserAnalytics(@RequestParam Long userId) {
        return ResultUtils.success(analyticsService.getUserAnalytics(userId));
    }
}
