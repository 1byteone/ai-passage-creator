package com.example.aipassagecreator.service;

import com.example.aipassagecreator.config.CircuitBreakerConfig;
import com.example.aipassagecreator.enums.ImageMethodEnum;
import com.example.aipassagecreator.model.dto.image.ImageData;
import com.example.aipassagecreator.model.dto.image.ImageRequest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 图片服务策略选择器
 * 根据图片来源类型选择对应的图片服务实现
 * 
 * 设计说明：
 * - 自动注册所有 ImageSearchService 实现
 * - 根据 ImageMethodEnum 的元数据自动选择正确的参数
 * - 支持服务可用性检查和自动降级
 * - 统一处理图片上传到 COS
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Service
@Slf4j
public class ImageServiceStrategy {

    @Resource
    private List<ImageSearchService> imageSearchServices;

    @Resource
    private CosService cosService;

    @Resource
    private CircuitBreakerConfig breaker;

    /**
     * 图片服务映射：ImageMethodEnum -> ImageSearchService
     */
    private final Map<ImageMethodEnum, ImageSearchService> serviceMap = new EnumMap<>(ImageMethodEnum.class);

    @PostConstruct
    public void init() {
        // 将所有 ImageSearchService 实现注册到映射表
        for (ImageSearchService service : imageSearchServices) {
            ImageMethodEnum method = service.getMethod();
            serviceMap.put(method, service);
            log.info("注册图片服务: {} -> {} (AI生图: {}, 降级: {})", 
                    method.getValue(), 
                    service.getClass().getSimpleName(),
                    method.isAiGenerated(),
                    method.isFallback());
        }
    }

    /**
     * 获取图片并上传到 COS（推荐方法）
     * 统一处理所有图片来源的上传逻辑。
     * <p>AI 生图方式（isAiGenerated）在首选服务失败后会按 [NANO_BANANA, AGNES] 链依次降级，
     * 全部失败才走 Picsum 兜底；非 AI 方式保持单次尝试 + Picsum 降级。</p>
     *
     * @param imageSource 图片来源
     * @param request     图片请求对象
     * @return 图片获取结果（包含 COS URL）
     */
    public ImageResult getImageAndUpload(String imageSource, ImageRequest request) {
        ImageMethodEnum method = resolveMethod(imageSource);

        // AI 生图：NANO_BANANA → AGNES → Picsum 三级降级
        if (method.isAiGenerated()) {
            ImageResult result = tryAiChain(method, request);
            if (result != null) {
                return result;
            }
            return handleFallbackWithUpload(request.getPosition());
        }

        ImageSearchService service = serviceMap.get(method);

        if (service == null || !service.isAvailable()) {
            log.warn("图片服务不可用: {}, 尝试降级", method);
            return handleFallbackWithUpload(request.getPosition());
        }

        // 图片熔断：获取/上传任一环节失败则计数，连续失败后 fail-fast 直走降级
        return breaker.execute("image",
                () -> doFetch(service, method, request),
                () -> handleFallbackWithUpload(request.getPosition()));
    }

    /**
     * AI 生图降级链：按 NANO_BANANA → AGNES 顺序尝试，各经熔断器。
     * 任一成功即返回，全部失败返回 null。
     */
    private ImageResult tryAiChain(ImageMethodEnum preferred, ImageRequest request) {
        ImageMethodEnum[] chain = {ImageMethodEnum.NANO_BANANA, ImageMethodEnum.AGNES};
        for (ImageMethodEnum candidate : chain) {
            ImageSearchService service = serviceMap.get(candidate);
            if (service == null || !service.isAvailable()) {
                log.debug("AI 降级链跳过不可用服务: {}", candidate);
                continue;
            }
            try {
                return breaker.execute("image",
                        () -> doFetch(service, candidate, request),
                        () -> null);
            } catch (Exception e) {
                log.warn("AI 降级链 {} 失败: {}", candidate, e.getMessage());
            }
        }
        return null;
    }

    /**
     * 获取图片数据并上传 COS。任一步骤失败均抛异常，
     * 由熔断器统一计数并触发降级。
     */
    private ImageResult doFetch(ImageSearchService service, ImageMethodEnum method, ImageRequest request) throws Exception {
        // 1. 获取图片数据
        ImageData imageData = service.getImageData(request);

        if (imageData == null || !imageData.isValid()) {
            log.warn("图片数据获取失败, 使用降级方案, method={}", method);
            throw new IllegalStateException("图片数据获取失败: " + method);
        }

        // 2. 上传到 COS
        String folder = getFolderForMethod(method);
        String cosUrl = cosService.uploadImageData(imageData, folder);

        if (cosUrl == null || cosUrl.isEmpty()) {
            log.warn("图片上传 COS 失败, 使用降级方案, method={}", method);
            throw new IllegalStateException("图片上传 COS 失败: " + method);
        }

        log.info("图片获取并上传成功, method={}, cosUrl={}", method, cosUrl);
        return new ImageResult(cosUrl, method);
    }

    /**
     * 根据图片方法获取 COS 文件夹
     */
    private String getFolderForMethod(ImageMethodEnum method) {
        return switch (method) {
            case PEXELS -> "pexels";
            case NANO_BANANA -> "nano-banana";
            case AGNES -> "agnes";
            case MERMAID -> "mermaid";
            case ICONIFY -> "iconify";
            case EMOJI_PACK -> "emoji-pack";
            case SVG_DIAGRAM -> "svg-diagram";
            case PICSUM -> "picsum";
        };
    }

    /**
     * 解析图片来源，处理未知值
     */
    private ImageMethodEnum resolveMethod(String imageSource) {
        ImageMethodEnum method = ImageMethodEnum.getByValue(imageSource);
        if (method == null) {
            log.warn("未知的图片来源: {}, 默认使用 {}", imageSource, ImageMethodEnum.getDefaultSearchMethod());
            return ImageMethodEnum.getDefaultSearchMethod();
        }
        return method;
    }

    /**
     * 处理降级逻辑（含上传）
     */
    private ImageResult handleFallbackWithUpload(Integer position) {
        int pos = position != null ? position : 1;
        String fallbackUrl = getFallbackImage(pos);
        
        // 将降级图片也上传到 COS
        ImageData fallbackData = ImageData.fromUrl(fallbackUrl);
        String cosUrl = cosService.uploadImageData(fallbackData, "fallback");
        
        // 如果上传失败，直接使用原始 URL
        String finalUrl = (cosUrl != null && !cosUrl.isEmpty()) ? cosUrl : fallbackUrl;
        return new ImageResult(finalUrl, ImageMethodEnum.getFallbackMethod());
    }

    /**
     * 获取指定方法的图片服务
     */
    public ImageSearchService getService(ImageMethodEnum method) {
        return serviceMap.get(method);
    }

    /**
     * 获取降级图片
     */
    public String getFallbackImage(int position) {
        ImageSearchService defaultService = serviceMap.get(ImageMethodEnum.getDefaultSearchMethod());
        if (defaultService != null) {
            return defaultService.getFallbackImage(position);
        }
        return String.format("https://picsum.photos/800/600?random=%d", position);
    }

    /**
     * 获取所有已注册的图片服务类型
     */
    public List<ImageMethodEnum> getRegisteredMethods() {
        return List.copyOf(serviceMap.keySet());
    }

    /**
     * 图片获取结果
     */
    public static class ImageResult {
        private final String url;
        private final ImageMethodEnum method;

        public ImageResult(String url, ImageMethodEnum method) {
            this.url = url;
            this.method = method;
        }

        public String getUrl() {
            return url;
        }

        public ImageMethodEnum getMethod() {
            return method;
        }

        public boolean isSuccess() {
            return url != null && !url.isEmpty();
        }
    }
}
