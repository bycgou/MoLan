package com.novel.system.ai.chain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novel.system.service.llm.LlmService;
import com.novel.system.service.llm.PromptTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidationServiceImplTest {

    @Mock
    private LlmService llmService;

    @Mock
    private PromptTemplateService promptTemplateService;

    private ValidationServiceImpl validationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ValidationRequest request;

    @BeforeEach
    void setUp() {
        validationService = new ValidationServiceImpl(llmService, promptTemplateService, objectMapper);

        request = new ValidationRequest();
        request.setNovelId("test-novel");
        request.setChapterNum(1);
        request.setChapterContent("测试章节内容");
        request.setChapterOutline("测试大纲");
        request.setCharacterStates("角色设定");
        request.setClueBook("伏笔记录");
        request.setTotalOutline("世界设定");
        request.setRhythmTag("quest");
    }

    // ====== Logic Tests ======

    @Test
    void testLogic_validJson_passes() {
        String jsonResponse = """
            {
              "causal_chain_valid": true,
              "consistency_valid": true,
              "conflict_description": "",
              "confidence": 0.95,
              "details": "因果链通顺，逻辑一致"
            }
            """;
        when(llmService.generateWithUsage(any(), anyString(), anyString()))
                .thenReturn(jsonResponse);

        LogicResult result = validationService.validateLogic(request);

        assertTrue(result.isPassed());
        assertEquals("通顺", result.getCausalChainStatus());
        assertEquals("一致", result.getConsistencyStatus());
        assertEquals("", result.getConflictDescription());
    }

    @Test
    void testLogic_conflictFails() {
        String jsonResponse = """
            {
              "causal_chain_valid": true,
              "consistency_valid": false,
              "conflict_description": "第三段与大纲矛盾，角色A不应在此时出现",
              "confidence": 0.88,
              "details": "发现逻辑冲突"
            }
            """;
        when(llmService.generateWithUsage(any(), anyString(), anyString()))
                .thenReturn(jsonResponse);

        LogicResult result = validationService.validateLogic(request);

        assertFalse(result.isPassed());
        assertEquals("通顺", result.getCausalChainStatus());
        assertEquals("存在矛盾", result.getConsistencyStatus());
        assertTrue(result.getConflictDescription().contains("矛盾"));
    }

    // ====== OOC Tests ======

    @Test
    void testOOC_normalScores_passes() {
        String jsonResponse = """
            {
              "language_style_score": 85,
              "behavior_motivation_score": 90,
              "knowledge_level_score": 80,
              "growth_adaptation_score": 75,
              "details": "角色表现符合设定"
            }
            """;
        when(llmService.generateWithUsage(any(), anyString(), anyString()))
                .thenReturn(jsonResponse);

        OOCResult result = validationService.validateOOC(request);

        assertTrue(result.isPassed());
        assertEquals(85, result.getLanguageStyleScore());
        assertEquals(90, result.getBehaviorMotivationScore());
        assertEquals(80, result.getKnowledgeLevelScore());
        assertEquals(75, result.getGrowthAdaptationScore());
        assertEquals(82, result.getTotalScore()); // (85+90+80+75)/4 = 82
    }

    @Test
    void testOOC_lowScore_fails() {
        String jsonResponse = """
            {
              "language_style_score": 40,
              "behavior_motivation_score": 50,
              "knowledge_level_score": 60,
              "growth_adaptation_score": 45,
              "details": "角色语言风格严重偏离设定"
            }
            """;
        when(llmService.generateWithUsage(any(), anyString(), anyString()))
                .thenReturn(jsonResponse);

        OOCResult result = validationService.validateOOC(request);

        assertFalse(result.isPassed());
        assertEquals(48, result.getTotalScore()); // (40+50+60+45)/4 = 48
    }

    // ====== Clue Tests ======

    @Test
    void testClue_missingFails() {
        String jsonResponse = """
            {
              "buried_count": 2,
              "recycled_count": 1,
              "has_missing": true,
              "has_unnatural": false,
              "missing_details": "第3章的神秘符文伏笔未在本章回收",
              "unnatural_details": ""
            }
            """;
        when(llmService.generateWithUsage(any(), anyString(), anyString()))
                .thenReturn(jsonResponse);

        ClueResult result = validationService.validateClue(request);

        assertFalse(result.isPassed());
        assertEquals(2, result.getBuriedCount());
        assertEquals(1, result.getRecycledCount());
        assertTrue(result.getMissingClues().contains("符文"));
    }

    // ====== Rhythm Tests ======

    @Test
    void testRhythm_appropriate_passes() {
        String jsonResponse = """
            {
              "expected_rhythm": "quest",
              "actual_rhythm": "quest",
              "emotion_curve": "递进上升，结尾达到高潮",
              "is_appropriate": true,
              "details": "节奏与预期一致"
            }
            """;
        when(llmService.generateWithUsage(any(), anyString(), anyString()))
                .thenReturn(jsonResponse);

        RhythmResult result = validationService.validateRhythm(request);

        assertTrue(result.isPassed());
        assertEquals("quest", result.getActualRhythm());
        assertTrue(result.getEmotionCurve().contains("递进"));
    }

    @Test
    void testRhythm_notAppropriate_fails() {
        String jsonResponse = """
            {
              "expected_rhythm": "quest",
              "actual_rhythm": "fire",
              "emotion_curve": "平淡无起伏",
              "is_appropriate": false,
              "details": "节奏与大纲预期不符"
            }
            """;
        when(llmService.generateWithUsage(any(), anyString(), anyString()))
                .thenReturn(jsonResponse);

        RhythmResult result = validationService.validateRhythm(request);

        assertFalse(result.isPassed());
        assertEquals("fire", result.getActualRhythm());
    }

    // ====== Setting Tests ======

    @Test
    void testSetting_violation_fails() {
        String jsonResponse = """
            {
              "world_rule_valid": false,
              "world_clock_valid": true,
              "conflicts": "第2段角色使用了未设定的魔法能力",
              "details": "发现世界观违规"
            }
            """;
        when(llmService.generateWithUsage(any(), anyString(), anyString()))
                .thenReturn(jsonResponse);

        SettingResult result = validationService.validateSetting(request);

        assertFalse(result.isPassed());
        assertEquals("不符合", result.getWorldRuleStatus());
        assertEquals("一致", result.getWorldClockStatus());
        assertTrue(result.getConflicts().contains("魔法"));
    }

    // ====== Edge Cases ======

    @Test
    void testMarkdownWrappedJson_parsesCorrectly() {
        String jsonResponse = "```json\n" +
                "{\n" +
                "  \"causal_chain_valid\": true,\n" +
                "  \"consistency_valid\": true,\n" +
                "  \"conflict_description\": \"\",\n" +
                "  \"confidence\": 0.9,\n" +
                "  \"details\": \"逻辑正常\"\n" +
                "}\n" +
                "```";
        when(llmService.generateWithUsage(any(), anyString(), anyString()))
                .thenReturn(jsonResponse);

        LogicResult result = validationService.validateLogic(request);

        assertTrue(result.isPassed());
        assertEquals("通顺", result.getCausalChainStatus());
    }

    @Test
    void testMalformedJson_fallbackFails() {
        String malformedResponse = "这不是一个有效的JSON响应，只是一段普通文本。";
        when(llmService.generateWithUsage(any(), anyString(), anyString()))
                .thenReturn(malformedResponse);

        LogicResult result = validationService.validateLogic(request);

        assertFalse(result.isPassed());
        assertEquals("解析失败", result.getConflictDescription());
    }

    @Test
    void testSetting_allValid_passes() {
        String jsonResponse = """
            {
              "world_rule_valid": true,
              "world_clock_valid": true,
              "conflicts": "",
              "details": "世界观和时间线均符合设定"
            }
            """;
        when(llmService.generateWithUsage(any(), anyString(), anyString()))
                .thenReturn(jsonResponse);

        SettingResult result = validationService.validateSetting(request);

        assertTrue(result.isPassed());
        assertEquals("符合", result.getWorldRuleStatus());
        assertEquals("一致", result.getWorldClockStatus());
        assertEquals("", result.getConflicts());
    }
}
