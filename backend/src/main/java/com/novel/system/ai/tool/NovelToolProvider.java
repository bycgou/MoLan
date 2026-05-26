package com.novel.system.ai.tool;

import com.novel.system.mcp.client.McpClientManager;
import com.novel.system.mcp.client.McpToolAdapter;
import com.novel.system.mcp.client.McpToolExecutor;
import dev.langchain4j.agent.tool.ToolSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.novel.system.ai.graph.Mode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class NovelToolProvider {

    private final CharacterTool characterTool;
    private final ClueTool clueTool;
    private final NovelTool novelTool;
    private final RagTool ragTool;

    private McpClientManager mcpClientManager;
    private McpToolAdapter mcpToolAdapter;

    public NovelToolProvider(CharacterTool characterTool, ClueTool clueTool,
                             NovelTool novelTool, RagTool ragTool) {
        this.characterTool = characterTool;
        this.clueTool = clueTool;
        this.novelTool = novelTool;
        this.ragTool = ragTool;
    }

    @Autowired(required = false)
    public void setMcpClientManager(McpClientManager mcpClientManager) {
        this.mcpClientManager = mcpClientManager;
        if (mcpClientManager != null) {
            this.mcpToolAdapter = new McpToolAdapter(mcpClientManager);
        }
    }

    public Object[] getAllTools() {
        List<Object> tools = new ArrayList<>();
        tools.addAll(List.of(characterTool, clueTool, novelTool, ragTool));

        if (mcpToolAdapter != null && mcpClientManager != null
                && !mcpClientManager.getAllClients().isEmpty()) {
            tools.add(new McpToolExecutor(mcpToolAdapter));
            log.info("已添加 MCP 工具执行器，当前总工具数: {}", tools.size());
        }

        return tools.toArray();
    }

    /**
     * 按模式过滤工具列表。FAST 模式不返回 RagTool。
     */
    public List<Object> getAllTools(Mode mode) {
        List<Object> tools = new ArrayList<>();
        tools.add(characterTool);
        tools.add(clueTool);
        tools.add(novelTool);
        if (mode == Mode.FULL) {
            tools.add(ragTool);
        }
        return tools;
    }

    public List<ToolSpecification> getMcpToolSpecifications() {
        if (mcpClientManager == null || mcpClientManager.getAllClients().isEmpty()) {
            return Collections.emptyList();
        }
        return mcpToolAdapter.getAllToolSpecifications();
    }

    public boolean hasMcpTools() {
        return mcpClientManager != null && !mcpClientManager.getAllClients().isEmpty();
    }

    public CharacterTool getCharacterTool() {
        return characterTool;
    }

    public ClueTool getClueTool() {
        return clueTool;
    }

    public NovelTool getNovelTool() {
        return novelTool;
    }

    public RagTool getRagTool() {
        return ragTool;
    }
}
