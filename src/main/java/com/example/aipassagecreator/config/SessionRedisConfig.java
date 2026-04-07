package com.example.aipassagecreator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Spring Session Redis 序列化配置
 * 使用 JSON 序列化替代 JDK 序列化,避免类路径问题
 */
@Configuration
public class SessionRedisConfig {

    /**
     * 配置 Redis Session 的序列化器为 JSON 格式
     * 添加 JavaTimeModule 以支持 LocalDateTime 等 Java 8 日期时间类型
     */
    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        // 创建 ObjectMapper 并注册 JavaTimeModule
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        // 返回配置了 ObjectMapper 的序列化器
        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }
}
