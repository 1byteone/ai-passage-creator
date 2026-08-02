package com.example.aipassagecreator.aop;

import cn.hutool.core.util.StrUtil;
import com.example.aipassagecreator.constant.ApiKeyConstant;
import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.service.ApiKeyService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API Key 认证拦截器 — 校验 Authorization: Bearer / X-Api-Key，把归属用户装入 request attribute。
 * 无 API Key 头的请求放行，交给 session 认证；无效 Key 是显式错误，不静默回退。
 */
@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    @Resource
    private ApiKeyService apiKeyService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = resolveToken(request);
        if (StrUtil.isBlank(token)) {
            return true;
        }
        User user = apiKeyService.validateAndGetUser(token);
        if (user == null) {
            throw new BusinessException(ErrorCode.API_KEY_INVALID_ERROR);
        }
        request.setAttribute(ApiKeyConstant.REQUEST_USER_ATTR, user);
        return true;
    }

    /** 解析 API Key：优先 Authorization: Bearer，其次 X-Api-Key */
    private String resolveToken(HttpServletRequest request) {
        String auth = request.getHeader(ApiKeyConstant.HEADER_AUTHORIZATION);
        if (StrUtil.isNotBlank(auth) && auth.startsWith(ApiKeyConstant.BEARER_PREFIX)) {
            return auth.substring(ApiKeyConstant.BEARER_PREFIX.length()).trim();
        }
        String x = request.getHeader(ApiKeyConstant.HEADER_X_API_KEY);
        return StrUtil.isBlank(x) ? null : x.trim();
    }
}
