package com.novel.system.mcp.server;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class McpSseEndpoint {

    private final McpServerManager serverManager;
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event()
                .name("endpoint")
                .data("/mcp/messages"));
        } catch (IOException e) {
            log.warn("发送 endpoint 信息失败: {}", e.getMessage());
        }

        return emitter;
    }

    /**
     * @deprecated Stub implementation — returns a hard-coded JSON-RPC success response.
     *   McpServerManager currently only supports Stdio transport and has no
     *   {@code handleMessage} method. Implement real JSON-RPC dispatch when
     *   the MCP SDK adds SSE transport support, or wire this to a future
     *   {@code McpServerManager.handleMessage(String)} entry-point.
     */
    @Deprecated
    @PostMapping("/messages")
    public String handleMessage(@RequestBody String jsonRpcRequest) {
        log.debug("收到 MCP 请求 (stub): {}", jsonRpcRequest);
        if (serverManager.getServer() == null) {
            return "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32000,\"message\":\"MCP Server 未初始化\"}}";
        }
        // TODO: delegate to McpServerManager.handleMessage() once SSE transport is supported
        return "{\"jsonrpc\":\"2.0\",\"result\":{\"status\":\"ok\"}}";
    }
}
