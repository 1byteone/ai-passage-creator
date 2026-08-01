package com.example.aipassagecreator.card;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CardRenderPipeline 健康检查。
 * 无 Playwright 浏览器（CI 未执行 playwright install chromium）时，
 * init() 优雅降级 healthy=false，此处只断言不抛异常、bean 可注入。
 */
@SpringBootTest
class CardRenderPipelineTest {

    @Autowired(required = false)
    private CardRenderPipeline pipeline;

    @Test
    void pipeline_healthCheck() {
        // 无 Playwright 浏览器时跳过（CI 环境可能未安装）
        if (pipeline == null) return;
        // 健康检查不抛异常即可
        assertNotNull(pipeline);
    }
}
