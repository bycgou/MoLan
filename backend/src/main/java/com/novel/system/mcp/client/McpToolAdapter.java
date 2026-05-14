package com.novel.system.mcp.client;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import io.modelcontextprotocol.spec.McpSchema.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class McpToolAdapter {

    private final McpClientManager mcpClientManager;

    public McpToolAdapter(McpClientManager mcpClientManager) {
        this.mcpClientManager = mcpClientManager;
    }

    public List<ToolSpecification> getAllToolSpecifications() {
        List<ToolSpecification> specs = new ArrayList<>();
        for (Map.Entry<String, List<Tool>> entry : mcpClientManager.getAllTools().entrySet()) {
            String serverName = entry.getKey();
            for (Tool tool : entry.getValue()) {
                specs.add(convertToToolSpecification(serverName, tool));
            }
        }
        return specs;
    }

    private ToolSpecification convertToToolSpecification(String serverName, Tool tool) {
        Map<String, Map<String, Object>> properties = new HashMap<>();
        List<String> required = new ArrayList<>();

        if (tool.inputSchema() != null) {
            if (tool.inputSchema().properties() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Map<String, Object>> schemaProps =
                    (Map<String, Map<String, Object>>) (Map<?, ?>) tool.inputSchema().properties();
                properties.putAll(schemaProps);
            }
            if (tool.inputSchema().required() != null) {
                @SuppressWarnings("unchecked")
                List<String> req = (List<String>) tool.inputSchema().required();
                required.addAll(req);
            }
        }

        JsonObjectSchema.Builder schemaBuilder = JsonObjectSchema.builder();
        for (Map.Entry<String, Map<String, Object>> entry : properties.entrySet()) {
            schemaBuilder.addProperty(entry.getKey(), toJsonSchemaElement(entry.getValue()));
        }
        if (!required.isEmpty()) {
            schemaBuilder.required(required);
        }

        String toolName = "mcp_" + serverName + "_" + tool.name();
        return ToolSpecification.builder()
            .name(toolName)
            .description("[MCP:" + serverName + "] " + tool.description())
            .parameters(schemaBuilder.build())
            .build();
    }

    @SuppressWarnings("unchecked")
    private JsonSchemaElement toJsonSchemaElement(Map<String, Object> prop) {
        String type = (String) prop.getOrDefault("type", "string");
        String description = (String) prop.get("description");

        return switch (type) {
            case "integer" -> {
                JsonIntegerSchema.Builder builder = JsonIntegerSchema.builder();
                if (description != null) builder.description(description);
                yield builder.build();
            }
            case "number" -> {
                JsonNumberSchema.Builder builder = JsonNumberSchema.builder();
                if (description != null) builder.description(description);
                yield builder.build();
            }
            case "boolean" -> {
                JsonBooleanSchema.Builder builder = JsonBooleanSchema.builder();
                if (description != null) builder.description(description);
                yield builder.build();
            }
            case "array" -> {
                JsonArraySchema.Builder builder = JsonArraySchema.builder();
                if (description != null) builder.description(description);
                Object items = prop.get("items");
                if (items instanceof Map<?, ?> itemsMap) {
                    builder.items(toJsonSchemaElement((Map<String, Object>) itemsMap));
                }
                yield builder.build();
            }
            default -> {
                JsonStringSchema.Builder builder = JsonStringSchema.builder();
                if (description != null) builder.description(description);
                yield builder.build();
            }
        };
    }

    public String executeTool(String toolName, Map<String, Object> arguments) {
        String[] parts = parseToolName(toolName);
        String serverName = parts[0];
        String mcpToolName = parts[1];

        try {
            CallToolResult result = mcpClientManager.callTool(serverName, mcpToolName, arguments);
            if (result.isError() != null && result.isError()) {
                log.warn("MCP 工具 [{}] 执行返回错误", toolName);
            }
            StringBuilder sb = new StringBuilder();
            if (result.content() != null) {
                for (Content content : result.content()) {
                    if (content instanceof TextContent textContent) {
                        sb.append(textContent.text());
                    }
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("MCP 工具 [{}] 执行失败: {}", toolName, e.getMessage(), e);
            return "工具执行失败: " + e.getMessage();
        }
    }

    private String[] parseToolName(String toolName) {
        String withoutPrefix = toolName.substring("mcp_".length());
        for (String serverName : mcpClientManager.getAllClients().keySet()) {
            String prefix = serverName + "_";
            if (withoutPrefix.startsWith(prefix)) {
                String mcpToolName = withoutPrefix.substring(prefix.length());
                return new String[]{serverName, mcpToolName};
            }
        }
        throw new IllegalArgumentException("无法解析 MCP 工具名: " + toolName);
    }
}
