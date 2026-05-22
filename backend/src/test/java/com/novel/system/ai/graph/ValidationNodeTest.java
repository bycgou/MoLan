package com.novel.system.ai.graph;

import com.novel.system.ai.chain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidationNodeTest {

    @Mock private ValidationService validationService;
    private ValidationNode node;

    @BeforeEach
    void setUp() {
        node = new ValidationNode(validationService);
    }

    @Test
    void mapsAllFiveDimensions() throws Exception {
        OOCResult ooc = new OOCResult();
        ooc.setPassed(true);
        ooc.setTotalScore(85);
        ooc.setLanguageStyleScore(90);
        ooc.setBehaviorMotivationScore(80);
        ooc.setKnowledgeLevelScore(85);
        ooc.setGrowthAdaptationScore(85);

        LogicResult logic = new LogicResult();
        logic.setPassed(true);
        logic.setCausalChainStatus("通顺");
        logic.setConsistencyStatus("一致");
        logic.setConflictDescription("无");

        ClueResult clue = new ClueResult();
        clue.setPassed(true);
        clue.setBuriedCount(1);
        clue.setRecycledCount(1);
        clue.setMissingClues("无");
        clue.setUnnaturalRecycle("无");

        SettingResult setting = new SettingResult();
        setting.setPassed(true);
        setting.setWorldRuleStatus("符合");
        setting.setWorldClockStatus("一致");
        setting.setConflicts("无");

        RhythmResult rhythm = new RhythmResult();
        rhythm.setPassed(true);
        rhythm.setExpectedRhythm("quest");
        rhythm.setActualRhythm("quest");

        ValidationResult vr = new ValidationResult();
        vr.setPassed(true);
        vr.setSummary("所有校验通过");
        vr.setOocResult(ooc);
        vr.setLogicResult(logic);
        vr.setClueResult(clue);
        vr.setSettingResult(setting);
        vr.setRhythmResult(rhythm);

        when(validationService.validateAll(any())).thenReturn(vr);

        NovelAgentState input = NovelAgentState.builder()
                .novelId("n1").chapterNum(1)
                .draftContent(NovelAgentState.DraftContent.builder().content("内容").wordCount(100).build())
                .chapterPlan(NovelAgentState.ChapterPlan.builder().summary("概要").build())
                .build();

        NovelAgentState result = node.apply(input);

        NovelAgentState.AuditResult audit = result.getAuditResult();
        assertTrue(audit.isOocPassed());
        assertEquals(85, audit.getOocScore());
        assertTrue(audit.isLogicPassed());
        assertTrue(audit.isCluePassed());
        assertTrue(audit.isSettingPassed());
        assertTrue(audit.isRhythmPassed());
        assertTrue(audit.isAllPassed());
        assertNotNull(audit.getOocDetail());
        assertNotNull(audit.getLogicDetail());
        assertNotNull(audit.getClueDetail());
        assertNotNull(audit.getSettingDetail());
        assertNotNull(audit.getRhythmDetail());
    }
}
