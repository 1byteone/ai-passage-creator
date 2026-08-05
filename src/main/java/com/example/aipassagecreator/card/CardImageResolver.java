package com.example.aipassagecreator.card;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * 卡片图片解析器 — 将配图 URL 下载并转为 base64 data URL。
 *
 * <p>标准卡片管线（{@link CardRenderPipeline}）禁用了 JS 并拦截所有外部网络请求，
 * 远程 COS 图片无法直接渲染。因此渲染前由后端下载图片并内联为 base64，
 * 使卡片完全离线渲染。下载失败返回 null，由模板降级为无图，不阻断整卡。</p>
 */
@Slf4j
@Component
public class CardImageResolver {

    /** 图片大小上限 2MB（与卡片 PNG 上限一致，避免超大图拖慢渲染） */
    private static final long MAX_IMAGE_BYTES = 2 * 1024 * 1024;

    private final OkHttpClient httpClient = new OkHttpClient();

    /**
     * 下载远程图片并转为 base64 data URL（优先取响应 Content-Type）。
     * 失败/超限返回 null。
     */
    public String toDataUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        // 已是 data URL 直接返回
        if (imageUrl.startsWith("data:")) {
            return imageUrl;
        }
        // classpath 静态素材（illustration 熔断兜底）→ 读 classpath 字节转 base64
        if (imageUrl.startsWith("classpath:")) {
            return loadClasspath(imageUrl);
        }
        try {
            Request request = new Request.Builder().url(imageUrl).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("卡片配图下载失败: url={}, code={}",
                            truncate(imageUrl), response.code());
                    return null;
                }
                byte[] body = response.body() == null ? new byte[0] : response.body().bytes();
                if (body.length == 0 || body.length > MAX_IMAGE_BYTES) {
                    log.warn("卡片配图大小不合法: url={}, bytes={}", truncate(imageUrl), body.length);
                    return null;
                }
                String contentType = response.header("Content-Type", "image/jpeg");
                if (!contentType.startsWith("image/")) {
                    contentType = "image/jpeg";
                }
                String b64 = Base64.getEncoder().encodeToString(body);
                return "data:" + contentType + ";base64," + b64;
            }
        } catch (Exception e) {
            log.warn("卡片配图下载异常: url={}, err={}", truncate(imageUrl), e.getMessage());
            return null;
        }
    }

    /** 截断长 URL 用于日志 */
    private static String truncate(String s) {
        return s != null && s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }

    /**
     * 读取 classpath 静态素材并转为 base64 data URL。
     * 同 HTTP 分支一致：超限/不存在/异常返回 null，由模板降级为无图。
     */
    private String loadClasspath(String imageUrl) {
        try {
            String path = imageUrl.substring("classpath:".length());
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                log.warn("卡片配图 classpath 资源不存在: path={}", path);
                return null;
            }
            byte[] body = resource.getInputStream().readAllBytes();
            if (body.length == 0 || body.length > MAX_IMAGE_BYTES) {
                log.warn("卡片配图大小不合法: path={}, bytes={}", path, body.length);
                return null;
            }
            String b64 = Base64.getEncoder().encodeToString(body);
            return "data:image/png;base64," + b64;
        } catch (Exception e) {
            log.warn("卡片配图 classpath 读取异常: url={}, err={}", truncate(imageUrl), e.getMessage());
            return null;
        }
    }
}
