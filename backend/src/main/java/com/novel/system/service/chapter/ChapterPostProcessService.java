package com.novel.system.service.chapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.config.LangChainConfig;
import com.novel.system.entity.mongo.CharacterState;
import com.novel.system.entity.mysql.ClueBook;
import com.novel.system.entity.mysql.LlmConfig;
import com.novel.system.entity.mysql.NovelBase;
import com.novel.system.mapper.NovelBaseMapper;
import com.novel.system.service.character.CharacterStateService;
import com.novel.system.service.clue.ClueBookService;
import com.novel.system.service.llm.LlmConfigService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 章节后处理服务
 * 
 * AI生成章节后，自动提取人物、伏笔、时间线信息并入库。
 * 使用 @Async 异步执行，不阻塞主流程。
 * 
 * @since 1.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterPostProcessService {

    private final LangChainConfig langChainConfig;
    private final LlmConfigService llmConfigService;
    private final CharacterStateService characterStateService;
    private final ClueBookService clueBookService;
    private final NovelBaseMapper novelBaseMapper;
    private final ObjectMapper objectMapper;

    /**
     * 异步后处理：从章节内容中提取人物、伏笔、时间线
     */
    @Async
    public void postProcess(String novelId, int chapterNum, String chapterContent, String chapterOutline) {
        log.info("开始后处理章节: 小说ID={}, 章节号={}", novelId, chapterNum);
        long start = System.currentTimeMillis();

        try {
            // 获取任意活跃LLM配置
            LlmConfig llmConfig = llmConfigService.getAnyActiveConfig("all");
            if (llmConfig == null) {
                log.warn("无活跃LLM配置，跳过后处理");
                return;
            }

            ChatLanguageModel model = langChainConfig.createChatModel(llmConfig, LangChainConfig.TaskType.STRUCTURED);

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

            // 1. 提取新人物
            extractCharacters(model, novelId, chapterNum, contentForExtraction, existingCharNames);

            // 2. 提取伏笔/线索
            extractClues(model, novelId, chapterNum, contentForExtraction, existingClueContents);

            // 3. 提取时间线变化
            extractTimeline(model, novelId, chapterNum, contentForExtraction);

            long elapsed = System.currentTimeMillis() - start;
            log.info("后处理完成: 小说ID={}, 章节号={}, 耗时={}ms", novelId, chapterNum, elapsed);

        } catch (Exception e) {
            log.error("后处理失败: 小说ID={}, 章节号={}, 错误: {}", novelId, chapterNum, e.getMessage(), e);
        }
    }

    /**
     * 提取新人物
     */
    private void extractCharacters(ChatLanguageModel model, String novelId, int chapterNum,
                                    String content, Set<String> existingNames) {
        try {
            String prompt = String.format(
                "你是一个小说分析助手。请从以下章节内容中提取新出场的人物信息。\n\n" +
                "已有人物：%s\n\n" +
                "章节内容：\n%s\n\n" +
                "请返回JSON数组，每个元素包含：\n" +
                "- name: 人物姓名\n" +
                "- traits: 核心性格特征（简短描述）\n" +
                "- background: 背景信息（如果有的话）\n" +
                "- location: 当前位置/地点\n\n" +
                "只提取本章新出现的、不在已有列表中的人物。如果没有新人物，返回空数组 []。\n" +
                "只返回JSON，不要其他内容。",
                existingNames.toString(), content
            );

            String response = model.generate(prompt);
            String jsonStr = extractJson(response);

            List<Map<String, String>> characters = objectMapper.readValue(jsonStr, new TypeReference<>() {});

            for (Map<String, String> charData : characters) {
                String name = charData.get("name");
                if (name == null || name.isBlank() || existingNames.contains(name)) {
                    continue;
                }

                try {
                    CharacterState character = new CharacterState();
                    character.setCharacterId(UUID.randomUUID().toString().replace("-", "").substring(0, 32));
                    character.setNovelId(novelId);
                    character.setCharacterName(name);

                    // 将特质字符串拆分为列表
                    String traitsStr = charData.get("traits");
                    if (traitsStr != null && !traitsStr.isBlank()) {
                        character.setCoreTraits(Arrays.asList(traitsStr.split("[,，、]")));
                    }

                    character.setBackground(charData.get("background"));

                    CharacterState.CurrentStatus status = new CharacterState.CurrentStatus();
                    status.setHealth(100);
                    status.setEmotion("平静");
                    status.setLocation(charData.getOrDefault("location", "未知"));
                    character.setCurrentStatus(status);

                    characterStateService.createCharacter(character);
                    existingNames.add(name);
                    log.info("自动提取新人物: {} (小说:{}, 章节:{})", name, novelId, chapterNum);
                } catch (Exception e) {
                    log.warn("创建人物失败: {}, 错误: {}", name, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("提取人物失败: {}", e.getMessage());
        }
    }

    /**
     * 提取伏笔/线索
     */
    private void extractClues(ChatLanguageModel model, String novelId, int chapterNum,
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

            String response = model.generate(prompt);
            String jsonStr = extractJson(response);

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
    private void extractTimeline(ChatLanguageModel model, String novelId, int chapterNum,
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

            String response = model.generate(prompt);
            String jsonStr = extractJson(response);

            Map<String, Object> timeline = objectMapper.readValue(jsonStr, new TypeReference<>() {});

            String timeDesc = (String) timeline.get("timeDescription");
            if (timeDesc != null && !timeDesc.isBlank()) {
                log.info("自动提取时间线: {} (小说:{}, 章节:{})", timeDesc, novelId, chapterNum);

                // 更新小说的时间线字段（如果存在）
                try {
                    NovelBase novel = novelBaseMapper.selectById(novelId);
                    if (novel != null) {
                        String currentTime = (String) timeline.get("season");
                        if (currentTime != null) {
                            // 将时间信息写入小说的 worldSetting 或扩展字段
                            log.debug("时间线详情: 季节={}, 天气={}, 日变化={}",
                                    timeline.get("season"),
                                    timeline.get("weather"),
                                    timeline.get("dayChange"));
                        }
                    }
                } catch (Exception e) {
                    log.warn("更新时间线数据失败: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("提取时间线失败: {}", e.getMessage());
        }
    }

    /**
     * 从LLM响应中提取JSON字符串（处理可能的markdown代码块包裹）
     */
    private String extractJson(String response) {
        if (response == null) return "[]";

        String trimmed = response.trim();

        // 去除 ```json ... ``` 包裹
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }

        // 去除多余的前缀文本
        int jsonStart = trimmed.indexOf('[');
        if (jsonStart < 0) jsonStart = trimmed.indexOf('{');
        if (jsonStart > 0) {
            trimmed = trimmed.substring(jsonStart);
        }

        // 去除尾部多余文本
        int jsonEndArray = trimmed.lastIndexOf(']');
        int jsonEndObj = trimmed.lastIndexOf('}');
        int jsonEnd = Math.max(jsonEndArray, jsonEndObj);
        if (jsonEnd > 0) {
            trimmed = trimmed.substring(0, jsonEnd + 1);
        }

        return trimmed;
    }
}
