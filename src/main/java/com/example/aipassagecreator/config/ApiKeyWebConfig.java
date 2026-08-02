package com.example.aipassagecreator.config;

import com.example.aipassagecreator.aop.ApiKeyInterceptor;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 注册 API Key 认证拦截器 — 全路径生效；无 Key 头的请求快速放行
 */
@Configuration
public class ApiKeyWebConfig implements WebMvcConfigurer {

    @Resource
    private ApiKeyInterceptor apiKeyInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiKeyInterceptor).addPathPatterns("/**");
    }
}
