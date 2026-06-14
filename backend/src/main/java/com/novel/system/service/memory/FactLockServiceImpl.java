package com.novel.system.service.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.entity.mongo.FactLock;
import com.novel.system.repository.mongo.FactLockRepository;
import com.novel.system.service.llm.LlmService;
import com.novel.system.util.JsonExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FactLockServiceImpl implements FactLockService {

    private final FactLockRepository factLockRepository;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    @Override
    public FactLock getOrCreate(String novelId) {
        return factLockRepository.findByNovelId(novelId)
                .orElseGet(() -> {
                    FactLock lock = new FactLock();
                    lock.setNovelId(novelId);
                    lock.setUpdatedAt(LocalDateTime.now());
                    return factLockRepository.save(lock);
                });
    }

    @Override
    public String buildFactLockSection(String novelId) {
        FactLock lock = getOrCreate(novelId);
        StringBuilder sb = new StringBuilder();
        sb.append("=== 事实锁 ===\n");

        if (!lock.getKnownCharacters().isEmpty()) {
            sb.append("已知角色: ").append(String.join(", ", lock.getKnownCharacters())).append("\n");
        }

        if (!lock.getDeathRecords().isEmpty()) {
            sb.append("已故角色: ");
            List<String> deaths = new ArrayList<>();
            for (FactLock.DeathRecord d : lock.getDeathRecords()) {
                deaths.add(d.getCharacterName() + "(第" + d.getChapterNum() + "章" + d.getCauseOfDeath() + ")");
            }
            sb.append(String.join(", ", deaths)).append("\n");
        }

        if (!lock.getRelationshipLocks().isEmpty()) {
            sb.append("关系锁定: ");
            List<String> rels = new ArrayList<>();
            for (FactLock.RelationshipLock r : lock.getRelationshipLocks().values()) {
                rels.add(r.getFromCharacter() + "->" + r.getToCharacter() +
                        "(" + r.getRelationType() + ",第" + r.getEstablishedChapter() + "章确立)");
            }
            sb.append(String.join(", ", rels)).append("\n");
        }

        if (!lock.getTimelineAnchors().isEmpty()) {
            sb.append("时间线: ");
            List<String> anchors = new ArrayList<>();
            for (FactLock.TimelineAnchor a : lock.getTimelineAnchors()) {
                anchors.add("第" + a.getChapterNum() + "章-" + a.getEvent());
            }
            sb.append(String.join(" -> ", anchors)).append("\n");
        }

        if (!lock.getWorldRuleLocks().isEmpty()) {
            sb.append("世界观硬规则: ").append(String.join(", ", lock.getWorldRuleLocks())).append("\n");
        }

        return sb.toString();
    }

    @Override
    public void updateAfterChapter(String novelId, int chapterNum, String chapterContent) {
        String systemPrompt = "你是一个小说事实提取器。从章节内容中提取事实信息，输出JSON。";
        String userPrompt = "以下是小说第" + chapterNum + "章的内容（截取前3000字）：\n\n"
                + chapterContent.substring(0, Math.min(3000, chapterContent.length()))
                + "\n\n请提取以下信息并输出JSON：\n"
                + "{\n"
                + "  \"new_characters\": [\"角色名\"],\n"
                + "  \"deaths\": [{\"name\": \"角色名\", \"cause\": \"死因\"}],\n"
                + "  \"relationships\": [{\"from\": \"角色A\", \"to\": \"角色B\", \"type\": \"关系类型\"}],\n"
                + "  \"events\": [{\"event\": \"事件描述\", \"worldDate\": \"小说内时间\"}]\n"
                + "}\n"
                + "如果某项没有，返回空数组。只输出JSON，不要其他内容。";

        try {
            String response = llmService.generateWithUsage(systemPrompt, userPrompt, "summary");
            String json = JsonExtractor.extractJson(response);
            JsonNode root = objectMapper.readTree(json);

            FactLock lock = getOrCreate(novelId);

            // New characters
            if (root.has("new_characters")) {
                for (JsonNode n : root.get("new_characters")) {
                    lock.getKnownCharacters().add(n.asText());
                }
            }

            // Deaths
            if (root.has("deaths")) {
                for (JsonNode d : root.get("deaths")) {
                    FactLock.DeathRecord record = new FactLock.DeathRecord(
                            d.get("name").asText(),
                            d.get("cause").asText(),
                            chapterNum,
                            d.get("cause").asText()
                    );
                    lock.getDeathRecords().add(record);
                }
            }

            // Relationships
            if (root.has("relationships")) {
                for (JsonNode r : root.get("relationships")) {
                    String from = r.get("from").asText();
                    String to = r.get("to").asText();
                    String key = from + "->" + to;
                    FactLock.RelationshipLock rel = new FactLock.RelationshipLock(
                            from, to, r.get("type").asText(), chapterNum, true
                    );
                    lock.getRelationshipLocks().put(key, rel);
                }
            }

            // Events
            if (root.has("events")) {
                for (JsonNode e : root.get("events")) {
                    FactLock.TimelineAnchor anchor = new FactLock.TimelineAnchor(
                            chapterNum,
                            e.get("event").asText(),
                            e.has("worldDate") ? e.get("worldDate").asText() : ""
                    );
                    lock.getTimelineAnchors().add(anchor);
                }
            }

            lock.setUpdatedAt(LocalDateTime.now());
            factLockRepository.save(lock);
            log.info("FactLock updated for novel {} chapter {}", novelId, chapterNum);

        } catch (Exception e) {
            log.error("Failed to update FactLock for novel {} chapter {}", novelId, chapterNum, e);
        }
    }

    @Override
    public FactLockViolationResult detectViolations(String novelId, int chapterNum, String chapterContent) {
        FactLock lock = getOrCreate(novelId);

        // Skip if no facts locked yet
        if (lock.getKnownCharacters().isEmpty() && lock.getDeathRecords().isEmpty()) {
            return new FactLockViolationResult(false, "LOW", "");
        }

        String systemPrompt = "你是一个小说事实一致性检查器。检查章节内容是否违反已确立的事实。输出JSON。";
        String userPrompt = buildViolationCheckPrompt(lock, chapterNum, chapterContent);

        try {
            String response = llmService.generateWithUsage(systemPrompt, userPrompt, "audit");
            String json = JsonExtractor.extractJson(response);
            JsonNode root = objectMapper.readTree(json);

            boolean hasViolation = root.has("violations") && root.get("violations").size() > 0;
            String severity = root.has("severity") ? root.get("severity").asText() : "LOW";
            String detail = root.has("detail") ? root.get("detail").asText() : "";

            if (hasViolation) {
                log.warn("FactLock violation detected in novel {} chapter {}: {}", novelId, chapterNum, detail);
            }

            return new FactLockViolationResult(hasViolation, severity, detail);

        } catch (Exception e) {
            log.error("FactLock violation check failed for novel {} chapter {}", novelId, chapterNum, e);
            return new FactLockViolationResult(false, "LOW", "");
        }
    }

    private String buildViolationCheckPrompt(FactLock lock, int chapterNum, String chapterContent) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 已确立的事实\n\n");

        if (!lock.getDeathRecords().isEmpty()) {
            sb.append("已故角色（绝对不能复活）:\n");
            for (FactLock.DeathRecord d : lock.getDeathRecords()) {
                sb.append("- ").append(d.getCharacterName()).append(": 第").append(d.getChapterNum())
                        .append("章").append(d.getCauseOfDeath()).append("\n");
            }
        }

        if (!lock.getTimelineAnchors().isEmpty()) {
            sb.append("\n时间线（不可打乱顺序）:\n");
            for (FactLock.TimelineAnchor a : lock.getTimelineAnchors()) {
                sb.append("- 第").append(a.getChapterNum()).append("章: ").append(a.getEvent()).append("\n");
            }
        }

        if (!lock.getWorldRuleLocks().isEmpty()) {
            sb.append("\n世界观硬规则（不可违反）:\n");
            for (String rule : lock.getWorldRuleLocks()) {
                sb.append("- ").append(rule).append("\n");
            }
        }

        sb.append("\n## 待检查章节内容\n\n");
        sb.append(chapterContent.substring(0, Math.min(3000, chapterContent.length())));

        sb.append("\n\n## 检查要求\n\n");
        sb.append("检查以上章节内容是否违反已确立的事实。输出JSON:\n");
        sb.append("{\n");
        sb.append("  \"violations\": [{\"type\": \"违规类型\", \"description\": \"描述\"}],\n");
        sb.append("  \"severity\": \"HIGH/MEDIUM/LOW\",\n");
        sb.append("  \"detail\": \"总体描述\"\n");
        sb.append("}\n");
        sb.append("违规类型: DEAD_CHARACTER_REVIVED, TIMELINE_CONTRADICTION, RELATIONSHIP_VIOLATION, WORLD_RULE_BREAK\n");
        sb.append("如果没有违规，violations为空数组，severity为LOW。只输出JSON。");

        return sb.toString();
    }
}
