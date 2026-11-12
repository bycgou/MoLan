package com.novel.system.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Data
@Component
public class JwtConfig {
    
    @Value("${jwt.secret}")
    private String secret;
    
    @Value("${jwt.expiration:86400000}")
    private long expiration;
    
    @Value("${jwt.header:Authorization}")
    private String header;
    
    @jakarta.annotation.PostConstruct
    public void validateSecret() {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalStateException("jwt.secret 未配置！请在 application.yml 中设置高强度密钥（至少32字节）");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("jwt.secret 长度不足32字节，安全性不够！请使用更长的密钥");
        }
        if (secret.contains("aibook") || secret.contains("default") || secret.contains("123456")) {
            throw new IllegalStateException("jwt.secret 使用了不安全的默认值，请更换为随机生成的高强度密钥");
        }
        if (secret.contains("changeme") || secret.contains("generate_with")) {
            throw new IllegalStateException("jwt.secret 使用了占位符默认值，请通过 JWT_SECRET 环境变量提供真实密钥（openssl rand -base64 48）");
        }
        if (secret.contains("dev-only") || secret.contains("development-only")) {
            throw new IllegalStateException("jwt.secret 使用了开发环境默认值，请通过 JWT_SECRET 环境变量提供真实密钥（openssl rand -base64 48）");
        }
        log.info("JWT密钥校验通过（长度: {} 字节）", secret.getBytes(StandardCharsets.UTF_8).length);
    }
    
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
    
    public String generateToken(String userId, String username, String role) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + expiration);
        
        return Jwts.builder()
            .subject(userId)
            .claim("username", username)
            .claim("role", role)
            .issuedAt(now)
            .expiration(exp)
            .signWith(getSigningKey())
            .compact();
    }
    
    public Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
    
    public String getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject();
    }
    
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }
    
    public String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("role", String.class);
    }
    
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
