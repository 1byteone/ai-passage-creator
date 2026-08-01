package com.example.aipassagecreator.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;

/**
 * Gson 工具类
 * 提供统一的 Gson 实例，避免重复创建
 *
 * @author <a href="https://codefather.cn">编程导航学习圈</a>
 */
@Slf4j
public class GsonUtils {

    /**
     * 单例 Gson 实例
     */
    private static final Gson GSON = new GsonBuilder()
            .create();

    private GsonUtils() {
        // 私有构造函数，防止实例化
    }

    /**
     * 获取 Gson 实例
     *
     * @return Gson 实例
     */
    public static Gson getInstance() {
        return GSON;
    }

    /**
     * 对象转 JSON 字符串
     *
     * @param obj 对象
     * @return JSON 字符串
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        return GSON.toJson(obj);
    }

    /**
     * JSON 字符串转对象
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型
     * @param <T>   泛型类型
     * @return 对象实例
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return GSON.fromJson(json, clazz);
    }

    /**
     * JSON 字符串转对象（支持泛型）
     *
     * @param json      JSON 字符串
     * @param typeToken TypeToken 类型引用
     * @param <T>       泛型类型
     * @return 对象实例
     */
    public static <T> T fromJson(String json, TypeToken<T> typeToken) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return GSON.fromJson(json, typeToken.getType());
    }

    /**
     * JSON 字符串转对象（支持 Type）
     *
     * @param json JSON 字符串
     * @param type Type 类型
     * @param <T>  泛型类型
     * @return 对象实例
     */
    public static <T> T fromJson(String json, Type type) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        return GSON.fromJson(json, type);
    }

    /**
     * 安全地将 JSON 字符串转为对象，解析失败时返回 null
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型
     * @param <T>   泛型类型
     * @return 对象实例，解析失败返回 null
     */
    public static <T> T fromJsonSafe(String json, Class<T> clazz) {
        try {
            return fromJson(json, clazz);
        } catch (JsonSyntaxException e) {
            log.error("JSON 解析失败, json={}", json, e);
            return null;
        }
    }

    /**
     * 安全地将 JSON 字符串转为对象（支持泛型），解析失败时返回 null
     *
     * @param json      JSON 字符串
     * @param typeToken TypeToken 类型引用
     * @param <T>       泛型类型
     * @return 对象实例，解析失败返回 null
     */
    public static <T> T fromJsonSafe(String json, TypeToken<T> typeToken) {
        try {
            return fromJson(json, typeToken);
        } catch (JsonSyntaxException e) {
            log.error("JSON 解析失败, json={}", json, e);
            return null;
        }
    }

    /**
     * 修复 LLM 输出的非法 JSON。
     * <p>处理三种情况：①剥离 ```json 围栏及前后说明文本；②补全截断的括号；③空输入返回空对象。
     *
     * @param json 原始 LLM 输出
     * @return 修复后的 JSON 字符串（无法修复时原样返回）
     */
    public static String tryFixJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return "{}";
        }
        String s = json.trim();
        // 1) 剥离代码围栏与前后非 JSON 文本：取第一个 { 到最后一个 } 或 ] 之间的内容
        int firstOpen = Math.min(indexOf(s, '{'), indexOf(s, '['));
        if (firstOpen == Integer.MAX_VALUE) {
            return s;
        }
        int lastClose = Math.max(s.lastIndexOf('}'), s.lastIndexOf(']'));
        if (lastClose > firstOpen) {
            s = s.substring(firstOpen, lastClose + 1);
        }
        // 2) 补全截断的括号（遍历统计 + 引号配对）
        int braces = 0, brackets = 0;
        boolean inString = false;
        char prev = 0;
        for (char c : s.toCharArray()) {
            if (inString) {
                if (c == '"' && prev != '\\') {
                    inString = false;
                }
            } else {
                switch (c) {
                    case '"' -> inString = true;
                    case '{' -> braces++;
                    case '}' -> braces--;
                    case '[' -> brackets++;
                    case ']' -> brackets--;
                    default -> { /* ignore */ }
                }
            }
            prev = c;
        }
        if (inString) {
            s = s + '"';
        }
        while (brackets > 0) { s = s + "]"; brackets--; }
        while (braces > 0) { s = s + "}"; braces--; }
        return s;
    }

    private static int indexOf(String s, char c) {
        int idx = s.indexOf(c);
        return idx < 0 ? Integer.MAX_VALUE : idx;
    }
}
