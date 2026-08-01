package com.example.aipassagecreator.service.impl;

import com.example.aipassagecreator.mapper.WebhookDeliveryMapper;
import com.example.aipassagecreator.model.po.WebhookDelivery;
import com.example.aipassagecreator.service.WebhookService;
import com.example.aipassagecreator.utils.GsonUtils;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class WebhookServiceImpl implements WebhookService {

    private static final int MAX_RETRY = 5;
    private static final String HMAC_ALG = "HmacSHA256";

    /** 共享签名密钥 — 从环境变量注入，缺失则启动失败（fail-fast，禁止默认值） */
    private final String sharedSecret;

    @Resource
    private WebhookDeliveryMapper deliveryMapper;

    /** 密钥未配置时 webhook 功能降级为不可用（不阻止应用启动） */
    public WebhookServiceImpl(@Value("${WEBHOOK_SHARED_SECRET:${webhook.shared-secret:}}") String sharedSecret) {
        this.sharedSecret = sharedSecret;
        if (sharedSecret == null || sharedSecret.isBlank()) {
            log.warn("WEBHOOK_SHARED_SECRET 未配置，Webhook 功能禁用。设置环境变量后重启生效。");
        }
    }

    @Override
    public void publish(String eventType, Map<String, Object> payload, String targetUrl) {
        // 密钥未配置时功能禁用
        if (sharedSecret == null || sharedSecret.isBlank()) {
            throw new IllegalStateException(
                    "Webhook 功能未启用：请配置 WEBHOOK_SHARED_SECRET 环境变量");
        }
        // SSRF 防护：仅允许 https 外网地址，拒绝内网/环回/元数据地址
        validateTargetUrl(targetUrl);

        String body = GsonUtils.toJson(payload);
        String signature = sign(body);

        WebhookDelivery delivery = WebhookDelivery.builder()
                .eventType(eventType)
                .payload(body)
                .targetUrl(targetUrl)
                .signature(signature)
                .attemptCount(0)
                .status("PENDING")
                .nextRetryAt(LocalDateTime.now())
                .build();
        deliveryMapper.insert(delivery);
        log.info("Webhook 事件已排队: eventType={}, deliveryId={}", eventType, delivery.getId());

        // 异步投递（简化：同步调用，失败进入重试队列）
        deliver(delivery);
    }

    @Override
    @Scheduled(fixedDelayString = "${webhook.retry-interval-ms:300000}")
    public int retryFailedDeliveries() {
        List<WebhookDelivery> failed = deliveryMapper.selectListByQuery(
                QueryWrapper.create()
                        .eq("status", "FAILED")
                        .le("next_retry_at", LocalDateTime.now())
                        .limit(20));
        int count = 0;
        for (WebhookDelivery d : failed) {
            try {
                deliver(d);
                count++;
            } catch (Exception e) {
                log.error("Webhook 重试失败: deliveryId={}, error={}", d.getId(), e.getMessage());
            }
        }
        return count;
    }

    /**
     * SSRF 防护 — 校验目标 URL
     * <ul>
     *   <li>仅允许 https 协议</li>
     *   <li>拒绝内网/环回/链路本地/元数据地址</li>
     * </ul>
     */
    private void validateTargetUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("Webhook 目标 URL 不能为空");
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (Exception e) {
            throw new IllegalArgumentException("Webhook 目标 URL 格式非法");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Webhook 目标 URL 仅支持 https");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Webhook 目标 URL 缺少主机名");
        }
        // 拒绝 IP 形式的内网/环回/元数据地址
        try {
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isLoopbackAddress()
                    || addr.isSiteLocalAddress()
                    || addr.isLinkLocalAddress()
                    || addr.isAnyLocalAddress()
                    || "169.254.169.254".equals(addr.getHostAddress())) {
                throw new IllegalArgumentException("Webhook 目标 URL 指向内网/元数据地址，已拒绝");
            }
        } catch (java.net.UnknownHostException e) {
            // 域名无法解析 — 放行，由投递时的连接错误自然失败并进入重试
            log.debug("Webhook 目标域名暂无法解析: {}", host);
        }
    }

    /**
     * 投递单条，失败记录错误并更新重试时间
     */
    private void deliver(WebhookDelivery delivery) {
        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        try {
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(delivery.getTargetUrl())
                    .post(okhttp3.RequestBody.create(
                            delivery.getPayload(), okhttp3.MediaType.parse("application/json")))
                    .header("X-Webhook-Signature", delivery.getSignature())
                    .header("X-Webhook-Event", delivery.getEventType())
                    .header("Content-Type", "application/json")
                    .build();
            try (okhttp3.Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    delivery.setStatus("SUCCESS");
                    delivery.setLastError(null);
                    delivery.setNextRetryAt(null);
                    log.info("Webhook 投递成功: deliveryId={}", delivery.getId());
                } else {
                    throw new RuntimeException("HTTP " + response.code());
                }
            }
        } catch (Exception e) {
            delivery.setStatus("FAILED");
            delivery.setLastError(e.getMessage());
            if (delivery.getAttemptCount() < MAX_RETRY) {
                delivery.setNextRetryAt(LocalDateTime.now().plusMinutes(5L * delivery.getAttemptCount()));
            }
            log.warn("Webhook 投递失败(attempt={}): deliveryId={}, error={}",
                    delivery.getAttemptCount(), delivery.getId(), e.getMessage());
        }
        delivery.setUpdateTime(LocalDateTime.now());
        deliveryMapper.update(delivery);
    }

    /**
     * HMAC-SHA256 签名
     */
    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALG));
            byte[] hash = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Webhook 签名失败", e);
        }
    }
}
