package com.novel.system.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    
    private final AuthFilter authFilter;
    
    public SecurityConfig(@Lazy AuthFilter authFilter) {
        this.authFilter = authFilter;
    }
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // 主安全链：只处理 REQUEST 类型的请求
        SecurityFilterChain mainChain = http
            .securityMatchers(matchers -> matchers
                .requestMatchers(new org.springframework.security.web.util.matcher.DispatcherTypeRequestMatcher(DispatcherType.REQUEST)))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**", "/genre-template/**", "/genre/**").permitAll()
                .requestMatchers("/mcp/**").permitAll()
                .requestMatchers("/error").permitAll()
                .requestMatchers("/admin/**").hasRole("admin")
                .requestMatchers("/style-card/**", "/novel/**", "/llm-config/**", "/chapter/**").authenticated()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"未登录或Token已过期\",\"data\":null}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":403,\"message\":\"无权限访问\",\"data\":null}");
                })
            )
            .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
        return mainChain;
    }

    /**
     * 放行所有 ASYNC/ERROR dispatch — 避免 SSE 连接关闭后 error page dispatch 触发 AccessDeniedException
     */
    @Bean
    public SecurityFilterChain asyncErrorPermitAllChain(HttpSecurity http) throws Exception {
        return http
            .securityMatchers(matchers -> matchers
                .requestMatchers(
                    new org.springframework.security.web.util.matcher.DispatcherTypeRequestMatcher(DispatcherType.ASYNC),
                    new org.springframework.security.web.util.matcher.DispatcherTypeRequestMatcher(DispatcherType.ERROR)))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .build();
    }
    
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
