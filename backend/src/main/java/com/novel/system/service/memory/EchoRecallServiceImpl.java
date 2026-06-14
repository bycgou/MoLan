package com.novel.system.service.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.entity.mongo.CharacterVoiceSample;
import com.novel.system.repository.mongo.CharacterVoiceSampleRepository;
import com.novel.system.service.llm.LlmService;
import com.novel.system.util.JsonExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EchoRecallServiceImpl implements EchoRecallService {

    private final CharacterVoiceSampleRepository voiceSampleRepository;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    private static final int MIN_AGE_CHAPTERS = 15;
    private static final int MAX_RESULTS = 2;

    @Override
    public String formatForPrompt(String novelId, int currentChapterNum, List<String> characters) {
        if (characters == null || characters.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        boolean hasContent = false;

        for (String character : characters) {
            List<VoiceSearchResult> results = searchVoiceSamples(
                    novelId, character, currentChapterNum, MIN_AGE_CHAPTERS);

            for (VoiceSearchResult result : results) {
                if (!hasContent) {
                    sb.append("=== 回响召回 ===\n");
                    hasContent = true;
                }
                sb.append("【回响】").append(character).append("在第").append(result.chapterNum())
                        .append("章曾说过：\"").append(result.dialogue()).append("\"\n");
                if (result.context() != null && !result.context().isEmpty()) {
                    sb.append("当时的场景：").append(result.context()).append("\n");
                }
                sb.append("\n");
            }
        }

        return hasContent ? sb.toString() : "";
    }

    @Override
    public void extractVoiceSamples(String novelId, int chapterNum, String chapterContent) {
        String systemPrompt = "你是一个小说台词分析师。从章节内容中提取角色的经典台词、宣言或内心独白。输出JSON。";
        String userPrompt = "以下是小说第" + chapterNum + "章的内容（截取前3000字）：\n\n"
                + chapterContent.substring(0, Math.min(3000, chapterContent.length()))
                + "\n\n请提取角色的经典台词并输出JSON：\n"
                + "{\n"
                + "  \"samples\": [{\n"
                + "    \"character\": \"角色名\",\n"
                + "    \"dialogue\": \"原话内容\",\n"
                + "    \"emotion\": \"当时情感\",\n"
                + "    \"situation\": \"场景描述\"\n"
                + "  }]\n"
                + "}\n"
                + "只提取有代表性的台词（宣言、转折性独白、关键对话），不要提取普通对话。\n"
                + "最多提取5条。只输出JSON。";

        try {
            String response = llmService.generateWithUsage(systemPrompt, userPrompt, "summary");
            String json = JsonExtractor.extractJson(response);
            JsonNode root = objectMapper.readTree(json);

            if (root.has("samples")) {
                List<CharacterVoiceSample> samples = new ArrayList<>();
                for (JsonNode s : root.get("samples")) {
                    CharacterVoiceSample sample = new CharacterVoiceSample();
                    sample.setNovelId(novelId);
                    sample.setCharacterName(s.has("character") ? s.get("character").asText() : "");
                    sample.setDialogue(s.has("dialogue") ? s.get("dialogue").asText() : "");
                    sample.setContext("第" + chapterNum + "章");
                    sample.setChapterNum(chapterNum);
                    sample.setEmotion(s.has("emotion") ? s.get("emotion").asText() : "");
                    sample.setSituation(s.has("situation") ? s.get("situation").asText() : "");
                    sample.setCreatedAt(LocalDateTime.now());
                    samples.add(sample);
                }
                voiceSampleRepository.saveAll(samples);
                log.info("Extracted {} voice samples from novel {} chapter {}",
                        samples.size(), novelId, chapterNum);
            }

        } catch (Exception e) {
            log.error("Failed to extract voice samples for novel {} chapter {}", novelId, chapterNum, e);
        }
    }

    @Override
    public List<VoiceSearchResult> searchVoiceSamples(String novelId, String characterName,
                                                       int currentChapterNum, int minAgeChapters) {
        int maxChapter = currentChapterNum - minAgeChapters;
        if (maxChapter <= 0) {
            return List.of();
        }

        List<CharacterVoiceSample> samples = voiceSampleRepository
                .findByNovelIdAndCharacterNameAndChapterNumLessThan(novelId, characterName, maxChapter);

        // Simple relevance: prioritize by chapter distance (further = more nostalgic)
        samples.sort((a, b) -> Integer.compare(b.getChapterNum(), a.getChapterNum()));

        List<VoiceSearchResult> results = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_RESULTS, samples.size()); i++) {
            CharacterVoiceSample s = samples.get(i);
            results.add(new VoiceSearchResult(
                    s.getDialogue(), s.getContext(), s.getChapterNum(), 1.0));
        }

        return results;
    }
}
