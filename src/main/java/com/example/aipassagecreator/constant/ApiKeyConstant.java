package com.example.aipassagecreator.constant;

/**
 * API Key 认证常量 — 头部名 / token 前缀 / request 属性键
 */
public final class ApiKeyConstant {

    private ApiKeyConstant() {
    }

    /** Authorization 头 */
    public static final String HEADER_AUTHORIZATION = "Authorization";
    /** Bearer 前缀 */
    public static final String BEARER_PREFIX = "Bearer ";
    /** 兼容的自定义头 */
    public static final String HEADER_X_API_KEY = "X-Api-Key";
    /** token 前缀，用于识别 API Key 请求 */
    public static final String TOKEN_PREFIX = "apc_";
    /** 校验通过后，拦截器把完整 User 放入此 request attribute */
    public static final String REQUEST_USER_ATTR = "apiKeyAuthUser";
}
