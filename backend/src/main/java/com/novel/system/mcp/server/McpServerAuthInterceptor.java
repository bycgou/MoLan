package com.novel.system.mcp.server;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class McpServerAuthInterceptor implements HandlerInterceptor {

    private final McpServerConfig config;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!config.getAuth().isEnabled()) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Missing or invalid Authorization header\"}");
            return false;
        }

        String token = authHeader.substring("Bearer ".length());
        boolean valid = config.getAuth().getKeys() != null &&
            config.getAuth().getKeys().stream()
                .anyMatch(k -> k.getKey().equals(token));

        if (!valid) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Invalid API key\"}");
            return false;
        }

        return true;
    }
}
