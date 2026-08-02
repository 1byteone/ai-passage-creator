package com.example.aipassagecreator.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.example.aipassagecreator.constant.ApiKeyConstant;
import com.example.aipassagecreator.constant.UserConstant;
import com.example.aipassagecreator.enums.UserRoleEnum;
import com.example.aipassagecreator.model.po.User;
import com.example.aipassagecreator.model.vo.LoginUserVO;
import com.example.aipassagecreator.exception.BusinessException;
import com.example.aipassagecreator.exception.ErrorCode;
import com.example.aipassagecreator.mapper.UserMapper;
import com.example.aipassagecreator.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

import static com.example.aipassagecreator.constant.UserConstant.USER_LOGIN_STATE;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        //1。校验参数
        if(StrUtil.hasBlank(userAccount,userPassword,checkPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"参数为空");
        }
        if(userAccount.length() < 4){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户账号过短");
        }
        if(userPassword.length() < 8 || checkPassword.length() < 8){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户密码过短");
        }
        if(!userPassword.equals(checkPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"两次输入的密码不一致");
        }
        //2.校验账户不能重复
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq(User::getUserAccount,userAccount);
        long count = this.mapper.selectCountByQuery(queryWrapper);
        if(count>0){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"账户已存在");
        }
        //3.加密
        String encryptPassword = getEncryptPassword(userPassword);
        //4.创建用户，插入数据库
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("无名小羊");
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean saved = this.save(user);
        if(!saved){
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"注册失败,数据库错误");
        }
        return user.getId();
    }

    /**
     * 获取加密密码
     * @param userPassword
     * @return
     */
    @Override
    public String getEncryptPassword(String userPassword) {
        // 盐值，混淆密码
        final String SALT = "yupi";
        return DigestUtils.md5DigestAsHex((userPassword + SALT).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 用户登录
     * @param userAccount
     * @param userPassword
     * @param request
     * @return
     */
    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        //1.校验参数
        if(StrUtil.hasBlank(userAccount,userPassword)){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"参数为空");
        }
        //2.加密
        String encryptPassword = getEncryptPassword(userPassword);
        //3.查询用户是否存在
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq(User::getUserAccount,userAccount);
        queryWrapper.eq(User::getUserPassword,encryptPassword);
        User user = this.mapper.selectOneByQuery(queryWrapper);
        if(user == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"用户不存在或密码错误");
        }
        //4.创建用户的登录态（只存储用户ID，避免序列化问题）
        request.getSession().setAttribute(USER_LOGIN_STATE, user.getId());
        //5.返回脱敏的用户信息
        return this.getLoginUserVO(request);
    }

    /**
     * 获取当前登录用户
     * @param request
     * @return
     */
    @Override
    public LoginUserVO getLoginUserVO(HttpServletRequest request) {
        // API Key 认证优先：拦截器已把完整用户放入 request attribute
        User apiKeyUser = (User) request.getAttribute(ApiKeyConstant.REQUEST_USER_ATTR);
        if (apiKeyUser != null) {
            return BeanUtil.copyProperties(apiKeyUser, LoginUserVO.class);
        }
        // 会话认证兜底（getSession(false) 不创建新会话）
        HttpSession session = request.getSession(false);
        Object userIdObj = session == null ? null : session.getAttribute(USER_LOGIN_STATE);
        if(userIdObj == null){
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        //从 session 中获取用户ID
        Long userId;
        if (userIdObj instanceof Long) {
            userId = (Long) userIdObj;
        } else if (userIdObj instanceof Integer) {
            userId = ((Integer) userIdObj).longValue();
        } else {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        //从数据库查询当前用户信息（保证数据更新）
        User currentUser = this.getById(userId);
        if(currentUser == null){
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return BeanUtil.copyProperties(currentUser, LoginUserVO.class);
    }


    @Override
    public User getLoginUser(HttpServletRequest request) {
        // API Key 认证优先：拦截器已把完整用户放入 request attribute
        User apiKeyUser = (User) request.getAttribute(ApiKeyConstant.REQUEST_USER_ATTR);
        if (apiKeyUser != null) {
            return apiKeyUser;
        }
        // 会话认证兜底（getSession(false) 不创建新会话）
        HttpSession session = request.getSession(false);
        Object userIdObj = session == null ? null : session.getAttribute(USER_LOGIN_STATE);
        if (userIdObj == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从 session 中获取用户ID
        Long userId;
        if (userIdObj instanceof Long) {
            userId = (Long) userIdObj;
        } else if (userIdObj instanceof Integer) {
            userId = ((Integer) userIdObj).longValue();
        } else {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库查询当前用户信息
        User currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    /**
     * 用户注销
     * @param request
     * @return
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        if(request.getSession().getAttribute(USER_LOGIN_STATE) == null){
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"用户未登录");
        }
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }


}
