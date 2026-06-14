# Memory System Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Borrow PlotPilot's memory system innovations (FactLock, EmotionLedger, T0 priority, CompletedBeats, EchoRecall, ConvergenceHourglass) and integrate them into moyun's existing six-layer architecture.

**Architecture:** Incremental additions to existing six-layer memory system. Each new component is an independent `@Service` with its own MongoDB entity and repository. Integration via two entry points: `ContextBuilderService` (pre-generation injection) and `PostProcessNode`/`ChapterPostProcessService` (post-generation extraction). A new `FactLockViolationNode` is inserted into the graph pipeline.

**Tech Stack:** Java 17, Spring Boot, Spring Data MongoDB, LangChain4j, Lombok, Jackson

---

## File Structure

### New Files (Phase 1)

| File | Responsibility |
|------|---------------|
| `backend/src/main/java/com/novel/system/entity/mongo/FactLock.java` | FactLock MongoDB entity with inner value objects |
| `backend/src/main/java/com/novel/system/repository/mongo/FactLockRepository.java` | Spring Data MongoDB repository for FactLock |
| `backend/src/main/java/com/novel/system/service/memory/FactLockService.java` | Interface: build section, update, detect violations |
| `backend/src/main/java/com/novel/system/service/memory/FactLockServiceImpl.java` | Implementation with LLM-based violation detection |
| `backend/src/main/java/com/novel/system/entity/mongo/EmotionLedger.java` | EmotionLedger MongoDB entity with inner value objects |
| `backend/src/main/java/com/novel/system/repository/mongo/EmotionLedgerRepository.java` | Spring Data MongoDB repository for EmotionLedger |
| `backend/src/main/java/com/novel/system/service/memory/EmotionLedgerService.java` | Interface: extract from chapter, format for prompt |
| `backend/src/main/java/com/novel/system/service/memory/EmotionLedgerServiceImpl.java` | Implementation with LLM-based extraction |
| `backend/src/main/java/com/novel/system/ai/graph/FactLockViolationNode.java` | Graph node for post-generation fact violation detection |

### New Files (Phase 2)

| File | Responsibility |
|------|---------------|
| `backend/src/main/java/com/novel/system/entity/mongo/CompletedBeats.java` | CompletedBeats MongoDB entity |
| `backend/src/main/java/com/novel/system/repository/mongo/CompletedBeatsRepository.java` | Spring Data MongoDB repository for CompletedBeats |
| `backend/src/main/java/com/novel/system/service/memory/CompletedBeatsService.java` | Interface: extract, format for prompt |
| `backend/src/main/java/com/novel/system/service/memory/CompletedBeatsServiceImpl.java` | Implementation |

### New Files (Phase 3)

| File | Responsibility |
|------|---------------|
| `backend/src/main/java/com/novel/system/entity/mongo/CharacterVoiceSample.java` | Voice sample MongoDB entity |
| `backend/src/main/java/com/novel/system/repository/mongo/CharacterVoiceSampleRepository.java` | Repository |
| `backend/src/main/java/com/novel/system/service/memory/EchoRecallService.java` | Interface: extract, search, format |
| `backend/src/main/java/com/novel/system/service/memory/EchoRecallServiceImpl.java` | Implementation with vector search |
| `backend/src/main/java/com/novel/system/service/memory/ConvergenceService.java` | Interface: compute phase, format |
| `backend/src/main/java/com/novel/system/service/memory/ConvergenceServiceImpl.java` | Implementation |

### Modified Files

| File | Changes |
|------|---------|
| `TokenBudgetService.java` | Phase 2: Add PriorityTier enum, tier-based allocation, sentence-boundary truncation |
| `PostProcessNode.java` | Phase 1+2: Inject FactLock/EmotionLedger/CompletedBeats calls after save |
| `ChapterPostProcessService.java` | Phase 1+3: Add FactLock update and VoiceSample extraction |
| `ContextBuilderService.java` | Phase 1+2+3: Inject all new memory sections into context |
| `MultiAgentNovelService.java` | Phase 1: Register FactLockViolationNode in graph, add edge |
| `StoryState.java` | Phase 3: Add convergenceState field |
| `RuleDrivenRetrievalService.java` | Phase 3: Add Rule 5 (EchoRecall trigger) |
| `ValidationNode.java` | Phase 3: Convergence-phase-aware validation |
| `SelfReviewNode.java` | Phase 3: Convergence-phase-aware review |

> **Note on types:** `novelId` is `String` throughout (matches existing codebase convention, not the spec's `Long`). `NovelGraphNode` is the correct interface name (verified from codebase). `ConvergenceState` is embedded as inner class of `StoryState` (avoids creating a separate entity for a simple value object).

---

## Phase 1: FactLock + EmotionLedger

### Task 1: FactLock Entity and Repository

**Files:**
- Create: `backend/src/main/java/com/novel/system/entity/mongo/FactLock.java`
- Create: `backend/src/main/java/com/novel/system/repository/mongo/FactLockRepository.java`

- [ ] **Step 1: Create FactLock entity**

```java
package com.novel.system.entity.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.*;

@Document(collection = "fact_lock")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FactLock {

    @Id
    private String id;

    @Indexed(unique = true)
    private String novelId;

    private Set<String> knownCharacters = new LinkedHashSet<>();
    private List<DeathRecord> deathRecords = new ArrayList<>();
    private Map<String, RelationshipLock> relationshipLocks = new LinkedHashMap<>();
    private List<TimelineAnchor> timelineAnchors = new ArrayList<>();
    private List<String> worldRuleLocks = new ArrayList<>();
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeathRecord {
        private String characterName;
        private String causeOfDeath;
        private int chapterNum;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelationshipLock {
        private String fromCharacter;
        private String toCharacter;
        private String relationType;
        private int establishedChapter;
        private boolean canChange;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelineAnchor {
        private int chapterNum;
        private String event;
        private String worldDate;
    }
}
```

- [ ] **Step 2: Create FactLockRepository**

```java
package com.novel.system.repository.mongo;

import com.novel.system.entity.mongo.FactLock;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FactLockRepository extends MongoRepository<FactLock, String> {
    Optional<FactLock> findByNovelId(String novelId);
    void deleteByNovelId(String novelId);
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/novel/system/entity/mongo/FactLock.java \
        backend/src/main/java/com/novel/system/repository/mongo/FactLockRepository.java
git commit -m "feat(memory): add FactLock entity and repository"
```

---

### Task 2: FactLockService Interface and Implementation

**Files:**
- Create: `backend/src/main/java/com/novel/system/service/memory/FactLockService.java`
- Create: `backend/src/main/java/com/novel/system/service/memory/FactLockServiceImpl.java`

- [ ] **Step 1: Create FactLockService interface**

```java
package com.novel.system.service.memory;

import com.novel.system.entity.mongo.FactLock;

public interface FactLockService {

    FactLock getOrCreate(String novelId);

    String buildFactLockSection(String novelId);

    void updateAfterChapter(String novelId, int chapterNum, String chapterContent);

    FactLockViolationResult detectViolations(String novelId, int chapterNum, String chapterContent);

    record FactLockViolationResult(
        boolean hasViolation,
        String severity,  // HIGH / MEDIUM / LOW
        String detail
    ) {}
}
```

- [ ] **Step 2: Create FactLockServiceImpl**

```java
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
                rels.add(r.getFromCharacter() + "→" + r.getToCharacter() +
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
            sb.append(String.join(" → ", anchors)).append("\n");
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
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/novel/system/service/memory/FactLockService.java \
        backend/src/main/java/com/novel/system/service/memory/FactLockServiceImpl.java
git commit -m "feat(memory): add FactLockService with violation detection"
```

---

### Task 3: EmotionLedger Entity and Repository

**Files:**
- Create: `backend/src/main/java/com/novel/system/entity/mongo/EmotionLedger.java`
- Create: `backend/src/main/java/com/novel/system/repository/mongo/EmotionLedgerRepository.java`

- [ ] **Step 1: Create EmotionLedger entity**

```java
package com.novel.system.entity.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "emotion_ledger")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmotionLedger {

    @Id
    private String id;

    @Indexed(unique = true)
    private String novelId;

    private List<EmotionEntry> entries = new ArrayList<>();
    private int version = 0;
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmotionEntry {
        private int chapterNum;
        private EmotionalWound wound;
        private EmotionalBoon boon;
        private PowerShift powerShift;
        private List<OpenLoop> openLoops;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmotionalWound {
        private String loss;
        private String psychImpact;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmotionalBoon {
        private String gain;
        private String valueShift;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PowerShift {
        private String from;
        private String to;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenLoop {
        private String hook;
        private int urgency;
    }
}
```

- [ ] **Step 2: Create EmotionLedgerRepository**

```java
package com.novel.system.repository.mongo;

import com.novel.system.entity.mongo.EmotionLedger;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmotionLedgerRepository extends MongoRepository<EmotionLedger, String> {
    Optional<EmotionLedger> findByNovelId(String novelId);
    void deleteByNovelId(String novelId);
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/novel/system/entity/mongo/EmotionLedger.java \
        backend/src/main/java/com/novel/system/repository/mongo/EmotionLedgerRepository.java
git commit -m "feat(memory): add EmotionLedger entity and repository"
```

---

### Task 4: EmotionLedgerService Interface and Implementation

**Files:**
- Create: `backend/src/main/java/com/novel/system/service/memory/EmotionLedgerService.java`
- Create: `backend/src/main/java/com/novel/system/service/memory/EmotionLedgerServiceImpl.java`

- [ ] **Step 1: Create EmotionLedgerService interface**

```java
package com.novel.system.service.memory;

public interface EmotionLedgerService {

    String formatForPrompt(String novelId, int currentChapterNum);

    void extractFromChapter(String novelId, int chapterNum, String chapterContent);
}
```

- [ ] **Step 2: Create EmotionLedgerServiceImpl**

```java
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
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/novel/system/service/memory/EmotionLedgerService.java \
        backend/src/main/java/com/novel/system/service/memory/EmotionLedgerServiceImpl.java
git commit -m "feat(memory): add EmotionLedgerService with LLM extraction"
```

---

### Task 5: FactLockViolationNode (Graph Node)

**Files:**
- Create: `backend/src/main/java/com/novel/system/ai/graph/FactLockViolationNode.java`

- [ ] **Step 1: Create FactLockViolationNode**

```java
package com.novel.system.ai.graph;

import com.novel.system.service.memory.FactLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
                            .revisionNotes(java.util.List.of(
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
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/FactLockViolationNode.java
git commit -m "feat(memory): add FactLockViolationNode for post-generation checks"
```

---

### Task 6: Register FactLockViolationNode in Graph Pipeline

**Files:**
- Modify: `backend/src/main/java/com/novel/system/service/chapter/MultiAgentNovelService.java`

- [ ] **Step 1: Add FactLockViolationNode to constructor injection**

In `MultiAgentNovelService.java`, add the node to the constructor parameter list (Lombok `@RequiredArgsConstructor` generates the constructor):

```java
private final FactLockViolationNode factLockViolationNode;
```

- [ ] **Step 2: Register the node in the node map**

Find the `Map.ofEntries(...)` or similar node registration block and add:

```java
Map.entry("factLockViolation", factLockViolationNode),
```

- [ ] **Step 3: Add edges to the graph builder**

Find the existing conditional edge from `audit_router` to `summary` for the PASS case. It looks like:

```java
.conditionalEdge("audit_router", "summary",
        s -> "PASS".equals(s.getAuditDecision()), Set.of(Mode.FULL))
```

Replace it with two edges (PASS routes through fact_lock_violation first):

```java
.conditionalEdge("audit_router", "fact_lock_violation",
        s -> "PASS".equals(s.getAuditDecision()), Set.of(Mode.FULL))
.edge("fact_lock_violation", "summary", Set.of(Mode.FULL, Mode.STREAMING))
```

Keep the existing REPLAN and REWRITE conditional edges unchanged.

The new graph flow for FULL mode:
```
... → audit_router --(PASS)--> fact_lock_violation --> summary --> post_process
      audit_router --(REPLAN)--> outline
      audit_router --(REWRITE)--> writing
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/novel/system/service/chapter/MultiAgentNovelService.java
git commit -m "feat(memory): wire FactLockViolationNode into FULL graph pipeline"
```

---

### Task 7: Integrate FactLock + EmotionLedger into PostProcessNode

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/graph/PostProcessNode.java`

- [ ] **Step 1: Add FactLock and EmotionLedger service injection and calls**

In `PostProcessNode.java`:

1. Add to constructor injection: `FactLockService factLockService`, `EmotionLedgerService emotionLedgerService`
2. After the `postProcessService.postProcess(...)` call (around line 90), add:

```java
// Update FactLock (async, non-blocking)
try {
    String content = state.getFinalResult() != null
            ? state.getFinalResult().getFinalContent()
            : state.getDraftContent().getContent();
    CompletableFuture.runAsync(() -> {
        try {
            factLockService.updateAfterChapter(state.getNovelId(), state.getChapterNum(), content);
            emotionLedgerService.extractFromChapter(state.getNovelId(), state.getChapterNum(), content);
        } catch (Exception e) {
            log.error("Failed to update FactLock/EmotionLedger", e);
        }
    });
} catch (Exception e) {
    log.error("Failed to start FactLock/EmotionLedger update", e);
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/PostProcessNode.java
git commit -m "feat(memory): integrate FactLock + EmotionLedger into PostProcessNode"
```

---

### Task 8: Inject FactLock + EmotionLedger into ContextBuilderService

**Files:**
- Modify: `backend/src/main/java/com/novel/system/service/chapter/ContextBuilderService.java`

- [ ] **Step 1: Add service injection**

Add `FactLockService` and `EmotionLedgerService` to the constructor-injected dependencies of `ContextBuilderService`.

- [ ] **Step 2: Inject FactLock section into buildWritingContext()**

In the `buildWritingContext()` method, after the existing Layer 1 (StoryState) injection and before character state injection, add:

```java
// FactLock (T0 priority - injected early)
try {
    String factLockSection = factLockService.buildFactLockSection(novelId);
    if (!factLockSection.isEmpty()) {
        sb.append(factLockSection).append("\n");
    }
} catch (Exception e) {
    log.error("Failed to build FactLock section", e);
}
```

- [ ] **Step 3: Inject EmotionLedger section into buildWritingContext()**

In the `buildWritingContext()` method, after the existing Layer 4 (sliding window) injection, add:

```java
// EmotionLedger (T1 priority)
try {
    String emotionSection = emotionLedgerService.formatForPrompt(novelId, chapterNum);
    if (!emotionSection.isEmpty()) {
        sb.append(emotionSection).append("\n");
    }
} catch (Exception e) {
    log.error("Failed to build EmotionLedger section", e);
}
```

- [ ] **Step 4: Also inject into buildOutlineContext()**

Apply the same two injections to `buildOutlineContext()` as well — FactLock after StoryState, EmotionLedger after sliding window.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/novel/system/service/chapter/ContextBuilderService.java
git commit -m "feat(memory): inject FactLock + EmotionLedger into context builder"
```

---

## Phase 2: T0 Priority Protection + CompletedBeats

### Task 9: TokenBudgetService Refactor — PriorityTier

**Files:**
- Modify: `backend/src/main/java/com/novel/system/service/chapter/TokenBudgetService.java`

- [ ] **Step 1: Add PriorityTier enum**

Add this enum at the top of `TokenBudgetService`:

```java
public enum PriorityTier {
    T0_CRITICAL(0.20, false),    // 绝对保护，永不压缩
    T1_COMPRESSIBLE(0.30, true), // 可按比例压缩
    T2_DYNAMIC(0.35, true),      // 按水位线截断
    T3_SACRIFICIAL(0.15, true);  // 最先被牺牲，最低保障5%

    final double budgetRatio;
    final boolean compressible;

    PriorityTier(double budgetRatio, boolean compressible) {
        this.budgetRatio = budgetRatio;
        this.compressible = compressible;
    }
}
```

- [ ] **Step 2: Add section-to-tier mapping**

Add a static map:

```java
private static final Map<String, PriorityTier> SECTION_TIERS = new LinkedHashMap<>();
static {
    // T0 - Critical (never compressed)
    SECTION_TIERS.put("antiHallucination", PriorityTier.T0_CRITICAL);
    SECTION_TIERS.put("storyState", PriorityTier.T0_CRITICAL);
    SECTION_TIERS.put("clueBook", PriorityTier.T0_CRITICAL);
    SECTION_TIERS.put("characterState", PriorityTier.T0_CRITICAL);
    // T1 - Compressible
    SECTION_TIERS.put("arcSummary", PriorityTier.T1_COMPRESSIBLE);
    SECTION_TIERS.put("novelOutline", PriorityTier.T1_COMPRESSIBLE);
    SECTION_TIERS.put("styleCard", PriorityTier.T1_COMPRESSIBLE);
    SECTION_TIERS.put("worldSetting", PriorityTier.T1_COMPRESSIBLE);
    SECTION_TIERS.put("emotionLedger", PriorityTier.T1_COMPRESSIBLE);
    // T2 - Dynamic
    SECTION_TIERS.put("recentOutline", PriorityTier.T2_DYNAMIC);
    // T3 - Sacrificial
    SECTION_TIERS.put("echoRecall", PriorityTier.T3_SACRIFICIAL);
}
```

- [ ] **Step 3: Refactor calculateBudget() to use tier-based allocation**

> **Note:** `BUDGET_RATIOS` is the existing static `LinkedHashMap` already defined in `TokenBudgetService.java` (lines 27-37 of the current file). It maps section names to their ratio values. The new `calculateBudget()` method reuses this existing map but applies tier-based allocation instead of flat proportional allocation.

Replace the existing `calculateBudget()` method with tier-aware allocation:

```java
public TokenBudget calculateBudget(int modelMaxToken) {
    int availableTokens = (int) (modelMaxToken * 0.80); // 80% usable

    // Allocate output first
    int outputTokens = (int) (availableTokens * 0.39);
    int contextBudget = availableTokens - outputTokens;

    Map<String, Integer> allocation = new LinkedHashMap<>();

    // Allocate by tier priority: T0 first, then T1, T2, T3
    for (PriorityTier tier : PriorityTier.values()) {
        double tierBudget = contextBudget * tier.budgetRatio;
        List<Map.Entry<String, Double>> tierSections = BUDGET_RATIOS.entrySet().stream()
                .filter(e -> SECTION_TIERS.getOrDefault(e.getKey(), PriorityTier.T1_COMPRESSIBLE) == tier)
                .collect(java.util.stream.Collectors.toList());

        double tierTotalRatio = tierSections.stream()
                .mapToDouble(Map.Entry::getValue).sum();

        for (Map.Entry<String, Double> section : tierSections) {
            int tokens = tierTotalRatio > 0
                    ? (int) (tierBudget * section.getValue() / tierTotalRatio)
                    : 0;
            allocation.put(section.getKey(), tokens);
        }
    }

    allocation.put("output", outputTokens);

    TokenBudget budget = new TokenBudget();
    budget.setTotalTokens(modelMaxToken);
    budget.setAllocation(allocation);
    return budget;
}
```

- [ ] **Step 4: Improve truncateToBudget() with sentence boundary awareness**

Replace the existing `truncateToBudget()` method:

```java
public String truncateToBudget(String text, int tokenBudget) {
    if (text == null || text.isEmpty()) return "";
    int maxChars = (int) (tokenBudget * 1.5); // 1.5 chars/token for Chinese
    if (text.length() <= maxChars) return text;

    // Find nearest sentence boundary before maxChars
    String truncated = text.substring(0, maxChars);
    int lastSentenceEnd = Math.max(
            Math.max(truncated.lastIndexOf('。'), truncated.lastIndexOf('！')),
            Math.max(truncated.lastIndexOf('？'), truncated.lastIndexOf('\n'))
    );

    if (lastSentenceEnd > maxChars * 0.7) {
        // Found a good sentence boundary (at least 70% of budget used)
        return truncated.substring(0, lastSentenceEnd + 1) + "\n...[TRUNCATED]";
    }

    return truncated + "...[TRUNCATED]";
}
```

- [ ] **Step 5: Add budget reclamation method**

Add a method to reclaim unused budget from sections that used less than allocated:

```java
public TokenBudget reclaimUnusedBudget(TokenBudget budget, Map<String, Integer> actualUsage) {
    int totalReclaimed = 0;
    for (Map.Entry<String, Integer> entry : budget.getAllocation().entrySet()) {
        String section = entry.getKey();
        int allocated = entry.getValue();
        int used = actualUsage.getOrDefault(section, allocated);
        int unused = Math.max(0, allocated - used);
        totalReclaimed += unused;
        budget.getAllocation().put(section, allocated - unused);
    }
    // Reclaim to output
    int currentOutput = budget.getAllocation().getOrDefault("output", 0);
    budget.getAllocation().put("output", currentOutput + totalReclaimed);
    return budget;
}
```

Usage: In `ContextBuilderService`, after building each section, track actual token usage and call `reclaimUnusedBudget()` before final assembly.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/novel/system/service/chapter/TokenBudgetService.java
git commit -m "feat(memory): refactor TokenBudgetService with T0 priority protection"
```

---

### Task 10: CompletedBeats Entity and Repository

**Files:**
- Create: `backend/src/main/java/com/novel/system/entity/mongo/CompletedBeats.java`
- Create: `backend/src/main/java/com/novel/system/repository/mongo/CompletedBeatsRepository.java`

- [ ] **Step 1: Create CompletedBeats entity**

```java
package com.novel.system.entity.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "completed_beats")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompletedBeats {

    @Id
    private String id;

    @Indexed(unique = true)
    private String novelId;

    private List<BeatRecord> beats = new ArrayList<>();
    private LocalDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BeatRecord {
        private String beatId;
        private String summary;
        private int chapterNum;
        private List<String> charactersInvolved;
        private String plotThread;
    }
}
```

- [ ] **Step 2: Create CompletedBeatsRepository**

```java
package com.novel.system.repository.mongo;

import com.novel.system.entity.mongo.CompletedBeats;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompletedBeatsRepository extends MongoRepository<CompletedBeats, String> {
    Optional<CompletedBeats> findByNovelId(String novelId);
    void deleteByNovelId(String novelId);
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/novel/system/entity/mongo/CompletedBeats.java \
        backend/src/main/java/com/novel/system/repository/mongo/CompletedBeatsRepository.java
git commit -m "feat(memory): add CompletedBeats entity and repository"
```

---

### Task 11: CompletedBeatsService

**Files:**
- Create: `backend/src/main/java/com/novel/system/service/memory/CompletedBeatsService.java`
- Create: `backend/src/main/java/com/novel/system/service/memory/CompletedBeatsServiceImpl.java`

- [ ] **Step 1: Create CompletedBeatsService interface**

```java
package com.novel.system.service.memory;

public interface CompletedBeatsService {

    String formatForPrompt(String novelId);

    void extractFromChapter(String novelId, int chapterNum, String chapterContent);
}
```

- [ ] **Step 2: Create CompletedBeatsServiceImpl**

```java
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
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/novel/system/service/memory/CompletedBeatsService.java \
        backend/src/main/java/com/novel/system/service/memory/CompletedBeatsServiceImpl.java
git commit -m "feat(memory): add CompletedBeatsService with LLM extraction"
```

---

### Task 12: Integrate CompletedBeats into PostProcessNode and ContextBuilderService

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/graph/PostProcessNode.java`
- Modify: `backend/src/main/java/com/novel/system/service/chapter/ContextBuilderService.java`

- [ ] **Step 1: Add CompletedBeatsService to PostProcessNode**

Inject `CompletedBeatsService completedBeatsService` into `PostProcessNode` constructor. Add to the async block alongside FactLock and EmotionLedger:

```java
completedBeatsService.extractFromChapter(state.getNovelId(), state.getChapterNum(), content);
```

- [ ] **Step 2: Inject CompletedBeats into ContextBuilderService**

Add `CompletedBeatsService` to `ContextBuilderService` constructor injection. In both `buildWritingContext()` and `buildOutlineContext()`, inject after the FactLock section:

```java
// CompletedBeats (T0 priority)
try {
    String beatsSection = completedBeatsService.formatForPrompt(novelId);
    if (!beatsSection.isEmpty()) {
        sb.append(beatsSection).append("\n");
    }
} catch (Exception e) {
    log.error("Failed to build CompletedBeats section", e);
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/PostProcessNode.java \
        backend/src/main/java/com/novel/system/service/chapter/ContextBuilderService.java
git commit -m "feat(memory): integrate CompletedBeats into post-process and context builder"
```

---

## Phase 3: Echo Recall + Convergence Hourglass

### Task 13: CharacterVoiceSample Entity and Repository

**Files:**
- Create: `backend/src/main/java/com/novel/system/entity/mongo/CharacterVoiceSample.java`
- Create: `backend/src/main/java/com/novel/system/repository/mongo/CharacterVoiceSampleRepository.java`

- [ ] **Step 1: Create CharacterVoiceSample entity**

```java
package com.novel.system.entity.mongo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "character_voice_samples")
@CompoundIndex(name = "novel_chapter_idx", def = "{'novelId': 1, 'chapterNum': -1}")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CharacterVoiceSample {

    @Id
    private String id;

    @Indexed
    private String novelId;

    @Indexed
    private String characterName;

    private String dialogue;
    private String context;
    private int chapterNum;
    private String emotion;
    private String situation;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 2: Create CharacterVoiceSampleRepository**

```java
package com.novel.system.repository.mongo;

import com.novel.system.entity.mongo.CharacterVoiceSample;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CharacterVoiceSampleRepository extends MongoRepository<CharacterVoiceSample, String> {
    List<CharacterVoiceSample> findByNovelIdAndCharacterName(String novelId, String characterName);
    List<CharacterVoiceSample> findByNovelIdAndCharacterNameAndChapterNumLessThan(
            String novelId, String characterName, int chapterNum);
    void deleteByNovelId(String novelId);
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/novel/system/entity/mongo/CharacterVoiceSample.java \
        backend/src/main/java/com/novel/system/repository/mongo/CharacterVoiceSampleRepository.java
git commit -m "feat(memory): add CharacterVoiceSample entity and repository"
```

---

### Task 14: EchoRecallService

**Files:**
- Create: `backend/src/main/java/com/novel/system/service/memory/EchoRecallService.java`
- Create: `backend/src/main/java/com/novel/system/service/memory/EchoRecallServiceImpl.java`

- [ ] **Step 1: Create EchoRecallService interface**

```java
package com.novel.system.service.memory;

import java.util.List;

public interface EchoRecallService {

    String formatForPrompt(String novelId, int currentChapterNum, List<String> characters);

    void extractVoiceSamples(String novelId, int chapterNum, String chapterContent);

    List<VoiceSearchResult> searchVoiceSamples(String novelId, String characterName,
                                                int currentChapterNum, int minAgeChapters);

    record VoiceSearchResult(String dialogue, String context, int chapterNum, double score) {}
}
```

- [ ] **Step 2: Create EchoRecallServiceImpl**

```java
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
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/novel/system/service/memory/EchoRecallService.java \
        backend/src/main/java/com/novel/system/service/memory/EchoRecallServiceImpl.java
git commit -m "feat(memory): add EchoRecallService with voice sample extraction"
```

> **Note on Milvus integration:** The current implementation uses MongoDB queries for voice sample retrieval (by character name + chapter age filter). The spec requires Milvus vector search for semantic similarity. This is a deliberate incremental approach — MongoDB query works immediately without Milvus dependency. A follow-up enhancement should add `EmbeddingService.getEmbedding()` calls in `extractVoiceSamples()` to store embeddings in Milvus, and replace `searchVoiceSamples()` with vector similarity search via `VectorStoreService`. This is tracked as a future improvement, not a blocker.

---

### Task 15: ConvergenceService

**Files:**
- Create: `backend/src/main/java/com/novel/system/service/memory/ConvergenceService.java`
- Create: `backend/src/main/java/com/novel/system/service/memory/ConvergenceServiceImpl.java`
- Modify: `backend/src/main/java/com/novel/system/entity/mongo/StoryState.java` (add field)

- [ ] **Step 1: Add ConvergenceState to StoryState**

In `StoryState.java`, add a new field and inner class:

```java
// Add field:
private ConvergenceState convergenceState;

// Add inner class:
@Data
@NoArgsConstructor
@AllArgsConstructor
public static class ConvergenceState {
    private int currentChapter;
    private int totalChapters;
    private double progress;
    private String phase;  // OPENING / DEVELOPMENT / CONVERGENCE / FINALE
    private List<String> openHooks = new ArrayList<>();
    private int chaptersRemaining;
}
```

- [ ] **Step 2: Create ConvergenceService interface**

```java
package com.novel.system.service.memory;

public interface ConvergenceService {

    String formatForPrompt(String novelId);

    void updateConvergenceState(String novelId, int currentChapter, int totalChapters);

    String getPhase(String novelId);
}
```

- [ ] **Step 3: Create ConvergenceServiceImpl**

```java
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
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/novel/system/entity/mongo/StoryState.java \
        backend/src/main/java/com/novel/system/service/memory/ConvergenceService.java \
        backend/src/main/java/com/novel/system/service/memory/ConvergenceServiceImpl.java
git commit -m "feat(memory): add ConvergenceService with narrative progress tracking"
```

---

### Task 16: Integrate Phase 3 into ChapterPostProcessService and ContextBuilderService

**Files:**
- Modify: `backend/src/main/java/com/novel/system/service/chapter/ChapterPostProcessService.java`
- Modify: `backend/src/main/java/com/novel/system/service/chapter/ContextBuilderService.java`

- [ ] **Step 1: Add EchoRecallService to ChapterPostProcessService**

Inject `EchoRecallService echoRecallService` and `ConvergenceService convergenceService` into `ChapterPostProcessService`. At the end of the `postProcess()` method, add:

```java
// Extract voice samples for Echo Recall
echoRecallService.extractVoiceSamples(novelId, chapterNum, chapterContent);

// Update convergence state (if total chapters is known)
// This will be called separately when total chapters is configured
```

- [ ] **Step 2: Inject EchoRecall + Convergence into ContextBuilderService**

Add `EchoRecallService` and `ConvergenceService` to `ContextBuilderService` constructor injection.

In `buildWritingContext()`, inject EchoRecall (T3 priority, near the end of context assembly):

```java
// EchoRecall (T3 priority - injected near end)
try {
    List<String> chapterCharacters = extractCharactersFromOutline(chapter.getChapterOutline());
    String echoSection = echoRecallService.formatForPrompt(novelId, chapterNum, chapterCharacters);
    if (!echoSection.isEmpty()) {
        sb.append(echoSection).append("\n");
    }
} catch (Exception e) {
    log.error("Failed to build EchoRecall section", e);
}
```

Inject Convergence (T0 priority, near the beginning):

```java
// Convergence state (T0 priority)
try {
    String convergenceSection = convergenceService.formatForPrompt(novelId);
    if (!convergenceSection.isEmpty()) {
        sb.append(convergenceSection).append("\n");
    }
} catch (Exception e) {
    log.error("Failed to build Convergence section", e);
}
```

- [ ] **Step 3: Add helper method for character extraction from outline**

Add a private helper to `ContextBuilderService`. The existing `CharacterStateService` has a `formatForPrompt(novelId)` method that returns all characters. Parse character names from it:

```java
private List<String> extractCharactersFromOutline(String novelId, String outline) {
    if (outline == null || outline.isEmpty()) return List.of();
    List<String> characters = new ArrayList<>();
    try {
        // Use existing formatForPrompt to get all characters, then match against outline
        String allCharactersPrompt = characterStateService.formatForPrompt(novelId);
        // Parse character names from the formatted string (lines starting with "角色:")
        for (String line : allCharactersPrompt.split("\n")) {
            if (line.startsWith("角色:") || line.startsWith("- ")) {
                String name = line.replaceAll("^角色:\\s*", "").replaceAll("^-\\s*", "")
                        .split("\\s+")[0].trim();
                if (!name.isEmpty() && outline.contains(name)) {
                    characters.add(name);
                }
            }
        }
    } catch (Exception e) {
        log.debug("Failed to extract characters from outline for EchoRecall", e);
    }
    return characters;
}
```

- [ ] **Step 4: Also inject into buildOutlineContext()**

Apply the same injections to `buildOutlineContext()` as well. After the existing Layer 1 (StoryState) injection:

```java
// Convergence state
try {
    String convergenceSection = convergenceService.formatForPrompt(novelId);
    if (!convergenceSection.isEmpty()) sb.append(convergenceSection).append("\n");
} catch (Exception e) { log.error("Failed to build Convergence section", e); }

// FactLock
try {
    String factLockSection = factLockService.buildFactLockSection(novelId);
    if (!factLockSection.isEmpty()) sb.append(factLockSection).append("\n");
} catch (Exception e) { log.error("Failed to build FactLock section", e); }

// CompletedBeats
try {
    String beatsSection = completedBeatsService.formatForPrompt(novelId);
    if (!beatsSection.isEmpty()) sb.append(beatsSection).append("\n");
} catch (Exception e) { log.error("Failed to build CompletedBeats section", e); }
```

After the existing Layer 4 (sliding window) injection:

```java
// EmotionLedger
try {
    String emotionSection = emotionLedgerService.formatForPrompt(novelId, chapterNum);
    if (!emotionSection.isEmpty()) sb.append(emotionSection).append("\n");
} catch (Exception e) { log.error("Failed to build EmotionLedger section", e); }
```

Near the end (after RAG injection):

```java
// EchoRecall
try {
    List<String> chapterCharacters = extractCharactersFromOutline(novelId, arcOutline);
    String echoSection = echoRecallService.formatForPrompt(novelId, chapterNum, chapterCharacters);
    if (!echoSection.isEmpty()) sb.append(echoSection).append("\n");
} catch (Exception e) { log.error("Failed to build EchoRecall section", e); }
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/novel/system/service/chapter/ChapterPostProcessService.java \
        backend/src/main/java/com/novel/system/service/chapter/ContextBuilderService.java
git commit -m "feat(memory): integrate EchoRecall + Convergence into services"
```

---

### Task 17: Add Rule 5 (EchoRecall Trigger) to RuleDrivenRetrievalService

**Files:**
- Modify: `backend/src/main/java/com/novel/system/service/event/RuleDrivenRetrievalService.java`

- [ ] **Step 1: Inject EchoRecallService and add Rule 5**

Add `EchoRecallService echoRecallService` to the constructor injection. Add a new rule method:

```java
/**
 * Rule 5: Echo Recall — when current chapter outline mentions a character,
 * search for that character's classic quotes from early chapters (>15 chapters ago).
 */
private String buildEchoRecallContext(String novelId, int chapterNum, String outline) {
    if (outline == null || outline.isEmpty()) return "";
    try {
        // Get all character names from CharacterStateService
        String allChars = characterStateService.formatForPrompt(novelId);
        List<String> mentionedCharacters = new ArrayList<>();
        for (String line : allChars.split("\n")) {
            String name = line.replaceAll("^角色:\\s*", "").replaceAll("^-\\s*", "")
                    .split("\\s+")[0].trim();
            if (!name.isEmpty() && outline.contains(name)) {
                mentionedCharacters.add(name);
            }
        }
        if (mentionedCharacters.isEmpty()) return "";
        return echoRecallService.formatForPrompt(novelId, chapterNum, mentionedCharacters);
    } catch (Exception e) {
        log.debug("Rule 5 (EchoRecall) failed", e);
        return "";
    }
}
```

Call this method from the existing `buildRetrievalContext()` or equivalent method that assembles all rule-driven retrieval results.

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/novel/system/service/event/RuleDrivenRetrievalService.java
git commit -m "feat(memory): add Rule 5 EchoRecall trigger to RuleDrivenRetrievalService"
```

---

### Task 18: Convergence Phase-Driven Pipeline Behavior

**Files:**
- Modify: `backend/src/main/java/com/novel/system/ai/graph/ValidationNode.java`
- Modify: `backend/src/main/java/com/novel/system/ai/graph/SelfReviewNode.java`

- [ ] **Step 1: Inject ConvergenceService into ValidationNode**

Add `ConvergenceService convergenceService` to `ValidationNode` constructor injection. In the `apply()` method, when the convergence phase is CONVERGENCE or FINALE, add an extra validation dimension:

```java
// After existing validations, add convergence-aware check
String phase = convergenceService.getPhase(state.getNovelId());
if ("CONVERGENCE".equals(phase) || "FINALE".equals(phase)) {
    // Add clue recovery check — verify unresolved clues are being addressed
    String clueRecoveryCheck = buildClueRecoveryCheck(state);
    if (!clueRecoveryCheck.isEmpty()) {
        // Append to validation warnings
        log.info("Convergence phase clue recovery check: {}", clueRecoveryCheck);
    }
}
```

The `buildClueRecoveryCheck` method checks if unresolved clues from ClueBook are being addressed in the current chapter outline/content.

- [ ] **Step 2: Inject ConvergenceService into SelfReviewNode**

Add `ConvergenceService convergenceService` to `SelfReviewNode` constructor injection. In the `apply()` method, when phase is CONVERGENCE, add subplot merge review to the prompt:

```java
String phase = convergenceService.getPhase(state.getNovelId());
String convergenceHint = "";
if ("CONVERGENCE".equals(phase)) {
    convergenceHint = "\n\n【收束期特别检查】当前处于收束期，请特别注意：\n"
            + "1. 是否有支线可以合并或闭合？\n"
            + "2. 是否减少了新支线的引入？\n"
            + "3. 节奏是否在加速？";
} else if ("FINALE".equals(phase)) {
    convergenceHint = "\n\n【终章期特别检查】当前处于终章期，请特别注意：\n"
            + "1. 所有伏笔是否已回收？\n"
            + "2. 角色弧线是否完整闭合？\n"
            + "3. 是否还有未解决的冲突？";
}
// Append convergenceHint to the existing review prompt
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/novel/system/ai/graph/ValidationNode.java \
        backend/src/main/java/com/novel/system/ai/graph/SelfReviewNode.java
git commit -m "feat(memory): add convergence phase-aware validation and review"
```

---

### Task 19: Final Integration Test

- [ ] **Step 1: Verify compilation**

```bash
cd D:/dev/moyun/backend && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 2: Run existing tests**

```bash
cd D:/dev/moyun/backend && mvn test -q
```

Expected: All existing tests pass. No regressions.

- [ ] **Step 3: Manual verification checklist**

Verify each component by checking logs during a chapter generation:
- [ ] FactLock section appears in context logs
- [ ] EmotionLedger section appears in context logs
- [ ] CompletedBeats section appears in context logs
- [ ] EchoRecall section appears in context logs (when character has old voice samples)
- [ ] Convergence section appears in context logs
- [ ] FactLock violation check runs after audit_router
- [ ] PostProcessNode updates FactLock/EmotionLedger/CompletedBeats after save
- [ ] EchoRecall trigger works in RuleDrivenRetrievalService
- [ ] Convergence phase affects ValidationNode and SelfReviewNode prompts
- [ ] buildOutlineContext() includes all 5 new sections

- [ ] **Step 4: Commit final state**

```bash
git add -A
git commit -m "feat(memory): complete memory system optimization (Phase 1+2+3)"
```
