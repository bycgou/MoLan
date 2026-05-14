package com.novel.system.mcp.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class McpToolExecutor {

    private final McpToolAdapter adapter;
    private static final ObjectMapper mapper = new ObjectMapper();

    public McpToolExecutor(McpToolAdapter adapter) {
        this.adapter = adapter;
    }

    @Tool({
        "Execute an MCP (Model Context Protocol) tool by name.",
        "Available MCP tools are listed in the system context.",
        "Use this when you need to call an external MCP tool.",
        "Parameters: toolName (format: mcp_{server}_{tool}), arguments (JSON string of arguments)"
    })
    public String executeMcpTool(
            @P("The MCP tool name, format: mcp_{serverName}_{toolName}") String toolName,
            @P("The arguments as a JSON string") String argumentsJson) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> arguments = mapper.readValue(argumentsJson, Map.class);
            return adapter.executeTool(toolName, arguments);
        } catch (Exception e) {
            log.error("执行 MCP 工具失败: {}", e.getMessage());
            return "执行失败: " + e.getMessage();
        }
    }
}
