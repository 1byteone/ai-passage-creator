package com.example.aipassagecreator.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 共享的 YAML 资源加载器。
 * <p>统一使用 SafeConstructor + LoaderOptions 限制，防止 billion-laughs / 递归炸弹 DoS；
 * 单文件限制 100KB，超限跳过并告警。</p>
 * <p>实现说明：SafeConstructor 出于安全拒绝为任意用户类型实例化（loadAs(Class) 会抛
 * ConstructorException），因此先安全解析为通用 Map/List 结构，再交由 Jackson
 * ObjectMapper.convertValue 绑定到目标 POJO。两类 DoS 防护（LoaderOptions 上限 + 文件大小）
 * 均不受影响。</p>
 */
@Slf4j
@Component
public class YamlResourceLoader {

    /** 单文件最大字节数：100KB */
    private static final long MAX_FILE_SIZE = 100 * 1024L;

    private final ResourcePatternResolver resolver;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public YamlResourceLoader(ResourceLoader resourceLoader) {
        this.resolver = ResourcePatternUtils.getResourcePatternResolver(resourceLoader);
    }

    /**
     * 扫描并加载所有匹配的 YAML 资源
     *
     * @param pattern classpath 通配符（如 classpath*:methodology/*.yaml）
     * @param clazz   目标类型
     * @param <T>     泛型类型
     * @return 解析结果列表
     */
    public <T> List<T> loadAll(String pattern, Class<T> clazz) {
        List<T> result = new ArrayList<>();
        try {
            Resource[] resources = resolver.getResources(pattern);
            for (Resource resource : resources) {
                if (!resource.exists() || !resource.isReadable()) {
                    continue;
                }
                long size = resource.contentLength();
                if (size > MAX_FILE_SIZE) {
                    log.warn("YAML 文件超过大小限制 {}KB, 跳过: {}", MAX_FILE_SIZE / 1024, resource.getFilename());
                    continue;
                }
                try {
                    LoaderOptions options = new LoaderOptions();
                    options.setMaxAliasesForCollections(50);
                    options.setCodePointLimit(1024 * 1024);
                    options.setNestingDepthLimit(50);
                    Yaml yaml = new Yaml(new SafeConstructor(options));
                    Object parsed = yaml.load(resource.getInputStream());
                    T def = convert(parsed, clazz);
                    if (def != null) {
                        result.add(def);
                    }
                } catch (Exception e) {
                    log.error("YAML 解析失败: {}", resource.getFilename(), e);
                }
            }
        } catch (IOException e) {
            log.error("YAML 资源扫描失败, pattern={}", pattern, e);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private <T> T convert(Object parsed, Class<T> clazz) {
        if (parsed == null) {
            return null;
        }
        if (clazz.isInstance(parsed)) {
            return (T) parsed;
        }
        return objectMapper.convertValue(parsed, clazz);
    }
}
