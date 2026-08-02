package com.example.aipassagecreator.methodology;

import com.example.aipassagecreator.config.YamlResourceLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * MethodologyRegistry 初始化失败语义单元测试（A1-3）。
 *
 * <p>方法论 yaml 是仓库内受控配置：解析/校验错误应在启动即 fail-fast，
 * 而非静默降级后运行时 get() 报"方法论不存在"。无配置文件时保留 default 兜底。</p>
 */
@ExtendWith(MockitoExtension.class)
class MethodologyRegistryInitTest {

    @Mock
    private YamlResourceLoader loader;

    @Test
    @DisplayName("yaml 解析异常 — 启动即抛 IllegalStateException，不做静默降级")
    void init_parseError_failsFast() {
        when(loader.loadAll(anyString(), any()))
                .thenThrow(new RuntimeException("SnakeYAML 无法解析 methodology/broken.yaml"));

        MethodologyRegistry registry = new MethodologyRegistry(loader);

        assertThrows(IllegalStateException.class, registry::init);
    }

    @Test
    @DisplayName("维度权重非法 — validate 异常同样 fail-fast")
    void init_invalidWeight_failsFast() {
        MethodologyDefinition bad = new MethodologyDefinition();
        bad.setName("bad");
        MethodologyDefinition.EvaluationDimension ed = new MethodologyDefinition.EvaluationDimension();
        ed.setKey("k");
        ed.setName("维度");
        ed.setWeight(0);
        bad.setEvaluationDimensions(List.of(ed));
        when(loader.loadAll(anyString(), any())).thenReturn(List.of(bad));

        MethodologyRegistry registry = new MethodologyRegistry(loader);

        assertThrows(IllegalStateException.class, registry::init);
    }

    @Test
    @DisplayName("无任何配置文件 — 保留内置 default 兜底，不 fail-fast")
    void init_noConfigs_usesBuiltinDefault() {
        when(loader.loadAll(anyString(), any())).thenReturn(List.of());

        MethodologyRegistry registry = new MethodologyRegistry(loader);
        registry.init();

        assertEquals(List.of("default"), registry.getNames());
        assertNotNull(registry.getDefault());
    }
}
