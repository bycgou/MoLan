package com.novel.system.controller;

import com.novel.system.service.user.UserService;
import com.novel.system.dto.request.LoginRequest;
import com.novel.system.dto.request.RegisterRequest;
import com.novel.system.dto.response.UserResponse;
import com.novel.system.dto.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    
    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ApiResponse<UserResponse> register(@RequestBody RegisterRequest request) {
        log.info("用户注册：{}", request);
        UserResponse response = userService.register(request);
        return ApiResponse.success(response);
    }
    
    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ApiResponse<UserResponse> login(@RequestBody LoginRequest request) {
        log.info("用户登录-----：{}", request);
        UserResponse response = userService.login(request);
        return ApiResponse.success(response);
    }
    
    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public ApiResponse<UserResponse> getCurrentUser(@RequestAttribute("userId") String userId) {
        var user = userService.getUserById(userId);
        UserResponse response = new UserResponse();
        response.setUserId(user.getUserId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setNickname(user.getNickname());
        response.setRole(user.getRole());
        response.setStatus(user.getStatus());
        return ApiResponse.success(response);
    }
}