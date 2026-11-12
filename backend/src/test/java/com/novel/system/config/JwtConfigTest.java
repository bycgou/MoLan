package com.novel.system.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class JwtConfigTest {

    private JwtConfig jwtConfig;

    @BeforeEach
    void setUp() {
        jwtConfig = new JwtConfig();
    }

    private void setSecret(String value) throws Exception {
        Field field = JwtConfig.class.getDeclaredField("secret");
        field.setAccessible(true);
        field.set(jwtConfig, value);
    }

    @Test
    void validateSecret_rejectsDefaultDevOnlySecret() throws Exception {
        setSecret("dev-only-jwt-secret-change-in-production-openssl-rand-base64-48");
        IllegalStateException ex = assertThrows(IllegalStateException.class, jwtConfig::validateSecret);
        assertTrue(ex.getMessage().contains("开发环境默认值"));
    }

    @Test
    void validateSecret_rejectsAnySecretContainingDevOnly() throws Exception {
        setSecret("dev-only-my-custom-secret-that-is-long-enough-for-length-check!!!");
        IllegalStateException ex = assertThrows(IllegalStateException.class, jwtConfig::validateSecret);
        assertTrue(ex.getMessage().contains("开发环境默认值"));
    }

    @Test
    void validateSecret_rejectsFormerRepoDefaultSecret() throws Exception {
        setSecret("moyun-local-dev-secret-2026-min32bytes-for-development-only");
        IllegalStateException ex = assertThrows(IllegalStateException.class, jwtConfig::validateSecret);
        assertTrue(ex.getMessage().contains("开发环境默认值"));
    }

    @Test
    void validateSecret_acceptsValidSecret() throws Exception {
        setSecret("a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4");
        assertDoesNotThrow(jwtConfig::validateSecret);
    }

    @Test
    void validateSecret_rejectsNullSecret() throws Exception {
        setSecret(null);
        IllegalStateException ex = assertThrows(IllegalStateException.class, jwtConfig::validateSecret);
        assertTrue(ex.getMessage().contains("未配置"));
    }

    @Test
    void validateSecret_rejectsEmptySecret() throws Exception {
        setSecret("   ");
        IllegalStateException ex = assertThrows(IllegalStateException.class, jwtConfig::validateSecret);
        assertTrue(ex.getMessage().contains("未配置"));
    }

    @Test
    void validateSecret_rejectsShortSecret() throws Exception {
        setSecret("short-secret");
        IllegalStateException ex = assertThrows(IllegalStateException.class, jwtConfig::validateSecret);
        assertTrue(ex.getMessage().contains("长度不足"));
    }

    @Test
    void validateSecret_rejectsChangemeSecret() throws Exception {
        setSecret("changeme-please-replace-this-with-a-real-secret-key-value!");
        IllegalStateException ex = assertThrows(IllegalStateException.class, jwtConfig::validateSecret);
        assertTrue(ex.getMessage().contains("占位符默认值"));
    }

    @Test
    void validateSecret_rejectsAibookSecret() throws Exception {
        setSecret("aibook-default-secret-key-that-is-long-enough-for-validation");
        IllegalStateException ex = assertThrows(IllegalStateException.class, jwtConfig::validateSecret);
        assertTrue(ex.getMessage().contains("不安全的默认值"));
    }

    @Test
    void applicationYml_hasNoJwtSecretFallback() throws Exception {
        String yml;
        try (InputStream in = getClass().getResourceAsStream("/application.yml")) {
            assertNotNull(in);
            yml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(yml.contains("secret: ${JWT_SECRET:}"),
                "jwt.secret 必须没有默认值，缺少 JWT_SECRET 时应由 JwtConfig 启动期校验拦下");
    }
}
