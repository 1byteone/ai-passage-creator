package com.example.aipassagecreator.controller;

import com.example.aipassagecreator.aop.AuthCheck;
import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.exception.ThrowUtils;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.model.po.Workspace;
import com.example.aipassagecreator.model.po.WorkspaceMember;
import com.example.aipassagecreator.service.UserService;
import com.example.aipassagecreator.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 团队协作空间 API
 */
@Slf4j
@RestController
@RequestMapping("/workspace")
@Tag(name = "WorkspaceController", description = "团队协作空间")
public class WorkspaceController {

    @Resource
    private WorkspaceService workspaceService;

    @Resource
    private UserService userService;

    /**
     * 创建空间
     */
    @PostMapping("/create")
    @Operation(summary = "创建协作空间")
    @AuthCheck(mustRole = "user")
    public BaseResponse<Workspace> create(@RequestBody Map<String, String> body,
                                          HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.get("name") == null,
                ErrorCode.PARAMS_ERROR, "空间名称不能为空");
        User loginUser = userService.getLoginUser(request);
        Workspace ws = workspaceService.create(
                body.get("name"), body.get("description"), loginUser.getId());
        return ResultUtils.success(ws);
    }

    /**
     * 获取空间详情
     */
    @GetMapping("/{workspaceId}")
    @Operation(summary = "获取空间详情")
    @AuthCheck(mustRole = "user")
    public BaseResponse<Workspace> getById(@PathVariable Long workspaceId,
                                           HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(workspaceService.getById(workspaceId, loginUser.getId()));
    }

    /**
     * 我加入的空间列表
     */
    @GetMapping("/list/mine")
    @Operation(summary = "获取我加入的空间")
    @AuthCheck(mustRole = "user")
    public BaseResponse<List<Workspace>> listMine(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(workspaceService.listByUser(loginUser.getId()));
    }

    /**
     * 更新空间
     */
    @PutMapping("/{workspaceId}")
    @Operation(summary = "更新空间信息")
    @AuthCheck(mustRole = "user")
    public BaseResponse<Workspace> update(@PathVariable Long workspaceId,
                                          @RequestBody Map<String, String> body,
                                          HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Workspace ws = workspaceService.update(
                workspaceId, body.get("name"), body.get("description"), loginUser.getId());
        return ResultUtils.success(ws);
    }

    /**
     * 归档空间
     */
    @PostMapping("/{workspaceId}/archive")
    @Operation(summary = "归档空间")
    @AuthCheck(mustRole = "user")
    public BaseResponse<Boolean> archive(@PathVariable Long workspaceId,
                                         HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        workspaceService.archive(workspaceId, loginUser.getId());
        return ResultUtils.success(true);
    }

    /**
     * 添加成员
     */
    @PostMapping("/{workspaceId}/members")
    @Operation(summary = "添加空间成员")
    @AuthCheck(mustRole = "user")
    public BaseResponse<Boolean> addMember(@PathVariable Long workspaceId,
                                           @RequestBody Map<String, Object> body,
                                           HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.get("userId") == null,
                ErrorCode.PARAMS_ERROR, "成员 userId 不能为空");
        User loginUser = userService.getLoginUser(request);
        Long targetUserId = ((Number) body.get("userId")).longValue();
        String role = (String) body.getOrDefault("role", "member");
        workspaceService.addMember(workspaceId, targetUserId, role, loginUser.getId());
        return ResultUtils.success(true);
    }

    /**
     * 移除成员
     */
    @DeleteMapping("/{workspaceId}/members/{userId}")
    @Operation(summary = "移除空间成员")
    @AuthCheck(mustRole = "user")
    public BaseResponse<Boolean> removeMember(@PathVariable Long workspaceId,
                                              @PathVariable Long userId,
                                              HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        workspaceService.removeMember(workspaceId, userId, loginUser.getId());
        return ResultUtils.success(true);
    }

    /**
     * 成员列表
     */
    @GetMapping("/{workspaceId}/members")
    @Operation(summary = "空间成员列表")
    @AuthCheck(mustRole = "user")
    public BaseResponse<List<WorkspaceMember>> listMembers(@PathVariable Long workspaceId,
                                                           HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(workspaceService.listMembers(workspaceId, loginUser.getId()));
    }
}
