package com.novel.system.ai.graph;

import com.novel.system.ai.chain.ChapterGenerationChain;
import com.novel.system.entity.mysql.NovelBase;
import com.novel.system.entity.mysql.NovelChapter;
import com.novel.system.mapper.NovelBaseMapper;
import com.novel.system.service.chapter.ContextBuilderService;
import com.novel.system.service.chapter.TokenBudgetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 正文生成节点 — 复用 WritingAgent + 增强上下文
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WritingNode implements NovelGraphNode {

    private final ChapterGenerationChain chain;
    private final ContextBuilderService contextBuilderService;
    private final TokenBudgetService tokenBudgetService;
    private final NovelBaseMapper novelBaseMapper;

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
        log.info("[{}] 生成正文, iterationCount={}", nodeName(), state.getIterationCount());

        NovelAgentState.ChapterPlan plan = state.getChapterPlan();

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

        // 增强上下文
        String enhancedContext = enhanceContext(writingContext, plan, state);

        // 统一使用6参数版本
        String content = chain.executeWritingChain(
                state.getNovelId(), state.getChapterNum(),
                plan.getSummary(), enhancedContext,
                state.getStyleCardId(), state.getLlmConfigId());

        NovelAgentState updated = state.toBuilder()
                .writingContext(writingContext)
                .draftContent(NovelAgentState.DraftContent.builder()
                        .content(content)
                        .wordCount(content.replaceAll("\\s+", "").length())
                        .build())
                .currentPhase(NovelAgentState.GenerationPhase.WRITING.name())
                .build();

        return updated;
    }

    private String enhanceContext(String baseContext, NovelAgentState.ChapterPlan plan,
                                   NovelAgentState state) {
        TokenBudgetService.TokenBudget budget = tokenBudgetService.calculateBudget(8192);
        StringBuilder sb = new StringBuilder(baseContext);

        // 角色动线
        if (plan.getCharacterActions() != null && !plan.getCharacterActions().isEmpty()) {
            StringBuilder charSb = new StringBuilder();
            charSb.append("\n\n=== 角色动线（写作时必须遵循）===\n");
            for (NovelAgentState.CharacterAction action : plan.getCharacterActions()) {
                charSb.append(String.format("【%s】\n", action.getCharacterName()));
                charSb.append(String.format("  角色定位：%s\n", action.getRole()));
                charSb.append(String.format("  本章目标：%s\n", action.getGoal()));
                charSb.append(String.format("  情绪变化：%s\n", action.getEmotionalState()));
                charSb.append(String.format("  关键行为：%s\n", action.getKeyAction()));
                if (action.getInteractions() != null) {
                    charSb.append(String.format("  互动：%s\n", String.join("、", action.getInteractions())));
                }
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

        // 修订意见（重写时注入）
        if (state.getDraftContent() != null && state.getDraftContent().getRevisionNotes() != null
                && !state.getDraftContent().getRevisionNotes().isEmpty()) {
            StringBuilder revSb = new StringBuilder();
            revSb.append("\n\n=== 上次自审修订意见（请针对性修改）===\n");
            for (String note : state.getDraftContent().getRevisionNotes()) {
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
