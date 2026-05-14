package com.novel.system.service.story;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.entity.mongo.StoryState;
import com.novel.system.repository.mongo.StoryStateRepository;
import com.novel.system.service.llm.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 故事状态服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StoryStateServiceImpl implements StoryStateService {

    private final StoryStateRepository storyStateRepository;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    @Override
    public StoryState getOrCreate(String novelId) {
        return storyStateRepository.findByNovelId(novelId)
            .orElseGet(() -> createDefaultState(novelId));
    }

    @Override
    public StoryState save(StoryState storyState) {
        storyState.setUpdateTime(LocalDateTime.now());
        return storyStateRepository.save(storyState);
    }

    @Override
    public StoryState updateAfterChapter(String novelId, int chapterNum, String chapterSummary, String chapterContent) {
        StoryState state = getOrCreate(novelId);

        // 递增版本号
        state.setVersion(state.getVersion() != null ? state.getVersion() + 1 : 1);
        state.setLastChapter(chapterNum);
        state.setUpdateTime(LocalDateTime.now());

        // 追加到近期事件（保留最近10条）
        if (state.getRecentMajorEvents() == null) {
            state.setRecentMajorEvents(new ArrayList<>());
        }
        state.getRecentMajorEvents().add(new StoryState.MajorEvent(chapterNum, chapterSummary));
        if (state.getRecentMajorEvents().size() > 10) {
            state.setRecentMajorEvents(
                state.getRecentMajorEvents().subList(
                    state.getRecentMajorEvents().size() - 10,
                    state.getRecentMajorEvents().size()
                )
            );
        }

        // 更新节奏状态
        if (state.getPacingState() == null) {
            state.setPacingState(new StoryState.PacingState());
        }
        state.getPacingState().setChaptersSinceLastClimax(
            state.getPacingState().getChaptersSinceLastClimax() != null
                ? state.getPacingState().getChaptersSinceLastClimax() + 1
                : 0
        );
        state.getPacingState().setChaptersSinceLastRest(
            state.getPacingState().getChaptersSinceLastRest() != null
                ? state.getPacingState().getChaptersSinceLastRest() + 1
                : 0
        );

        // 使用 LLM 从章节内容中提取结构化状态变化
        if (chapterContent != null && !chapterContent.isEmpty()) {
            extractAndUpdateState(state, chapterContent);
        }

        // 背景压缩触发条件：每20章触发一次
        if (state.getLastCompressChapter() == null) {
            state.setLastCompressChapter(0);
        }
        if (chapterNum - state.getLastCompressChapter() >= 20) {
            compressBackstory(state, chapterNum);
        }

        log.info("更新故事状态: novelId={}, chapter={}, version={}", novelId, chapterNum, state.getVersion());
        return save(state);
    }

    /**
     * 使用 LLM 从章节内容中提取状态变化并更新 StoryState
     */
    @SuppressWarnings("unchecked")
    private void extractAndUpdateState(StoryState state, String chapterContent) {
        try {
            // 截取内容避免 token 超限
            String contentForExtraction = chapterContent.length() > 3000
                ? chapterContent.substring(0, 3000) : chapterContent;

            String currentStateDesc = formatCurrentStateForExtraction(state);

            String systemPrompt = "你是一个小说状态分析助手。请从章节内容中提取故事状态变化。" +
                "只返回JSON，不要其他内容。";

            String userMessage = String.format(
                "当前状态：%s\n\n章节内容：\n%s\n\n" +
                "请返回JSON对象，包含以下字段（仅返回有变化的字段，无变化的字段省略）：\n" +
                "{\n" +
                "  \"timelineUpdate\": {\"worldDate\": \"故事内日期\", \"season\": \"季节\", \"timeOfDay\": \"时段\"},\n" +
                "  \"protagonistUpdate\": {\"location\": \"位置\", \"mood\": \"情绪\", \"powerLevel\": \"实力等级\", " +
                "\"currentGoal\": \"当前目标\", \"physicalCondition\": \"身体状况\", \"inventory\": [\"物品1\", \"物品2\"]},\n" +
                "  \"sceneContextUpdate\": {\"activeCharacters\": [{\"id\": \"\", \"name\": \"\", \"state\": \"\"}], " +
                "\"immediateThreat\": \"即时威胁\", \"lastMajorEvent\": \"本章重大事件描述\"},\n" +
                "  \"narrativeFlagsUpdate\": {\"currentPOV\": \"当前叙事视角角色名\", " +
                "\"unresolvedCliffhangers\": [\"悬念1\"], \"recentReveals\": [\"揭露1\"]}\n" +
                "}",
                currentStateDesc, contentForExtraction);

            String response = llmService.generateWithUsage(systemPrompt, userMessage, "summary");

            // 解析 JSON 响应
            String jsonStr = extractJson(response);
            Map<String, Object> extraction = objectMapper.readValue(jsonStr, new TypeReference<>() {});

            // 更新 timeline
            if (extraction.containsKey("timelineUpdate")) {
                Map<String, String> tl = (Map<String, String>) extraction.get("timelineUpdate");
                if (state.getTimeline() == null) {
                    state.setTimeline(new StoryState.TimelineState());
                }
                if (tl.containsKey("worldDate")) state.getTimeline().setWorldDate(tl.get("worldDate"));
                if (tl.containsKey("season")) state.getTimeline().setSeason(tl.get("season"));
                if (tl.containsKey("timeOfDay")) state.getTimeline().setTimeOfDay(tl.get("timeOfDay"));
            }

            // 更新 protagonist
            if (extraction.containsKey("protagonistUpdate")) {
                Map<String, Object> p = (Map<String, Object>) extraction.get("protagonistUpdate");
                if (state.getProtagonist() == null) {
                    state.setProtagonist(new StoryState.ProtagonistState());
                }
                if (p.containsKey("name")) state.getProtagonist().setName((String) p.get("name"));
                if (p.containsKey("location")) state.getProtagonist().setLocation((String) p.get("location"));
                if (p.containsKey("mood")) state.getProtagonist().setMood((String) p.get("mood"));
                if (p.containsKey("powerLevel")) state.getProtagonist().setPowerLevel((String) p.get("powerLevel"));
                if (p.containsKey("currentGoal")) state.getProtagonist().setCurrentGoal((String) p.get("currentGoal"));
                if (p.containsKey("physicalCondition")) state.getProtagonist().setPhysicalCondition((String) p.get("physicalCondition"));
                if (p.containsKey("inventory")) {
                    state.getProtagonist().setInventory((List<String>) p.get("inventory"));
                }
            }

            // 更新 sceneContext
            if (extraction.containsKey("sceneContextUpdate")) {
                Map<String, Object> sc = (Map<String, Object>) extraction.get("sceneContextUpdate");
                if (state.getSceneContext() == null) {
                    state.setSceneContext(new StoryState.SceneContextState());
                }
                if (sc.containsKey("activeCharacters")) {
                    List<Map<String, String>> acList = (List<Map<String, String>>) sc.get("activeCharacters");
                    List<StoryState.ActiveCharacter> activeChars = new ArrayList<>();
                    for (Map<String, String> ac : acList) {
                        activeChars.add(new StoryState.ActiveCharacter(
                            ac.getOrDefault("id", ""),
                            ac.getOrDefault("name", ""),
                            ac.getOrDefault("state", "")
                        ));
                    }
                    state.getSceneContext().setActiveCharacters(activeChars);
                }
                if (sc.containsKey("immediateThreat")) state.getSceneContext().setImmediateThreat((String) sc.get("immediateThreat"));
                if (sc.containsKey("lastMajorEvent")) state.getSceneContext().setLastMajorEvent((String) sc.get("lastMajorEvent"));
            }

            // 更新 narrativeFlags
            if (extraction.containsKey("narrativeFlagsUpdate")) {
                Map<String, Object> nf = (Map<String, Object>) extraction.get("narrativeFlagsUpdate");
                if (state.getNarrativeFlags() == null) {
                    state.setNarrativeFlags(new StoryState.NarrativeFlagsState());
                }
                if (nf.containsKey("currentPOV")) state.getNarrativeFlags().setCurrentPOV((String) nf.get("currentPOV"));
                if (nf.containsKey("unresolvedCliffhangers")) {
                    state.getNarrativeFlags().setUnresolvedCliffhangers((List<String>) nf.get("unresolvedCliffhangers"));
                }
                if (nf.containsKey("recentReveals")) {
                    state.getNarrativeFlags().setRecentReveals((List<String>) nf.get("recentReveals"));
                }
            }

            log.info("LLM提取故事状态变化成功");

        } catch (Exception e) {
            log.warn("LLM提取故事状态变化失败（不影响基本更新）: {}", e.getMessage());
        }
    }

    /**
     * 格式化当前状态用于 LLM 提取提示
     */
    private String formatCurrentStateForExtraction(StoryState state) {
        StringBuilder sb = new StringBuilder();
        if (state.getTimeline() != null && state.getTimeline().getWorldDate() != null) {
            sb.append("时间：").append(state.getTimeline().getWorldDate());
            if (state.getTimeline().getSeason() != null) sb.append("，季节：").append(state.getTimeline().getSeason());
            if (state.getTimeline().getTimeOfDay() != null) sb.append("，时段：").append(state.getTimeline().getTimeOfDay());
            sb.append("\n");
        }
        if (state.getProtagonist() != null) {
            sb.append("主角：").append(state.getProtagonist().getName() != null ? state.getProtagonist().getName() : "未知");
            if (state.getProtagonist().getLocation() != null) sb.append("，位置：").append(state.getProtagonist().getLocation());
            if (state.getProtagonist().getMood() != null) sb.append("，情绪：").append(state.getProtagonist().getMood());
            if (state.getProtagonist().getPowerLevel() != null) sb.append("，实力：").append(state.getProtagonist().getPowerLevel());
            sb.append("\n");
        }
        if (state.getSceneContext() != null && state.getSceneContext().getLastMajorEvent() != null) {
            sb.append("上一重大事件：").append(state.getSceneContext().getLastMajorEvent()).append("\n");
        }
        return sb.isEmpty() ? "无" : sb.toString();
    }

    /**
     * 压缩背景：将现有背景摘要 + 近期重大事件合并为紧凑摘要
     * 每20章触发一次，保留关键伏笔、角色变化、实力变化
     */
    private void compressBackstory(StoryState state, int currentChapter) {
        try {
            String existingBackstory = state.getCompressedBackstory();
            String recentEvents = "";
            if (state.getRecentMajorEvents() != null && !state.getRecentMajorEvents().isEmpty()) {
                recentEvents = state.getRecentMajorEvents().stream()
                    .map(e -> "第" + e.getChapter() + "章：" + e.getEvent())
                    .collect(java.util.stream.Collectors.joining("\n"));
            }

            String systemPrompt = "你是小说背景压缩助手。只输出压缩后的摘要，不超过500字。" +
                "保留所有重要伏笔（已埋/已回收状态）、角色关系变化、实力变化、关键物品归属。" +
                "删除已解决的冲突和不再相关的日常细节。";
            String userMessage = String.format(
                "当前背景摘要：\n%s\n\n近期重大事件：\n%s\n\n请合并为一份紧凑的背景摘要。",
                existingBackstory != null ? existingBackstory : "无",
                recentEvents);

            String compressed = llmService.generateWithUsage(systemPrompt, userMessage, "summary");
            if (compressed != null && !compressed.isBlank()) {
                state.setCompressedBackstory(compressed);
                state.setLastCompressChapter(currentChapter);
                log.info("背景压缩完成: novelId={}, chapter={}, 压缩后{}字",
                    state.getNovelId(), currentChapter, compressed.length());
            }
        } catch (Exception e) {
            log.warn("背景压缩失败（不影响基本更新）: {}", e.getMessage());
        }
    }

    /**
     * 从 LLM 响应中提取 JSON
     */
    private String extractJson(String response) {
        if (response == null) return "{}";
        String trimmed = response.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        int jsonStart = trimmed.indexOf('{');
        if (jsonStart > 0) trimmed = trimmed.substring(jsonStart);
        int jsonEnd = trimmed.lastIndexOf('}');
        if (jsonEnd > 0) trimmed = trimmed.substring(0, jsonEnd + 1);
        return trimmed;
    }

    @Override
    public void updateTimeline(String novelId, String worldDate, String season, String timeOfDay) {
        StoryState state = getOrCreate(novelId);
        if (state.getTimeline() == null) {
            state.setTimeline(new StoryState.TimelineState());
        }
        if (worldDate != null) state.getTimeline().setWorldDate(worldDate);
        if (season != null) state.getTimeline().setSeason(season);
        if (timeOfDay != null) state.getTimeline().setTimeOfDay(timeOfDay);
        save(state);
    }

    @Override
    public void updateProtagonistLocation(String novelId, String location) {
        if (location == null || location.isBlank()) return;
        StoryState state = getOrCreate(novelId);
        if (state.getProtagonist() == null) {
            state.setProtagonist(new StoryState.ProtagonistState());
        }
        state.getProtagonist().setLocation(location);
        save(state);
        log.debug("程序化更新主角位置: {} (小说:{})", location, novelId);
    }

    @Override
    public void updateProtagonistPowerLevel(String novelId, String powerLevel) {
        if (powerLevel == null || powerLevel.isBlank()) return;
        StoryState state = getOrCreate(novelId);
        if (state.getProtagonist() == null) {
            state.setProtagonist(new StoryState.ProtagonistState());
        }
        state.getProtagonist().setPowerLevel(powerLevel);
        save(state);
        log.debug("程序化更新主角实力: {} (小说:{})", powerLevel, novelId);
    }

    @Override
    public void updateProtagonistInventory(String novelId, List<String> items) {
        if (items == null || items.isEmpty()) return;
        StoryState state = getOrCreate(novelId);
        if (state.getProtagonist() == null) {
            state.setProtagonist(new StoryState.ProtagonistState());
        }
        List<String> current = state.getProtagonist().getInventory();
        if (current == null) {
            current = new ArrayList<>();
        }
        for (String item : items) {
            if (item != null && !item.isBlank() && !current.contains(item)) {
                current.add(item);
            }
        }
        state.getProtagonist().setInventory(current);
        save(state);
        log.debug("程序化更新主角物品: {} (小说:{})", items, novelId);
    }

    @Override
    public String formatForPrompt(StoryState state) {
        if (state == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【当前故事状态】\n");

        // 时间线
        if (state.getTimeline() != null) {
            StoryState.TimelineState tl = state.getTimeline();
            sb.append("时间：").append(tl.getWorldDate() != null ? tl.getWorldDate() : "未知");
            if (tl.getSeason() != null) {
                sb.append("，季节：").append(tl.getSeason());
            }
            if (tl.getTimeOfDay() != null) {
                sb.append("，时段：").append(tl.getTimeOfDay());
            }
            sb.append("\n");
        }

        // 主角状态
        if (state.getProtagonist() != null) {
            StoryState.ProtagonistState p = state.getProtagonist();
            sb.append("主角：").append(p.getName() != null ? p.getName() : "未知");
            if (p.getPowerLevel() != null) {
                sb.append("，实力：").append(p.getPowerLevel());
            }
            if (p.getLocation() != null) {
                sb.append("，位置：").append(p.getLocation());
            }
            if (p.getMood() != null) {
                sb.append("，情绪：").append(p.getMood());
            }
            if (p.getPhysicalCondition() != null) {
                sb.append("，身体：").append(p.getPhysicalCondition());
            }
            sb.append("\n");
            if (p.getCurrentGoal() != null) {
                sb.append("目标：").append(p.getCurrentGoal()).append("\n");
            }
        }

        // 节奏状态
        if (state.getPacingState() != null) {
            StoryState.PacingState ps = state.getPacingState();
            sb.append("节奏：紧张度 ").append(ps.getTensionLevel() != null ? ps.getTensionLevel() : "?");
            sb.append("/10");
            if (ps.getRecommendation() != null) {
                sb.append("，").append(ps.getRecommendation());
            }
            sb.append("\n");
        }

        // 叙事标记
        if (state.getNarrativeFlags() != null) {
            StoryState.NarrativeFlagsState nf = state.getNarrativeFlags();
            if (nf.getUnresolvedCliffhangers() != null && !nf.getUnresolvedCliffhangers().isEmpty()) {
                sb.append("未解决悬念：").append(String.join("、", nf.getUnresolvedCliffhangers())).append("\n");
            }
        }

        return sb.toString();
    }

    private StoryState createDefaultState(String novelId) {
        StoryState state = new StoryState();
        state.setNovelId(novelId);
        state.setVersion(0);
        state.setLastChapter(0);
        state.setTimeline(new StoryState.TimelineState());
        state.setProtagonist(new StoryState.ProtagonistState());
        state.setSceneContext(new StoryState.SceneContextState());
        state.setPacingState(new StoryState.PacingState(5, 0, 0, ""));
        state.setRecentMajorEvents(new ArrayList<>());
        state.setNarrativeFlags(new StoryState.NarrativeFlagsState());
        state.setCreateTime(LocalDateTime.now());
        state.setUpdateTime(LocalDateTime.now());

        log.info("创建默认故事状态: novelId={}", novelId);
        return storyStateRepository.save(state);
    }
}
