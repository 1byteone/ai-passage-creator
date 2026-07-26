package com.example.aipassagecreator.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

@Getter
public enum UserRoleEnum {

    USER("用户", "user"),
    VIP("VIP会员", "vip"),
    ADMIN("管理员", "admin");

    private final String text;

    private final String value;

    UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值的value
     * @return 枚举值
     */
    public static UserRoleEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (UserRoleEnum anEnum : UserRoleEnum.values()) {
            if (anEnum.value.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }

    /**
     * 判断用户角色是否满足所需角色
     * <p>
     * 规则（AuthInterceptor 与 SkillController 共用同一语义）：
     * <ul>
     *     <li>admin 拥有所有权限</li>
     *     <li>要求 admin 时，非 admin 一律拒绝</li>
     *     <li>要求 user 时，user 和 vip 都放行（vip 是 user 的超集）</li>
     *     <li>其余情况精确匹配（如 vip 专属能力）</li>
     * </ul>
     *
     * @param userRole     用户实际角色
     * @param requiredRole 接口/能力所需角色
     * @return 是否满足
     */
    public static boolean satisfies(UserRoleEnum userRole, UserRoleEnum requiredRole) {
        if (userRole == null) {
            return false;
        }
        // 未声明所需角色，登录即可
        if (requiredRole == null) {
            return true;
        }
        // 管理员拥有所有权限
        if (ADMIN.equals(userRole)) {
            return true;
        }
        // 要求管理员权限但用户不是管理员
        if (ADMIN.equals(requiredRole)) {
            return false;
        }
        // vip 是 user 的超集，均可访问普通用户能力
        if (USER.equals(requiredRole)) {
            return true;
        }
        // 精确角色匹配
        return requiredRole.equals(userRole);
    }

    /**
     * 基于角色字符串的便捷重载
     *
     * @param userRole     用户实际角色值
     * @param requiredRole 所需角色值
     * @return 是否满足
     */
    public static boolean satisfies(String userRole, String requiredRole) {
        return satisfies(getEnumByValue(userRole), getEnumByValue(requiredRole));
    }
}