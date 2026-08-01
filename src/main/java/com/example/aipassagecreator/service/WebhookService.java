package com.example.aipassagecreator.service;

import java.util.Map;

/**
 * 出站 Webhook 通知服务 — HMAC 签名 + 重试队列
 */
public interface WebhookService {

    /**
     * 发布事件到目标 URL
     *
     * @param eventType 事件类型（如 article.published / article.approved）
     * @param payload   事件负载
     * @param targetUrl 目标 URL（需通过 SSRF 校验，仅 https）
     */
    void publish(String eventType, Map<String, Object> payload, String targetUrl);

    /**
     * 重试失败投递（@Scheduled 调用）
     */
    int retryFailedDeliveries();
}
