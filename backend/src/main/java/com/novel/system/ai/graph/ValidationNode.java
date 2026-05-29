package com.novel.system.ai.graph;

import com.novel.system.ai.chain.*;
import com.novel.system.entity.mongo.CharacterState;
import com.novel.system.entity.mysql.ClueBook;
import com.novel.system.entity.mysql.NovelBase;
import com.novel.system.mapper.NovelBaseMapper;
import com.novel.system.service.character.CharacterStateService;
import com.novel.system.service.clue.ClueBookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 五重并行校验节点 — 完整映射5个维度
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ValidationNode implements NovelGraphNode {

    private final ValidationService validationService;
    private final NovelBaseMapper novelBaseMapper;
    private final CharacterStateService characterStateService;
    private final ClueBookService clueBookService;

    @Override
    public String nodeName() { return "validation"; }

    @Override
    public NovelAgentState apply(NovelAgentState state) throws Exception {
        log.info("[{}] 五重并行校验", nodeName());

        NovelAgentState.DraftContent draft = state.getDraftContent();
        if (draft == null || draft.getContent() == null) {
            log.warn("[{}] draft 为空，跳过校验", nodeName());
            return state.toBuilder()
                    .currentPhase(NovelAgentState.GenerationPhase.VALIDATION.name())
                    .build();
        }
        NovelAgentState.ChapterPlan plan = state.getChapterPlan();

        // 查询校验所需的完整上下文
        NovelBase novel = novelBaseMapper.selectById(state.getNovelId());

        String characterStatesText = buildCharacterStatesText(
                characterStateService.getCharactersByNovelId(state.getNovelId()));

        String clueBookText = buildClueBookText(
                clueBookService.getCluesByNovelId(state.getNovelId()));

        ValidationRequest request = new ValidationRequest();
        request.setNovelId(state.getNovelId());
        request.setChapterNum(state.getChapterNum());
        request.setChapterContent(draft.getContent());
        request.setChapterOutline(plan.getSummary());
        request.setTotalOutline(novel != null ? novel.getTotalOutline() : null);
        request.setWorldSetting(novel != null ? novel.getWorldSetting() : null);
        request.setCharacterStates(characterStatesText);
        request.setClueBook(clueBookText);
        request.setRhythmTag(state.getRhythmTag());

        ValidationResult result = validationService.validateAll(request);

        NovelAgentState.AuditResult.AuditResultBuilder auditBuilder = NovelAgentState.AuditResult.builder()
                .allPassed(result.getPassed());

        if (result.getOocResult() != null) {
            auditBuilder.oocPassed(result.getOocResult().isPassed())
                    .oocScore(result.getOocResult().getTotalScore())
                    .oocDetail(String.format("语言风格:%d, 行为动机:%d, 知识水平:%d, 成长适应:%d",
                            result.getOocResult().getLanguageStyleScore(),
                            result.getOocResult().getBehaviorMotivationScore(),
                            result.getOocResult().getKnowledgeLevelScore(),
                            result.getOocResult().getGrowthAdaptationScore()));
        }
        if (result.getLogicResult() != null) {
            auditBuilder.logicPassed(result.getLogicResult().isPassed())
                    .logicDetail(String.format("因果链:%s, 一致性:%s, 冲突:%s",
                            result.getLogicResult().getCausalChainStatus(),
                            result.getLogicResult().getConsistencyStatus(),
                            result.getLogicResult().getConflictDescription()));
        }
        if (result.getClueResult() != null) {
            auditBuilder.cluePassed(result.getClueResult().isPassed())
                    .clueDetail(String.format("埋设:%d, 回收:%d, 遗漏:%s, 不合理:%s",
                            result.getClueResult().getBuriedCount(),
                            result.getClueResult().getRecycledCount(),
                            result.getClueResult().getMissingClues(),
                            result.getClueResult().getUnnaturalRecycle()));
        }
        if (result.getSettingResult() != null) {
            auditBuilder.settingPassed(result.getSettingResult().isPassed())
                    .settingDetail(String.format("世界规则:%s, 时钟:%s, 冲突:%s",
                            result.getSettingResult().getWorldRuleStatus(),
                            result.getSettingResult().getWorldClockStatus(),
                            result.getSettingResult().getConflicts()));
        }
        if (result.getRhythmResult() != null) {
            auditBuilder.rhythmPassed(result.getRhythmResult().isPassed())
                    .rhythmDetail(String.format("预期:%s, 实际:%s",
                            result.getRhythmResult().getExpectedRhythm(),
                            result.getRhythmResult().getActualRhythm()));
        }

        List<String> suggestions = new ArrayList<>();
        if (!result.getPassed() && result.getSummary() != null) {
            suggestions.add(result.getSummary());
        }
        auditBuilder.suggestions(suggestions);

        NovelAgentState updated = state.toBuilder()
                .auditResult(auditBuilder.build())
                .currentPhase(NovelAgentState.GenerationPhase.VALIDATION.name())
                .build();

        return StateHelper.appendTrace(updated, nodeName());
    }

    private String buildCharacterStatesText(List<CharacterState> characters) {
        if (characters == null || characters.isEmpty()) return "无";
        return characters.stream()
                .map(c -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append(c.getCharacterName());
                    if (c.getRole() != null) sb.append("(").append(c.getRole()).append(")");
                    if (c.getCoreTraits() != null && !c.getCoreTraits().isEmpty()) {
                        sb.append(" 特质=").append(String.join(",", c.getCoreTraits()));
                    }
                    if (c.getVoiceStyle() != null) sb.append(" 语气=").append(c.getVoiceStyle());
                    CharacterState.CurrentStatus cs = c.getCurrentStatus();
                    if (cs != null) {
                        sb.append(" 状态=");
                        if (cs.getEmotion() != null) sb.append("情绪:").append(cs.getEmotion()).append(" ");
                        if (cs.getLocation() != null) sb.append("位置:").append(cs.getLocation()).append(" ");
                        if (cs.getStatus() != null) sb.append(cs.getStatus());
                    }
                    return sb.toString().trim();
                })
                .collect(Collectors.joining("\n"));
    }

    private String buildClueBookText(List<ClueBook> clues) {
        if (clues == null || clues.isEmpty()) return "无";
        return clues.stream()
                .map(c -> {
                    String priorityText = c.getPriority() != null ?
                            (c.getPriority() == 1 ? "高" : c.getPriority() == 2 ? "中" : "低") : "中";
                    String statusText = c.getStatus() != null && c.getStatus() == 1 ? "已回收" : "未回收";
                    return String.format("[优先级:%s] %s (状态:%s, 埋设章:%s, 回收章:%s)",
                            priorityText, c.getClueContent(), statusText,
                            c.getBuryChapter() != null ? c.getBuryChapter() : "?",
                            c.getRecycleChapter() != null ? c.getRecycleChapter() : "待定");
                })
                .collect(Collectors.joining("\n"));
    }
}
