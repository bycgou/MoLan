package com.novel.system.service.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.entity.mongo.CompletedBeats;
import com.novel.system.repository.mongo.CompletedBeatsRepository;
import com.novel.system.service.llm.LlmService;
import com.novel.system.util.JsonExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompletedBeatsServiceImpl implements CompletedBeatsService {

    private final CompletedBeatsRepository completedBeatsRepository;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    private CompletedBeats getOrCreate(String novelId) {
        return completedBeatsRepository.findByNovelId(novelId)
                .orElseGet(() -> {
                    CompletedBeats beats = new CompletedBeats();
                    beats.setNovelId(novelId);
                    beats.setUpdatedAt(LocalDateTime.now());
                    return completedBeatsRepository.save(beats);
                });
    }

    @Override
    public String formatForPrompt(String novelId) {
        Optional<CompletedBeats> opt = completedBeatsRepository.findByNovelId(novelId);
        if (opt.isEmpty() || opt.get().getBeats().isEmpty()) {
            return "";
        }

        CompletedBeats cb = opt.get();
        StringBuilder sb = new StringBuilder();
        sb.append("=== 已完成节拍 ===\n");

        int count = 0;
        for (int i = cb.getBeats().size() - 1; i >= 0 && count < 20; i--, count++) {
            CompletedBeats.BeatRecord beat = cb.getBeats().get(i);
            sb.append("[").append(count + 1).append("] ")
                    .append(beat.getSummary())
                    .append(" (第").append(beat.getChapterNum()).append("章");
            if (beat.getPlotThread() != null && !beat.getPlotThread().isEmpty()) {
                sb.append(", 线:").append(beat.getPlotThread());
            }
            sb.append(")\n");
        }

        return sb.toString();
    }

    @Override
    public void extractFromChapter(String novelId, int chapterNum, String chapterContent) {
        String systemPrompt = "你是一个小说节拍分析师。从章节内容中提取已完成的剧情节拍，输出JSON。";
        String userPrompt = "以下是小说第" + chapterNum + "章的内容（截取前3000字）：\n\n"
                + chapterContent.substring(0, Math.min(3000, chapterContent.length()))
                + "\n\n请提取本章完成的剧情节拍并输出JSON：\n"
                + "{\n"
                + "  \"beats\": [{\n"
                + "    \"summary\": \"节拍描述\",\n"
                + "    \"characters\": [\"角色名\"],\n"
                + "    \"plotThread\": \"所属剧情线\"\n"
                + "  }]\n"
                + "}\n"
                + "已完成节拍是指本章中已经完成、不可逆转的剧情事件。\n"
                + "只输出JSON，不要其他内容。";

        try {
            String response = llmService.generateWithUsage(systemPrompt, userPrompt, "summary");
            String json = JsonExtractor.extractJson(response);
            JsonNode root = objectMapper.readTree(json);

            CompletedBeats cb = getOrCreate(novelId);

            if (root.has("beats")) {
                for (JsonNode b : root.get("beats")) {
                    List<String> characters = new ArrayList<>();
                    if (b.has("characters")) {
                        for (JsonNode c : b.get("characters")) {
                            characters.add(c.asText());
                        }
                    }

                    CompletedBeats.BeatRecord record = new CompletedBeats.BeatRecord(
                            UUID.randomUUID().toString().substring(0, 8),
                            b.has("summary") ? b.get("summary").asText() : "",
                            chapterNum,
                            characters,
                            b.has("plotThread") ? b.get("plotThread").asText() : ""
                    );
                    cb.getBeats().add(record);
                }
            }

            cb.setUpdatedAt(LocalDateTime.now());
            completedBeatsRepository.save(cb);
            log.info("CompletedBeats updated for novel {} chapter {}: {} beats",
                    novelId, chapterNum,
                    root.has("beats") ? root.get("beats").size() : 0);

        } catch (Exception e) {
            log.error("Failed to extract CompletedBeats for novel {} chapter {}", novelId, chapterNum, e);
        }
    }
}
