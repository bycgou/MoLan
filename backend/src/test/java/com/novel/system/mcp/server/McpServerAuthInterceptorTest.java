package com.novel.system.mcp.server;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpServerAuthInterceptorTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private McpServerConfig config;
    private McpServerAuthInterceptor interceptor;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() {
        config = new McpServerConfig();
        interceptor = new McpServerAuthInterceptor(config);
    }

    private void stubResponseWriter() throws Exception {
        responseWriter = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    private void enableAuth(String... keys) {
        config.getAuth().setEnabled(true);
        List<McpServerConfig.ApiKeyConfig> keyConfigs = new java.util.ArrayList<>();
        for (String k : keys) {
            McpServerConfig.ApiKeyConfig kc = new McpServerConfig.ApiKeyConfig();
            kc.setKey(k);
            keyConfigs.add(kc);
        }
        config.getAuth().setKeys(keyConfigs);
    }

    @Test
    void authDisabled_returnsTrue() throws Exception {
        config.getAuth().setEnabled(false);
        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void missingAuthHeader_returns401() throws Exception {
        stubResponseWriter();
        enableAuth("secret-key");
        when(request.getHeader("Authorization")).thenReturn(null);

        assertFalse(interceptor.preHandle(request, response, new Object()));
        verify(response).setStatus(401);
        assertTrue(responseWriter.toString().contains("Missing or invalid Authorization header"));
    }

    @Test
    void invalidBearerPrefix_returns401() throws Exception {
        stubResponseWriter();
        enableAuth("secret-key");
        when(request.getHeader("Authorization")).thenReturn("Token secret-key");

        assertFalse(interceptor.preHandle(request, response, new Object()));
        verify(response).setStatus(401);
    }

    @Test
    void validApiKey_returnsTrue() throws Exception {
        enableAuth("my-secret-key");
        when(request.getHeader("Authorization")).thenReturn("Bearer my-secret-key");

        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void invalidApiKey_returns403() throws Exception {
        stubResponseWriter();
        enableAuth("my-secret-key");
        when(request.getHeader("Authorization")).thenReturn("Bearer wrong-key");

        assertFalse(interceptor.preHandle(request, response, new Object()));
        verify(response).setStatus(403);
        assertTrue(responseWriter.toString().contains("Invalid API key"));
    }

    @Test
    void multipleKeys_matchingKey_returnsTrue() throws Exception {
        enableAuth("key-one", "key-two", "key-three");
        when(request.getHeader("Authorization")).thenReturn("Bearer key-two");

        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void multipleKeys_noMatch_returns403() throws Exception {
        stubResponseWriter();
        enableAuth("key-one", "key-two");
        when(request.getHeader("Authorization")).thenReturn("Bearer unknown");

        assertFalse(interceptor.preHandle(request, response, new Object()));
        verify(response).setStatus(403);
    }

    @Test
    void nullKeysList_returns403() throws Exception {
        stubResponseWriter();
        config.getAuth().setEnabled(true);
        config.getAuth().setKeys(null);
        when(request.getHeader("Authorization")).thenReturn("Bearer any-token");

        assertFalse(interceptor.preHandle(request, response, new Object()));
        verify(response).setStatus(403);
    }

    @Test
    void constantTimeComparison_equalKeys_returnsTrue() throws Exception {
        // Ensure MessageDigest.isEqual handles equal byte arrays correctly
        enableAuth("abcdefghij1234567890");
        when(request.getHeader("Authorization")).thenReturn("Bearer abcdefghij1234567890");

        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void constantTimeComparison_sameLengthDifferentContent_returnsFalse() throws Exception {
        // Same length, different content — the case timing attacks exploit
        stubResponseWriter();
        enableAuth("abcdefghij1234567890");
        when(request.getHeader("Authorization")).thenReturn("Bearer abcdefghij123456789X");

        assertFalse(interceptor.preHandle(request, response, new Object()));
        verify(response).setStatus(403);
    }

    @Test
    void constantTimeComparison_differentLength_returnsFalse() throws Exception {
        stubResponseWriter();
        enableAuth("short");
        when(request.getHeader("Authorization")).thenReturn("Bearer a-much-longer-key");

        assertFalse(interceptor.preHandle(request, response, new Object()));
        verify(response).setStatus(403);
    }
}
