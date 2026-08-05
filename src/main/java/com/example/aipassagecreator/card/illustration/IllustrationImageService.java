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
            log.error("插画人物 AI 生图异常，熔断静态素材: style={}, err={}",
                    style.getName(), e.getMessage());
        }
        return staticLibrary.getUrl(style);
    }
}
