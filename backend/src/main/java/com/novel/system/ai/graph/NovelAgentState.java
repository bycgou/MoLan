package com.novel.system.ai.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class NovelAgentState {

    // ==================== 请求输入 ====================
    private String novelId;
    private int chapterNum;
    private String userIntent;
    private String styleCardId;
    private String llmConfigId;

    // ==================== 上下文（构建一次，复用多次）====================
    private String outlineContext;
    private String writingContext;

    // ==================== Phase 1 输出 ====================
    private ChapterPlan chapterPlan;
    private PreWriteValidation preWriteValidation;

    // ==================== Phase 2 输出 ====================
    private String selectedIdea;
    private List<String> implementationIdeas;
    private DraftContent draftContent;

    // ==================== Phase 3 输出 ====================
    private AuditResult auditResult;

    // ==================== Phase 4 输出 ====================
    private FinalResult finalResult;

    // ==================== 流程控制 ====================
    @Builder.Default
    private int iterationCount = 0;
    @Builder.Default
    private int planRetryCount = 0;
    @Builder.Default
    private int writeRetryCount = 0;
    private String currentPhase;
    @Builder.Default
    private List<String> executionTrace = new ArrayList<>();
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    // ==================== 枚举 ====================

    public enum GenerationPhase {
        INIT, CONTEXT, ARC_CHECK, OUTLINE, CLUE_PLAN, CHAR_PLAN,
        PRE_WRITE_VALID, IDEA_SELECT, WRITING, SELF_REVIEW,
        VALIDATION, QUALITY_CHECK, AUDIT_ROUTER,
        SUMMARY, POST_PROCESS, COMPLETED, FAILED
    }

    public enum Severity {
        LOW, MEDIUM, HIGH
    }

    // ==================== 内嵌数据结构 ====================

    @Getter
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChapterPlan {
        private String title;
        private String summary;
        private List<String> keyPoints;
        private String hook;
        private List<String> implementationIdeas;
        private List<ClueAction> clueActions;
        private List<CharacterAction> characterActions;
        private String emotionalArc;
        private String climaxPoint;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClueAction {
        private String clueId;
        private String action;       // PLANT / ADVANCE / RESOLVE / SKIP
        private String description;
        private boolean fromOrchestration;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CharacterAction {
        private String characterName;
        private String characterId;
        private String role;           // PROTAGONIST / ANTAGONIST / SUPPORTING
        private String goal;
        private String emotionalState;
        private String keyAction;
        private List<String> interactions;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PreWriteValidation {
        private boolean passed;
        private List<String> violations;
        private List<String> warnings;
        private List<String> suggestions;
    }

    @Getter
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DraftContent {
        private String content;
        private int wordCount;
        @Builder.Default
        private Severity severity = Severity.LOW;
        private List<String> revisionNotes;
    }

    @Getter
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditResult {
        // 五重校验
        private boolean oocPassed;
        private int oocScore;
        private String oocDetail;
        private boolean logicPassed;
        private String logicDetail;
        private boolean cluePassed;
        private String clueDetail;
        private boolean settingPassed;
        private String settingDetail;
        private boolean rhythmPassed;
        private String rhythmDetail;
        // 质量评分
        private double qualityScore;
        private boolean qualityPassed;
        private String qualityAssessment;
        // 伏笔落地检查
        private boolean clueLandingPassed;
        private List<String> clueLandingIssues;
        // 综合
        private boolean allPassed;
        private String overallAssessment;
        private List<String> suggestions;
    }

    @Getter
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FinalResult {
        private String finalContent;
        private int wordCount;
        private double qualityScore;
        private String chapterId;
        private String summary;
    }
}
