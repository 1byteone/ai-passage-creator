package com.example.aipassagecreator.config;

import cn.hutool.core.util.StrUtil;
import com.example.aipassagecreator.constant.UserConstant;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 在本地或测试环境中幂等创建 VIP 测试账号。
 *
 * <p>该初始化器同时受 Profile 和显式开关保护，默认及生产环境不会创建测试账号。</p>
 */
@Component
@Profile("(local | test) & !prod & !production")
@ConditionalOnProperty(prefix = "app.test-vip-account", name = "enabled", havingValue = "true")
@Slf4j
public class TestVipAccountInitializer implements ApplicationRunner {

    private static final String TEST_ACCOUNT_PROFILE = "仅用于本地开发与自动化测试";

    private final UserService userService;
    private final String account;
    private final String password;
    private final String displayName;

    public TestVipAccountInitializer(
            UserService userService,
            @Value("${app.test-vip-account.account:vip_test}") String account,
            @Value("${app.test-vip-account.password:}") String password,
            @Value("${app.test-vip-account.display-name:VIP 测试账号}") String displayName) {
        this.userService = userService;
        this.account = account;
        this.password = password;
        this.displayName = displayName;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        validateConfiguration();

        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq(User::getUserAccount, account);
        User testUser = userService.getOne(queryWrapper);
        boolean created = testUser == null;

        if (created) {
            testUser = new User();
            testUser.setUserAccount(account);
            testUser.setVipTime(LocalDateTime.now());
        } else if (testUser.getVipTime() == null) {
            testUser.setVipTime(LocalDateTime.now());
        }

        testUser.setUserPassword(userService.getEncryptPassword(password));
        testUser.setUserName(displayName);
        testUser.setUserProfile(TEST_ACCOUNT_PROFILE);
        testUser.setUserRole(UserConstant.VIP_ROLE);
        testUser.setQuota(Math.max(
                testUser.getQuota() == null ? 0 : testUser.getQuota(),
                UserConstant.DEFAULT_QUOTA));

        boolean persisted = created
                ? userService.save(testUser)
                : userService.updateById(testUser);
        if (!persisted) {
            throw new IllegalStateException("VIP 测试账号初始化失败");
        }

        log.info("VIP 测试账号已{}，account={}", created ? "创建" : "刷新", account);
    }

    private void validateConfiguration() {
        if (StrUtil.isBlank(account) || account.length() < 4) {
            throw new IllegalStateException("app.test-vip-account.account 至少需要 4 个字符");
        }
        if (StrUtil.isBlank(password) || password.length() < 8) {
            throw new IllegalStateException(
                    "启用 VIP 测试账号时必须提供至少 8 位的 app.test-vip-account.password");
        }
        if (StrUtil.isBlank(displayName)) {
            throw new IllegalStateException("app.test-vip-account.display-name 不能为空");
        }
    }
}
