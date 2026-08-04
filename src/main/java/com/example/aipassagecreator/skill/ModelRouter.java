package com.example.aipassagecreator.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModelRouter {

    private final ChatModel agnesChatModel;
    private final ChatModel dashscopeChatModel;
    private final ModelRouterConfig config;

    /**
     * 根据阶段模型名和 skill 默认模型解析出 ChatModel 实例
     * @param phaseModel 阶段指定的模型（可空）
     * @param skillDefault skill 默认模型（可空）
     * @return ChatModel 实例
     */
    public ChatModel resolve(String phaseModel, String skillDefault) {
        String modelName = phaseModel != null ? phaseModel : skillDefault;
        if (modelName == null) {
            modelName = config.getDefaultModel();
        }
        log.debug("ModelRouter 解析模型: phaseModel={}, skillDefault={}, resolved={}",
                phaseModel, skillDefault, modelName);
        return switch (modelName) {
            case "agnes" -> agnesChatModel;
            case "dashscope" -> dashscopeChatModel;
            default -> {
                log.warn("未知模型: {}, 使用默认模型 {}", modelName, config.getDefaultModel());
                yield resolve(config.getDefaultModel(), null);
            }
        };
    }

    /**
     * 解析出实际生效的模型名称（与 {@link #resolve} 的取值逻辑保持一致）
     * <p>
     * 用于日志与用量统计记录真实使用的模型，而非阶段声明值。
     *
     * @param phaseModel   阶段指定的模型（可空）
     * @param skillDefault skill 默认模型（可空）
     * @return 生效的模型名称
     */
    public String resolveModelName(String phaseModel, String skillDefault) {
        String modelName = phaseModel != null ? phaseModel : skillDefault;
        if (modelName == null) {
            modelName = config.getDefaultModel();
        }
        // 未知模型会在 resolve() 中回落到默认模型，此处保持一致
        return switch (modelName) {
            case "agnes", "dashscope" -> modelName;
            default -> config.getDefaultModel();
        };
    }

    /**
     * 带降级策略的模型解析：主模型不可用则降级
     */
    public ChatModel resolveWithFallback(String phaseModel, String skillDefault) {
        try {
            ChatModel primary = resolve(phaseModel, skillDefault);
            // 主模型已由 resolve() 完成解析，无需额外探活
            return primary;
        } catch (Exception e) {
            log.warn("模型 {} 不可用, 降级到 {}", phaseModel, config.getFallback());
            return resolve(config.getFallback(), config.getFallback());
        }
    }

    /**
     * 解析降级模型实例（由配置的 fallback 模型名决定，默认 dashscope）。
     * <p>
     * 与 {@link #resolve} 的返回实例必然不同（agnes ≠ dashscope），
     * 供调用方在运行时 LLM 故障时切换到真正不同的模型重试。
     */
    public ChatModel resolveFallback() {
        return resolve(config.getFallback(), config.getFallback());
    }

    /** 获取图片生成模型名称 */
    public String getImageModel() {
        return config.getImageModel();
    }
}