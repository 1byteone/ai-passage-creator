package com.example.aipassagecreator.controller;

import com.example.aipassagecreator.annotation.RateLimit;
import com.example.aipassagecreator.aop.AuthCheck;
import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.constant.UserConstant;
import com.example.aipassagecreator.model.dto.apikey.ApiKeyCreateRequest;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.model.vo.ApiKeyCreateVO;
import com.example.aipassagecreator.model.vo.ApiKeyVO;
import com.example.aipassagecreator.service.ApiKeyService;
import com.example.aipassagecreator.service.UserService;
import com.mybatisflex.core.paginate.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台级 API Key 管理 — 机器/外部客户端调用认证凭证
 */
@Slf4j
@RestController
@RequestMapping("/api-key")
@Tag(name = "ApiKeyController", description = "API Key 管理")
public class ApiKeyController {

    private static final long MAX_PAGE_SIZE = 50;

    @Resource
    private ApiKeyService apiKeyService;

    @Resource
    private UserService userService;

    /**
     * 创建 API Key（不指定 userId 时为本人创建；admin 可指定任意用户）
     */
    @PostMapping
    @Operation(summary = "创建 API Key（明文仅此一次返回）")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    @RateLimit(limit = 5, window = 60, key = "api_key_create")
    public BaseResponse<ApiKeyCreateVO> create(@Valid @RequestBody ApiKeyCreateRequest request,
                                               HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        ApiKeyCreateVO vo = apiKeyService.createKey(
                loginUser.getId(), isAdmin, request.getUserId(), request.getName(), request.getExpiresAt());
        return ResultUtils.success(vo);
    }

    /**
     * 查询 Key 列表（不指定 userId 时查本人；admin 可查任意用户）
     */
    @GetMapping
    @Operation(summary = "查询 API Key 列表（脱敏）")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Page<ApiKeyVO>> list(@RequestParam(required = false) Long userId,
                                             @RequestParam(defaultValue = "1") long pageNum,
                                             @RequestParam(defaultValue = "10") long pageSize,
                                             HttpServletRequest httpRequest) {
        if (pageNum < 1) {
            pageNum = 1;
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            pageSize = 10;
        }
        User loginUser = userService.getLoginUser(httpRequest);
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        Page<ApiKeyVO> page = apiKeyService.listKeys(loginUser.getId(), isAdmin, userId, pageNum, pageSize);
        return ResultUtils.success(page);
    }

    /**
     * 吊销 API Key（本人或 admin）
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "吊销 API Key")
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public BaseResponse<Boolean> revoke(@PathVariable Long id, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        apiKeyService.revokeKey(loginUser.getId(), isAdmin, id);
        return ResultUtils.success(true);
    }
}
