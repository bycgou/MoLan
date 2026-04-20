package com.novel.system.service.user;

import com.novel.system.entity.mysql.User;
import com.novel.system.dto.request.LoginRequest;
import com.novel.system.dto.request.RegisterRequest;
import com.novel.system.dto.response.UserResponse;

/**
 * 用户服务
 */
public interface UserService {
    
    /**
     * 用户注册
     */
    UserResponse register(RegisterRequest request);
    
    /**
     * 用户登录
     */
    UserResponse login(LoginRequest request);
    
    /**
     * 根据ID获取用户信息
     */
    User getUserById(String userId);
    
    /**
     * 根据用户名获取用户信息
     */
    User getUserByUsername(String username);
    
    /**
     * 更新用户信息
     */
    User updateUser(String userId, User user);
    
    /**
     * 修改密码
     */
    boolean changePassword(String userId, String oldPassword, String newPassword);
    
    /**
     * 启用/禁用用户
     */
    boolean updateUserStatus(String userId, Integer status);
}