package com.novel.system.ai.graph;

import com.novel.system.ai.chain.ChapterGenerationChain;
import com.novel.system.entity.mysql.LlmConfig;
import com.novel.system.entity.mysql.NovelBase;
import com.novel.system.entity.mysql.NovelChapter;
import com.novel.system.mapper.NovelBaseMapper;
import com.novel.system.service.chapter.ContextBuilderService;
import com.novel.system.service.chapter.TokenBudgetService;
import com.novel.system.service.llm.LlmConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 正文生成节点 — 复用 WritingAgent + 增强上下文
 */
@Slf4j
@Component
public class WritingNode implements NovelGraphNode {

    private final ChapterGenerationChain chain;
    private final ContextBuilderService contextBuilderService;
    private final TokenBudgetService tokenBudgetService;
    private final NovelBaseMapper novelBaseMapper;
    private final LlmConfigService llmConfigService;
    /** 去标签化：把角色动线从「角色定位：X」改成叙述句，避免模型照抄成提纲腔。 */
    private final boolean decontextualizePlan;

    public WritingNode(ChapterGenerationChain chain,
                       ContextBuilderService contextBuilderService,
                       TokenBudgetService tokenBudgetService,
                       NovelBaseMapper novelBaseMapper,
                       LlmConfigService llmConfigService,
                       @org.springframework.beans.factory.annotation.Value(
                               "${writing.decontextualize-plan:false}") boolean decontextualizePlan) {
        this.chain = chain;
        this.contextBuilderService = contextBuilderService;
        this.tokenBudgetService = tokenBudgetService;
        this.novelBaseMapper = novelBaseMapper;
        this.llmConfigService = llmConfigService;
        this.decontextualizePlan = decontextualizePlan;
    }

    @Override
    public String nodeName() { return "writing"; }

    @Override
    public NovelAgentState resetState(NovelAgentState state, String source) {
        NovelAgentState.DraftContent draft = state.getDraftContent();
        if (draft != null) {
            return state.toBuilder()
                    .draftContent(draft.toBuilder()
                            .severity(NovelAgentState.Severity.HIGH)
                            .build())
                    .build();
        }
        return state;
    }

    @Override
    public ErrorStrategy errorStrategy() { return ErrorStrategy.RETRY; }

    @Override
    public int maxRetries() { return 2; }

    @Override
    public boolean shouldSkip(NovelAgentState state) {
        return state.getDraftContent() != null
                && state.getDraftContent().getSeverity() != NovelAgentState.Severity.HIGH;
    }

    @Override
    public NovelAgentState apply(NovelAgentState state) throws Exception {
        log.info("[{}] 生成正文, iterationCount={}, novelId={}, chapter={}",
                nodeName(), state.getIterationCount(), state.getNovelId(), state.getChapterNum());

        NovelAgentState.ChapterPlan plan = state.getChapterPlan();
        if (plan == null) {
            log.error("[{}] chapterPlan 为 null，无法生成正文。novelId={}, chapter={}",
                    nodeName(), state.getNovelId(), state.getChapterNum());
            throw new RuntimeException("章纲数据缺失，无法生成正文");
        }
        if (plan.getSummary() == null || plan.getSummary().isEmpty()) {
            log.error("[{}] chapterPlan.summary 为空，无法生成正文。novelId={}, chapter={}, plan.title={}",
                    nodeName(), state.getNovelId(), state.getChapterNum(), plan.getTitle());
            throw new RuntimeException("章纲摘要为空，无法生成正文");
        }

        log.info("[{}] 章纲摘要长度={}, styleCardId={}, llmConfigId={}",
                nodeName(), plan.getSummary().length(), state.getStyleCardId(), state.getLlmConfigId());

        // 复用 state 中的 outlineContext，构建 writingContext
        String writingContext = state.getWritingContext();
        if (writingContext == null) {
            NovelBase novel = novelBaseMapper.selectById(state.getNovelId());
            NovelChapter tempChapter = new NovelChapter();
            tempChapter.setChapterOutline(plan.getSummary());
            writingContext = contextBuilderService.buildWritingContext(
                    state.getNovelId(), state.getChapterNum(),
                    tempChapter, novel, state.getSelectedIdea());
        }
        log.info("[{}] writingContext 构建完成, 长度={}", nodeName(), writingContext != null ? writingContext.length() : 0);

        // 增强上下文
        String enhancedContext = enhanceContext(writingContext, plan, state);
        log.info("[{}] enhancedContext 构建完成, 长度={}", nodeName(), enhancedContext.length());

        // 统一使用6参数版本，有 SseEmitter 时走流式
        String content;
        if (state.getSseEmitter() != null) {
            log.info("[{}] 开始流式写作生成...", nodeName());
            content = chain.executeWritingChainStreaming(
                    state.getNovelId(), state.getChapterNum(),
                    plan.getSummary(), enhancedContext,
                    state.getStyleCardId(), state.getLlmConfigId(),
                    state.getSseEmitter());
        } else {
            log.info("[{}] 开始非流式写作生成...", nodeName());
            content = chain.executeWritingChain(
                    state.getNovelId(), state.getChapterNum(),
                    plan.getSummary(), enhancedContext,
                    state.getStyleCardId(), state.getLlmConfigId());
        }
        log.info("[{}] 写作生成完成, 内容长度={}", nodeName(), content != null ? content.length() : 0);

        NovelAgentState updated = state.toBuilder()
                .writingContext(writingContext)
                .draftContent(NovelAgentState.DraftContent.builder()
                        .content(content)
                        .wordCount(content.replaceAll("\\s+", "").length())
                        .build())
                // 建议消费一次即失效，避免下一轮重复注入同一批问题
                .pendingRevisionNotes(null)
                .currentPhase(NovelAgentState.GenerationPhase.WRITING.name())
                .build();

        return updated;
    }

    /**
     * 修订意见的来源：门禁与自审写进 {@code pendingRevisionNotes}，
     * 老字段 {@code draftContent.revisionNotes} 作为回退。
     */
    private List<String> resolveRevisionNotes(NovelAgentState state) {
        if (state.getPendingRevisionNotes() != null && !state.getPendingRevisionNotes().isEmpty()) {
            return state.getPendingRevisionNotes();
        }
        if (state.getDraftContent() != null) {
            return state.getDraftContent().getRevisionNotes();
        }
        return null;
    }

    private String taggedCharacterLine(NovelAgentState.CharacterAction action) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("【%s】\n", action.getCharacterName()));
        sb.append(String.format("  角色定位：%s\n", action.getRole()));
        sb.append(String.format("  本章目标：%s\n", action.getGoal()));
        sb.append(String.format("  情绪变化：%s\n", action.getEmotionalState()));
        sb.append(String.format("  关键行为：%s\n", action.getKeyAction()));
        if (action.getInteractions() != null) {
            sb.append(String.format("  互动：%s\n", String.join("、", action.getInteractions())));
        }
        return sb.toString();
    }

    /**
     * 去标签化版本：把规划条目写成叙述句，模型不容易照抄成提纲腔。
     */
    private String narrativeCharacterLine(NovelAgentState.CharacterAction action) {
        StringBuilder sb = new StringBuilder();
        sb.append(action.getCharacterName());
        if (action.getRole() != null) {
            sb.append("（").append(action.getRole()).append("）");
        }
        if (action.getGoal() != null) {
            sb.append("这一章要做的是").append(action.getGoal());
        }
        if (action.getEmotionalState() != null) {
            sb.append("，情绪变化是").append(action.getEmotionalState());
        }
        if (action.getKeyAction() != null) {
            sb.append("，关键动作是").append(action.getKeyAction());
        }
        if (action.getInteractions() != null && !action.getInteractions().isEmpty()) {
            sb.append("，和").append(String.join("、", action.getInteractions())).append("有交集");
        }
        sb.append("。\n");
        return sb.toString();
    }

    private String enhanceContext(String baseContext, NovelAgentState.ChapterPlan plan,
                                   NovelAgentState state) {
        int maxTokens = 8192;
        try {
            LlmConfig config = llmConfigService.getConfigById(state.getLlmConfigId());
            if (config != null && config.getMaxTokens() != null && config.getMaxTokens() > 0) {
                maxTokens = config.getMaxTokens();
            }
        } catch (Exception e) {
            log.warn("[{}] 无法获取LLM配置, 使用默认maxTokens={}", nodeName(), maxTokens);
        }
        TokenBudgetService.TokenBudget budget = tokenBudgetService.calculateBudget(maxTokens);
        StringBuilder sb = new StringBuilder(baseContext);

        // 角色动线
        if (plan.getCharacterActions() != null && !plan.getCharacterActions().isEmpty()) {
            StringBuilder charSb = new StringBuilder();
            charSb.append("\n\n=== 角色动线（写作时必须遵循）===\n");
            for (NovelAgentState.CharacterAction action : plan.getCharacterActions()) {
                charSb.append(decontextualizePlan
                        ? narrativeCharacterLine(action)
                        : taggedCharacterLine(action));
            }
            sb.append(tokenBudgetService.truncateToBudget(charSb.toString(),
                    budget.getAllocated("characterState")));
        }

        // 伏笔操作
        if (plan.getClueActions() != null && !plan.getClueActions().isEmpty()) {
            StringBuilder clueSb = new StringBuilder();
            clueSb.append("\n\n=== 伏笔操作（写作时必须体现）===\n");
            for (NovelAgentState.ClueAction action : plan.getClueActions()) {
                String marker = action.isFromOrchestration() ? "[卷级编排]" : "[补充]";
                clueSb.append(String.format("%s[%s] %s: %s\n",
                        marker, action.getAction(), action.getClueId(), action.getDescription()));
            }
            sb.append(tokenBudgetService.truncateToBudget(clueSb.toString(),
                    budget.getAllocated("clueBook")));
        }

        // 修订意见（门禁 + 自审共用同一注入来源）
        List<String> revisionNotes = resolveRevisionNotes(state);
        if (revisionNotes != null && !revisionNotes.isEmpty()) {
            StringBuilder revSb = new StringBuilder();
            revSb.append("\n\n=== 待修订问题（请针对性修改，逐条处理）===\n");
            for (String note : revisionNotes) {
                revSb.append("- ").append(note).append("\n");
            }
            sb.append(revSb.toString());
        }

        // 情绪弧线
        if (plan.getEmotionalArc() != null) {
            sb.append("\n\n=== 情绪弧线 ===\n").append(plan.getEmotionalArc()).append("\n");
        }
        if (plan.getClimaxPoint() != null) {
            sb.append("\n=== 高潮点 ===\n").append(plan.getClimaxPoint()).append("\n");
        }

        return sb.toString();
    }
}
