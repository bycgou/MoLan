package com.novel.system.service.memory;

import com.novel.system.entity.mongo.StoryState;
import com.novel.system.service.story.StoryStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConvergenceServiceImpl implements ConvergenceService {

    private final StoryStateService storyStateService;

    @Override
    public String formatForPrompt(String novelId) {
        StoryState state = storyStateService.getOrCreate(novelId);
        StoryState.ConvergenceState cs = state.getConvergenceState();

        if (cs == null || cs.getTotalChapters() == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== 叙事进度 ===\n");
        sb.append("当前第").append(cs.getCurrentChapter()).append("章/共")
                .append(cs.getTotalChapters()).append("章(")
                .append(String.format("%.0f", cs.getProgress() * 100)).append("%) → ")
                .append(getPhaseLabel(cs.getPhase())).append("\n");

        if (cs.getChaptersRemaining() > 0) {
            sb.append("距离完结: ").append(cs.getChaptersRemaining()).append("章\n");
        }

        if (cs.getOpenHooks() != null && !cs.getOpenHooks().isEmpty()) {
            sb.append("未闭合钩子: ");
            for (int i = 0; i < cs.getOpenHooks().size(); i++) {
                sb.append("[").append(i + 1).append("]").append(cs.getOpenHooks().get(i));
                if (i < cs.getOpenHooks().size() - 1) sb.append(" ");
            }
            sb.append("\n");
        }

        // Phase-specific guidance
        sb.append("建议: ").append(getPhaseGuidance(cs.getPhase())).append("\n");

        return sb.toString();
    }

    @Override
    public void updateConvergenceState(String novelId, int currentChapter, int totalChapters) {
        StoryState state = storyStateService.getOrCreate(novelId);

        double progress = totalChapters > 0 ? (double) currentChapter / totalChapters : 0;
        String phase = computePhase(progress);
        int remaining = Math.max(0, totalChapters - currentChapter);

        // Get open hooks from narrative flags
        java.util.List<String> openHooks = new java.util.ArrayList<>();
        if (state.getNarrativeFlags() != null
                && state.getNarrativeFlags().getUnresolvedCliffhangers() != null) {
            openHooks.addAll(state.getNarrativeFlags().getUnresolvedCliffhangers());
        }

        StoryState.ConvergenceState cs = new StoryState.ConvergenceState(
                currentChapter, totalChapters, progress, phase, openHooks, remaining);

        state.setConvergenceState(cs);
        storyStateService.save(state);
        log.info("Convergence state updated: novel={}, chapter={}/{}, phase={}",
                novelId, currentChapter, totalChapters, phase);
    }

    @Override
    public String getPhase(String novelId) {
        StoryState state = storyStateService.getOrCreate(novelId);
        if (state.getConvergenceState() != null) {
            return state.getConvergenceState().getPhase();
        }
        return "DEVELOPMENT";
    }

    private String computePhase(double progress) {
        if (progress < 0.25) return "OPENING";
        if (progress < 0.75) return "DEVELOPMENT";
        if (progress < 0.90) return "CONVERGENCE";
        return "FINALE";
    }

    private String getPhaseLabel(String phase) {
        return switch (phase) {
            case "OPENING" -> "开局期";
            case "DEVELOPMENT" -> "发展期";
            case "CONVERGENCE" -> "收束期";
            case "FINALE" -> "终章期";
            default -> "发展期";
        };
    }

    private String getPhaseGuidance(String phase) {
        return switch (phase) {
            case "OPENING" -> "侧重世界观建立、角色引入、基调设定";
            case "DEVELOPMENT" -> "冲突升级、支线展开，保持节奏张弛有度";
            case "CONVERGENCE" -> "加速回收伏笔，合并支线，提升紧张感，减少新支线引入";
            case "FINALE" -> "强制闭合所有开放钩子，高潮收束，不留悬案";
            default -> "保持节奏张弛有度";
        };
    }
}
