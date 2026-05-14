package com.novel.system.mcp.server.tool;

import com.novel.system.mcp.server.McpServerConfig;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

@Slf4j
public abstract class BaseMcpTools {

    protected final McpServerConfig mcpConfig;

    protected BaseMcpTools(McpServerConfig mcpConfig) {
        this.mcpConfig = mcpConfig;
    }

    protected String resolveUserId() {
        if (mcpConfig.getAuth() != null && mcpConfig.getAuth().getKeys() != null
            && !mcpConfig.getAuth().getKeys().isEmpty()) {
            return mcpConfig.getAuth().getKeys().get(0).getUserId();
        }
        return "mcp-user";
    }

    private final List<SyncToolSpecification> toolSpecs = new ArrayList<>();

    public abstract void registerAll();

    protected void addTool(String name, String description,
                           Map<String, Object> properties,
                           List<String> required,
                           ToolHandler handler) {
        JsonSchema schema = new JsonSchema("object", properties, required, null, null, null);
        Tool tool = new Tool(name, description, schema);

        BiFunction<McpSyncServerExchange, Map<String, Object>, CallToolResult> callHandler =
            (exchange, arguments) -> {
                try {
                    String result = handler.handle(arguments);
                    return new CallToolResult(List.of(new TextContent(result)), false);
                } catch (Exception e) {
                    log.error("MCP 工具 [{}] 执行失败: {}", name, e.getMessage(), e);
                    return new CallToolResult(
                        List.of(new TextContent("执行失败: " + e.getMessage())), true);
                }
            };

        toolSpecs.add(new SyncToolSpecification(tool, callHandler));
        log.debug("注册 MCP 工具: {}", name);
    }

    public List<SyncToolSpecification> getToolSpecifications() {
        return toolSpecs;
    }

    public int getToolCount() {
        return toolSpecs.size();
    }

    @FunctionalInterface
    protected interface ToolHandler {
        String handle(Map<String, Object> arguments);
    }
}
