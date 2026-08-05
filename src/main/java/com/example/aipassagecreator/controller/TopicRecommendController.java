package com.example.aipassagecreator.controller;

import com.example.aipassagecreator.aop.AuthCheck;
import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.constant.UserConstant;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.model.vo.TopicRecommendVO;
import com.example.aipassagecreator.service.TopicRecommendService;
import com.example.aipassagecreator.service.UserService;
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
 * 推荐选题 API — 混合来源：平台热门 + 用户历史 + AI 动态生成
 */
@Slf4j
@RestController
@RequestMapping("/topic")
@Tag(name = "TopicRecommendController", description = "推荐选题")
public class TopicRecommendController {

    @Resource
    private TopicRecommendService topicRecommendService;

    @Resource
    private UserService userService;

    @GetMapping("/recommend")
    @Operation(summary = "获取推荐选题（热门/历史/AI 混合）")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<TopicRecommendVO> recommend(
            @RequestParam(defaultValue = "false") boolean refresh,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(topicRecommendService.recommend(refresh, loginUser));
    }
}
