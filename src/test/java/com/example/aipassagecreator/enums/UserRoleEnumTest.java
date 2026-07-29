package com.example.aipassagecreator.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 角色权限矩阵测试
 * <p>
 * 锁定 AuthInterceptor 与 SkillController 共用的角色判定语义，
 * 防止两处逻辑再次分叉。
 */
class UserRoleEnumTest {

    @DisplayName("角色权限矩阵：admin 豁免 + vip 为 user 超集 + 其余精确匹配")
    @ParameterizedTest(name = "userRole={0}, requiredRole={1} -> {2}")
    @CsvSource({
            // admin 拥有所有权限
            "admin, user,  true",
            "admin, vip,   true",
            "admin, admin, true",
            // vip 是 user 的超集，但不具备 admin 权限
            "vip,   user,  true",
            "vip,   vip,   true",
            "vip,   admin, false",
            // 普通用户只能访问 user 级能力
            "user,  user,  true",
            "user,  vip,   false",
            "user,  admin, false",
    })
    void satisfiesFollowsRoleMatrix(String userRole, String requiredRole, boolean expected) {
        boolean actual = UserRoleEnum.satisfies(userRole, requiredRole);
        if (expected) {
            assertTrue(actual, userRole + " 应当满足 " + requiredRole);
        } else {
            assertFalse(actual, userRole + " 不应满足 " + requiredRole);
        }
    }

    @Test
    @DisplayName("未声明所需角色时登录即可放行")
    void nullRequiredRoleAllowsAnyLoggedInUser() {
        assertTrue(UserRoleEnum.satisfies("user", null));
        assertTrue(UserRoleEnum.satisfies("vip", null));
        assertTrue(UserRoleEnum.satisfies("admin", null));
    }

    @Test
    @DisplayName("角色缺失或非法时一律拒绝")
    void unknownUserRoleIsRejected() {
        assertFalse(UserRoleEnum.satisfies((String) null, "user"));
        assertFalse(UserRoleEnum.satisfies("", "user"));
        assertFalse(UserRoleEnum.satisfies("guest", "user"));
    }

    @Test
    @DisplayName("回归：admin 不应被 vip 专属 Skill 拒绝")
    void adminIsNotRejectedByVipOnlySkill() {
        // 修复前 SkillController 的判定为 "vip".equals(role) && "vip".equals(userRole)，
        // 导致 admin 无法使用 vip 技能
        assertTrue(UserRoleEnum.satisfies("admin", "vip"));
    }

    @Test
    @DisplayName("回归：requiredRoles 含 user 时不应对未知角色无条件放行")
    void userRequirementDoesNotAllowUnknownRole() {
        // 修复前 SkillController 的判定为 "user".equals(role)，完全忽略用户实际角色，
        // 任何请求都会被放行
        assertFalse(UserRoleEnum.satisfies("guest", "user"));
        assertFalse(UserRoleEnum.satisfies((String) null, "user"));
    }
}
