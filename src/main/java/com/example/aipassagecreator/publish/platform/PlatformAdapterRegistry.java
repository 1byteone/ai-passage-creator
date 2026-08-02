package com.example.aipassagecreator.publish.platform;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 平台适配器注册中心。自动发现所有 PlatformAdapter bean 并建立 platform→adapter 映射。
 */
@Slf4j
@Component
public class PlatformAdapterRegistry {

    private final Map<String, PlatformAdapter> adapters = new ConcurrentHashMap<>();
    private final List<PlatformAdapter> adapterList;

    public PlatformAdapterRegistry(List<PlatformAdapter> adapterList) {
        this.adapterList = adapterList;
    }

    @PostConstruct
    public void init() {
        for (PlatformAdapter adapter : adapterList) {
            adapters.put(adapter.platform(), adapter);
            log.info("注册平台适配器: {}", adapter.platform());
        }
        log.info("PlatformAdapterRegistry 初始化完成，共 {} 个适配器", adapters.size());
    }

    public PlatformAdapter get(String platform) {
        PlatformAdapter adapter = adapters.get(platform);
        if (adapter == null) {
            throw new IllegalArgumentException(
                    "不支持的发布平台: " + platform + "，可用: " + adapters.keySet());
        }
        return adapter;
    }

    public boolean exists(String platform) {
        return platform != null && adapters.containsKey(platform);
    }

    public List<String> platforms() {
        return List.copyOf(adapters.keySet());
    }
}
