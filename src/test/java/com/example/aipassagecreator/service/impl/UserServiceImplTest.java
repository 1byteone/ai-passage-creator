package com.example.aipassagecreator.service.impl;

import com.example.aipassagecreator.constant.ApiKeyConstant;
import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.mapper.UserMapper;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.model.vo.LoginUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.DigestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 用户服务单元测试 — 注册/登录核心逻辑
 *
 * <p>UserServiceImpl 继承 MyBatis-Flex {@code ServiceImpl}，数据库操作走父类 {@code mapper} 字段，
 * 因此用 {@link ReflectionTestUtils} 把 mock 注入父字段，避免直接 {@code @InjectMocks} 注入不到。</p>
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        // 注入父类 ServiceImpl 的 mapper 字段
        ReflectionTestUtils.setField(userService, "mapper", userMapper);

        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUserAccount("testuser");
        mockUser.setUserName("测试用户");
        // 匹配 UserServiceImpl 中盐值 "yupi" 的加密方式
        mockUser.setUserPassword(DigestUtils.md5DigestAsHex(("12345678" + "yupi").getBytes()));
        mockUser.setUserRole("user");
    }

    @Test
    @DisplayName("注册成功 — 正常流程")
    void userRegister_success() {
        when(userMapper.selectCountByQuery(any())).thenReturn(0L);
        // IService.save() 委托 mapper.insert(entity, ignoreNulls)，用 thenAnswer 回填自增 ID
        when(userMapper.insert(any(), anyBoolean())).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(1L);
            return 1;
        });

        long result = userService.userRegister("newuser", "password123", "password123");

        assertEquals(1L, result);
        verify(userMapper, times(1)).insert(any(), anyBoolean());
    }

    @Test
    @DisplayName("注册成功 — 自动生成 DiceBear 默认头像")
    void userRegister_assignsDiceBearAvatar() {
        when(userMapper.selectCountByQuery(any())).thenReturn(0L);
        when(userMapper.insert(any(), anyBoolean())).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(1L);
            return 1;
        });

        userService.userRegister("avatar_user", "password123", "password123");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture(), anyBoolean());
        User saved = captor.getValue();
        // 以账号为 seed 生成确定性 DiceBear Open Peeps 头像
        assertNotNull(saved.getUserAvatar());
        assertTrue(saved.getUserAvatar().startsWith("https://api.dicebear.com/10.x/open-peeps/svg"));
        assertTrue(saved.getUserAvatar().contains("seed=avatar_user"));
    }

    @Test
    @DisplayName("注册失败 — 账号已存在")
    void userRegister_duplicateAccount() {
        when(userMapper.selectCountByQuery(any())).thenReturn(1L);

        assertThrows(BusinessException.class,
                () -> userService.userRegister("testuser", "password123", "password123"));
        verify(userMapper, never()).insert(any(), anyBoolean());
    }

    @Test
    @DisplayName("注册失败 — 密码不一致")
    void userRegister_passwordMismatch() {
        assertThrows(BusinessException.class,
                () -> userService.userRegister("newuser", "password123", "different"));
        verify(userMapper, never()).insert(any(), anyBoolean());
    }

    @Test
    @DisplayName("登录成功 — 正确凭证写入登录态并返回用户信息")
    void userLogin_success() {
        when(userMapper.selectOneByQuery(any())).thenReturn(mockUser);
        when(userMapper.selectOneById(1L)).thenReturn(mockUser);

        MockHttpServletRequest request = new MockHttpServletRequest();
        var result = userService.userLogin("testuser", "12345678", request);

        assertNotNull(result);
        assertEquals("测试用户", result.getUserName());
        // 登录态写入 session
        assertEquals(1L, request.getSession().getAttribute("user_login"));
    }

    @Test
    @DisplayName("登录失败 — 账号不存在")
    void userLogin_userNotFound() {
        when(userMapper.selectOneByQuery(any())).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> userService.userLogin("nonexistent", "password123", new MockHttpServletRequest()));
    }

    @Test
    @DisplayName("登录失败 — 密码错误时查询无匹配记录")
    void userLogin_wrongPassword() {
        // 不 stub selectOneByQuery，默认返回 null → 视为无匹配用户
        assertThrows(BusinessException.class,
                () -> userService.userLogin("testuser", "wrongpassword", new MockHttpServletRequest()));
    }

    @Test
    @DisplayName("获取登录用户VO — API Key 认证优先读 request attribute，无需 session")
    void getLoginUserVO_apiKeyAttribute_returnsUser() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ApiKeyConstant.REQUEST_USER_ATTR, mockUser);

        LoginUserVO result = userService.getLoginUserVO(request);

        assertNotNull(result);
        assertEquals("测试用户", result.getUserName());
        // 不依赖 session / 数据库
        verify(userMapper, never()).selectOneById(any());
    }

    @Test
    @DisplayName("获取登录用户实体 — API Key request attribute 优先")
    void getLoginUser_apiKeyAttribute_returnsUser() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ApiKeyConstant.REQUEST_USER_ATTR, mockUser);

        User result = userService.getLoginUser(request);

        assertEquals(1L, result.getId());
        verify(userMapper, never()).selectOneById(any());
    }
}
