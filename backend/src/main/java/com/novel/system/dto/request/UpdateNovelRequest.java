package com.novel.system.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 更新小说请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateNovelRequest {
    
    /** 小说名称 */
    private String novelName;
    
    /** 小说题材 */
    private String genre;

    /** 小说类型标签（JSON数组） */
    private String novelType;

    /** 故事基调（JSON数组） */
    private String tone;

    /** 禁止雷点（JSON数组） */
    private String tabooList;

    /** 小说总纲 */
    private String totalOutline;
    
    /** 世界观设定 */
    private String worldSetting;
    
    /** 状态：0-草稿，1-连载中，2-已完结 */
    private Integer status;
}
