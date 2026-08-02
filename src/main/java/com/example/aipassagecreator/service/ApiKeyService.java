package com.example.aipassagecreator.service;

import com.example.aipassagecreator.model.po.ApiKey;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.model.vo.ApiKeyCreateVO;
import com.example.aipassagecreator.model.vo.ApiKeyVO;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;

import java.time.LocalDateTime;

public interface ApiKeyService extends IService<ApiKey> {

    /**
     * 创建 API Key（明文仅此一次返回）
     *
     * @param operatorId   操作者
     * @param isAdmin      操作者是否管理员
     * @param targetUserId 归属用户；为空表示本人（非 admin 指定他人抛 NO_AUTH）
     */
    ApiKeyCreateVO createKey(Long operatorId, boolean isAdmin, Long targetUserId,
                             String name, LocalDateTime expiresAt);

    /**
     * 查询 Key 列表（脱敏）
     */
    Page<ApiKeyVO> listKeys(Long operatorId, boolean isAdmin, Long targetUserId,
                            long pageNum, long pageSize);

    /**
     * 吊销 API Key（逻辑删除，立即失效）
     */
    boolean revokeKey(Long operatorId, boolean isAdmin, Long keyId);

    /**
     * 校验 token 并返回归属用户；无效返回 null（供拦截器使用）
     */
    User validateAndGetUser(String apiKey);
}
