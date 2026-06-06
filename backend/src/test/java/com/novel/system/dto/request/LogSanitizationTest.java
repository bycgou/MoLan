package com.novel.system.dto.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 DTO toString() 不泄露密码等敏感字段
 */
class LogSanitizationTest {

    @Test
    @DisplayName("LoginRequest toString 应包含用户名但不包含密码")
    void loginRequestToStringExcludesPassword() {
        LoginRequest req = new LoginRequest();
        req.setUsername("testUser");
        req.setPassword("s3cret!");

        String str = req.toString();

        assertTrue(str.contains("testUser"),
                "toString() 应包含用户名");
        assertFalse(str.contains("s3cret!"),
                "toString() 不应包含密码明文");
        assertFalse(str.toLowerCase().contains("password=s3cret"),
                "toString() 不应以 password=value 形式暴露密码");
    }

    @Test
    @DisplayName("RegisterRequest toString 应包含用户名但不包含密码")
    void registerRequestToStringExcludesPassword() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newUser");
        req.setPassword("P@ssw0rd");
        req.setEmail("user@example.com");
        req.setUsername("newUser");

        String str = req.toString();

        assertTrue(str.contains("newUser"),
                "toString() 应包含用户名");
        assertTrue(str.contains("user@example.com"),
                "toString() 应包含邮箱");
        assertFalse(str.contains("P@ssw0rd"),
                "toString() 不应包含密码明文");
    }
}
