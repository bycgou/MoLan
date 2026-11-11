package com.novel.system.ai.deslop;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 随包规则表的体检 —— 规则表是静态资源，改坏了必须在这里被拦住。
 */
class DeslopRulesetLoaderPackagedTest {

    private static DeslopRuleset ruleset;

    @BeforeAll
    static void loadPackagedRuleset() {
        ruleset = new DeslopRulesetProvider().get();
    }

    @Test
    void loadsFromClasspath() {
        assertNotNull(ruleset);
        assertEquals(2, ruleset.getVersion());
        assertTrue(ruleset.getRules().size() >= 45,
                "规则数应不少于 45，实际 " + ruleset.getRules().size());
    }

    @Test
    void ruleIdsAreUnique() {
        Set<String> ids = ruleset.getRules().stream()
                .map(DeslopRule::getId)
                .collect(Collectors.toSet());
        assertEquals(ruleset.getRules().size(), ids.size());
    }

    @Test
    void whitelistsDialogue() {
        assertTrue(ruleset.getWhitelistScopes().contains("dialogue"));
        assertTrue(ruleset.whitelistsDialogue());
    }

    @Test
    void everyRuleHasMessageSuggestionAndEvidence() {
        for (DeslopRule rule : ruleset.getRules()) {
            assertNotNull(rule.getMessage(), rule.getId() + " 缺 message");
            assertFalse(rule.getMessage().isBlank(), rule.getId() + " message 为空");
            assertNotNull(rule.getSuggestion(), rule.getId() + " 缺 suggestion");
            assertFalse(rule.getSuggestion().isBlank(), rule.getId() + " suggestion 为空");
            assertNotNull(rule.getEvidence(), rule.getId() + " 缺 evidence");
            assertFalse(rule.getEvidence().isBlank(), rule.getId() + " evidence 为空");
        }
    }

    @Test
    void blockingRulesAreFewAndMechanicalOnly() {
        List<DeslopRule> blocking = ruleset.getRules().stream()
                .filter(r -> r.getSeverity() == DeslopSeverity.BLOCKING)
                .toList();
        assertTrue(blocking.size() <= 3, "blocking 规则不应超过 3 条，实际 " + blocking.size());
        for (DeslopRule rule : blocking) {
            assertEquals(DeslopReview.MECHANICAL, rule.getReview(), rule.getId() + " 违反铁律一");
        }
    }

    @Test
    void semanticRulesAreAdvisoryOnly() {
        for (DeslopRule rule : ruleset.getRules()) {
            if (rule.getReview() == DeslopReview.SEMANTIC) {
                assertEquals(DeslopSeverity.ADVISORY, rule.getSeverity(),
                        rule.getId() + " 违反铁律二");
            }
        }
    }

    @Test
    void autofixOnlyOnTheTwoDensityRules() {
        Set<String> autofixIds = ruleset.getRules().stream()
                .filter(r -> r.getAutofix() != null)
                .map(DeslopRule::getId)
                .collect(Collectors.toSet());
        assertEquals(Set.of("emphasis-filler-density", "filler-adverb-density"), autofixIds);
    }

    @Test
    void everyRuleHasExactlyOneJudgement() {
        for (DeslopRule rule : ruleset.getRules()) {
            int count = (rule.getPattern() != null ? 1 : 0)
                    + (rule.getDensity() != null ? 1 : 0)
                    + (rule.getDetector() != null ? 1 : 0);
            assertEquals(1, count, rule.getId() + " 判据数量不是 1");
        }
    }

    @Test
    void everyPatternCompiles() {
        for (DeslopRule rule : ruleset.getRules()) {
            if (rule.getPattern() != null) {
                DeslopRulesetLoader.assertPatternCompiles(rule.getPattern(), rule.getId());
            }
            if (rule.getDensity() != null) {
                DeslopRulesetLoader.assertPatternCompiles(rule.getDensity().getPattern(), rule.getId());
            }
        }
    }

    @Test
    void promptExampleRecitationRuleIsAdvisory() {
        DeslopRule rule = ruleset.getRules().stream()
                .filter(r -> "prompt-example-recitation".equals(r.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少提示词复读自监测规则"));
        assertEquals(DeslopSeverity.ADVISORY, rule.getSeverity());
        assertEquals(DeslopReview.MECHANICAL, rule.getReview());
    }

    @Test
    void resourceIsOnClasspath() {
        assertNotNull(getClass().getClassLoader().getResourceAsStream("deslop/deslop-rules.yaml"));
    }

    @Test
    void providerCachesTheSameInstance() {
        DeslopRulesetProvider provider = new DeslopRulesetProvider();
        assertTrue(provider.get() == provider.get());
    }

    @Test
    void packagedRulesetParsesFromRawStream() {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("deslop/deslop-rules.yaml")) {
            DeslopRuleset parsed = DeslopRulesetLoader.parse(in);
            assertEquals(ruleset.getRules().size(), parsed.getRules().size());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
