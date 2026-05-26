package com.novel.system.ai.graph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GradedAuditCalculatorTest {

    private ValidationConfig config;
    private GradedAuditCalculator calculator;

    @BeforeEach
    void setUp() {
        config = new ValidationConfig();
        config.setUseGradedAudit(true);
        config.setPassThreshold(60.0);
        config.setDimensions(Map.of(
                "ooc", new ValidationConfig.DimensionConfig(0.20, ValidationConfig.Severity.FATAL),
                "logic", new ValidationConfig.DimensionConfig(0.20, ValidationConfig.Severity.FATAL),
                "setting", new ValidationConfig.DimensionConfig(0.15, ValidationConfig.Severity.FATAL),
                "clue", new ValidationConfig.DimensionConfig(0.15, ValidationConfig.Severity.WARNING),
                "rhythm", new ValidationConfig.DimensionConfig(0.10, ValidationConfig.Severity.WARNING),
                "quality", new ValidationConfig.DimensionConfig(0.10, ValidationConfig.Severity.WARNING),
                "clueLanding", new ValidationConfig.DimensionConfig(0.10, ValidationConfig.Severity.WARNING)
        ));
        calculator = new GradedAuditCalculator(config);
    }

    @Test
    void allPass_highScore() {
        NovelAgentState.AuditResult audit = NovelAgentState.AuditResult.builder()
                .oocPassed(true).oocScore(90)
                .logicPassed(true)
                .settingPassed(true)
                .cluePassed(true)
                .rhythmPassed(true)
                .qualityPassed(true).qualityScore(85.0)
                .clueLandingPassed(true)
                .build();

        GradedAuditCalculator.GradedResult result = calculator.calculate(audit);

        assertTrue(result.isAllPassed());
        assertTrue(result.getFatalFailures().isEmpty());
        assertTrue(result.getWarnings().isEmpty());
        // 0.20*90 + 0.20*100 + 0.15*100 + 0.15*100 + 0.10*100 + 0.10*85 + 0.10*100 = 95.5
        assertTrue(result.getWeightedScore() > 90);
    }

    @Test
    void fatalFailure_blocksPass() {
        NovelAgentState.AuditResult audit = NovelAgentState.AuditResult.builder()
                .oocPassed(false).oocScore(30)  // FATAL fail
                .logicPassed(true)
                .settingPassed(true)
                .cluePassed(true)
                .rhythmPassed(true)
                .qualityPassed(true).qualityScore(90.0)
                .clueLandingPassed(true)
                .build();

        GradedAuditCalculator.GradedResult result = calculator.calculate(audit);

        assertFalse(result.isAllPassed());
        assertTrue(result.getFatalFailures().contains("ooc"));
    }

    @Test
    void warningOnly_doesNotBlockIfScoreAboveThreshold() {
        NovelAgentState.AuditResult audit = NovelAgentState.AuditResult.builder()
                .oocPassed(true).oocScore(80)
                .logicPassed(true)
                .settingPassed(true)
                .cluePassed(false)  // WARNING fail
                .rhythmPassed(true)
                .qualityPassed(true).qualityScore(80.0)
                .clueLandingPassed(true)
                .build();

        GradedAuditCalculator.GradedResult result = calculator.calculate(audit);

        // FATAL all passed, weighted = 0.20*80 + 0.20*100 + 0.15*100 + 0.15*0 + 0.10*100 + 0.10*80 + 0.10*100 = 79
        assertTrue(result.isAllPassed());
        assertTrue(result.getFatalFailures().isEmpty());
        assertTrue(result.getWarnings().contains("clue"));
    }

    @Test
    void multipleWarnings_canDropBelowThreshold() {
        NovelAgentState.AuditResult audit = NovelAgentState.AuditResult.builder()
                .oocPassed(true).oocScore(50)
                .logicPassed(true)
                .settingPassed(true)
                .cluePassed(false)   // WARNING fail
                .rhythmPassed(false) // WARNING fail
                .qualityPassed(false).qualityScore(40.0)  // WARNING fail
                .clueLandingPassed(true)
                .build();

        GradedAuditCalculator.GradedResult result = calculator.calculate(audit);

        // weighted = 0.20*50 + 0.20*100 + 0.15*100 + 0.15*0 + 0.10*0 + 0.10*40 + 0.10*100 = 59
        assertFalse(result.isAllPassed()); // 59 < 60
        assertEquals(3, result.getWarnings().size());
    }
}
