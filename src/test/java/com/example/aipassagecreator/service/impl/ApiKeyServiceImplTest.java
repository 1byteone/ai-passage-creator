package com.example.aipassagecreator.service.impl;

import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.mapper.ApiKeyMapper;
import com.example.aipassagecreator.mapper.UserMapper;
import com.example.aipassagecreator.model.po.ApiKey;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.model.vo.ApiKeyCreateVO;
import com.example.aipassagecreator.model.vo.ApiKeyVO;
import com.example.aipassagecreator.utils.ApiKeyGenerator;
import com.mybatisflex.core.paginate.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * API Key 服务单元测试 — 创建(明文仅一次)/校验/吊销/归属校验
 *
 * <p>与 UserServiceImplTest 相同：MyBatis-Flex {@code ServiceImpl} 的 {@code mapper} 父字段
 * 用 {@link ReflectionTestUtils} 注入。</p>
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyServiceImplTest {

    @Mock
    private ApiKeyMapper apiKeyMapper;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private ApiKeyServiceImpl apiKeyService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(apiKeyService, "mapper", apiKeyMapper);
    }

    @Test
    @DisplayName("创建 Key — 只存 SHA-256 哈希，明文仅一次返回")
    void createKey_storesHashOnlyAndReturnsPlaintextOnce() {
        when(userMapper.selectOneById(1L)).thenReturn(user(1L));
        when(apiKeyMapper.insert(any(), anyBoolean())).thenAnswer(inv -> {
            ((ApiKey) inv.getArgument(0)).setId(100L);
            return 1;
        });

        ApiKeyCreateVO vo = apiKeyService.createKey(1L, false, null, "test-key", null);

        assertNotNull(vo.getApiKey());
        assertTrue(vo.getApiKey().startsWith("apc_"));
        assertEquals("apc_", vo.getApiKeyPrefix().substring(0, 4));

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyMapper).insert(captor.capture(), anyBoolean());
        ApiKey saved = captor.getValue();
        // 落库的是 hash，不是明文
        assertNotEquals(vo.getApiKey(), saved.getApiKeyHash());
        assertEquals(ApiKeyGenerator.hash(vo.getApiKey()), saved.getApiKeyHash());
        assertEquals(1L, saved.getUserId());
        assertEquals("test-key", saved.getName());
    }

    @Test
    @DisplayName("创建 Key — 非 admin 指定他人归属抛无权限")
    void createKey_nonAdminSpecifiesOthers_throwsNoAuth() {
        assertThrows(BusinessException.class,
                () -> apiKeyService.createKey(1L, false, 2L, "x", null));
        verify(apiKeyMapper, never()).insert(any(), anyBoolean());
    }

    @Test
    @DisplayName("创建 Key — 目标用户不存在抛参数错误")
    void createKey_targetUserNotFound_throwsParamsError() {
        when(userMapper.selectOneById(99L)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> apiKeyService.createKey(1L, true, 99L, "x", null));
    }

    @Test
    @DisplayName("校验 — 有效 Key 返回归属用户并节流更新 lastUsedAt")
    void validateAndGetUser_validKey_returnsUser() {
        String token = ApiKeyGenerator.generate();
        ApiKey key = ApiKey.builder().id(1L).userId(2L)
                .apiKeyHash(ApiKeyGenerator.hash(token))
                .lastUsedAt(LocalDateTime.now()).build(); // 距现在 < 5 分钟 → 不写库
        when(apiKeyMapper.selectOneByQuery(any())).thenReturn(key);
        when(userMapper.selectOneById(2L)).thenReturn(user(2L));

        User result = apiKeyService.validateAndGetUser(token);

        assertEquals(2L, result.getId());
        verify(apiKeyMapper, never()).update(any(), anyBoolean());
    }

    @Test
    @DisplayName("校验 — 超过节流窗口会更新 lastUsedAt")
    void validateAndGetUser_updatesLastUsedAfterThrottleWindow() {
        String token = ApiKeyGenerator.generate();
        ApiKey key = ApiKey.builder().id(1L).userId(2L)
                .apiKeyHash(ApiKeyGenerator.hash(token))
                .lastUsedAt(LocalDateTime.now().minusMinutes(30)).build();
        when(apiKeyMapper.selectOneByQuery(any())).thenReturn(key);
        when(userMapper.selectOneById(2L)).thenReturn(user(2L));

        apiKeyService.validateAndGetUser(token);

        verify(apiKeyMapper).update(any(ApiKey.class), anyBoolean());
    }

    @Test
    @DisplayName("校验 — 未知 hash 返回 null")
    void validateAndGetUser_unknownHash_returnsNull() {
        when(apiKeyMapper.selectOneByQuery(any())).thenReturn(null);

        assertNull(apiKeyService.validateAndGetUser("apc_bad"));
    }

    @Test
    @DisplayName("校验 — 过期 Key 返回 null，且不查用户")
    void validateAndGetUser_expiredKey_returnsNull() {
        String token = ApiKeyGenerator.generate();
        ApiKey key = ApiKey.builder().id(1L).userId(2L)
                .apiKeyHash(ApiKeyGenerator.hash(token))
                .expiresAt(LocalDateTime.now().minusMinutes(1)).build();
        when(apiKeyMapper.selectOneByQuery(any())).thenReturn(key);

        assertNull(apiKeyService.validateAndGetUser(token));
        verify(userMapper, never()).selectOneById(any());
    }

    @Test
    @DisplayName("校验 — 空白 token 直接返回 null，不发起查询")
    void validateAndGetUser_blankToken_returnsNull() {
        assertNull(apiKeyService.validateAndGetUser("  "));
        verify(apiKeyMapper, never()).selectOneByQuery(any());
    }

    @Test
    @DisplayName("查询 — 自助查询本人 Key，返回脱敏视图")
    void listKeys_selfService_returnsMasked() {
        ApiKey row = ApiKey.builder().id(1L).userId(1L).name("test-key")
                .apiKeyPrefix("apc_abc123").build();
        Page<ApiKey> dbPage = new Page<>(1, 10);
        dbPage.setRecords(List.of(row));
        dbPage.setTotalRow(1);
        // ServiceImpl.page → pageAs(page, qw, null)：第三参为 null，用零参 any()（类型化锁定 R=ApiKey）匹配
        when(apiKeyMapper.paginateAs(any(), any(), org.mockito.ArgumentMatchers.<Class<ApiKey>>any()))
                .thenReturn(dbPage);

        Page<ApiKeyVO> result = apiKeyService.listKeys(1L, false, null, 1, 10);

        assertEquals(1, result.getTotalRow());
        ApiKeyVO vo = result.getRecords().get(0);
        assertEquals("test-key", vo.getName());
        assertEquals("apc_abc123", vo.getApiKeyPrefix());
        assertEquals(1L, vo.getUserId());
    }

    @Test
    @DisplayName("吊销 — 本人可吊销自己的 Key")
    void revokeKey_ownerCanRevoke() {
        ApiKey key = ApiKey.builder().id(1L).userId(1L).build();
        when(apiKeyMapper.selectOneById(1L)).thenReturn(key);
        when(apiKeyMapper.deleteById(1L)).thenReturn(1);

        assertTrue(apiKeyService.revokeKey(1L, false, 1L));
        verify(apiKeyMapper).deleteById(1L);
    }

    @Test
    @DisplayName("吊销 — 非本人且非 admin 抛无权限")
    void revokeKey_nonOwnerWithoutAdmin_throwsNoAuth() {
        ApiKey key = ApiKey.builder().id(1L).userId(2L).build();
        when(apiKeyMapper.selectOneById(1L)).thenReturn(key);

        assertThrows(BusinessException.class, () -> apiKeyService.revokeKey(1L, false, 1L));
        verify(apiKeyMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("吊销 — admin 可吊销他人 Key")
    void revokeKey_adminCanRevokeOthers() {
        ApiKey key = ApiKey.builder().id(1L).userId(2L).build();
        when(apiKeyMapper.selectOneById(1L)).thenReturn(key);
        when(apiKeyMapper.deleteById(1L)).thenReturn(1);

        assertTrue(apiKeyService.revokeKey(1L, true, 1L));
    }

    @Test
    @DisplayName("吊销 — Key 不存在抛未找到")
    void revokeKey_notFound_throwsNotFound() {
        when(apiKeyMapper.selectOneById(99L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> apiKeyService.revokeKey(1L, false, 99L));
    }

    private User user(Long id) {
        User u = new User();
        u.setId(id);
        u.setUserRole("user");
        return u;
    }
}
