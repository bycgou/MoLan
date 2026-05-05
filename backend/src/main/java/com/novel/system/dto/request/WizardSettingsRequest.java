package com.novel.system.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 向导设定请求DTO - Step 1 输入
 */
@Data
public class WizardSettingsRequest {
    @NotBlank(message = "小说名称不能为空")
    private String novelName;
    private String genre;
    private String novelType;
    private String tone;
    private String tabooList;
    private String totalOutline;
    private String worldSetting;
    private String prompt;
    private String styleCardId;
    private String llmConfigId;
}
