package com.novel.system.config;

import com.novel.system.interceptor.OwnerAccessInterceptor;
import com.novel.system.mcp.server.McpServerAuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired(required = false)
    private McpServerAuthInterceptor mcpServerAuthInterceptor;

    @Autowired
    private OwnerAccessInterceptor ownerAccessInterceptor;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.asList(
            System.getProperty("cors.allowed-origins",
                "http://localhost:5173,http://localhost:3000,http://localhost:21060,http://127.0.0.1:21060")
        .split(",")));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Owner 权限校验 — 在 AuthFilter 之后执行
        registry.addInterceptor(ownerAccessInterceptor)
                .addPathPatterns("/novel/**", "/llm-config/**", "/chapter/**", "/style-card/**")
                .excludePathPatterns(
                        "/novel/list",           // 列表接口已有 userId 过滤
                        "/novel",                // POST 创建
                        "/novel/auto-generate-outline"  // 不带资源 ID
                );

        // MCP 认证拦截器
        if (mcpServerAuthInterceptor != null) {
            registry.addInterceptor(mcpServerAuthInterceptor)
                    .addPathPatterns("/mcp/**");
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**")
            .addResourceLocations("classpath:/static/");
    }
}
