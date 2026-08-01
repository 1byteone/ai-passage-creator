package com.example.aipassagecreator.controller;

import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;

/**
 * 国际化测试/配置 API
 */
@Slf4j
@RestController
@RequestMapping("/i18n")
@Tag(name = "I18nController", description = "国际化")
public class I18nController {

    @Resource
    private MessageSource messageSource;

    /**
     * 获取本地化消息（通过 Accept-Language 协商）
     */
    @GetMapping("/message")
    @Operation(summary = "获取本地化消息")
    public BaseResponse<String> getMessage(@RequestParam(defaultValue = "error.params") String key) {
        Locale locale = LocaleContextHolder.getLocale();
        return ResultUtils.success(messageSource.getMessage(key, null, locale));
    }

    /**
     * 当前协商语言
     */
    @GetMapping("/locale")
    @Operation(summary = "当前语言")
    public BaseResponse<Map<String, String>> getLocale() {
        Locale locale = LocaleContextHolder.getLocale();
        return ResultUtils.success(Map.of(
                "language", locale.getLanguage(),
                "displayName", locale.getDisplayName(),
                "country", locale.getCountry()));
    }
}
