package com.novel.system.ai.graph;

import com.novel.system.ai.agent.WritingAgent;
import com.novel.system.ai.chain.ChapterGenerationChain;
import com.novel.system.config.LangChainConfig;
import com.novel.system.entity.mysql.LlmConfig;
import com.novel.system.entity.mysql.NovelBase;
import com.novel.system.entity.mysql.NovelChapter;
import com.novel.system.mapper.NovelBaseMapper;
import com.novel.system.service.chapter.ContextBuilderService;
import com.novel.system.service.chapter.TokenBudgetService;
import com.novel.system.service.llm.LlmConfigService;
import com.novel.system.service.style.StyleCardService;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 正文生成节点 — 复用 WritingAgent + 增强上下文
 * 支持同步（FULL模式）和流式（FAST模式，通过SseEmitter）两种输出方式
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WritingNode implements NovelGraphNode {

    private final ChapterGenerationChain chain;
    private final ContextBuilderService contextBuilderService;
    private final TokenBudgetService tokenBudgetService;
    private final NovelBaseMapper novelBaseMapper;
    private final LangChainConfig langChainConfig;
    private final LlmConfigService llmConfigService;
    private final StyleCardService styleCardService;

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
        log.info("[{}] 生成正文, iterationCount={}, streaming={}",
                nodeName(), state.getIterationCount(), state.getSseEmitter() != null);

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

        String content;
        if (state.getSseEmitter() != null) {
            content = executeStreaming(state, plan.getSummary(), enhancedContext);
        } else {
            content = chain.executeWritingChain(
                    state.getNovelId(), state.getChapterNum(),
                    plan.getSummary(), enhancedContext,
                    state.getStyleCardId(), state.getLlmConfigId());
        }

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

    /**
     * 流式执行：通过 StreamingChatModel 逐token发送到SseEmitter，阻塞直到完成。
     * WritingNode 在 apply() 中同步返回完整内容，GraphRunner 继续执行后续节点。
     */
    private String executeStreaming(NovelAgentState state, String outline, String context) throws Exception {
        SseEmitter emitter = state.getSseEmitter();

        LlmConfig llmConfig = llmConfigService.getConfigById(state.getLlmConfigId());
        if (llmConfig == null) {
            throw new RuntimeException("找不到LLM配置: " + state.getLlmConfigId());
        }

        StreamingChatModel streamingModel = langChainConfig.createStreamingChatModel(llmConfig);

        // 构建系统消息（与 WritingAgent @SystemMessage 一致）
        String systemMessage = getBaseSystemMessage();

        // 构建用户消息（与 ChapterGenerationChain.executeWritingChain 6参数版本一致）
        StringBuilder userPromptBuilder = new StringBuilder();
        userPromptBuilder.append(String.format("小说ID: %s\n章节号: 第%d章\n\n",
                state.getNovelId(), state.getChapterNum()));

        // 注入文风卡片内容
        String styleCardId = state.getStyleCardId();
        if (styleCardId != null && !styleCardId.isEmpty()) {
            try {
                String styleInjection = styleCardService.buildStyleInjection(styleCardId);
                if (styleInjection != null && !styleInjection.isEmpty()) {
                    userPromptBuilder.append(styleInjection).append("\n\n");
                }
            } catch (Exception e) {
                log.warn("获取文风卡片失败: {}", e.getMessage());
            }
        }

        // 附加指令
        if (state.getUserIntent() != null && !state.getUserIntent().isBlank()) {
            userPromptBuilder.append("===用户附加指令===\n")
                    .append(state.getUserIntent().trim())
                    .append("\n\n");
        }

        userPromptBuilder.append(String.format(
                "===章纲===\n%s\n\n===上下文信息===\n%s\n\n请根据章纲生成第%d章正文。",
                outline, context, state.getChapterNum()));

        String userPrompt = userPromptBuilder.toString();

        // 流式调用，阻塞直到完成
        CompletableFuture<String> future = new CompletableFuture<>();
        StringBuilder fullContent = new StringBuilder();

        dev.langchain4j.data.message.SystemMessage sysMsg =
                dev.langchain4j.data.message.SystemMessage.from(systemMessage);
        dev.langchain4j.data.message.UserMessage userMsg =
                dev.langchain4j.data.message.UserMessage.from(userPrompt);

        streamingModel.chat(
                dev.langchain4j.model.chat.request.ChatRequest.builder()
                        .messages(sysMsg, userMsg)
                        .build(),
                new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String token) {
                        try {
                            fullContent.append(token);
                            emitter.send(SseEmitter.event().data(token));
                        } catch (Exception e) {
                            log.error("发送token失败: {}", e.getMessage());
                            future.completeExceptionally(e);
                        }
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse response) {
                        // 流式完成，发送 writing_complete 事件通知客户端
                        try {
                            String fullText = response.aiMessage().text();
                            fullContent.setLength(0);
                            fullContent.append(fullText);
                            emitter.send(SseEmitter.event().name("writing_complete").data(""));
                        } catch (Exception e) {
                            log.warn("发送writing_complete事件失败: {}", e.getMessage());
                        }
                        future.complete(fullContent.toString());
                    }

                    @Override
                    public void onError(Throwable error) {
                        log.error("流式生成失败: {}", error.getMessage());
                        future.completeExceptionally(error);
                    }
                });

        // 阻塞等待流式完成（最多10分钟）
        return future.get(10, TimeUnit.MINUTES);
    }

    /**
     * 从 WritingAgent 的 @SystemMessage 注解中提取系统指令。
     */
    private String getBaseSystemMessage() {
        try {
            dev.langchain4j.service.SystemMessage annotation =
                    WritingAgent.class.getMethod("generateChapter", String.class)
                            .getAnnotation(dev.langchain4j.service.SystemMessage.class);
            if (annotation == null) return "";
            String[] values = annotation.value();
            if (values == null || values.length == 0) return "";
            return String.join("", values).trim();
        } catch (Exception e) {
            log.warn("无法获取WritingAgent系统消息: {}", e.getMessage());
            return "";
        }
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
