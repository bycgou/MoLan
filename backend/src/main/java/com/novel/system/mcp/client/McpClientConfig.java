package com.novel.system.mcp.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "mcp.client")
@Data
public class McpClientConfig {

    private boolean enabled = false;
    private Map<String, McpServerConfig> servers = new HashMap<>();

    @Data
    public static class McpServerConfig {
        private boolean enabled = true;
        private String transport;
        private String command;
        private List<String> args;
        private String url;
        private Map<String, String> env;
        private int startupTimeout = 30;
        private int healthCheckInterval = 60;
        private boolean required = false;
    }
}
