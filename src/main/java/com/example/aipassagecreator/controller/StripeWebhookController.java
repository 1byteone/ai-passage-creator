package com.example.aipassagecreator.controller;

import com.example.aipassagecreator.service.PaymentService;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/webhook")
@Slf4j
@Hidden
public class StripeWebhookController {

    @Resource
    private PaymentService paymentService;

    /**
     * 处理 Stripe Webhook 回调
     */
    @PostMapping("/stripe")
    public String handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        
        try {
            // 验证 Webhook 签名
            Event event = paymentService.constructEvent(payload, sigHeader);
            
            log.info("收到 Stripe Webhook 事件, type={}", event.getType());
            
            // 处理事件
            switch (event.getType()) {
                case "checkout.session.completed":
                    // 支付成功
                    Session session = extractSessionFromEvent(event);
                    if (session != null) {
                        paymentService.handlePaymentSuccess(session);
                    }
                    break;
                    
                case "checkout.session.async_payment_succeeded":
                    // 异步支付成功
                    Session asyncSession = extractSessionFromEvent(event);
                    if (asyncSession != null) {
                        paymentService.handlePaymentSuccess(asyncSession);
                    }
                    break;
                    
                default:
                    log.info("未处理的事件类型: {}", event.getType());
                    break;
            }
            
            return "success";
        } catch (Exception e) {
            log.error("处理 Stripe Webhook 失败", e);
            return "error";
        }
    }

    /**
     * 从 Event 中提取 Session 对象
     */
    private Session extractSessionFromEvent(Event event) {
        try {
            // 方法1: 尝试直接反序列化
            Optional<StripeObject> stripeObjectOpt = event.getDataObjectDeserializer().getObject();
            if (stripeObjectOpt.isPresent() && stripeObjectOpt.get() instanceof Session) {
                return (Session) stripeObjectOpt.get();
            }
            
            // 方法2: 如果方法1失败，使用 deserializeUnsafe（适用于 API 版本不匹配的情况）
            StripeObject unsafeObject = event.getDataObjectDeserializer().deserializeUnsafe();
            if (unsafeObject != null && unsafeObject instanceof Session) {
                log.warn("使用 deserializeUnsafe 解析 Session，可能存在 API 版本差异");
                return (Session) unsafeObject;
            }
            
            // 方法3: 从原始 JSON 中获取 session ID，然后主动查询
            String sessionId = event.getData().getRawJsonObject()
                    .getAsJsonObject("object")
                    .get("id")
                    .getAsString();
            
            log.info("通过 Session ID 主动查询: {}", sessionId);
            return Session.retrieve(sessionId);
            
        } catch (Exception e) {
            log.error("提取 Session 对象失败, eventId={}", event.getId(), e);
            return null;
        }
    }
}
