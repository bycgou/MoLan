package com.novel.system.mcp.server;

import com.novel.system.mcp.server.tool.*;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class McpServerManager {

    private final McpServerConfig config;
    private final NovelMcpTools novelMcpTools;
    private final ChapterMcpTools chapterMcpTools;
    private final CreationMcpTools creationMcpTools;
    private final CharacterMcpTools characterMcpTools;
    private final ClueMcpTools clueMcpTools;
    private final RagMcpTools ragMcpTools;
    private final AnalysisMcpTools analysisMcpTools;
    private final StyleMcpTools styleMcpTools;

    private McpSyncServer mcpServer;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        if (!config.isEnabled()) {
            log.info("MCP Server 已禁用");
            return;
        }

        try {
            registerAllTools();

            List<SyncToolSpecification> allSpecs = getAllToolSpecifications();
            int totalTools = allSpecs.size();

            // Select transport based on config
            McpServerTransportProvider transportProvider;
            if ("sse".equalsIgnoreCase(config.getTransport())) {
                log.warn("SSE传输在当前MCP SDK版本中不可用，降级为Stdio传输");
                transportProvider = new StdioServerTransportProvider();
            } else {
                transportProvider = new StdioServerTransportProvider();
                log.info("MCP Server 使用 Stdio 传输");
            }

            mcpServer = McpServer.sync(transportProvider)
                .serverInfo(config.getName(), config.getVersion())
                .capabilities(ServerCapabilities.builder()
                    .tools(true)
                    .build())
                .tools(allSpecs)
                .build();

            log.info("MCP Server [{}] 启动成功，已注册工具数: {}", config.getName(), totalTools);
        } catch (Exception e) {
            log.error("MCP Server 启动失败: {}", e.getMessage(), e);
        }
    }

    private void registerAllTools() {
        novelMcpTools.registerAll();
        chapterMcpTools.registerAll();
        creationMcpTools.registerAll();
        characterMcpTools.registerAll();
        clueMcpTools.registerAll();
        ragMcpTools.registerAll();
        analysisMcpTools.registerAll();
        styleMcpTools.registerAll();
    }

    private List<SyncToolSpecification> getAllToolSpecifications() {
        List<SyncToolSpecification> allSpecs = new ArrayList<>();
        allSpecs.addAll(novelMcpTools.getToolSpecifications());
        allSpecs.addAll(chapterMcpTools.getToolSpecifications());
        allSpecs.addAll(creationMcpTools.getToolSpecifications());
        allSpecs.addAll(characterMcpTools.getToolSpecifications());
        allSpecs.addAll(clueMcpTools.getToolSpecifications());
        allSpecs.addAll(ragMcpTools.getToolSpecifications());
        allSpecs.addAll(analysisMcpTools.getToolSpecifications());
        allSpecs.addAll(styleMcpTools.getToolSpecifications());
        return allSpecs;
    }

    public McpSyncServer getServer() {
        return mcpServer;
    }
}
