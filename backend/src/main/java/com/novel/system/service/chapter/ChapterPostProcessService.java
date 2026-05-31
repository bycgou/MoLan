package com.novel.system.service.chapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.util.JsonExtractor;
import com.novel.system.config.LangChainConfig;
import com.novel.system.entity.mongo.CharacterState;
import com.novel.system.entity.mongo.NovelGraph;
import com.novel.system.entity.mysql.ClueBook;
import com.novel.system.entity.mysql.NovelBase;
import com.novel.system.mapper.NovelBaseMapper;
import com.novel.system.repository.mongo.NovelGraphRepository;
import com.novel.system.service.character.CharacterStateService;
import com.novel.system.service.clue.ClueBookService;
import com.novel.system.service.event.ContentExtractorService;
import com.novel.system.service.llm.LlmService;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 章节后处理服务
 *
 * AI生成章节后，自动提取人物、伏笔、时间线、事件信息并入库，更新五层记忆架构各层数据：
 * - 第一层：更新StoryState（时间线、主角状态、场景上下文、压缩背景）
 * - 第二层：提取伏笔到ClueBook
 * - 第三层：提取/更新人物状态到CharacterState + NovelGraph
 * - 第五层：提取事件到EventGraph
 * - 第六层：生成章节摘要+向量嵌入到ChapterSummary
 * 使用 @Async 异步执行，不阻塞主流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterPostProcessService {

    private final LlmService llmService;
    private final CharacterStateService characterStateService;
    private final ClueBookService clueBookService;
    private final NovelBaseMapper novelBaseMapper;
    private final ObjectMapper objectMapper;
    private final com.novel.system.service.story.StoryStateService storyStateService;
    private final NovelGraphRepository novelGraphRepository;
    private final com.novel.system.service.event.EventExtractionService eventExtractionService;
    private final com.novel.system.service.event.EventGraphService eventGraphService;
    private final com.novel.system.service.event.ContentExtractorService contentExtractorService;

    private final java.util.concurrent.ConcurrentHashMap.KeySetView<String, Boolean> processedChapters =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 异步后处理：从章节内容中提取人物、伏笔、时间线
     */
    @Async("postProcessExecutor")
    public CompletableFuture<Void> postProcess(String novelId, int chapterNum, String chapterContent, String chapterOutline) {
        return postProcess(novelId, chapterNum, chapterContent, chapterOutline, null);
    }

    /**
     * 异步后处理（带 LLM 提供的人物 JSON）
     */
    @Async("postProcessExecutor")
    public CompletableFuture<Void> postProcess(String novelId, int chapterNum, String chapterContent, String chapterOutline, String characterJson) {
        log.info("开始后处理章节: 小说ID={}, 章节号={}", novelId, chapterNum);

        String contentHash = chapterContent != null
                ? org.springframework.util.DigestUtils.md5DigestAsHex(chapterContent.getBytes()).substring(0, 8)
                : "null";
        String idempotentKey = novelId + ":" + chapterNum + ":" + contentHash;
        if (!processedChapters.add(idempotentKey)) {
            log.info("章节后处理已执行，跳过: novelId={}, chapter={}", novelId, chapterNum);
            return CompletableFuture.completedFuture(null);
        }

        long start = System.currentTimeMillis();

        try {
            // 通过 LlmService 统一模型选择逻辑
            String userId = resolveUserId(novelId);
            ChatModel model;
            try {
                model = llmService.getModelForUsage("all", userId, LangChainConfig.TaskType.STRUCTURED);
            } catch (RuntimeException e) {
                log.warn("无活跃LLM配置，跳过后处理: {}", e.getMessage());
                return CompletableFuture.completedFuture(null);
            }

            // 获取已有数据（用于去重）
            List<CharacterState> existingChars = characterStateService.getCharactersByNovelId(novelId);
            Set<String> existingCharNames = new HashSet<>();
            for (CharacterState c : existingChars) {
                existingCharNames.add(c.getCharacterName());
            }

            List<ClueBook> existingClues = clueBookService.getCluesByburyChapter(novelId, chapterNum);
            Set<String> existingClueContents = new HashSet<>();
            for (ClueBook c : existingClues) {
                existingClueContents.add(c.getClueContent());
            }

            // 截取内容（避免token超限）
            String contentForExtraction = chapterContent.length() > 4000
                    ? chapterContent.substring(0, 4000) : chapterContent;

            // 1. 提取所有提到的人物
            if (characterJson != null && !characterJson.isEmpty()) {
                // LLM 已提供人物 JSON，直接解析
                extractCharactersFromJson(characterJson, novelId, chapterNum, existingCharNames);
            } else {
                // 回退到 LLM 提取
                extractCharacters(model, novelId, chapterNum, contentForExtraction, existingCharNames);
            }

            // 2. 提取伏笔/线索
            extractClues(model, novelId, chapterNum, contentForExtraction, existingClueContents);

            // 3. 提取时间线变化
            extractTimeline(model, novelId, chapterNum, contentForExtraction);

            // 4. 同步所有人物到图谱节点
            syncCharactersToGraph(novelId, chapterNum);

            // 5. 提取事件到事件图谱
            extractEvents(novelId, chapterNum, contentForExtraction, existingCharNames);

            long elapsed = System.currentTimeMillis() - start;
            log.info("后处理完成: 小说ID={}, 章节号={}, 耗时={}ms", novelId, chapterNum, elapsed);

        } catch (Exception e) {
            processedChapters.remove(idempotentKey);
            log.error("后处理失败: 小说ID={}, 章节号={}, 错误: {}", novelId, chapterNum, e.getMessage(), e);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 提取人物信息（所有提到的人物 + 更新已有人物状态 + 人物关系）
     *
     * @return 本章提取到的所有人物名称集合
     */
    private Set<String> extractCharacters(ChatModel model, String novelId, int chapterNum,
                                    String content, Set<String> existingNames) {
        Set<String> allExtractedNames = new HashSet<>();

        try {
            // 获取已有人物完整信息（用于更新状态）
            List<CharacterState> existingChars = characterStateService.getCharactersByNovelId(novelId);

            // === 第一步：LLM 提取所有提到的人物 ===
            String prompt = String.format(
                "你是一个小说分析助手。请从以下章节内容中提取**所有提到的人物**" +
                "（包括直接出场的、对话中提到的、旁白提及的，但不包括路人甲乙丙等无名角色）。\n\n" +
                "已有人物：%s\n\n" +
                "章节内容：\n%s\n\n" +
                "请返回JSON对象，包含以下字段：\n" +
                "1. \"allCharacters\": 本章所有提到的人物数组，每个元素包含：\n" +
                "   - name: 人物姓名（必填）\n" +
                "   - traits: 核心性格特征（简短描述，可选）\n" +
                "   - background: 背景信息（可选）\n" +
                "   - location: 当前位置/地点（可选）\n" +
                "   - isNew: 是否为新出场人物（boolean）\n" +
                "2. \"existingUpdates\": 已有人物状态变化数组，每个元素包含：\n" +
                "   - name: 人物姓名\n" +
                "   - location: 新位置（如有变化）\n" +
                "   - emotion: 新情绪状态\n" +
                "   - status: 新状态描述\n" +
                "3. \"relationships\": 人物关系数组，每个元素包含：\n" +
                "   - character1: 人物1姓名\n" +
                "   - character2: 人物2姓名\n" +
                "   - type: 关系类型（朋友/敌人/恋人/亲属/师徒/同事/其他）\n" +
                "   - description: 关系描述\n\n" +
                "如果没有相应内容，对应数组返回空数组 []。\n" +
                "只返回JSON，不要其他内容。",
                existingNames.toString(), content
            );

            String response = chatWithRetry(model, prompt, 3);
            String jsonStr = JsonExtractor.extractJson(response);
            Map<String, Object> result = objectMapper.readValue(jsonStr, new TypeReference<>() {});

            // 1. 处理所有提到的人物（新建或记录名称）
            List<Map<String, Object>> allCharacters = (List<Map<String, Object>>) result.getOrDefault("allCharacters", Collections.emptyList());
            for (Map<String, Object> charData : allCharacters) {
                String name = (String) charData.get("name");
                if (name == null || name.isBlank()) continue;
                allExtractedNames.add(name);

                Boolean isNew = charData.get("isNew") instanceof Boolean ? (Boolean) charData.get("isNew") : true;
                if (isNew && !existingNames.contains(name)) {
                    try {
                        CharacterState character = new CharacterState();
                        character.setCharacterId(UUID.randomUUID().toString().replace("-", "").substring(0, 32));
                        character.setNovelId(novelId);
                        character.setCharacterName(name);
                        character.setRole("龙套");

                        String traitsStr = (String) charData.get("traits");
                        if (traitsStr != null && !traitsStr.isBlank()) {
                            character.setCoreTraits(Arrays.asList(traitsStr.split("[,，、]")));
                        }

                        character.setBackground((String) charData.get("background"));

                        CharacterState.CurrentStatus status = new CharacterState.CurrentStatus();
                        status.setHealth(100);
                        status.setEmotion("平静");
                        status.setLocation(charData.getOrDefault("location", "未知").toString());
                        character.setCurrentStatus(status);

                        characterStateService.createCharacter(character);
                        existingNames.add(name);
                        log.info("自动提取新人物: {} (小说:{}, 章节:{})", name, novelId, chapterNum);
                    } catch (Exception e) {
                        log.warn("创建人物失败: {}, 错误: {}", name, e.getMessage());
                    }
                }
            }

            // 2. 更新已有人物状态
            List<Map<String, String>> updates = (List<Map<String, String>>) result.getOrDefault("existingUpdates", Collections.emptyList());
            for (Map<String, String> update : updates) {
                String name = update.get("name");
                if (name == null || name.isBlank()) continue;

                try {
                    for (CharacterState existing : existingChars) {
                        if (name.equals(existing.getCharacterName())) {
                            boolean changed = false;
                            if (existing.getCurrentStatus() == null) {
                                existing.setCurrentStatus(new CharacterState.CurrentStatus());
                            }
                            if (update.containsKey("location") && update.get("location") != null
                                    && !update.get("location").isBlank()) {
                                existing.getCurrentStatus().setLocation(update.get("location"));
                                changed = true;
                            }
                            if (update.containsKey("emotion") && update.get("emotion") != null
                                    && !update.get("emotion").isBlank()) {
                                existing.getCurrentStatus().setEmotion(update.get("emotion"));
                                changed = true;
                            }
                            if (update.containsKey("status") && update.get("status") != null
                                    && !update.get("status").isBlank()) {
                                existing.getCurrentStatus().setStatus(update.get("status"));
                                changed = true;
                            }
                            if (changed) {
                                existing.setUpdateTime(LocalDateTime.now());
                                characterStateService.updateCharacter(existing);
                                log.info("更新人物状态: {} (小说:{}, 章节:{})", name, novelId, chapterNum);
                            }
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.warn("更新人物状态失败: {}, 错误: {}", name, e.getMessage());
                }
            }

            // 3. 记录人物关系
            List<Map<String, String>> relationships = (List<Map<String, String>>) result.getOrDefault("relationships", Collections.emptyList());
            for (Map<String, String> rel : relationships) {
                String char1Name = rel.get("character1");
                String char2Name = rel.get("character2");

                if (char1Name == null || char2Name == null) continue;

                try {
                    String char1Id = findCharacterId(existingChars, char1Name);
                    String char2Id = findCharacterId(existingChars, char2Name);
                    if (char1Id != null && char2Id != null) {
                        characterStateService.updateRelationship(novelId, char1Id, char2Id, 10);
                        log.info("记录人物关系: {} <-> {} (小说:{}, 章节:{})",
                                char1Name, char2Name, novelId, chapterNum);
                    }
                } catch (Exception e) {
                    log.warn("记录人物关系失败: {} <-> {}, 错误: {}", char1Name, char2Name, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("LLM提取人物失败: {}", e.getMessage());
        }

        log.info("人物提取完成: 共{}个角色 (小说:{}, 章节:{})", allExtractedNames.size(), novelId, chapterNum);
        return allExtractedNames;
    }

    /**
     * 从 LLM 提供的 JSON 中提取人物信息（流式生成时 LLM 直接输出）
     */
    private void extractCharactersFromJson(String characterJson, String novelId, int chapterNum,
                                            Set<String> existingNames) {
        try {
            Map<String, Object> data = objectMapper.readValue(characterJson, new TypeReference<>() {});

            // 处理人物
            List<Map<String, Object>> characters = (List<Map<String, Object>>) data.getOrDefault("characters", Collections.emptyList());
            for (Map<String, Object> charData : characters) {
                String name = (String) charData.get("name");
                if (name == null || name.isBlank()) continue;

                Boolean isNew = charData.get("isNew") instanceof Boolean ? (Boolean) charData.get("isNew") : true;
                if (isNew && !existingNames.contains(name)) {
                    try {
                        CharacterState character = new CharacterState();
                        character.setCharacterId(UUID.randomUUID().toString().replace("-", "").substring(0, 32));
                        character.setNovelId(novelId);
                        character.setCharacterName(name);
                        character.setRole("龙套");

                        CharacterState.CurrentStatus status = new CharacterState.CurrentStatus();
                        status.setHealth(100);
                        status.setEmotion(charData.get("emotion") instanceof String ? (String) charData.get("emotion") : "平静");
                        status.setLocation(charData.get("location") instanceof String ? (String) charData.get("location") : "未知");
                        character.setCurrentStatus(status);

                        characterStateService.createCharacter(character);
                        existingNames.add(name);
                        log.info("JSON提取新人物: {} (小说:{}, 章节:{})", name, novelId, chapterNum);
                    } catch (Exception e) {
                        log.warn("JSON创建人物失败: {}, 错误: {}", name, e.getMessage());
                    }
                } else if (!isNew && existingNames.contains(name)) {
                    // 更新已有人物状态
                    try {
                        CharacterState existing = characterStateService.getCharactersByNovelId(novelId).stream()
                                .filter(c -> name.equals(c.getCharacterName())).findFirst().orElse(null);
                        if (existing != null) {
                            if (charData.get("location") instanceof String) {
                                existing.getCurrentStatus().setLocation((String) charData.get("location"));
                            }
                            if (charData.get("emotion") instanceof String) {
                                existing.getCurrentStatus().setEmotion((String) charData.get("emotion"));
                            }
                            characterStateService.updateCharacter(existing);
                            log.info("JSON更新人物状态: {} (小说:{}, 章节:{})", name, novelId, chapterNum);
                        }
                    } catch (Exception e) {
                        log.warn("JSON更新人物失败: {}, 错误: {}", name, e.getMessage());
                    }
                }
            }

            // 处理关系
            List<Map<String, Object>> relationships = (List<Map<String, Object>>) data.getOrDefault("relationships", Collections.emptyList());
            List<CharacterState> existingChars = characterStateService.getCharactersByNovelId(novelId);
            for (Map<String, Object> rel : relationships) {
                String from = (String) rel.get("from");
                String to = (String) rel.get("to");
                if (from == null || to == null) continue;
                try {
                    String fromId = findCharacterId(existingChars, from);
                    String toId = findCharacterId(existingChars, to);
                    if (fromId != null && toId != null) {
                        characterStateService.updateRelationship(novelId, fromId, toId, 10);
                        log.info("JSON记录人物关系: {} <-> {} (小说:{}, 章节:{})", from, to, novelId, chapterNum);
                    }
                } catch (Exception e) {
                    log.warn("JSON记录关系失败: {} <-> {}, 错误: {}", from, to, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("解析人物JSON失败，回退到LLM提取: {}", e.getMessage());
            // JSON 解析失败时回退到 LLM 提取
            try {
                String userId = resolveUserId(novelId);
                ChatModel model = llmService.getModelForUsage("all", userId, LangChainConfig.TaskType.STRUCTURED);
                extractCharacters(model, novelId, chapterNum,
                        characterJson.length() > 4000 ? characterJson.substring(0, 4000) : characterJson,
                        existingNames);
            } catch (RuntimeException ex) {
                log.warn("回退LLM提取也失败: {}", ex.getMessage());
            }
        }
    }

    /**
     * 根据人物名称查找ID
     */
    private String findCharacterId(List<CharacterState> characters, String name) {
        for (CharacterState c : characters) {
            if (name.equals(c.getCharacterName())) {
                return c.getCharacterId();
            }
        }
        return null;
    }

    /**
     * 从SecurityContextHolder获取当前用户ID
     */
    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof String) {
            String principal = (String) auth.getPrincipal();
            if (!"anonymousUser".equals(principal)) {
                return principal;
            }
        }
        return null;
    }

    /**
     * 解析用户ID：优先从SecurityContext获取，@Async线程中回退到小说作者ID
     */
    private String resolveUserId(String novelId) {
        String userId = getCurrentUserId();
        if (userId != null && !userId.isEmpty()) {
            return userId;
        }
        try {
            NovelBase novel = novelBaseMapper.selectById(novelId);
            if (novel != null && novel.getUserId() != null) {
                return novel.getUserId();
            }
        } catch (Exception e) {
            log.warn("通过novelId查询userId失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 带重试的 LLM 调用，应对临时网络抖动
     */
    private String chatWithRetry(ChatModel model, String prompt, int maxRetries) {
        for (int i = 1; i <= maxRetries; i++) {
            try {
                return model.chat(prompt);
            } catch (Exception e) {
                if (i < maxRetries && isRetriable(e)) {
                    log.warn("后处理LLM调用失败，第{}次重试: {}", i, e.getMessage());
                    try { Thread.sleep(2000L * i); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                } else {
                    throw e;
                }
            }
        }
        throw new RuntimeException("LLM调用失败");
    }

    private boolean isRetriable(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        return msg.contains("ConnectException")
            || msg.contains("TimeoutException")
            || msg.contains("SocketTimeoutException")
            || msg.contains("ClosedChannelException")
            || msg.contains("rate_limit")
            || msg.contains("429")
            || msg.contains("503");
    }

    /**
     * 提取伏笔/线索
     */
    private void extractClues(ChatModel model, String novelId, int chapterNum,
                               String content, Set<String> existingClues) {
        try {
            String prompt = String.format(
                "你是一个小说分析助手。请从以下章节内容中识别可能的伏笔、悬念或待回收线索。\n\n" +
                "已存在的伏笔：%s\n\n" +
                "章节内容：\n%s\n\n" +
                "请返回JSON数组，每个元素包含：\n" +
                "- content: 伏笔/线索描述\n" +
                "- priority: 优先级（1=高, 2=中, 3=低）\n" +
                "- relatedCharacters: 相关人物（逗号分隔）\n\n" +
                "只提取本章新埋下的伏笔。如果没有，返回空数组 []。\n" +
                "只返回JSON，不要其他内容。",
                existingClues.toString(), content
            );

            String response = chatWithRetry(model, prompt, 3);
            String jsonStr = JsonExtractor.extractJson(response, "[]");

            List<Map<String, Object>> clues = objectMapper.readValue(jsonStr, new TypeReference<>() {});

            for (Map<String, Object> clueData : clues) {
                String clueContent = (String) clueData.get("content");
                if (clueContent == null || clueContent.isBlank() || existingClues.contains(clueContent)) {
                    continue;
                }

                try {
                    ClueBook clue = new ClueBook();
                    clue.setClueId(UUID.randomUUID().toString().replace("-", "").substring(0, 32));
                    clue.setNovelId(novelId);
                    clue.setClueContent(clueContent);
                    clue.setPriority(clueData.get("priority") != null
                            ? ((Number) clueData.get("priority")).intValue() : 2);
                    clue.setBuryChapter(chapterNum);
                    clue.setRelatedCharacters((String) clueData.get("relatedCharacters"));
                    clue.setStatus(0); // 未回收
                    clue.setCreateTime(LocalDateTime.now());

                    clueBookService.createClue(clue);
                    existingClues.add(clueContent);
                    log.info("自动提取伏笔: {} (小说:{}, 章节:{})", clueContent, novelId, chapterNum);
                } catch (Exception e) {
                    log.warn("创建伏笔失败: {}, 错误: {}", clueContent, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("提取伏笔失败: {}", e.getMessage());
        }
    }

    /**
     * 提取时间线变化
     */
    private void extractTimeline(ChatModel model, String novelId, int chapterNum,
                                  String content) {
        try {
            String prompt = String.format(
                "你是一个小说分析助手。请从以下章节内容中分析时间线变化。\n\n" +
                "章节内容：\n%s\n\n" +
                "请返回JSON对象，包含：\n" +
                "- timeDescription: 本章的时间描述（如'清晨'、'三天后'、'午夜'等）\n" +
                "- season: 季节（如果提及）\n" +
                "- weather: 天气（如果提及）\n" +
                "- dayChange: 相对于前一章经过的天数（整数，如不确定返回0）\n\n" +
                "如果无法确定时间信息，返回空对象 {}。\n" +
                "只返回JSON，不要其他内容。",
                content.length() > 3000 ? content.substring(0, 3000) : content
            );

            String response = chatWithRetry(model, prompt, 3);
            String jsonStr = JsonExtractor.extractJson(response);

            Map<String, Object> timeline = objectMapper.readValue(jsonStr, new TypeReference<>() {});

            String timeDesc = (String) timeline.get("timeDescription");
            if (timeDesc != null && !timeDesc.isBlank()) {
                log.info("自动提取时间线: {} (小说:{}, 章节:{})", timeDesc, novelId, chapterNum);

                // 持久化时间线到 StoryState
                try {
                    storyStateService.updateTimeline(
                        novelId,
                        timeDesc,
                        (String) timeline.get("season"),
                        null  // timeOfDay 由 timeDescription 推断
                    );
                    log.info("时间线已持久化到StoryState: novelId={}, chapter={}", novelId, chapterNum);
                } catch (Exception e) {
                    log.warn("持久化时间线到StoryState失败: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("提取时间线失败: {}", e.getMessage());
        }
    }

    /**
     * 同步所有人物到关系图谱节点
     * 确保 character_state 中的每个人物在 novel_graph.nodes 中都有对应节点
     */
    private void syncCharactersToGraph(String novelId, int chapterNum) {
        try {
            List<CharacterState> allChars = characterStateService.getCharactersByNovelId(novelId);
            if (allChars.isEmpty()) return;

            NovelGraph graph = novelGraphRepository.findById(novelId).orElseGet(() -> {
                NovelGraph g = new NovelGraph();
                g.setNovelId(novelId);
                g.setNodes(new ArrayList<>());
                g.setEdges(new ArrayList<>());
                return g;
            });

            Set<String> existingNodeNames = new HashSet<>();
            for (NovelGraph.Node n : graph.getNodes()) {
                existingNodeNames.add(n.getName());
            }

            int added = 0;
            for (CharacterState c : allChars) {
                if (c.getCharacterName() != null && !existingNodeNames.contains(c.getCharacterName())) {
                    NovelGraph.Node node = new NovelGraph.Node();
                    node.setId(c.getCharacterId());
                    node.setName(c.getCharacterName());
                    node.setType("person");
                    graph.getNodes().add(node);
                    existingNodeNames.add(c.getCharacterName());
                    added++;
                }
            }

            if (added > 0) {
                novelGraphRepository.save(graph);
                log.info("同步人物到图谱: 新增{}个节点 (小说:{}, 章节:{})", added, novelId, chapterNum);
            }
        } catch (Exception e) {
            log.warn("同步人物到图谱失败: {}", e.getMessage());
        }
    }

    /**
     * 提取事件到事件图谱
     * 使用LLM提取关键事件，同时用程序化提取补充对话事件
     */
    private void extractEvents(String novelId, int chapterNum,
                                String content, Set<String> knownCharacters) {
        try {
            // 1. LLM提取关键事件
            List<com.novel.system.entity.mongo.EventNode> llmEvents =
                eventExtractionService.extractEvents(novelId, chapterNum, content, knownCharacters);

            // 2. 程序化提取对话事件（作为补充）
            List<ContentExtractorService.DialogueEvent> dialogues =
                contentExtractorService.extractDialogues(content);
            List<com.novel.system.entity.mongo.EventNode> dialogueEvents = new java.util.ArrayList<>();
            for (ContentExtractorService.DialogueEvent d : dialogues) {
                com.novel.system.entity.mongo.EventNode node =
                    new com.novel.system.entity.mongo.EventNode();
                node.setEventId(java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 32));
                node.setNovelId(novelId);
                node.setChapter(chapterNum);
                node.setType("dialogue");
                node.setContent(d.getSpeaker() + "：" + d.getContent());
                node.setCharacters(java.util.Collections.singletonList(d.getSpeaker()));
                node.setImportance(3);
                node.setTags(java.util.Collections.singletonList("对话"));
                node.setLinkedEvents(java.util.Collections.emptyList());
                node.setCreateTime(java.time.LocalDateTime.now());
                dialogueEvents.add(node);
            }

            // 3. 合并保存
            java.util.List<com.novel.system.entity.mongo.EventNode> allEvents =
                new java.util.ArrayList<>(llmEvents);
            allEvents.addAll(dialogueEvents);

            if (!allEvents.isEmpty()) {
                eventGraphService.saveAll(allEvents);
                log.info("事件图谱更新: novelId={}, chapter={}, 保存{}个事件(LLM:{}, 对话:{})",
                    novelId, chapterNum, allEvents.size(), llmEvents.size(), dialogueEvents.size());
            }

            // 4. 程序化提取 → 兜底更新StoryState
            String newLocation = contentExtractorService.extractLocation(content);
            if (newLocation != null) {
                try {
                    storyStateService.updateProtagonistLocation(novelId, newLocation);
                    log.info("程序化兜底更新主角位置: {} (小说:{}, 章节:{})", newLocation, novelId, chapterNum);
                } catch (Exception e) {
                    log.warn("程序化更新位置失败: {}", e.getMessage());
                }
            }

            String newPowerLevel = contentExtractorService.extractPowerLevel(content);
            if (newPowerLevel != null) {
                try {
                    storyStateService.updateProtagonistPowerLevel(novelId, newPowerLevel);
                    log.info("程序化兜底更新主角实力: {} (小说:{}, 章节:{})", newPowerLevel, novelId, chapterNum);
                } catch (Exception e) {
                    log.warn("程序化更新实力失败: {}", e.getMessage());
                }
            }

            List<String> newItems = contentExtractorService.extractItems(content);
            if (!newItems.isEmpty()) {
                try {
                    storyStateService.updateProtagonistInventory(novelId, newItems);
                    log.info("程序化兜底更新主角物品: {} (小说:{}, 章节:{})", newItems, novelId, chapterNum);
                } catch (Exception e) {
                    log.warn("程序化更新物品失败: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.warn("事件图谱提取失败（不影响后处理）: {}", e.getMessage());
        }
    }

}
