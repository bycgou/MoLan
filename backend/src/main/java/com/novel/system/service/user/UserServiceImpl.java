package com.novel.system.service.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.novel.system.entity.mysql.User;
import com.novel.system.mapper.UserMapper;
import com.novel.system.service.user.UserService;
import com.novel.system.dto.request.LoginRequest;
import com.novel.system.dto.request.RegisterRequest;
import com.novel.system.dto.response.UserResponse;
import com.novel.system.config.JwtConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 用户服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtConfig jwtConfig;
    
    @Override
    public UserResponse register(RegisterRequest request) {
        // 检查用户名是否已存在
        User existingUser = baseMapper.findByUsername(request.getUsername());
        if (existingUser != null) {
            throw new RuntimeException("用户名已存在");
        }
        
        // 检查邮箱是否已存在
        existingUser = baseMapper.findByEmail(request.getEmail());
        if (existingUser != null) {
            throw new RuntimeException("邮箱已被注册");
        }
        
        // 创建新用户
        User user = new User();
        user.setUserId(java.util.UUID.randomUUID().toString().replace("-", ""));
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setRole("user"); // 默认角色
        user.setStatus(1); // 默认启用
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        
        save(user);
        
        // 生成token
        String token = jwtConfig.generateToken(user.getUserId(), user.getUsername(), user.getRole());
        
        // 构建响应
        UserResponse response = new UserResponse();
        response.setUserId(user.getUserId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setNickname(user.getNickname());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus());
        response.setToken(token);
        
        return response;
    }
    
    @Override
    public UserResponse login(LoginRequest request) {
        // 查找用户
        User user = baseMapper.findByUsername(request.getUsername());
        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }
        
        // 检查密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }
        
        // 检查用户状态
        if (user.getStatus() != 1) {
            throw new RuntimeException("账号已被禁用");
        }
        
        // 生成token
        String token = jwtConfig.generateToken(user.getUserId(), user.getUsername(), user.getRole());
        
        // 构建响应
        UserResponse response = new UserResponse();
        response.setUserId(user.getUserId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setNickname(user.getNickname());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus());
        response.setToken(token);
        
        return response;
    }
    
    @Override
    public User getUserById(String userId) {
        return getById(userId);
    }
    
    @Override
    public User getUserByUsername(String username) {
        return baseMapper.findByUsername(username);
    }
    
    @Override
    public User updateUser(String userId, User user) {
        User existingUser = getById(userId);
        if (existingUser == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 更新用户信息
        if (user.getEmail() != null) {
            existingUser.setEmail(user.getEmail());
        }
        if (user.getNickname() != null) {
            existingUser.setNickname(user.getNickname());
        }
        existingUser.setUpdateTime(LocalDateTime.now());
        
        updateById(existingUser);
        return existingUser;
    }
    
    @Override
    public boolean changePassword(String userId, String oldPassword, String newPassword) {
        User user = getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 检查旧密码
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("旧密码错误");
        }
        
        // 更新密码
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdateTime(LocalDateTime.now());
        
        return updateById(user);
    }
    
    @Override
    public boolean updateUserStatus(String userId, Integer status) {
        User user = getById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        user.setStatus(status);
        user.setUpdateTime(LocalDateTime.now());
        
        return updateById(user);
    }
}