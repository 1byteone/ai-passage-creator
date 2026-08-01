package com.example.aipassagecreator.service.impl;

import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.mapper.UserMapper;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.service.QuotaService;
import com.example.aipassagecreator.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.aipassagecreator.constant.UserConstant.ADMIN_ROLE;
import static com.example.aipassagecreator.constant.UserConstant.VIP_ROLE;

@Service
@Slf4j
public class QuotaServiceImpl implements QuotaService {

    @Resource
    private UserService userService;

    @Resource
    private UserMapper userMapper;

    @Override
    public boolean hasQuota(User user) {
        // 管理员无限配额
        if (isAdmin(user)) {
            return true;
        }
        // 从数据库查询最新配额，避免使用缓存的旧数据
        User freshUser = userService.getById(user.getId());
        if (freshUser == null) {
            return false;
        }
        Integer quota = freshUser.getQuota();
        return quota != null && quota > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void consumeQuota(User user) {
        // 管理员和VIP用户不消耗配额
        if (isAdmin(user) || isVip(user)) {
            return;
        }

        // 使用原子更新：UPDATE user SET quota = quota - 1 WHERE id = ? AND quota > 0
        // 通过影响行数判断是否成功，避免并发问题
        int affectedRows = userMapper.decrementQuota(user.getId());

        if (affectedRows > 0) {
            log.info("用户配额已消耗, userId={}", user.getId());
        } else {
            log.warn("用户配额扣减失败（可能配额不足或并发冲突）, userId={}", user.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkAndConsumeQuota(User user) {
        checkAndConsumeQuota(user, "配额不足，无法创建文章");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkAndConsumeQuota(User user, String insufficientMessage) {
        // 管理员和VIP跳过检查
        if (isAdmin(user) || isVip(user)) {
            return;
        }

        // 使用原子更新：检查与消费合并为一个原子操作
        // UPDATE user SET quota = quota - 1 WHERE id = ? AND quota > 0
        int affectedRows = userMapper.decrementQuota(user.getId());

        if (affectedRows == 0) {
            // 影响行数为0，说明配额不足（已被其他请求消耗）
            throw new BusinessException(ErrorCode.OPERATION_ERROR, insufficientMessage);
        }

        log.info("用户配额检查并消耗成功, userId={}", user.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundQuota(User user) {
        // 管理员和VIP未扣减过配额，无需退还
        if (isAdmin(user) || isVip(user)) {
            return;
        }

        int affectedRows = userMapper.incrementQuota(user.getId());

        if (affectedRows > 0) {
            log.info("用户配额已退还, userId={}", user.getId());
        } else {
            log.warn("用户配额退还失败, userId={}", user.getId());
        }
    }

    /**
     * 判断是否为管理员
     */
    private boolean isAdmin(User user) {
        return ADMIN_ROLE.equals(user.getUserRole());
    }

    /**
     * 判断是否为VIP
     */
    private boolean isVip(User user) {
        return VIP_ROLE.equals(user.getUserRole());
    }
}
