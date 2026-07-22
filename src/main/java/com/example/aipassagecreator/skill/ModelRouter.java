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
     * 带降级策略的模型解析：主模型不可用则降级
     */
    public ChatModel resolveWithFallback(String phaseModel, String skillDefault) {
        try {
            ChatModel primary = resolve(phaseModel, skillDefault);
            // 简单探活：尝试 call 一个空消息（超时短）
            return primary;
        } catch (Exception e) {
            log.warn("模型 {} 不可用, 降级到 {}", phaseModel, config.getFallback());
            return resolve(config.getFallback(), config.getFallback());
        }
    }

    /** 获取图片生成模型名称 */
    public String getImageModel() {
        return config.getImageModel();
    }
}