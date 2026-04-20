package com.novel.system.entity.mongo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志文档 (MongoDB)
 */
@Document(collection = "audit_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    
    /** MongoDB文档ID */
    @Id
    private String id;
    
    /** 所属小说ID */
    @Indexed
    private String novelId;
    
    /** 审计的章节号 */
    @Indexed
    private Integer chapterNum;
    
    /** 审计类型：consistency-一致性/ooc-人设偏离/logic-逻辑/clue-伏笔 */
    private String auditType;
    
    /** 审计结果：pass-通过/fail-未通过 */
    private String result;
    
    /** 详细审计报告文本 */
    private String report;
    
    /** 发现的问题列表 */
    private List<AuditIssue> issues;
    
    /** 修复建议列表 */
    private List<String> suggestions;
    
    /** 用户是否采纳审计建议 */
    private Boolean adopted;
    
    /** 创建时间 */
    private LocalDateTime createTime;
    
    /**
     * 审计问题
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditIssue {
        /** 问题类型：ooc-人设偏离/logic-逻辑/clue-伏笔/setting-设定/rhythm-节奏 */
        private String type;
        /** 问题描述 */
        private String description;
        /** 严重程度：high-高/medium-中/low-低 */
        private String severity;
        /** 问题在文中的位置 */
        private String location;
    }
}
