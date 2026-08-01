package com.example.aipassagecreator.service;

import java.util.Map;

/**
 * 出站 Webhook 通知服务 — HMAC 签名 + 重试队列
 */
public interface WebhookService {

    /** 签名用的共享密钥（生产从环境变量注入） */
    String SHARED_SECRET = System.getenv().getOrDefault("WEBHOOK_SHARED_SECRET", "dev-webhook-secret");

    /**
     * 发布事件到目标 URL
     *
     * @param eventType 事件类型（如 article.published / article.approved）
     * @param payload   事件负载
     * @param targetUrl 目标 URL
     */
    void publish(String eventType, Map<String, Object> payload, String targetUrl);

    /**
     * 重试失败投递（@Scheduled 调用）
     */
    int retryFailedDeliveries();
}
