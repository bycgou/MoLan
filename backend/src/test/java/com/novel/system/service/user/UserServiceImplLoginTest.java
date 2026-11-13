package com.novel.system.service.user;

import com.novel.system.config.JwtConfig;
import com.novel.system.dto.request.LoginRequest;
import com.novel.system.dto.response.UserResponse;
import com.novel.system.entity.mysql.User;
import com.novel.system.exception.BusinessException;
import com.novel.system.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 登录失败必须返回 401/403，而不是通用 500
 *
 * 演示与答辩现场经常有人输错密码，如果统一返回 500「服务器内部错误」，
 * 会被误判成服务挂了，掩盖真正的提示信息。
 */
class UserServiceImplLoginTest {

    private static final String RAW_PASSWORD = "Xb7!mQ2#vL9@pK4$zT6&n";

    @Mock
    private UserMapper userMapper;

    private UserServiceImpl userService;
    private BCryptPasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        encoder = new BCryptPasswordEncoder();

        JwtConfig jwtConfig = new JwtConfig();
        setField(jwtConfig, "secret", "unit-test-secret-key-at-least-32-bytes-long!!");
        setField(jwtConfig, "expiration", 3600000L);

        userService = new UserServiceImpl(encoder, jwtConfig);
        setField(userService, "baseMapper", userMapper);
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Class<?> type = target instanceof UserServiceImpl
                    ? com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class
                    : target.getClass();
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private LoginRequest request(String username, String password) {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }

    private User user(int status) {
        User user = new User();
        user.setUserId("u-1");
        user.setUsername("xpb_demo");
        user.setPassword(encoder.encode(RAW_PASSWORD));
        user.setRole("user");
        user.setStatus(status);
        return user;
    }

    @Test
    void login_withCorrectPassword_returnsToken() {
        when(userMapper.findByUsername(anyString())).thenReturn(user(1));

        UserResponse response = userService.login(request("xpb_demo", RAW_PASSWORD));

        assertEquals("xpb_demo", response.getUsername());
        assertNotNull(response.getToken());
    }

    @Test
    void login_withWrongPassword_throws401() {
        when(userMapper.findByUsername(anyString())).thenReturn(user(1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.login(request("xpb_demo", "wrong-password")));
        assertEquals(401, ex.getCode());
        assertEquals("用户名或密码错误", ex.getMessage());
    }

    @Test
    void login_withUnknownUser_throws401() {
        when(userMapper.findByUsername(anyString())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.login(request("nobody", RAW_PASSWORD)));
        assertEquals(401, ex.getCode());
    }

    @Test
    void login_withDisabledAccount_throws403() {
        when(userMapper.findByUsername(anyString())).thenReturn(user(0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.login(request("xpb_demo", RAW_PASSWORD)));
        assertEquals(403, ex.getCode());
        assertEquals("账号已被禁用", ex.getMessage());
    }
}
