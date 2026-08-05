package com.example.aipassagecreator.card.illustration;

import com.example.aipassagecreator.service.AgnesImageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 插画人物生图编排服务 — AI 主力 + 静态熔断。
 * <p>AI 生图成功返回远程 URL（后续由 CardImageResolver base64 内联）；
 * 失败/异常静默回退静态素材，不中断卡片生成（熔断行为）。</p>
 */
@Slf4j
@Service
public class IllustrationImageService {

    private final AgnesImageService agnesImageService;
    private final IllustrationPromptBuilder promptBuilder;
    private final StaticIllustrationLibrary staticLibrary;

    public IllustrationImageService(AgnesImageService agnesImageService,
                                    IllustrationPromptBuilder promptBuilder,
                                    StaticIllustrationLibrary staticLibrary) {
        this.agnesImageService = agnesImageService;
        this.promptBuilder = promptBuilder;
        this.staticLibrary = staticLibrary;
    }

    /**
     * 直接返回静态素材 URL — 无 AI 调用，确定性、秒回。
     * <p>供预览等实时场景使用：预览无需等 AI 生图（最长 180s），直接用静态兜底保证响应速度；
     * 正式生成才走 {@link #generateCoverImage}（AI 主力 → 静态熔断）。</p>
     */
    public String getStaticFallbackUrl(IllustrationCharacterStyle style) {
        return staticLibrary.getUrl(style);
    }

    /**
     * 生成封面插画人物图 URL。AI 失败自动熔断静态素材。
     */
    public String generateCoverImage(String mainTitle, IllustrationCharacterStyle style) {
        String prompt = promptBuilder.build(mainTitle, style);
        try {
            String url = agnesImageService.searchImage(prompt);
            if (url != null && !url.isBlank()) {
                log.info("插画人物 AI 生图成功: style={}, mainTitle={}", style.getName(), mainTitle);
                return url;
            }
            log.warn("插画人物 AI 生图返回空，熔断静态素材: style={}", style.getName());
        } catch (Exception e) {
            // 记录完整异常栈便于排查熔断根因
            log.error("插画人物 AI 生图异常，熔断静态素材: style={}", style.getName(), e);
        }
        return staticLibrary.getUrl(style);
    }
}
