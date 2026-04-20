package com.novel.system.entity.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

/**
 * 小说记忆文档 (MongoDB) - 压缩后的全局背景摘要
 */
@Data
@Document(collection = "novel_memory")
public class NovelMemory {
    /** MongoDB文档ID */
    @Id
    private String id;
    
    /** 所属小说ID */
    private String novelId;
    
    /** 记忆覆盖的起始章节号 */
    private Integer startChapter;
    
    /** 记忆覆盖的结束章节号 */
    private Integer endChapter;
    
    /** 压缩后的全局背景摘要文本 */
    private String compressedContent;
    
    /** 创建时间 */
    private LocalDateTime createTime = LocalDateTime.now();
    
    // Explicit getters/setters (in case Lombok @Data is not working)
    public String getCompressedContent() { return compressedContent; }
    public Integer getEndChapter() { return endChapter; }
    public void setNovelId(String novelId) { this.novelId = novelId; }
    public void setStartChapter(Integer startChapter) { this.startChapter = startChapter; }
    public void setEndChapter(Integer endChapter) { this.endChapter = endChapter; }
    public void setCompressedContent(String compressedContent) { this.compressedContent = compressedContent; }
}
