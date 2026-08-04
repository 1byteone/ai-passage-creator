package com.example.aipassagecreator.service;

import com.example.aipassagecreator.model.po.Workspace;
import com.example.aipassagecreator.model.po.WorkspaceMember;

import java.util.List;

/**
 * 团队协作空间服务
 */
public interface WorkspaceService {

    /** 角色常量 */
    String ROLE_OWNER = "owner";
    String ROLE_ADMIN = "admin";
    String ROLE_MEMBER = "member";
    String ROLE_VIEWER = "viewer";

    /**
     * 创建空间（创建者自动成为 owner）
     */
    Workspace create(String name, String description, Long ownerId);

    /**
     * 获取空间详情（校验成员资格）
     */
    Workspace getById(Long workspaceId, Long userId);

    /**
     * 获取用户加入的所有空间
     */
    List<Workspace> listByUser(Long userId);

    /**
     * 更新空间信息（仅 owner/admin）
     */
    Workspace update(Long workspaceId, String name, String description, Long userId);

    /**
     * 归档空间（仅 owner）
     */
    void archive(Long workspaceId, Long userId);

    /**
     * 恢复已归档空间（仅 owner）
     */
    void unarchive(Long workspaceId, Long userId);

    /**
     * 添加成员（仅 owner/admin）
     */
    void addMember(Long workspaceId, Long targetUserId, String role, Long operatorId);

    /**
     * 移除成员（仅 owner/admin，不能移除 owner）
     */
    void removeMember(Long workspaceId, Long targetUserId, Long operatorId);

    /**
     * 获取空间成员列表
     */
    List<WorkspaceMember> listMembers(Long workspaceId, Long userId);

    /**
     * 校验用户在某空间的角色权限
     *
     * @return 用户角色，无权限返回 null
     */
    String getRole(Long workspaceId, Long userId);

    /**
     * 检查用户是否至少具备指定角色
     */
    boolean hasRole(Long workspaceId, Long userId, String requiredRole);
}
