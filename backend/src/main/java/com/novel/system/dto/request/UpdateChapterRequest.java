package com.novel.system.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新章节请求DTO
 * 仅暴露允许修改的字段，防止越权修改 novelId、isDeleted 等敏感字段
 */
@Data
@NoArgsConstructor
public class UpdateChapterRequest {

    /** 章节标题 */
    private String chapterTitle;

    /** 章节正文内容 */
    private String content;

    /** 剧情指纹（300字摘要，用于RAG检索） */
    private String plotFingerprint;
}
