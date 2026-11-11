package com.novel.system.ai.deslop;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规则表加载期校验 —— 报错而不是带病运行。
 */
class DeslopRulesetLoaderTest {

    private static String yaml(String rules) {
        return "version: 2\nwhitelist_scopes:\n  - dialogue\nrules:\n" + rules;
    }

    private static String rule(String id, String severity, String review, String extra) {
        return "  - id: " + id + "\n"
                + "    severity: " + severity + "\n"
                + "    review: " + review + "\n"
                + (extra == null ? "" : extra)
                + "    message: '消息'\n"
                + "    suggestion: '改法'\n";
    }

    @Test
    void acceptsCompliantRule() {
        DeslopRuleset ruleset = DeslopRulesetLoader.parse(yaml(
                rule("cliche-face", "major", "mechanical", "    pattern: '(眼中闪过)'\n")));
        assertEquals(1, ruleset.getRules().size());
        assertEquals("cliche-face", ruleset.getRules().get(0).getId());
        assertEquals(DeslopSeverity.MAJOR, ruleset.getRules().get(0).getSeverity());
        assertEquals(DeslopReview.MECHANICAL, ruleset.getRules().get(0).getReview());
    }

    @Test
    void rejectsBlockingWithSemanticReview() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                DeslopRulesetLoader.parse(yaml(
                        rule("bad-rule", "blocking", "semantic", "    pattern: '(x)'\n"))));
        assertTrue(e.getMessage().contains("铁律一"), e.getMessage());
    }

    @Test
    void rejectsSemanticWithNonAdvisorySeverity() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                DeslopRulesetLoader.parse(yaml(
                        rule("bad-rule", "major", "semantic", "    pattern: '(x)'\n"))));
        assertTrue(e.getMessage().contains("铁律二"), e.getMessage());
    }

    @Test
    void acceptsSemanticWithAdvisorySeverity() {
        DeslopRuleset ruleset = DeslopRulesetLoader.parse(yaml(
                rule("soft-rule", "advisory", "semantic", "    pattern: '(x)'\n")));
        assertEquals(DeslopReview.SEMANTIC, ruleset.getRules().get(0).getReview());
    }

    @Test
    void rejectsTwoJudgementsAtOnce() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                DeslopRulesetLoader.parse(yaml(
                        rule("bad-rule", "major", "mechanical",
                                "    pattern: '(x)'\n    density:\n      pattern: '(y)'\n      per: 1000\n      limit: 3\n"))));
        assertTrue(e.getMessage().contains("必须恰好给一个"), e.getMessage());
    }

    @Test
    void rejectsMissingJudgement() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                DeslopRulesetLoader.parse(yaml(rule("bad-rule", "major", "mechanical", null))));
        assertTrue(e.getMessage().contains("必须恰好给一个"), e.getMessage());
    }

    @Test
    void acceptsDetectorOnlyRule() {
        DeslopRuleset ruleset = DeslopRulesetLoader.parse(yaml(
                rule("sentence-length-run", "advisory", "mechanical", "    detector: sentence_length_run\n")));
        assertEquals("sentence_length_run", ruleset.getRules().get(0).getDetector());
    }

    @Test
    void rejectsUnknownDetector() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                DeslopRulesetLoader.parse(yaml(
                        rule("bad-rule", "major", "mechanical", "    detector: mystery_detector\n"))));
        assertTrue(e.getMessage().contains("未实现的检测器"), e.getMessage());
    }

    @Test
    void rejectsNonKebabCaseId() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                DeslopRulesetLoader.parse(yaml(
                        rule("Bad_Rule", "major", "mechanical", "    pattern: '(x)'\n"))));
        assertTrue(e.getMessage().contains("kebab-case"), e.getMessage());
    }

    @Test
    void rejectsDuplicateId() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                DeslopRulesetLoader.parse(yaml(
                        rule("same-id", "major", "mechanical", "    pattern: '(x)'\n")
                                + rule("same-id", "major", "mechanical", "    pattern: '(y)'\n"))));
        assertTrue(e.getMessage().contains("重复规则 id"), e.getMessage());
    }

    @Test
    void rejectsAutofixOnPatternRule() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                DeslopRulesetLoader.parse(yaml(
                        rule("bad-rule", "major", "mechanical",
                                "    pattern: '(x)'\n    autofix: delete\n"))));
        assertTrue(e.getMessage().contains("铁律三"), e.getMessage());
    }

    @Test
    void rejectsAutofixOnBlockingRule() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                DeslopRulesetLoader.parse(yaml(
                        rule("bad-rule", "blocking", "mechanical",
                                "    density:\n      pattern: '(y)'\n      per: 1000\n      limit: 3\n    autofix: delete\n"))));
        assertTrue(e.getMessage().contains("铁律三"), e.getMessage());
    }

    @Test
    void rejectsAutofixOnSemanticRule() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                DeslopRulesetLoader.parse(yaml(
                        rule("bad-rule", "advisory", "semantic",
                                "    density:\n      pattern: '(y)'\n      per: 1000\n      limit: 3\n    autofix: delete\n"))));
        assertTrue(e.getMessage().contains("铁律三"), e.getMessage());
    }

    @Test
    void acceptsAutofixOnDensityRule() {
        DeslopRuleset ruleset = DeslopRulesetLoader.parse(yaml(
                rule("filler-density", "advisory", "mechanical",
                        "    density:\n      pattern: '(缓缓)'\n      per: 1000\n      limit: 3\n    autofix: delete\n")));
        assertEquals("delete", ruleset.getRules().get(0).getAutofix());
    }

    @Test
    void rejectsMissingSuggestion() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                DeslopRulesetLoader.parse("version: 2\nrules:\n"
                        + "  - id: bad-rule\n    severity: major\n    review: mechanical\n"
                        + "    pattern: '(x)'\n    message: '消息'\n"));
        assertTrue(e.getMessage().contains("suggestion"), e.getMessage());
    }

    @Test
    void rejectsEmptyRuleset() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                DeslopRulesetLoader.parse("version: 2\nrules: []\n"));
        assertTrue(e.getMessage().contains("至少要有 1 条"), e.getMessage());
    }

    @Test
    void rejectsUncompilablePattern() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                DeslopRulesetLoader.parse(yaml(
                        rule("bad-rule", "major", "mechanical", "    pattern: '(unclosed'\n"))));
        assertTrue(e.getMessage().contains("正则无法编译"), e.getMessage());
        assertTrue(e.getMessage().contains("bad-rule"), e.getMessage());
    }

    @Test
    void rejectsUncompilableDensityPattern() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                DeslopRulesetLoader.parse(yaml(
                        rule("bad-rule", "major", "mechanical",
                                "    density:\n      pattern: '[unclosed'\n      per: 1000\n      limit: 3\n"))));
        assertTrue(e.getMessage().contains("正则无法编译"), e.getMessage());
    }

    @Test
    void rejectsUnsupportedWhitelistScope() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () ->
                DeslopRulesetLoader.parse("version: 2\nwhitelist_scopes:\n  - inner_thought\nrules:\n"
                        + rule("a-rule", "major", "mechanical", "    pattern: '(x)'\n")));
        assertTrue(e.getMessage().contains("白名单作用域不支持"), e.getMessage());
    }
}
