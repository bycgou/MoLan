package com.novel.system.ai.graph;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.util.JsonExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorAgentImpl implements AuthorAgent {

    private final ChatModelProvider chatModelProvider;
    private final ObjectMapper objectMapper;
    private final AuthorAgentConfig authorConfig;

    private static final Set<String> LLM_CHECKPOINTS = Set.of(
            "outline", "writing", "audit_router"
    );

    @Override
    public AuthorDirective beforeNode(String nodeName, NovelAgentState state) {
        if (!authorConfig.isEnabled() || !authorConfig.getCheckpoints().contains(nodeName)) {
            return AuthorDirective.builder()
                    .action(AuthorDirective.Action.APPROVE)
                    .build();
        }

        if (!LLM_CHECKPOINTS.contains(nodeName)) {
            return AuthorDirective.builder()
                    .action(AuthorDirective.Action.APPROVE)
                    .build();
        }

        log.info("[AuthorAgent] beforeNode 检查点: {}", nodeName);

        try {
            String prompt = buildBeforePrompt(nodeName, state);
            String response = chatModelProvider.getStructuredModel(state.getLlmConfigId()).chat(prompt);
            AuthorDirective directive = parseDirective(response);
            directive.setTargetNode(nodeName);

            if (directive.shouldIntervene()) {
                log.info("[AuthorAgent] 介入: action={}, rationale={}",
                        directive.getAction(), directive.getRationale());
            }

            return directive;
        } catch (Exception e) {
            log.error("[AuthorAgent] beforeNode 失败: {} - {}", nodeName, e.getMessage());
            return AuthorDirective.builder()
                    .action(AuthorDirective.Action.APPROVE)
                    .build();
        }
    }

    @Override
    public NovelAgentState afterNode(String nodeName, NovelAgentState state, AuthorDirective directive) {
        if (directive == null || !directive.shouldIntervene()) {
            return state.toBuilder()
                    .authorDirective(directive)
                    .build();
        }

        log.info("[AuthorAgent] afterNode 处理: node={}, action={}", nodeName, directive.getAction());

        NovelAgentState.NovelAgentStateBuilder builder = state.toBuilder()
                .authorDirective(directive);

        switch (directive.getAction()) {
            case REVISE:
                builder = applyRevisionDirective(builder, nodeName, directive);
                break;
            case REJECT:
                builder = applyRejectionDirective(builder, nodeName, directive);
                break;
            case REDIRECT:
                builder = applyRedirectDirective(builder, directive);
                break;
            default:
                break;
        }

        return builder.build();
    }

    private NovelAgentState.NovelAgentStateBuilder applyRevisionDirective(
            NovelAgentState.NovelAgentStateBuilder builder, String nodeName, AuthorDirective directive) {

        if ("outline".equals(nodeName) && builder.build().getChapterPlan() != null) {
            NovelAgentState.ChapterPlan plan = builder.build().getChapterPlan();
            List<String> notes = directive.getCreativeNotes();
            String revisionHint = notes != null && !notes.isEmpty()
                    ? String.join("；", notes) : directive.getRationale();

            builder.chapterPlan(plan.toBuilder()
                    .summary(plan.getSummary() + "\n【作者修订】" + revisionHint)
                    .build());
        }

        if ("writing".equals(nodeName) && builder.build().getDraftContent() != null) {
            NovelAgentState.DraftContent draft = builder.build().getDraftContent();
            List<String> existingNotes = draft.getRevisionNotes() != null
                    ? new ArrayList<>(draft.getRevisionNotes()) : new ArrayList<>();
            if (directive.getCreativeNotes() != null) {
                existingNotes.addAll(directive.getCreativeNotes());
            }
            builder.draftContent(draft.toBuilder()
                    .severity(NovelAgentState.Severity.HIGH)
                    .revisionNotes(existingNotes)
                    .build());
        }

        return builder;
    }

    private NovelAgentState.NovelAgentStateBuilder applyRejectionDirective(
            NovelAgentState.NovelAgentStateBuilder builder, String nodeName, AuthorDirective directive) {

        if ("outline".equals(nodeName)) {
            builder.chapterPlan(null)
                    .preWriteValidation(null);
        }

        if ("writing".equals(nodeName)) {
            NovelAgentState.DraftContent draft = builder.build().getDraftContent();
            if (draft != null) {
                builder.draftContent(draft.toBuilder()
                        .severity(NovelAgentState.Severity.HIGH)
                        .revisionNotes(List.of("作者否决: " + directive.getRationale()))
                        .build());
            }
        }

        if ("audit_router".equals(nodeName)) {
            builder.auditDecision("REWRITE")
                    .rewriteCount(builder.build().getRewriteCount() + 1);
        }

        return builder;
    }

    private NovelAgentState.NovelAgentStateBuilder applyRedirectDirective(
            NovelAgentState.NovelAgentStateBuilder builder, AuthorDirective directive) {

        String target = directive.getTargetNode();
        if (target != null) {
            builder.auditDecision("REPLAN");
            if ("outline".equals(target)) {
                builder.chapterPlan(null)
                        .preWriteValidation(null);
            }
        }

        return builder;
    }

    private String buildBeforePrompt(String nodeName, NovelAgentState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是小说的作者和创意总监。你正在监督AI生成第").append(state.getChapterNum()).append("章。\n\n");

        if (state.getUserIntent() != null && !state.getUserIntent().isEmpty()) {
            sb.append("【作者意图】").append(state.getUserIntent()).append("\n\n");
        }

        if (state.getAuthorDirective() != null && state.getAuthorDirective().getNarrativeDirection() != null) {
            sb.append("【叙事方向】").append(state.getAuthorDirective().getNarrativeDirection()).append("\n\n");
        }

        switch (nodeName) {
            case "outline" -> sb.append(buildOutlineCheckpoint(state));
            case "writing" -> sb.append(buildWritingCheckpoint(state));
            case "audit_router" -> sb.append(buildAuditCheckpoint(state));
        }

        sb.append("""
                
                作为作者，请判断当前状态是否符合你的创作意图。
                输出JSON：
                {"action":"APPROVE/REVISE/REJECT/REDIRECT",
                 "rationale":"理由",
                 "creativeNotes":["创作建议1"],
                 "styleAdjustment":"文风调整方向",
                 "narrativeDirection":"叙事方向指引"}
                
                action说明：
                - APPROVE: 符合意图，继续
                - REVISE: 方向对但需微调，注入creativeNotes到后续流程
                - REJECT: 方向错误，需要重做
                - REDIRECT: 需要跳转到其他阶段重做
                """);

        return sb.toString();
    }

    private String buildOutlineCheckpoint(NovelAgentState state) {
        NovelAgentState.ChapterPlan plan = state.getChapterPlan();
        if (plan == null) return "【检查点】即将生成章纲，请预判方向。\n";

        return String.format("""
                【检查点】章纲已生成，请审查：
                标题：%s
                概要：%s
                要点：%s
                钩子：%s
                角色动线：%s
                伏笔操作：%s
                """,
                plan.getTitle(), plan.getSummary(),
                plan.getKeyPoints() != null ? String.join("；", plan.getKeyPoints()) : "无",
                plan.getHook(),
                formatCharacterActions(plan.getCharacterActions()),
                formatClueActions(plan.getClueActions()));
    }

    private String buildWritingCheckpoint(NovelAgentState state) {
        NovelAgentState.DraftContent draft = state.getDraftContent();
        NovelAgentState.ChapterPlan plan = state.getChapterPlan();
        if (draft == null) return "【检查点】即将生成正文。\n";

        String contentPreview = draft.getContent() != null && draft.getContent().length() > 3000
                ? draft.getContent().substring(0, 3000) + "..."
                : draft.getContent();

        return String.format("""
                【检查点】正文已生成，请审查：
                章纲概要：%s
                字数：%d
                自审严重度：%s
                修订意见：%s
                正文预览：
                %s
                """,
                plan != null ? plan.getSummary() : "无",
                draft.getWordCount(),
                draft.getSeverity(),
                draft.getRevisionNotes() != null ? String.join("；", draft.getRevisionNotes()) : "无",
                contentPreview);
    }

    private String buildAuditCheckpoint(NovelAgentState state) {
        NovelAgentState.AuditResult audit = state.getAuditResult();
        if (audit == null) return "【检查点】审校结果尚未生成。\n";

        return String.format("""
                【检查点】审校完成，请做最终决策：
                OOC: %s (分:%d)
                逻辑: %s
                伏笔: %s
                设定: %s
                节奏: %s
                质量: %.1f
                综合通过: %s
                建议: %s
                """,
                audit.isOocPassed() ? "通过" : "未通过", audit.getOocScore(),
                audit.isLogicPassed() ? "通过" : "未通过",
                audit.isCluePassed() ? "通过" : "未通过",
                audit.isSettingPassed() ? "通过" : "未通过",
                audit.isRhythmPassed() ? "通过" : "未通过",
                audit.getQualityScore(),
                audit.isAllPassed() ? "是" : "否",
                audit.getSuggestions() != null ? String.join("；", audit.getSuggestions()) : "无");
    }

    private AuthorDirective parseDirective(String json) {
        try {
            String cleaned = JsonExtractor.extractJson(json);
            JsonNode node = objectMapper.readTree(cleaned);

            AuthorDirective.Action action;
            try {
                action = AuthorDirective.Action.valueOf(node.path("action").asText("APPROVE"));
            } catch (IllegalArgumentException e) {
                action = AuthorDirective.Action.APPROVE;
            }

            List<String> notes = new ArrayList<>();
            if (node.has("creativeNotes") && node.get("creativeNotes").isArray()) {
                node.get("creativeNotes").forEach(n -> notes.add(n.asText()));
            }

            return AuthorDirective.builder()
                    .action(action)
                    .rationale(node.path("rationale").asText(""))
                    .creativeNotes(notes)
                    .styleAdjustment(node.path("styleAdjustment").asText(null))
                    .narrativeDirection(node.path("narrativeDirection").asText(null))
                    .build();
        } catch (Exception e) {
            log.error("[AuthorAgent] 解析指令失败: {}", e.getMessage());
            return AuthorDirective.builder()
                    .action(AuthorDirective.Action.APPROVE)
                    .build();
        }
    }

    private String formatCharacterActions(List<NovelAgentState.CharacterAction> actions) {
        if (actions == null || actions.isEmpty()) return "无";
        StringBuilder sb = new StringBuilder();
        for (NovelAgentState.CharacterAction a : actions) {
            sb.append(String.format("%s(%s): %s", a.getCharacterName(), a.getRole(), a.getKeyAction()));
        }
        return sb.toString();
    }

    private String formatClueActions(List<NovelAgentState.ClueAction> actions) {
        if (actions == null || actions.isEmpty()) return "无";
        StringBuilder sb = new StringBuilder();
        for (NovelAgentState.ClueAction a : actions) {
            sb.append(String.format("[%s] %s: %s", a.getAction(), a.getClueId(), a.getDescription()));
        }
        return sb.toString();
    }
}
