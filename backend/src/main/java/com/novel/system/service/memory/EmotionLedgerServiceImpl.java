package com.novel.system.service.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.entity.mongo.EmotionLedger;
import com.novel.system.repository.mongo.EmotionLedgerRepository;
import com.novel.system.service.llm.LlmService;
import com.novel.system.util.JsonExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmotionLedgerServiceImpl implements EmotionLedgerService {

    private final EmotionLedgerRepository emotionLedgerRepository;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    private EmotionLedger getOrCreate(String novelId) {
        return emotionLedgerRepository.findByNovelId(novelId)
                .orElseGet(() -> {
                    EmotionLedger ledger = new EmotionLedger();
                    ledger.setNovelId(novelId);
                    ledger.setUpdatedAt(LocalDateTime.now());
                    return emotionLedgerRepository.save(ledger);
                });
    }

    @Override
    public String formatForPrompt(String novelId, int currentChapterNum) {
        Optional<EmotionLedger> opt = emotionLedgerRepository.findByNovelId(novelId);
        if (opt.isEmpty() || opt.get().getEntries().isEmpty()) {
            return "";
        }

        EmotionLedger ledger = opt.get();
        StringBuilder sb = new StringBuilder();
        sb.append("=== 情感账本（近5章） ===\n");

        int count = 0;
        for (int i = ledger.getEntries().size() - 1; i >= 0 && count < 5; i--, count++) {
            EmotionLedger.EmotionEntry entry = ledger.getEntries().get(i);
            sb.append("第").append(entry.getChapterNum()).append("章: ");

            if (entry.getWound() != null && entry.getWound().getLoss() != null
                    && !entry.getWound().getLoss().isEmpty()) {
                sb.append("伤痕[").append(entry.getWound().getLoss())
                        .append("→").append(entry.getWound().getPsychImpact()).append("] ");
            } else {
                sb.append("伤痕[-] ");
            }

            if (entry.getBoon() != null && entry.getBoon().getGain() != null
                    && !entry.getBoon().getGain().isEmpty()) {
                sb.append("恩惠[").append(entry.getBoon().getGain())
                        .append("→").append(entry.getBoon().getValueShift()).append("] ");
            } else {
                sb.append("恩惠[-] ");
            }

            if (entry.getPowerShift() != null && entry.getPowerShift().getFrom() != null
                    && !entry.getPowerShift().getFrom().isEmpty()) {
                sb.append("权力[").append(entry.getPowerShift().getFrom())
                        .append("→").append(entry.getPowerShift().getTo()).append("] ");
            } else {
                sb.append("权力[-] ");
            }

            if (entry.getOpenLoops() != null && !entry.getOpenLoops().isEmpty()) {
                sb.append("开钩[");
                List<String> loops = new ArrayList<>();
                for (EmotionLedger.OpenLoop loop : entry.getOpenLoops()) {
                    loops.add(loop.getHook() + "|紧急度" + loop.getUrgency());
                }
                sb.append(String.join(", ", loops)).append("]");
            } else {
                sb.append("开钩[-]");
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    @Override
    public void extractFromChapter(String novelId, int chapterNum, String chapterContent) {
        String systemPrompt = "你是一个小说情感分析师。从章节内容中提取情感维度变化，输出JSON。";
        String userPrompt = "以下是小说第" + chapterNum + "章的内容（截取前3000字）：\n\n"
                + chapterContent.substring(0, Math.min(3000, chapterContent.length()))
                + "\n\n请提取本章的情感维度变化并输出JSON：\n"
                + "{\n"
                + "  \"wound\": {\"loss\": \"核心丧失\", \"psychImpact\": \"心理影响\"},\n"
                + "  \"boon\": {\"gain\": \"核心获得\", \"valueShift\": \"价值变化\"},\n"
                + "  \"powerShift\": {\"from\": \"之前状态\", \"to\": \"之后状态\"},\n"
                + "  \"openLoops\": [{\"hook\": \"未解钩子\", \"urgency\": 8}]\n"
                + "}\n"
                + "如果某维度没有变化，对应字段值填空字符串或空数组。urgency范围1-10。只输出JSON。";

        try {
            String response = llmService.generateWithUsage(systemPrompt, userPrompt, "summary");
            String json = JsonExtractor.extractJson(response);
            JsonNode root = objectMapper.readTree(json);

            EmotionLedger ledger = getOrCreate(novelId);

            EmotionLedger.EmotionEntry entry = new EmotionLedger.EmotionEntry();
            entry.setChapterNum(chapterNum);

            // Parse wound
            if (root.has("wound")) {
                JsonNode w = root.get("wound");
                entry.setWound(new EmotionLedger.EmotionalWound(
                        w.has("loss") ? w.get("loss").asText() : "",
                        w.has("psychImpact") ? w.get("psychImpact").asText() : ""
                ));
            }

            // Parse boon
            if (root.has("boon")) {
                JsonNode b = root.get("boon");
                entry.setBoon(new EmotionLedger.EmotionalBoon(
                        b.has("gain") ? b.get("gain").asText() : "",
                        b.has("valueShift") ? b.get("valueShift").asText() : ""
                ));
            }

            // Parse powerShift
            if (root.has("powerShift")) {
                JsonNode p = root.get("powerShift");
                entry.setPowerShift(new EmotionLedger.PowerShift(
                        p.has("from") ? p.get("from").asText() : "",
                        p.has("to") ? p.get("to").asText() : ""
                ));
            }

            // Parse openLoops
            List<EmotionLedger.OpenLoop> loops = new ArrayList<>();
            if (root.has("openLoops")) {
                for (JsonNode l : root.get("openLoops")) {
                    loops.add(new EmotionLedger.OpenLoop(
                            l.has("hook") ? l.get("hook").asText() : "",
                            l.has("urgency") ? l.get("urgency").asInt(5) : 5
                    ));
                }
            }
            entry.setOpenLoops(loops);

            ledger.getEntries().add(entry);
            ledger.setVersion(ledger.getVersion() + 1);
            ledger.setUpdatedAt(LocalDateTime.now());
            emotionLedgerRepository.save(ledger);
            log.info("EmotionLedger updated for novel {} chapter {}", novelId, chapterNum);

        } catch (Exception e) {
            log.error("Failed to extract EmotionLedger for novel {} chapter {}", novelId, chapterNum, e);
        }
    }
}
