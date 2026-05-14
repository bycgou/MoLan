package com.novel.system.ai.chain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.service.llm.LlmService;
import com.novel.system.service.llm.PromptTemplateService;
import com.novel.system.ai.chain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 校验服务实现 - 基于LLM的真实校验
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationServiceImpl implements ValidationService {
    
    private final LlmService llmService;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;

    private static final int OOC_THRESHOLD = 30;
    private static final int MIN_SCORE_THRESHOLD = 70;
    
    @Override
    public OOCResult validateOOC(ValidationRequest request) {
        log.info("开始OOC检测，小说ID: {}, 章节号: {}", request.getNovelId(), request.getChapterNum());
        
        OOCResult result = new OOCResult();
        
        try {
            String systemPrompt = promptTemplateService.getActiveTemplate("ooc_validator");
            String userMessage = buildOOCPrompt(request);
            
            String llmResponse = llmService.generateWithUsage(systemPrompt, userMessage, "audit");
            result = parseOOCResponse(llmResponse);
            
            log.info("OOC检测完成，总分: {}, 是否OOC: {}", result.getTotalScore(), result.isPassed());
            
        } catch (Exception e) {
            log.error("OOC检测失败: {}", e.getMessage(), e);
            // 异常时不通过，需要人工审核
            result.setPassed(false);
            result.setTotalScore(0);
            result.setLanguageStyleScore(0);
            result.setBehaviorMotivationScore(0);
            result.setKnowledgeLevelScore(0);
            result.setGrowthAdaptationScore(0);
        }
        
        return result;
    }
    
    @Override
    public LogicResult validateLogic(ValidationRequest request) {
        log.info("开始逻辑校验，小说ID: {}, 章节号: {}", request.getNovelId(), request.getChapterNum());
        
        LogicResult result = new LogicResult();
        
        try {
            String userMessage = buildLogicPrompt(request);
            String systemPrompt = "你是专业的小说逻辑审查Agent。请分析以下章节内容，检测是否存在逻辑问题。";
            
            String llmResponse = llmService.generateWithUsage(systemPrompt, userMessage, "audit");
            result = parseLogicResponse(llmResponse);
            
            log.info("逻辑校验完成，是否通过: {}", result.isPassed());
            
        } catch (Exception e) {
            log.error("逻辑校验失败: {}", e.getMessage(), e);
            result.setPassed(false);
            result.setConflictDescription("校验服务异常: " + e.getMessage());
            result.setCausalChainStatus("未知");
            result.setConsistencyStatus("未知");
        }
        
        return result;
    }
    
    @Override
    public ClueResult validateClue(ValidationRequest request) {
        log.info("开始伏笔校验，小说ID: {}, 章节号: {}", request.getNovelId(), request.getChapterNum());
        
        ClueResult result = new ClueResult();
        
        try {
            String userMessage = buildCluePrompt(request);
            String systemPrompt = "你是专业的伏笔管理Agent。请分析章节内容中的伏笔埋设和回收情况。";
            
            String llmResponse = llmService.generateWithUsage(systemPrompt, userMessage, "audit");
            result = parseClueResponse(llmResponse);
            
            log.info("伏笔校验完成，埋设: {}个, 回收: {}个", result.getBuriedCount(), result.getRecycledCount());
            
        } catch (Exception e) {
            log.error("伏笔校验失败: {}", e.getMessage(), e);
            result.setPassed(false);
            result.setBuriedCount(0);
            result.setRecycledCount(0);
            result.setMissingClues("校验服务异常");
            result.setUnnaturalRecycle("未知");
        }
        
        return result;
    }
    
    @Override
    public SettingResult validateSetting(ValidationRequest request) {
        log.info("开始设定校验，小说ID: {}, 章节号: {}", request.getNovelId(), request.getChapterNum());
        
        SettingResult result = new SettingResult();
        
        try {
            String userMessage = buildSettingPrompt(request);
            String systemPrompt = "你是专业的世界观设定审查Agent。请检测章节内容是否违反了已设定的世界规则。";
            
            String llmResponse = llmService.generateWithUsage(systemPrompt, userMessage, "audit");
            result = parseSettingResponse(llmResponse);
            
            log.info("设定校验完成，是否通过: {}", result.isPassed());
            
        } catch (Exception e) {
            log.error("设定校验失败: {}", e.getMessage(), e);
            result.setPassed(false);
            result.setWorldRuleStatus("校验服务异常");
            result.setWorldClockStatus("未知");
            result.setConflicts("校验服务异常: " + e.getMessage());
        }
        
        return result;
    }
    
    @Override
    public RhythmResult validateRhythm(ValidationRequest request) {
        log.info("开始节奏校验，小说ID: {}, 章节号: {}", request.getNovelId(), request.getChapterNum());
        
        RhythmResult result = new RhythmResult();
        
        try {
            String userMessage = buildRhythmPrompt(request);
            String systemPrompt = "你是专业的节奏分析Agent。请分析章节的情感曲线和节奏类型。";
            
            String llmResponse = llmService.generateWithUsage(systemPrompt, userMessage, "audit");
            result = parseRhythmResponse(llmResponse);
            
            log.info("节奏校验完成，预期: {}, 实际: {}", result.getExpectedRhythm(), result.getActualRhythm());
            
        } catch (Exception e) {
            log.error("节奏校验失败: {}", e.getMessage(), e);
            result.setPassed(false);
            result.setExpectedRhythm(request.getRhythmTag());
            result.setActualRhythm("未知");
            result.setEmotionCurve("校验服务异常: " + e.getMessage());
        }
        
        return result;
    }
    
    @Override
    public ValidationResult validateAll(ValidationRequest request) {
        log.info("开始综合校验，小说ID: {}, 章节号: {}", request.getNovelId(), request.getChapterNum());

        ValidationResult result = new ValidationResult();

        try {
            // 捕获当前线程的SecurityContext，传播到子线程
            SecurityContext securityContext = SecurityContextHolder.getContext();

            CompletableFuture<OOCResult> oocFuture = CompletableFuture.supplyAsync(() -> {
                SecurityContextHolder.setContext(securityContext);
                try { return validateOOC(request); } finally { SecurityContextHolder.clearContext(); }
            });
            CompletableFuture<LogicResult> logicFuture = CompletableFuture.supplyAsync(() -> {
                SecurityContextHolder.setContext(securityContext);
                try { return validateLogic(request); } finally { SecurityContextHolder.clearContext(); }
            });
            CompletableFuture<ClueResult> clueFuture = CompletableFuture.supplyAsync(() -> {
                SecurityContextHolder.setContext(securityContext);
                try { return validateClue(request); } finally { SecurityContextHolder.clearContext(); }
            });
            CompletableFuture<SettingResult> settingFuture = CompletableFuture.supplyAsync(() -> {
                SecurityContextHolder.setContext(securityContext);
                try { return validateSetting(request); } finally { SecurityContextHolder.clearContext(); }
            });
            CompletableFuture<RhythmResult> rhythmFuture = CompletableFuture.supplyAsync(() -> {
                SecurityContextHolder.setContext(securityContext);
                try { return validateRhythm(request); } finally { SecurityContextHolder.clearContext(); }
            });
            
            CompletableFuture.allOf(oocFuture, logicFuture, clueFuture, settingFuture, rhythmFuture).join();
            
            OOCResult oocResult = oocFuture.get();
            LogicResult logicResult = logicFuture.get();
            ClueResult clueResult = clueFuture.get();
            SettingResult settingResult = settingFuture.get();
            RhythmResult rhythmResult = rhythmFuture.get();
            
            boolean allPassed = oocResult.isPassed() && logicResult.isPassed() && 
                              clueResult.isPassed() && settingResult.isPassed() && rhythmResult.isPassed();
            
            result.setPassed(allPassed);
            if (allPassed) {
                result.setSummary("所有校验通过");
            } else {
                // 构建详细的失败原因摘要
                StringBuilder detailSummary = new StringBuilder();
                detailSummary.append("校验未通过，具体问题：");
                
                if (!oocResult.isPassed()) {
                    detailSummary.append(String.format("\n[OOC检测] 总分:%d (阈值:%d)",
                        oocResult.getTotalScore(), MIN_SCORE_THRESHOLD));
                }
                if (!logicResult.isPassed()) {
                    detailSummary.append(String.format("\n[逻辑校验] %s, 因果链:%s, 一致性:%s",
                        logicResult.getConflictDescription(),
                        logicResult.getCausalChainStatus(),
                        logicResult.getConsistencyStatus()));
                }
                if (!clueResult.isPassed()) {
                    detailSummary.append(String.format("\n[伏笔校验] 埋设:%d个, 回收:%d个, 遗漏:%s, 回收不合理:%s",
                        clueResult.getBuriedCount(), clueResult.getRecycledCount(),
                        clueResult.getMissingClues(), clueResult.getUnnaturalRecycle()));
                }
                if (!settingResult.isPassed()) {
                    detailSummary.append(String.format("\n[设定校验] 世界观规则:%s, 世界时钟:%s, 冲突:%s",
                        settingResult.getWorldRuleStatus(),
                        settingResult.getWorldClockStatus(),
                        settingResult.getConflicts()));
                }
                if (!rhythmResult.isPassed()) {
                    detailSummary.append(String.format("\n[节奏校验] 预期:%s, 实际:%s",
                        rhythmResult.getExpectedRhythm(),
                        rhythmResult.getActualRhythm()));
                }
                
                result.setSummary(detailSummary.toString());
            }
            result.setOocResult(oocResult);
            result.setLogicResult(logicResult);
            result.setClueResult(clueResult);
            result.setSettingResult(settingResult);
            result.setRhythmResult(rhythmResult);
            
            log.info("综合校验完成，结果: {}", allPassed ? "通过" : "失败");
            if (!allPassed) {
                log.info("校验失败详情: {}", result.getSummary());
            }
            
        } catch (Exception e) {
            log.error("综合校验失败: {}", e.getMessage(), e);
            // 异常时不通过，需要人工审核
            result.setPassed(false);
            result.setSummary("校验系统异常，需要人工审核: " + e.getMessage());
            result.setOocResult(new OOCResult());
            result.setLogicResult(new LogicResult());
            result.setClueResult(new ClueResult());
            result.setSettingResult(new SettingResult());
            result.setRhythmResult(new RhythmResult());
        }
        
        return result;
    }
    
    // ========== 构建Prompt ==========
    
    private String buildOOCPrompt(ValidationRequest request) {
        return """
            ===待检测内容===
            %s

            ===人物设定===
            %s

            请检测以上内容是否存在OOC（Out Of Character）问题。严格按以下JSON格式输出，不要有其他内容：
            {
              "language_style_score": 0到100的整数,
              "behavior_motivation_score": 0到100的整数,
              "knowledge_level_score": 0到100的整数,
              "growth_adaptation_score": 0到100的整数,
              "details": "详细分析"
            }
            """.formatted(
                truncate(request.getChapterContent(), 8000),
                truncate(request.getCharacterStates(), 2000)
            );
    }
    
    private String buildLogicPrompt(ValidationRequest request) {
        return """
            ===待检测内容===
            %s

            ===章节大纲===
            %s

            请分析以上内容是否存在逻辑问题。严格按以下JSON格式输出，不要有其他内容：
            {
              "causal_chain_valid": true或false,
              "consistency_valid": true或false,
              "conflict_description": "冲突描述，无冲突则为空字符串",
              "confidence": 0.0到1.0的置信度,
              "details": "详细分析"
            }
            """.formatted(
                truncate(request.getChapterContent(), 8000),
                truncate(request.getChapterOutline(), 2000)
            );
    }
    
    private String buildCluePrompt(ValidationRequest request) {
        return """
            ===待检测内容===
            %s

            ===已有伏笔===
            %s

            请分析伏笔埋设和回收情况。严格按以下JSON格式输出，不要有其他内容：
            {
              "buried_count": 本章新埋伏笔数,
              "recycled_count": 本章回收伏笔数,
              "has_missing": true或false,
              "has_unnatural": true或false,
              "missing_details": "遗漏的伏笔描述，无则为空字符串",
              "unnatural_details": "不合理的回收描述，无则为空字符串"
            }
            """.formatted(
                truncate(request.getChapterContent(), 8000),
                truncate(request.getClueBook(), 2000)
            );
    }
    
    private String buildSettingPrompt(ValidationRequest request) {
        return """
            ===待检测内容===
            %s

            ===世界设定===
            %s

            请检测章节内容是否违反了已设定的世界观规则。严格按以下JSON格式输出，不要有其他内容：
            {
              "world_rule_valid": true或false,
              "world_clock_valid": true或false,
              "conflicts": "设定冲突描述，无冲突则为空字符串",
              "details": "详细分析"
            }
            """.formatted(
                truncate(request.getChapterContent(), 8000),
                truncate(request.getTotalOutline(), 3000)
            );
    }
    
    private String buildRhythmPrompt(ValidationRequest request) {
        return """
            ===待检测内容===
            %s

            请分析章节的情感曲线和节奏。严格按以下JSON格式输出，不要有其他内容：
            {
              "expected_rhythm": "预期节奏类型(quest/fire/constellation)",
              "actual_rhythm": "实际节奏类型(quest/fire/constellation)",
              "emotion_curve": "情感曲线描述",
              "is_appropriate": true或false,
              "details": "详细分析"
            }
            """.formatted(
                truncate(request.getChapterContent(), 8000)
            );
    }
    
    // ========== 解析LLM响应 ==========
    
    private OOCResult parseOOCResponse(String response) {
        OOCResult result = new OOCResult();
        try {
            String json = extractJson(response);
            JsonNode node = objectMapper.readTree(json);

            int langScore = node.path("language_style_score").asInt(80);
            int behavScore = node.path("behavior_motivation_score").asInt(80);
            int knowScore = node.path("knowledge_level_score").asInt(80);
            int growthScore = node.path("growth_adaptation_score").asInt(80);

            result.setLanguageStyleScore(langScore);
            result.setBehaviorMotivationScore(behavScore);
            result.setKnowledgeLevelScore(knowScore);
            result.setGrowthAdaptationScore(growthScore);

            int total = (langScore + behavScore + knowScore + growthScore) / 4;
            result.setTotalScore(total);
            result.setPassed(total >= MIN_SCORE_THRESHOLD);
        } catch (Exception e) {
            log.warn("解析OOC响应失败: {}", e.getMessage());
            result.setPassed(false);
            result.setTotalScore(0);
        }
        return result;
    }
    
    private LogicResult parseLogicResponse(String response) {
        LogicResult result = new LogicResult();
        try {
            String json = extractJson(response);
            JsonNode node = objectMapper.readTree(json);

            boolean causalValid = node.path("causal_chain_valid").asBoolean(true);
            boolean consistencyValid = node.path("consistency_valid").asBoolean(true);

            result.setPassed(causalValid && consistencyValid);
            result.setCausalChainStatus(causalValid ? "通顺" : "存在问题");
            result.setConsistencyStatus(consistencyValid ? "一致" : "存在矛盾");
            result.setConflictDescription(node.path("conflict_description").asText("无"));
        } catch (Exception e) {
            log.warn("解析逻辑校验响应失败: {}", e.getMessage());
            result.setPassed(false);
            result.setConflictDescription("解析失败");
            result.setCausalChainStatus("未知");
            result.setConsistencyStatus("未知");
        }
        return result;
    }
    
    private ClueResult parseClueResponse(String response) {
        ClueResult result = new ClueResult();
        try {
            String json = extractJson(response);
            JsonNode node = objectMapper.readTree(json);

            result.setBuriedCount(node.path("buried_count").asInt(0));
            result.setRecycledCount(node.path("recycled_count").asInt(0));
            boolean hasMissing = node.path("has_missing").asBoolean(false);
            boolean hasUnnatural = node.path("has_unnatural").asBoolean(false);
            result.setPassed(!hasMissing && !hasUnnatural);
            result.setMissingClues(node.path("missing_details").asText("无"));
            result.setUnnaturalRecycle(hasUnnatural ? node.path("unnatural_details").asText("有") : "无");
        } catch (Exception e) {
            log.warn("解析伏笔校验响应失败: {}", e.getMessage());
            result.setPassed(false);
            result.setBuriedCount(0);
            result.setRecycledCount(0);
            result.setMissingClues("解析失败");
            result.setUnnaturalRecycle("未知");
        }
        return result;
    }
    
    private SettingResult parseSettingResponse(String response) {
        SettingResult result = new SettingResult();
        try {
            String json = extractJson(response);
            JsonNode node = objectMapper.readTree(json);

            boolean worldRuleValid = node.path("world_rule_valid").asBoolean(true);
            boolean worldClockValid = node.path("world_clock_valid").asBoolean(true);

            result.setPassed(worldRuleValid && worldClockValid);
            result.setWorldRuleStatus(worldRuleValid ? "符合" : "不符合");
            result.setWorldClockStatus(worldClockValid ? "一致" : "不一致");
            result.setConflicts(node.path("conflicts").asText("无"));
        } catch (Exception e) {
            log.warn("解析设定校验响应失败: {}", e.getMessage());
            result.setPassed(false);
            result.setWorldRuleStatus("解析失败");
            result.setWorldClockStatus("未知");
            result.setConflicts("解析失败");
        }
        return result;
    }
    
    private RhythmResult parseRhythmResponse(String response) {
        RhythmResult result = new RhythmResult();
        try {
            String json = extractJson(response);
            JsonNode node = objectMapper.readTree(json);

            result.setExpectedRhythm(node.path("expected_rhythm").asText("quest"));
            result.setActualRhythm(node.path("actual_rhythm").asText("quest"));
            result.setEmotionCurve(node.path("emotion_curve").asText("正常"));
            result.setPassed(node.path("is_appropriate").asBoolean(true));
        } catch (Exception e) {
            log.warn("解析节奏校验响应失败: {}", e.getMessage());
            result.setPassed(false);
            result.setActualRhythm("unknown");
            result.setEmotionCurve("解析失败");
        }
        return result;
    }
    
    // ========== 辅助方法 ==========

    private String extractJson(String response) {
        if (response == null) return "{}";
        String trimmed = response.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.replaceAll("^```json\\s*", "").replaceAll("\\s*```$", "");
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceAll("^```\\s*", "").replaceAll("\\s*```$", "");
        }
        int start = trimmed.indexOf('{');
        if (start < 0) start = trimmed.indexOf('[');
        if (start > 0) trimmed = trimmed.substring(start);
        int endObj = trimmed.lastIndexOf('}');
        int endArr = trimmed.lastIndexOf(']');
        int end = Math.max(endObj, endArr);
        if (end > 0) trimmed = trimmed.substring(0, end + 1);
        return trimmed;
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.isEmpty()) return "无";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
