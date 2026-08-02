package com.example.aipassagecreator.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.example.aipassagecreator.aop.AuthCheck;
import com.example.aipassagecreator.common.BaseResponse;
import com.example.aipassagecreator.common.DeleteRequest;
import com.example.aipassagecreator.common.ResultUtils;
import com.example.aipassagecreator.constant.UserConstant;
import com.example.aipassagecreator.model.dto.user.UserQueryRequest;
import com.example.aipassagecreator.model.dto.user.UserUpdateRequest;
import com.example.aipassagecreator.model.dto.user.UserAddRequest;
import com.example.aipassagecreator.model.dto.user.UserLoginRequest;
import com.example.aipassagecreator.model.dto.user.UserRegisterRequest;
import com.example.aipassagecreator.model.po.User;

import com.example.aipassagecreator.model.vo.LoginUserVO;
import com.example.aipassagecreator.model.vo.UserVO;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.exception.ThrowUtils;
import com.example.aipassagecreator.service.UserService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/user")
public class UserController {

    private static final int MAX_MANAGE_PAGE_SIZE = 50;

    private static final Set<String> MANAGEABLE_ROLES = Set.of(
            UserConstant.DEFAULT_ROLE,
            UserConstant.VIP_ROLE,
            UserConstant.ADMIN_ROLE);

    @Resource
    private UserService userService;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@Valid @RequestBody UserRegisterRequest userRegisterRequest) {
        ThrowUtils.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        long result = userService.userRegister(userAccount, userPassword, checkPassword);
        return ResultUtils.success(result);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@Valid @RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(loginUserVO);
    }

    /**
     * 获取当前登录用户
     */
    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        LoginUserVO loginUser = userService.getLoginUserVO(request);
        return ResultUtils.success(loginUser);
    }

    /**
     * 用户注销
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    /**
     * 创建用户（管理员）
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@Valid @RequestBody UserAddRequest userAddRequest) {
        // 实现略，详见源码
        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR);
        User user = new User();
        BeanUtil.copyProperties(userAddRequest, user);
        //默认密码12345678
        final String defaultPassword = "12345678";
        user.setUserPassword(userService.getEncryptPassword(defaultPassword));
        boolean saved = userService.save(user);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(user.getId());
    }

    /**
     * 分页查询用户（管理员）。
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVoByPage(
            @Valid @RequestBody UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        int current = userQueryRequest.getCurrent();
        int pageSize = userQueryRequest.getPageSize();
        ThrowUtils.throwIf(
                current < 1 || pageSize < 1 || pageSize > MAX_MANAGE_PAGE_SIZE,
                ErrorCode.PARAMS_ERROR,
                "分页参数不合法");

        String userRole = StrUtil.trim(userQueryRequest.getUserRole());
        ThrowUtils.throwIf(
                StrUtil.isNotBlank(userRole) && !MANAGEABLE_ROLES.contains(userRole),
                ErrorCode.PARAMS_ERROR,
                "用户角色不合法");

        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq(User::getIsDelete, 0)
                .orderBy(User::getCreateTime, false);
        if (StrUtil.isNotBlank(userQueryRequest.getUserAccount())) {
            queryWrapper.like(
                    User::getUserAccount,
                    StrUtil.trim(userQueryRequest.getUserAccount()));
        }
        if (StrUtil.isNotBlank(userQueryRequest.getUserName())) {
            queryWrapper.like(
                    User::getUserName,
                    StrUtil.trim(userQueryRequest.getUserName()));
        }
        if (StrUtil.isNotBlank(userRole)) {
            queryWrapper.eq(User::getUserRole, userRole);
        }

        Page<User> userPage = userService.page(new Page<>(current, pageSize), queryWrapper);
        Page<UserVO> userVOPage = new Page<>();
        userVOPage.setPageNumber(userPage.getPageNumber());
        userVOPage.setPageSize(userPage.getPageSize());
        userVOPage.setTotalRow(userPage.getTotalRow());
        List<UserVO> userVOList = userPage.getRecords().stream()
                .map(UserVO::objToVo)
                .toList();
        userVOPage.setRecords(userVOList);

        return ResultUtils.success(userVOPage);
    }

    /**
     * 更新用户资料与角色（管理员）。
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(
            @RequestBody UserUpdateRequest userUpdateRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(
                userUpdateRequest == null || userUpdateRequest.getId() == null,
                ErrorCode.PARAMS_ERROR);

        User targetUser = userService.getById(userUpdateRequest.getId());
        ThrowUtils.throwIf(targetUser == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");

        String nextRole = StrUtil.trim(userUpdateRequest.getUserRole());
        ThrowUtils.throwIf(
                StrUtil.isNotBlank(nextRole) && !MANAGEABLE_ROLES.contains(nextRole),
                ErrorCode.PARAMS_ERROR,
                "用户角色不合法");

        User loginUser = userService.getLoginUser(request);
        boolean changesOwnRole = targetUser.getId().equals(loginUser.getId())
                && StrUtil.isNotBlank(nextRole)
                && !UserConstant.ADMIN_ROLE.equals(nextRole);
        ThrowUtils.throwIf(
                changesOwnRole,
                ErrorCode.FORBIDDEN_ERROR,
                "不能移除自己的管理员权限");

        User updateUser = new User();
        BeanUtil.copyProperties(userUpdateRequest, updateUser);
        updateUser.setUserRole(StrUtil.isBlank(nextRole) ? null : nextRole);
        boolean updated = userService.updateById(updateUser);
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "用户更新失败");
        return ResultUtils.success(true);
    }

    /**
     * 删除用户（管理员）
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(
            @RequestBody DeleteRequest deleteRequest,
            HttpServletRequest request) {
        // 实现略，详见源码
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(
                deleteRequest.getId().equals(loginUser.getId()),
                ErrorCode.FORBIDDEN_ERROR,
                "不能删除当前登录账号");
        ThrowUtils.throwIf(
                userService.getById(deleteRequest.getId()) == null,
                ErrorCode.NOT_FOUND_ERROR,
                "用户不存在");
        boolean deleted = userService.removeById(deleteRequest.getId());
        ThrowUtils.throwIf(!deleted, ErrorCode.OPERATION_ERROR, "用户删除失败");
        return ResultUtils.success(true);
    }
}
