package com.novel.system.entity.mysql;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 章节历史版本实体
 */
@Data
public class NovelChapterVersion {
    /** 版本ID，主键 */
    private String versionId;
    /** 所属章节ID */
    private String chapterId;
    /** 所属小说ID */
    private String novelId;
    /** 章节序号 */
    private Integer chapterNum;
    /** 章节标题快照 */
    private String chapterTitle;
    /** 章节正文内容快照 */
    private String content;
    /** 版本字数 */
    private Integer wordCount = 0;
    /** 版本描述（如：手动修改前保存） */
    private String versionDesc;
    /** 版本创建时间 */
    private LocalDateTime createTime;
}
