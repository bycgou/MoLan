package com.novel.system.dto.vo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 生成上下文DTO - 用于Redis序列化
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerationContext {
    /** 小说ID */
    private String novelId;
    /** 小说题材 */
    private String genre;
    /** 小说总纲 */
    private String totalOutline;
    /** 当前卷大纲 */
    private String arcOutline;
    /** 当前卷名称 */
    private String arcName;
    /** 世界观设定 */
    private String worldSetting;
    /** 人物状态JSON字符串 */
    private String characterStates;
    /** 未回收的伏笔列表 */
    private List<String> unrecycledClues;
    /** 当前章节大纲 */
    private String outline;
    /** 实现思路列表（AI生成的多个写作方向） */
    private List<String> implementationIdeas;
    /** 上一章剧情指纹 */
    private String lastChapterFingerprint;
}
