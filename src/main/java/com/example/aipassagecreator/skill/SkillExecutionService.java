package com.example.aipassagecreator.skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Skill 异步执行服务
 * 作为 Spring Bean，确保 @Async 注解生效
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillExecutionService {

    private final SkillRegistry skillRegistry;

    @Async("skillExecutor")
    public void executeAsync(String skillName, Map<String, Object> inputs,
                             Consumer<String> streamHandler, Long userId) {
        SkillExecution execution = skillRegistry.createExecution(skillName, inputs);
        execution.execute(streamHandler, userId);
    }
}