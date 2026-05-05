package com.novel.system.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建小说请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateNovelRequest {
    
    /** 小说名称 */
    @NotBlank(message = "小说名称不能为空")
    @Size(max = 100, message = "小说名称不能超过100个字符")
    private String novelName;
    
    /** 小说题材（如：玄幻、言情、悬疑等） */
    private String genre;

    /** 小说类型标签（JSON数组，如["爽文","穿越"]） */
    private String novelType;

    /** 故事基调（JSON数组，如["轻松","温馨"]） */
    private String tone;

    /** 禁止雷点（JSON数组，如["NTR","虐主"]） */
    private String tabooList;

    /** 小说总纲 */
    private String totalOutline;
    
    /** 世界观设定 */
    private String worldSetting;
}
