package com.example.aipassagecreator.service.impl;

import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.mapper.WorkspaceMapper;
import com.example.aipassagecreator.mapper.WorkspaceMemberMapper;
import com.example.aipassagecreator.model.po.Workspace;
import com.example.aipassagecreator.model.po.WorkspaceMember;
import com.example.aipassagecreator.service.WorkspaceService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class WorkspaceServiceImpl implements WorkspaceService {

    private static final List<String> ROLE_HIERARCHY = List.of(
            ROLE_VIEWER, ROLE_MEMBER, ROLE_ADMIN, ROLE_OWNER);

    @Resource
    private WorkspaceMapper workspaceMapper;

    @Resource
    private WorkspaceMemberMapper memberMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Workspace create(String name, String description, Long ownerId) {
        Workspace ws = Workspace.builder()
                .name(name)
                .description(description)
                .ownerId(ownerId)
                .memberCount(1)
                .status("ACTIVE")
                .build();
        workspaceMapper.insert(ws);

        // owner 自动成为成员
        addMemberRecord(ws.getId(), ownerId, ROLE_OWNER);
        log.info("创建协作空间: id={}, name={}, owner={}", ws.getId(), name, ownerId);
        return ws;
    }

    @Override
    public Workspace getById(Long workspaceId, Long userId) {
        String role = getRole(workspaceId, userId);
        if (role == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "不在该空间成员中");
        }
        return workspaceMapper.selectOneById(workspaceId);
    }

    @Override
    public List<Workspace> listByUser(Long userId) {
        return workspaceMapper.selectListByQuery(
                QueryWrapper.create()
                        .in("id", QueryWrapper.create()
                                .select("workspace_id")
                                .from("workspace_member")
                                .where("user_id = " + userId))
                        .eq("status", "ACTIVE")
                        .orderBy("create_time", false));
    }

    @Override
    public Workspace update(Long workspaceId, String name, String description, Long userId) {
        requireRole(workspaceId, userId, ROLE_ADMIN);
        Workspace ws = workspaceMapper.selectOneById(workspaceId);
        if (ws == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }
        if (name != null && !name.isBlank()) ws.setName(name);
        if (description != null) ws.setDescription(description);
        workspaceMapper.update(ws);
        return ws;
    }

    @Override
    public void archive(Long workspaceId, Long userId) {
        requireRole(workspaceId, userId, ROLE_OWNER);
        Workspace ws = workspaceMapper.selectOneById(workspaceId);
        if (ws != null) {
            ws.setStatus("ARCHIVED");
            workspaceMapper.update(ws);
            log.info("归档协作空间: id={}", workspaceId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addMember(Long workspaceId, Long targetUserId, String role, Long operatorId) {
        requireRole(workspaceId, operatorId, ROLE_ADMIN);
        if (ROLE_OWNER.equals(role)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "owner 角色不可添加，仅限创建者");
        }
        // 去重
        WorkspaceMember existing = findMember(workspaceId, targetUserId);
        if (existing != null) {
            existing.setRole(role);
            memberMapper.update(existing);
        } else {
            addMemberRecord(workspaceId, targetUserId, role);
            Workspace ws = workspaceMapper.selectOneById(workspaceId);
            if (ws != null) {
                ws.setMemberCount(ws.getMemberCount() + 1);
                workspaceMapper.update(ws);
            }
        }
        log.info("添加空间成员: workspaceId={}, userId={}, role={}", workspaceId, targetUserId, role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long workspaceId, Long targetUserId, Long operatorId) {
        requireRole(workspaceId, operatorId, ROLE_ADMIN);
        WorkspaceMember target = findMember(workspaceId, targetUserId);
        if (target == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "成员不存在");
        }
        if (ROLE_OWNER.equals(target.getRole())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "不能移除空间创建者");
        }
        memberMapper.deleteById(target.getId());
        Workspace ws = workspaceMapper.selectOneById(workspaceId);
        if (ws != null && ws.getMemberCount() > 0) {
            ws.setMemberCount(ws.getMemberCount() - 1);
            workspaceMapper.update(ws);
        }
        log.info("移除空间成员: workspaceId={}, userId={}", workspaceId, targetUserId);
    }

    @Override
    public List<WorkspaceMember> listMembers(Long workspaceId, Long userId) {
        requireRole(workspaceId, userId, ROLE_VIEWER);
        return memberMapper.selectListByQuery(
                QueryWrapper.create().eq("workspace_id", workspaceId));
    }

    @Override
    public String getRole(Long workspaceId, Long userId) {
        WorkspaceMember member = findMember(workspaceId, userId);
        return member == null ? null : member.getRole();
    }

    @Override
    public boolean hasRole(Long workspaceId, Long userId, String requiredRole) {
        String role = getRole(workspaceId, userId);
        if (role == null) return false;
        int reqIdx = ROLE_HIERARCHY.indexOf(requiredRole);
        int userIdx = ROLE_HIERARCHY.indexOf(role);
        return userIdx >= reqIdx;
    }

    // ─── helpers ──────────────────────────────

    private void requireRole(Long workspaceId, Long userId, String minRole) {
        if (!hasRole(workspaceId, userId, minRole)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR,
                    "需要 " + minRole + " 及以上权限");
        }
    }

    private WorkspaceMember findMember(Long workspaceId, Long userId) {
        return memberMapper.selectOneByQuery(
                QueryWrapper.create()
                        .eq("workspace_id", workspaceId)
                        .eq("user_id", userId));
    }

    private void addMemberRecord(Long workspaceId, Long userId, String role) {
        WorkspaceMember member = WorkspaceMember.builder()
                .workspaceId(workspaceId)
                .userId(userId)
                .role(role)
                .build();
        memberMapper.insert(member);
    }
}
