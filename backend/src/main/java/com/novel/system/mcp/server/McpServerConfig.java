package com.novel.system.mcp.server;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "mcp.server")
@Data
public class McpServerConfig {

    private boolean enabled = false;
    private String name = "moyun-novel-mcp-server";
    private String version = "1.0.0";
    private String transport = "stdio";
    private AuthConfig auth = new AuthConfig();

    @Data
    public static class AuthConfig {
        private boolean enabled = false;
        private String type = "api-key";
        private List<ApiKeyConfig> keys;
    }

    @Data
    public static class ApiKeyConfig {
        private String name;
        private String key;
        private String userId;
    }
}
