package com.novel.system.ai.graph;

import com.novel.system.mapper.NovelChapterMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContinuityValidationNode implements NovelGraphNode {

    private static final int PREVIOUS_TAIL_CHARS = 1200;
    private static final int CURRENT_HEAD_CHARS = 800;
    private static final int MAX_REWRITE_BEFORE_FORCE_PASS = 1;
    private static final Pattern QUOTE_PATTERN = Pattern.compile("[\u201c\u201d\u300c\u300d\"]{1}[^ \t\r\n\u201c\u201d\u300c\u300d\"]{1,12}");

    private final NovelChapterMapper chapterMapper;

    @Override
    public String nodeName() { return "continuity_validation"; }

    @Override
    public boolean shouldSkip(NovelAgentState state) {
        return state.getMode() == Mode.FAST;
    }

    @Override
    public NovelAgentState apply(NovelAgentState state) throws Exception {
        log.info("[{}] 跨章连续性校验", nodeName());

        List<String> violations = new ArrayList<>();

        String prevTail = getPreviousChapterTail(state.getNovelId(), state.getChapterNum());
        String currentHead = getCurrentHead(state);

        if (state.getChapterPlan() != null && StringUtils.hasText(state.getChapterPlan().getHook())) {
            if (!containsRelatedPhrase(currentHead, state.getChapterPlan().getHook())) {
                violations.add("本章开头未明显承接章纲 hook");
            }
        }

        if (StringUtils.hasText(prevTail)) {
            String prevEndingPhrase = extractEndingPhrase(prevTail);
            if (StringUtils.hasText(prevEndingPhrase) && !containsRelatedPhrase(currentHead, prevEndingPhrase)) {
                violations.add("本章开头未承接上一章结尾关键句");
            }
        }

        NovelAgentState updated = StateHelper.appendTrace(state, nodeName());

        if (!violations.isEmpty()) {
            if (updated.getRewriteCount() < MAX_REWRITE_BEFORE_FORCE_PASS) {
                log.info("[{}] 未通过，触发一次定向重写: {}", nodeName(), violations);
                return updated.toBuilder()
                        .auditDecision("REWRITE")
                        .rewriteCount(updated.getRewriteCount() + 1)
                        .iterationCount(updated.getIterationCount() + 1)
                        .currentPhase(NovelAgentState.GenerationPhase.VALIDATION.name())
                        .build();
            }
            log.warn("[{}] 仍未通过，但已达连续性重试上限，继续后续校验: {}", nodeName(), violations);
        }

        return updated.toBuilder()
                .currentPhase(NovelAgentState.GenerationPhase.VALIDATION.name())
                .build();
    }

    private String getPreviousChapterTail(String novelId, int chapterNum) {
        if (chapterNum <= 1) {
            return null;
        }
        try {
            var prev = chapterMapper.selectByNovelIdAndChapterNum(novelId, chapterNum - 1);
            if (prev == null || !StringUtils.hasText(prev.getContent())) {
                return null;
            }
            String content = prev.getContent();
            return content.substring(Math.max(0, content.length() - PREVIOUS_TAIL_CHARS));
        } catch (Exception e) {
            log.warn("获取上一章结尾失败: {}", e.getMessage());
            return null;
        }
    }

    private String getCurrentHead(NovelAgentState state) {
        if (state.getDraftContent() != null && StringUtils.hasText(state.getDraftContent().getContent())) {
            String content = state.getDraftContent().getContent();
            return content.substring(0, Math.min(CURRENT_HEAD_CHARS, content.length()));
        }
        if (state.getFinalResult() != null && StringUtils.hasText(state.getFinalResult().getFinalContent())) {
            String content = state.getFinalResult().getFinalContent();
            return content.substring(0, Math.min(CURRENT_HEAD_CHARS, content.length()));
        }
        return null;
    }

    private String extractEndingPhrase(String prevTail) {
        String[] lines = prevTail.split("\\r?\\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                Matcher matcher = QUOTE_PATTERN.matcher(line);
                if (matcher.find()) {
                    return matcher.group();
                }
                if (line.length() > 30) {
                    return line.substring(line.length() - Math.min(60, line.length()));
                }
                return line;
            }
        }
        return null;
    }

    private boolean containsRelatedPhrase(String text, String phrase) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(phrase)) {
            return true;
        }
        String normalizedText = normalize(text);
        String normalizedPhrase = normalize(phrase);
        String[] keywords = normalizedPhrase.split("[，。、,.\s]+");
        int matchCount = 0;
        for (String keyword : keywords) {
            if (keyword.length() >= 2 && normalizedText.contains(keyword)) {
                matchCount++;
            }
        }
        return matchCount >= Math.max(1, Math.min(2, keywords.length));
    }

    private String normalize(String value) {
        return value.replaceAll("[\\s\u3000]+", "");
    }
}