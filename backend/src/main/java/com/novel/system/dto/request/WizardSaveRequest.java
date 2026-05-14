package com.novel.system.dto.request;

import com.novel.system.dto.response.ArcOutlineDTO;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.List;

/**
 * 向导保存请求DTO - Step 3 输入（用户确认卷纲后保存）
 */
@Data
public class WizardSaveRequest {
    @NotBlank(message = "小说名称不能为空")
    private String novelName;
    private String genre;
    private String novelType;
    private String tone;
    private String tabooList;
    private String totalOutline;
    private String worldSetting;
    private String styleCardId;
    private String llmConfigId;
    private List<ArcOutlineDTO> arcs;
}
