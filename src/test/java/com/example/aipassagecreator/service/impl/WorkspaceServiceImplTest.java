package com.example.aipassagecreator.service.impl;

import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.mapper.WorkspaceMapper;
import com.example.aipassagecreator.mapper.WorkspaceMemberMapper;
import com.example.aipassagecreator.model.po.Workspace;
import com.example.aipassagecreator.model.po.WorkspaceMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 协作空间服务单元测试 — 数据完整性守卫
 *
 * <p>覆盖本轮审计新增/修复的防御：create 空名校验、getById 存在性优先、
 * 归档只读、addMember 角色白名单。</p>
 */
@ExtendWith(MockitoExtension.class)
class WorkspaceServiceImplTest {

    @Mock
    private WorkspaceMapper workspaceMapper;

    @Mock
    private WorkspaceMemberMapper memberMapper;

    @InjectMocks
    private WorkspaceServiceImpl workspaceService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(workspaceService, "workspaceMapper", workspaceMapper);
        ReflectionTestUtils.setField(workspaceService, "memberMapper", memberMapper);
    }

    private Workspace workspace(Long id, String status) {
        return Workspace.builder().id(id).name("编辑部").status(status).ownerId(1L).build();
    }

    private WorkspaceMember member(Long wsId, Long userId, String role) {
        return WorkspaceMember.builder().id(userId).workspaceId(wsId).userId(userId).role(role).build();
    }

    @Test
    @DisplayName("创建 — 空白名称抛参数错误")
    void create_blankName_throwsParamsError() {
        assertThrows(BusinessException.class,
                () -> workspaceService.create("   ", null, 1L));
        verify(workspaceMapper, never()).insert(any());
    }

    @Test
    @DisplayName("创建 — 名称超长抛参数错误")
    void create_nameTooLong_throwsParamsError() {
        String longName = "a".repeat(129);
        assertThrows(BusinessException.class,
                () -> workspaceService.create(longName, null, 1L));
    }

    @Test
    @DisplayName("创建 — 正常名称 trim 后落库，owner 自动成为成员")
    void create_validName_trimsAndAddsOwner() {
        when(workspaceMapper.insert(any(Workspace.class))).thenAnswer(inv -> {
            ((Workspace) inv.getArgument(0)).setId(10L);
            return 1;
        });

        Workspace result = workspaceService.create("  编辑部  ", "团队空间", 1L);

        assertEquals("编辑部", result.getName());
        assertEquals(1, result.getMemberCount());
        // owner 自动成为成员
        verify(memberMapper).insert(any(WorkspaceMember.class));
    }

    @Test
    @DisplayName("详情 — 空间不存在优先返回未找到，而非无权限")
    void getById_workspaceMissing_throwsNotFound() {
        when(workspaceMapper.selectOneById(99L)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> workspaceService.getById(99L, 1L));
    }

    @Test
    @DisplayName("详情 — 非成员访问已存在空间抛无权限")
    void getById_notMember_throwsNoAuth() {
        when(workspaceMapper.selectOneById(1L)).thenReturn(workspace(1L, "ACTIVE"));
        when(memberMapper.selectOneByQuery(any())).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> workspaceService.getById(1L, 2L));
    }

    @Test
    @DisplayName("更新 — 归档空间拒绝修改（只读）")
    void update_archivedWorkspace_throwsOperationError() {
        when(memberMapper.selectOneByQuery(any())).thenReturn(member(1L, 1L, "owner"));
        when(workspaceMapper.selectOneById(1L)).thenReturn(workspace(1L, "ARCHIVED"));

        assertThrows(BusinessException.class,
                () -> workspaceService.update(1L, "新名字", null, 1L));
        verify(workspaceMapper, never()).update(any(Workspace.class));
    }

    @Test
    @DisplayName("添加成员 — 非法角色被拒绝")
    void addMember_invalidRole_throwsParamsError() {
        when(memberMapper.selectOneByQuery(any())).thenReturn(member(1L, 1L, "admin"));
        when(workspaceMapper.selectOneById(1L)).thenReturn(workspace(1L, "ACTIVE"));

        assertThrows(BusinessException.class,
                () -> workspaceService.addMember(1L, 2L, "superuser", 1L));
        verify(memberMapper, never()).insert(any(WorkspaceMember.class));
    }

    @Test
    @DisplayName("添加成员 — owner 角色不可通过接口分配")
    void addMember_ownerRole_throwsParamsError() {
        when(memberMapper.selectOneByQuery(any())).thenReturn(member(1L, 1L, "owner"));
        when(workspaceMapper.selectOneById(1L)).thenReturn(workspace(1L, "ACTIVE"));

        assertThrows(BusinessException.class,
                () -> workspaceService.addMember(1L, 2L, "owner", 1L));
    }

    @Test
    @DisplayName("添加成员 — 归档空间拒绝添加")
    void addMember_archivedWorkspace_throwsOperationError() {
        when(memberMapper.selectOneByQuery(any())).thenReturn(member(1L, 1L, "owner"));
        when(workspaceMapper.selectOneById(1L)).thenReturn(workspace(1L, "ARCHIVED"));

        assertThrows(BusinessException.class,
                () -> workspaceService.addMember(1L, 2L, "member", 1L));
    }

    @Test
    @DisplayName("移除成员 — 归档空间拒绝移除")
    void removeMember_archivedWorkspace_throwsOperationError() {
        when(memberMapper.selectOneByQuery(any())).thenReturn(member(1L, 1L, "owner"));
        when(workspaceMapper.selectOneById(1L)).thenReturn(workspace(1L, "ARCHIVED"));

        assertThrows(BusinessException.class,
                () -> workspaceService.removeMember(1L, 2L, 1L));
    }
}
