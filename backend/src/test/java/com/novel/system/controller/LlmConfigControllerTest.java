package com.novel.system.controller;

import com.novel.system.dto.response.ApiResponse;
import com.novel.system.entity.mysql.LlmConfig;
import com.novel.system.service.llm.LlmConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmConfigControllerTest {

    @Mock
    private LlmConfigService llmConfigService;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private LlmConfigController controller;

    private LlmConfig existingConfig;
    private static final String CONFIG_ID = "config-001";
    private static final String OWNER_ID = "user-owner";
    private static final String OTHER_USER_ID = "user-other";

    @BeforeEach
    void setUp() {
        existingConfig = new LlmConfig();
        existingConfig.setConfigId(CONFIG_ID);
        existingConfig.setUserId(OWNER_ID);
        existingConfig.setConfigName("Test Config");
    }

    // ===== deleteConfig =====

    @Test
    void deleteConfig_owner_returns200() {
        when(httpRequest.getAttribute("userId")).thenReturn(OWNER_ID);
        when(llmConfigService.getConfigById(CONFIG_ID)).thenReturn(existingConfig);

        ApiResponse<Void> response = controller.deleteConfig(CONFIG_ID, httpRequest);

        assertEquals(200, response.getCode());
        assertEquals("删除成功", response.getMessage());
        verify(llmConfigService).deleteConfig(CONFIG_ID);
    }

    @Test
    void deleteConfig_nonOwner_returns403() {
        when(httpRequest.getAttribute("userId")).thenReturn(OTHER_USER_ID);
        when(llmConfigService.getConfigById(CONFIG_ID)).thenReturn(existingConfig);

        ApiResponse<Void> response = controller.deleteConfig(CONFIG_ID, httpRequest);

        assertEquals(403, response.getCode());
        assertEquals("无权删除该配置", response.getMessage());
        verify(llmConfigService, never()).deleteConfig(anyString());
    }

    @Test
    void deleteConfig_noAuth_returns401() {
        when(httpRequest.getAttribute("userId")).thenReturn(null);

        ApiResponse<Void> response = controller.deleteConfig(CONFIG_ID, httpRequest);

        assertEquals(401, response.getCode());
        assertEquals("未登录或Token已过期", response.getMessage());
        verify(llmConfigService, never()).deleteConfig(anyString());
    }

    @Test
    void deleteConfig_configNotFound_returns404() {
        when(httpRequest.getAttribute("userId")).thenReturn(OWNER_ID);
        when(llmConfigService.getConfigById(CONFIG_ID)).thenReturn(null);

        ApiResponse<Void> response = controller.deleteConfig(CONFIG_ID, httpRequest);

        assertEquals(404, response.getCode());
        assertEquals("配置不存在", response.getMessage());
        verify(llmConfigService, never()).deleteConfig(anyString());
    }
}
