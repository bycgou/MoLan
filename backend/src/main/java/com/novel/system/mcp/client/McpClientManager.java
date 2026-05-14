package com.novel.system.mcp.client;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.spec.McpSchema.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class McpClientManager implements DisposableBean {

    private final McpClientConfig mcpClientConfig;
    private final Map<String, McpSyncClient> clients = new ConcurrentHashMap<>();
    private final Map<String, List<Tool>> serverTools = new ConcurrentHashMap<>();

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        if (!mcpClientConfig.isEnabled()) {
            log.info("MCP Client 集成已禁用");
            return;
        }

        for (Map.Entry<String, McpClientConfig.McpServerConfig> entry :
                mcpClientConfig.getServers().entrySet()) {
            String name = entry.getKey();
            McpClientConfig.McpServerConfig config = entry.getValue();

            if (!config.isEnabled()) {
                log.info("MCP Server [{}] 已禁用，跳过", name);
                continue;
            }

            try {
                McpSyncClient client = createClient(name, config);
                clients.put(name, client);

                client.initialize();
                ListToolsResult toolsResult = client.listTools();
                List<Tool> tools = toolsResult.tools();
                serverTools.put(name, tools);

                log.info("MCP Server [{}] 初始化成功，可用工具数: {}", name, tools.size());
                for (Tool tool : tools) {
                    log.debug("  - {}: {}", tool.name(), tool.description());
                }
            } catch (Exception e) {
                log.error("MCP Server [{}] 初始化失败: {}", name, e.getMessage(), e);
                if (config.isRequired()) {
                    throw new RuntimeException("必须的 MCP Server [" + name + "] 启动失败", e);
                }
            }
        }
    }

    private McpSyncClient createClient(String name, McpClientConfig.McpServerConfig config) {
        if (!"stdio".equals(config.getTransport())) {
            throw new IllegalArgumentException("不支持的传输类型: " + config.getTransport());
        }

        ServerParameters.Builder paramBuilder = ServerParameters.builder(config.getCommand());
        if (config.getArgs() != null) {
            paramBuilder.args(config.getArgs());
        }
        if (config.getEnv() != null) {
            paramBuilder.env(config.getEnv());
        }

        ServerParameters params = paramBuilder.build();
        StdioClientTransport transport = new StdioClientTransport(params);

        log.info("启动 MCP Server [{}]: {} {}", name, config.getCommand(),
            config.getArgs() != null ? String.join(" ", config.getArgs()) : "");

        return McpClient.sync(transport)
            .requestTimeout(Duration.ofSeconds(config.getStartupTimeout()))
            .build();
    }

    public McpSyncClient getClient(String name) {
        return clients.get(name);
    }

    public Map<String, McpSyncClient> getAllClients() {
        return Collections.unmodifiableMap(clients);
    }

    public List<Tool> getTools(String serverName) {
        return serverTools.getOrDefault(serverName, Collections.emptyList());
    }

    public Map<String, List<Tool>> getAllTools() {
        return Collections.unmodifiableMap(serverTools);
    }

    public CallToolResult callTool(String serverName, String toolName, Map<String, Object> arguments) {
        McpSyncClient client = clients.get(serverName);
        if (client == null) {
            throw new IllegalArgumentException("MCP Server [" + serverName + "] 未连接");
        }

        CallToolRequest request = new CallToolRequest(toolName, arguments);
        return client.callTool(request);
    }

    public boolean isServerAvailable(String name) {
        return clients.containsKey(name);
    }

    @Override
    public void destroy() {
        for (Map.Entry<String, McpSyncClient> entry : clients.entrySet()) {
            try {
                entry.getValue().close();
                log.info("MCP Client [{}] 已关闭", entry.getKey());
            } catch (Exception e) {
                log.warn("关闭 MCP Client [{}] 失败: {}", entry.getKey(), e.getMessage());
            }
        }
        clients.clear();
        serverTools.clear();
    }
}
