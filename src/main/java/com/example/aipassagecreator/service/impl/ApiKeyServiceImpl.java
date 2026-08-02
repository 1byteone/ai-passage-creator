package com.example.aipassagecreator.service.impl;

import cn.hutool.core.util.StrUtil;
import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.mapper.ApiKeyMapper;
import com.example.aipassagecreator.mapper.UserMapper;
import com.example.aipassagecreator.model.po.ApiKey;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.model.vo.ApiKeyCreateVO;
import com.example.aipassagecreator.model.vo.ApiKeyVO;
import com.example.aipassagecreator.service.ApiKeyService;
import com.example.aipassagecreator.utils.ApiKeyGenerator;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ApiKeyServiceImpl extends ServiceImpl<ApiKeyMapper, ApiKey> implements ApiKeyService {

    /** lastUsedAt 节流写入间隔：距上次 5 分钟内不重复写，防写放大 */
    private static final long LAST_USED_THROTTLE_MINUTES = 5;

    @Resource
    private UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiKeyCreateVO createKey(Long operatorId, boolean isAdmin, Long targetUserId,
                                    String name, LocalDateTime expiresAt) {
        Long ownerId = resolveOwnerId(operatorId, isAdmin, targetUserId);
        User owner = userMapper.selectOneById(ownerId);
        if (owner == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "目标用户不存在");
        }
        String plain = ApiKeyGenerator.generate();
        ApiKey apiKey = ApiKey.builder()
                .userId(ownerId)
                .name(name)
                .apiKeyHash(ApiKeyGenerator.hash(plain))
                .apiKeyPrefix(ApiKeyGenerator.prefix(plain))
                .expiresAt(expiresAt)
                .build();
        if (!this.save(apiKey)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建 API Key 失败");
        }
        ApiKeyCreateVO vo = new ApiKeyCreateVO();
        vo.setId(apiKey.getId());
        vo.setName(name);
        vo.setApiKey(plain);
        vo.setApiKeyPrefix(apiKey.getApiKeyPrefix());
        vo.setExpiresAt(expiresAt);
        return vo;
    }

    @Override
    public Page<ApiKeyVO> listKeys(Long operatorId, boolean isAdmin, Long targetUserId,
                                   long pageNum, long pageSize) {
        Long ownerId = resolveOwnerId(operatorId, isAdmin, targetUserId);
        QueryWrapper qw = new QueryWrapper();
        qw.eq(ApiKey::getUserId, ownerId);
        Page<ApiKey> page = this.page(new Page<>(pageNum, pageSize), qw);
        Page<ApiKeyVO> voPage = new Page<>();
        voPage.setPageNumber(page.getPageNumber());
        voPage.setPageSize(page.getPageSize());
        voPage.setTotalRow(page.getTotalRow());
        List<ApiKeyVO> voList = page.getRecords().stream().map(this::toVO).toList();
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean revokeKey(Long operatorId, boolean isAdmin, Long keyId) {
        ApiKey key = this.getById(keyId);
        if (key == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "API Key 不存在");
        }
        if (!key.getUserId().equals(operatorId) && !isAdmin) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        return this.removeById(keyId);
    }

    @Override
    public User validateAndGetUser(String apiKey) {
        if (StrUtil.isBlank(apiKey)) {
            return null;
        }
        QueryWrapper qw = new QueryWrapper();
        qw.eq(ApiKey::getApiKeyHash, ApiKeyGenerator.hash(apiKey));
        ApiKey key = this.getOne(qw);
        if (key == null) {
            return null;
        }
        if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        User user = userMapper.selectOneById(key.getUserId());
        if (user == null) {
            return null;
        }
        throttledTouch(key);
        return user;
    }

    /** 非 admin 指定他人 → 无权限；targetUserId 为空 → 本人 */
    private Long resolveOwnerId(Long operatorId, boolean isAdmin, Long targetUserId) {
        if (targetUserId == null) {
            return operatorId;
        }
        if (!isAdmin) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        return targetUserId;
    }

    private ApiKeyVO toVO(ApiKey key) {
        ApiKeyVO vo = new ApiKeyVO();
        vo.setId(key.getId());
        vo.setUserId(key.getUserId());
        vo.setName(key.getName());
        vo.setApiKeyPrefix(key.getApiKeyPrefix());
        vo.setLastUsedAt(key.getLastUsedAt());
        vo.setExpiresAt(key.getExpiresAt());
        vo.setCreateTime(key.getCreateTime());
        return vo;
    }

    /** 距上次使用超 5 分钟才更新 lastUsedAt，避免每次请求写库 */
    private void throttledTouch(ApiKey key) {
        LocalDateTime last = key.getLastUsedAt();
        if (last != null && last.plusMinutes(LAST_USED_THROTTLE_MINUTES).isAfter(LocalDateTime.now())) {
            return;
        }
        ApiKey update = new ApiKey();
        update.setId(key.getId());
        update.setLastUsedAt(LocalDateTime.now());
        this.updateById(update);
    }
}
