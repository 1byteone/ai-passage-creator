package com.example.aipassagecreator.config;

import com.example.aipassagecreator.constant.UserConstant;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.model.vo.LoginUserVO;
import com.example.aipassagecreator.service.QuotaService;
import com.example.aipassagecreator.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:test_vip_account;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:test-vip-schema.sql",
        "app.test-vip-account.enabled=true",
        "app.test-vip-account.account=vip_test",
        "app.test-vip-account.password=VipTest@2026",
        "app.test-vip-account.display-name=VIP 测试账号"
})
@ActiveProfiles("test")
@DirtiesContext
class TestVipAccountIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private QuotaService quotaService;

    @Autowired
    private TestVipAccountInitializer initializer;

    @Test
    void shouldLoginAsVipAndKeepQuotaUnchanged() {
        initializer.run(new DefaultApplicationArguments(new String[0]));

        QueryWrapper accountQuery = QueryWrapper.create()
                .eq(User::getUserAccount, "vip_test");
        assertEquals(1, userService.list(accountQuery).size());

        MockHttpServletRequest request = new MockHttpServletRequest();

        LoginUserVO loginUser = userService.userLogin("vip_test", "VipTest@2026", request);

        assertNotNull(loginUser.getId());
        assertEquals(UserConstant.VIP_ROLE, loginUser.getUserRole());
        assertNotNull(loginUser.getVipTime());

        User persistedUser = userService.getById(loginUser.getId());
        int quotaBefore = persistedUser.getQuota();

        quotaService.checkAndConsumeQuota(persistedUser);

        User userAfterQuotaCheck = userService.getById(loginUser.getId());
        assertEquals(quotaBefore, userAfterQuotaCheck.getQuota());
    }
}
