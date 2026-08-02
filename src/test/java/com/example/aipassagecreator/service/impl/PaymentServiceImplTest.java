package com.example.aipassagecreator.service.impl;

import com.example.aipassagecreator.config.StripeConfig;
import com.example.aipassagecreator.constant.UserConstant;
import com.example.aipassagecreator.enums.PaymentStatusEnum;
import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.mapper.PaymentRecordMapper;
import com.example.aipassagecreator.mapper.UserMapper;
import com.example.aipassagecreator.model.po.PaymentRecord;
import com.example.aipassagecreator.model.po.User;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;

/**
 * 支付服务单元测试 — 支付会话创建和 Webhook 处理
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRecordMapper paymentRecordMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private StripeConfig stripeConfig;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUserRole("user");
        mockUser.setQuota(0);
    }

    @Test
    @DisplayName("创建支付会话 — 非 VIP 用户成功")
    void createVipPaymentSession_notVip() throws StripeException {
        when(userMapper.selectOneById(1L)).thenReturn(mockUser);
        when(stripeConfig.getSuccessUrl()).thenReturn("https://example.com/success");
        when(stripeConfig.getCancelUrl()).thenReturn("https://example.com/cancel");

        // 使用 MockedStatic 模拟 Session.create()
        try (MockedStatic<Session> sessionMock = mockStatic(Session.class)) {
            Session mockSession = mock(Session.class);
            when(mockSession.getUrl()).thenReturn("https://checkout.stripe.com/test");
            sessionMock.when(() -> Session.create(any(SessionCreateParams.class))).thenReturn(mockSession);

            String url = paymentService.createVipPaymentSession(1L);

            assertNotNull(url);
            assertTrue(url.contains("checkout.stripe.com"));
        }
    }

    @Test
    @DisplayName("创建支付会话 — VIP 用户被拒绝")
    void createVipPaymentSession_alreadyVip() {
        mockUser.setUserRole(UserConstant.VIP_ROLE);
        when(userMapper.selectOneById(1L)).thenReturn(mockUser);

        assertThrows(BusinessException.class, () -> paymentService.createVipPaymentSession(1L));
    }

    @Test
    @DisplayName("Webhook 签名验证 — 非法签名抛出 Stripe 异常")
    void constructEvent_invalidSignature() throws Exception {
        when(stripeConfig.getWebhookSecret()).thenReturn("whsec_test");

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenThrow(new SignatureVerificationException("签名验证失败", "bad_signature"));

            assertThrows(SignatureVerificationException.class,
                    () -> paymentService.constructEvent("{}", "bad_signature"));
            webhookMock.verify(() -> Webhook.constructEvent("{}", "bad_signature", "whsec_test"));
        }
    }

    @Test
    @DisplayName("Webhook 签名验证 — 合法签名返回 Event")
    void constructEvent_validSignature() throws Exception {
        when(stripeConfig.getWebhookSecret()).thenReturn("whsec_test");
        Event mockEvent = mock(Event.class);

        try (MockedStatic<Webhook> webhookMock = mockStatic(Webhook.class)) {
            webhookMock.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(mockEvent);

            Event result = paymentService.constructEvent("{}", "good_signature");
            assertSame(mockEvent, result);
        }
    }

    @Test
    @DisplayName("处理支付成功事件 — 更新支付记录并升级用户为 VIP")
    void handlePaymentSuccess_success() {
        Session session = mock(Session.class);
        when(session.getId()).thenReturn("cs_test_123");
        when(session.getMetadata()).thenReturn(Map.of("userId", "1"));
        when(session.getPaymentIntent()).thenReturn("pi_test_123");

        PaymentRecord record = new PaymentRecord();
        record.setId(1L);
        record.setStatus(PaymentStatusEnum.PENDING.getValue());

        when(paymentRecordMapper.selectOneByQuery(any())).thenReturn(record);
        when(paymentRecordMapper.update(any())).thenReturn(1);
        when(userMapper.update(any())).thenReturn(1);

        paymentService.handlePaymentSuccess(session);

        // 支付记录状态更新为 SUCCEEDED，且携带 paymentIntent
        verify(paymentRecordMapper).update(argThat(r ->
                PaymentStatusEnum.SUCCEEDED.getValue().equals(r.getStatus())
                        && "pi_test_123".equals(r.getStripePaymentIntentId())));
        // 用户升级为 VIP
        verify(userMapper).update(argThat(u ->
                UserConstant.VIP_ROLE.equals(u.getUserRole())
                        && u.getVipTime() != null));
    }

    @Test
    @DisplayName("处理支付成功事件 — 已处理记录幂等跳过，不重复升级")
    void handlePaymentSuccess_idempotent() {
        Session session = mock(Session.class);
        when(session.getId()).thenReturn("cs_test_123");
        when(session.getMetadata()).thenReturn(Map.of("userId", "1"));

        PaymentRecord record = new PaymentRecord();
        record.setId(1L);
        record.setStatus(PaymentStatusEnum.SUCCEEDED.getValue());

        when(paymentRecordMapper.selectOneByQuery(any())).thenReturn(record);

        paymentService.handlePaymentSuccess(session);

        verify(paymentRecordMapper, never()).update(any());
        verify(userMapper, never()).update(any());
    }

    @Test
    @DisplayName("处理支付成功事件 — 支付记录不存在时静默跳过")
    void handlePaymentSuccess_recordNotFound() {
        Session session = mock(Session.class);
        when(session.getId()).thenReturn("cs_test_missing");

        when(paymentRecordMapper.selectOneByQuery(any())).thenReturn(null);

        paymentService.handlePaymentSuccess(session);

        verify(paymentRecordMapper, never()).update(any());
        verify(userMapper, never()).update(any());
    }

    @Test
    @DisplayName("处理退款 — 正常流程：VIP 撤销 + 记录标记退款")
    void handleRefund_success() throws Exception {
        PaymentRecord record = new PaymentRecord();
        record.setId(1L);
        record.setUserId(1L);
        record.setStripeSessionId("cs_test_123");
        record.setStripePaymentIntentId("pi_test_123");
        record.setStatus(PaymentStatusEnum.SUCCEEDED.getValue());

        User user = new User();
        user.setId(1L);
        user.setUserRole(UserConstant.VIP_ROLE);

        when(paymentRecordMapper.selectOneByQuery(any())).thenReturn(record);
        when(userMapper.selectOneById(1L)).thenReturn(user);
        // CAS 抢占退款状态成功
        when(paymentRecordMapper.updateByQuery(any(), any())).thenReturn(1);
        when(userMapper.update(any())).thenReturn(1);

        // 模拟 Stripe Refund API
        try (MockedStatic<Refund> refundMock = mockStatic(Refund.class)) {
            Refund mockRefund = mock(Refund.class);
            when(mockRefund.getId()).thenReturn("re_test");
            when(mockRefund.getStatus()).thenReturn("succeeded");
            refundMock.when(() -> Refund.create(any(RefundCreateParams.class)))
                    .thenReturn(mockRefund);

            boolean result = paymentService.handleRefund(1L, "用户申请退款");

            assertTrue(result);
            // 用户身份被撤销回默认角色
            verify(userMapper).update(argThat(u ->
                    UserConstant.DEFAULT_ROLE.equals(u.getUserRole())
                            && u.getQuota() == UserConstant.DEFAULT_QUOTA));
        }
    }

    @Test
    @DisplayName("处理退款 — 非 VIP 用户被拒绝")
    void handleRefund_notVip() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUserRole(UserConstant.DEFAULT_ROLE);

        when(userMapper.selectOneById(1L)).thenReturn(user);

        assertThrows(BusinessException.class, () -> paymentService.handleRefund(1L, "用户申请退款"));
    }

    @Test
    @DisplayName("处理退款 — CAS 抢占失败时返回 false，不重复退款")
    void handleRefund_claimFailed() throws Exception {
        PaymentRecord record = new PaymentRecord();
        record.setId(1L);
        record.setUserId(1L);
        record.setStripeSessionId("cs_test_123");
        record.setStripePaymentIntentId("pi_test_123");
        record.setStatus(PaymentStatusEnum.SUCCEEDED.getValue());

        User user = new User();
        user.setId(1L);
        user.setUserRole(UserConstant.VIP_ROLE);

        when(paymentRecordMapper.selectOneByQuery(any())).thenReturn(record);
        when(userMapper.selectOneById(1L)).thenReturn(user);
        // CAS 抢占失败 → 说明已被并发处理
        when(paymentRecordMapper.updateByQuery(any(), any())).thenReturn(0);

        boolean result = paymentService.handleRefund(1L, "用户申请退款");

        assertFalse(result);
        // 抢占失败时不应发起任何数据库更新或 Stripe 退款
        verify(paymentRecordMapper, never()).update(any());
    }
}