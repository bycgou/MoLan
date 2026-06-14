package com.novel.system.ai.graph;

import com.novel.system.service.memory.FactLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class FactLockViolationNode implements NovelGraphNode {

    private final FactLockService factLockService;

    @Override
    public String nodeName() {
        return "fact_lock_violation";
    }

    @Override
    public boolean shouldSkip(NovelAgentState state) {
        // Skip in FAST mode — no fact checking needed for fast generation
        return state.getMode() == Mode.FAST;
    }

    @Override
    public ErrorStrategy errorStrategy() {
        return ErrorStrategy.SKIP;
    }

    @Override
    public NovelAgentState apply(NovelAgentState state) throws Exception {
        String content = state.getFinalResult() != null && state.getFinalResult().getFinalContent() != null
                ? state.getFinalResult().getFinalContent()
                : (state.getDraftContent() != null ? state.getDraftContent().getContent() : "");

        if (content.isEmpty()) {
            log.warn("No content to check for FactLock violations");
            return state;
        }

        FactLockService.FactLockViolationResult result =
                factLockService.detectViolations(state.getNovelId(), state.getChapterNum(), content);

        if (result.hasViolation()) {
            if ("HIGH".equals(result.severity())) {
                // HIGH severity: add revision notes to trigger rewrite
                log.warn("HIGH FactLock violation in novel {} chapter {}: {}",
                        state.getNovelId(), state.getChapterNum(), result.detail());

                if (state.getDraftContent() != null) {
                    var draft = state.getDraftContent().toBuilder()
                            .severity(NovelAgentState.Severity.HIGH)
                            .revisionNotes(List.of(
                                    "事实违规: " + result.detail(),
                                    "请修正违规内容后重新生成"
                            ))
                            .build();
                    return state.toBuilder()
                            .draftContent(draft)
                            .currentPhase("fact_lock_violation")
                            .build();
                }
            } else {
                // MEDIUM/LOW: add warnings for next chapter
                log.info("FactLock violation (severity={}) in novel {} chapter {}: {}",
                        result.severity(), state.getNovelId(), state.getChapterNum(), result.detail());
            }
        }

        return state.toBuilder().currentPhase("fact_lock_checked").build();
    }
}
