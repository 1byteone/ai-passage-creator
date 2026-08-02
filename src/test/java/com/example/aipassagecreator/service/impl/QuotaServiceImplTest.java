package com.example.aipassagecreator.service.impl;

import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.mapper.UserMapper;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 配额服务单元测试 — 配额检查/扣减/退还核心逻辑
 */
@ExtendWith(MockitoExtension.class)
class QuotaServiceImplTest {

    @Mock
    private UserService userService;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private QuotaServiceImpl quotaService;

    private User adminUser;
    private User vipUser;
    private User normalUser;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUserRole("admin");

        vipUser = new User();
        vipUser.setId(2L);
        vipUser.setUserRole("vip");
        vipUser.setQuota(100);

        normalUser = new User();
        normalUser.setId(3L);
        normalUser.setUserRole("user");
        normalUser.setQuota(5);
    }

    @Test
    @DisplayName("管理员无限配额 — 不查库直接放行")
    void hasQuota_adminReturnsTrue() {
        assertTrue(quotaService.hasQuota(adminUser));
        verify(userService, never()).getById(anyLong());
    }

    @Test
    @DisplayName("非管理员从数据库读最新配额 — 有余额返回 true")
    void hasQuota_vipWithQuota() {
        when(userService.getById(2L)).thenReturn(vipUser);

        assertTrue(quotaService.hasQuota(vipUser));
    }

    @Test
    @DisplayName("普通用户配额为 0 时返回 false")
    void hasQuota_normalUserNoQuota() {
        normalUser.setQuota(0);
        when(userService.getById(3L)).thenReturn(normalUser);

        assertFalse(quotaService.hasQuota(normalUser));
    }

    @Test
    @DisplayName("用户不存在时返回 false")
    void hasQuota_userNotFound() {
        when(userService.getById(3L)).thenReturn(null);

        assertFalse(quotaService.hasQuota(normalUser));
    }

    @Test
    @DisplayName("检查并消耗配额 — 原子扣减成功")
    void checkAndConsumeQuota_success() {
        when(userMapper.decrementQuota(3L)).thenReturn(1);

        assertDoesNotThrow(() -> quotaService.checkAndConsumeQuota(normalUser, "配额不足"));
        verify(userMapper, times(1)).decrementQuota(3L);
    }

    @Test
    @DisplayName("检查并消耗配额 — 配额不足时抛业务异常")
    void checkAndConsumeQuota_insufficient() {
        when(userMapper.decrementQuota(3L)).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> quotaService.checkAndConsumeQuota(normalUser, "配额不足"));
        verify(userMapper, times(1)).decrementQuota(3L);
    }

    @Test
    @DisplayName("检查并消耗配额 — 管理员/VIP 不扣减")
    void checkAndConsumeQuota_privilegedSkip() {
        quotaService.checkAndConsumeQuota(adminUser, "配额不足");
        quotaService.checkAndConsumeQuota(vipUser, "配额不足");

        verify(userMapper, never()).decrementQuota(anyLong());
    }

    @Test
    @DisplayName("退还配额 — 原子递增 quota")
    void refundQuota_success() {
        when(userMapper.incrementQuota(3L)).thenReturn(1);

        assertDoesNotThrow(() -> quotaService.refundQuota(normalUser));
        verify(userMapper, times(1)).incrementQuota(3L);
    }

    @Test
    @DisplayName("退还配额 — 管理员/VIP 未扣过，不退还")
    void refundQuota_privilegedSkip() {
        quotaService.refundQuota(adminUser);
        quotaService.refundQuota(vipUser);

        verify(userMapper, never()).incrementQuota(anyLong());
    }
}
