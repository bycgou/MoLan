package com.novel.system.config;

import com.novel.system.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * 认证过滤器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {
    
    private final JwtConfig jwtConfig;
    private final UserService userService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) 
            throws ServletException, IOException {
        
        // 获取请求头中的token
        String token = request.getHeader(jwtConfig.getHeader());
        
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7); // 去掉Bearer前缀
            
            try {
                // 验证token
                if (jwtConfig.validateToken(token)) {
                    // 从token中获取用户ID
                    String userId = jwtConfig.getUserIdFromToken(token);
                    // 获取用户信息
                    var user = userService.getUserById(userId);
                    if (user != null) {
                        // 设置请求属性（向后兼容）
                        request.setAttribute("userId", userId);
                        request.setAttribute("username", user.getUsername());
                        request.setAttribute("role", user.getRole());
                        
                        // 设置 Spring Security 认证上下文
                        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
                        var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (Exception e) {
                log.error("Token验证失败", e);
            }
        }
        
        filterChain.doFilter(request, response);
    }
}