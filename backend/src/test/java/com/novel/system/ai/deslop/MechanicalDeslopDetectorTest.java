package com.novel.system.ai.deslop;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 机械层检测语义 —— 判据是「模式」不是「逐处修单」。
 */
class MechanicalDeslopDetectorTest {

    private static DeslopRuleset ruleset(DeslopRule... rules) {
        return new DeslopRuleset(2, List.of(rules), List.of("dialogue"));
    }

    private static DeslopRule patternRule(String id, String pattern, DeslopScope scope) {
        return DeslopRule.builder()
                .id(id)
                .severity(DeslopSeverity.MAJOR)
                .review(DeslopReview.MECHANICAL)
                .pattern(pattern)
                .scope(scope)
                .message("消息")
                .suggestion("改法")
                .languages(List.of("zh"))
                .evidence("证据")
                .build();
    }

    @Test
    void dialogueMaskingKeepsLengthUnchanged() {
        String text = "他说：「眼中闪过一丝冷意。」然后走了。";
        String masked = MechanicalDeslopDetector.maskDialogue(text);
        assertEquals(text.length(), masked.length());
        assertFalse(masked.contains("眼中闪过"));
    }

    @Test
    void densityReportsOnlyWhenAboveAllowance() {
        DeslopRule rule = DeslopRule.builder()
                .id("filler-density")
                .severity(DeslopSeverity.ADVISORY)
                .review(DeslopReview.MECHANICAL)
                .density(DeslopDensity.builder().pattern("缓缓").per(1000).limit(3).build())
                .scope(DeslopScope.FULL)
                .message("密度超基线")
                .suggestion("改法")
                .build();
        DeslopRuleset rs = ruleset(rule);

        // 3000 字，per=1000，limit=3 → allowance = 9.0
        String nine = "缓缓".repeat(9) + "甲".repeat(2982);
        assertEquals(3000, nine.length());
        assertTrue(MechanicalDeslopDetector.scan(nine, rs, 3).isEmpty(),
                "9 处命中不应超过基线 9.0");

        String ten = "缓缓".repeat(10) + "甲".repeat(2980);
        assertEquals(3000, ten.length());
        List<DeslopFinding> findings = MechanicalDeslopDetector.scan(ten, rs, 3);
        assertEquals(1, findings.size());
        assertTrue(findings.get(0).getMessage().contains("10 处"), findings.get(0).getMessage());
    }

    @Test
    void chapterTailRuleIgnoresHitsInTheMiddle() {
        DeslopRule rule = patternRule("tail-rule", "命中", DeslopScope.CHAPTER_TAIL);
        DeslopRuleset rs = ruleset(rule);

        String middle = "第一段\n\n第二段命中\n\n第三段\n\n第四段\n\n第五段";
        assertTrue(MechanicalDeslopDetector.scan(middle, rs, 3).isEmpty(),
                "章末 3 段之外的命中不应上报");

        String atEnd = "第一段\n\n第二段\n\n第三段\n\n第四段\n\n第五段命中";
        assertEquals(1, MechanicalDeslopDetector.scan(atEnd, rs, 3).size());
    }

    @Test
    void reportsAtMostFiveHitsPerRule() {
        DeslopRule rule = patternRule("many-hits", "命中", DeslopScope.FULL);
        DeslopRuleset rs = ruleset(rule);

        String text = "命中".repeat(10);
        List<DeslopFinding> findings = MechanicalDeslopDetector.scan(text, rs, 3);
        assertEquals(MechanicalDeslopDetector.MAX_HITS_PER_RULE, findings.size());
    }

    @Test
    void hitsInsideDialogueAreExempted() {
        DeslopRule rule = patternRule("cliche-face", "眼中闪过", DeslopScope.FULL);
        DeslopRuleset rs = ruleset(rule);

        assertTrue(MechanicalDeslopDetector.scan("他喊道：「眼中闪过。」", rs, 3).isEmpty());
        assertEquals(1, MechanicalDeslopDetector.scan("他眼中闪过一丝冷意。", rs, 3).size());
    }

    @Test
    void sentenceLengthRunFiresOnThreeSimilarLengthSentences() {
        DeslopRule rule = DeslopRule.builder()
                .id("sentence-length-run")
                .severity(DeslopSeverity.ADVISORY)
                .review(DeslopReview.MECHANICAL)
                .detector("sentence_length_run")
                .message("句长复读")
                .suggestion("改法")
                .build();
        DeslopRuleset rs = ruleset(rule);

        String flat = "他走了很久没有回头。他走了很久没有回头。他走了很久没有回头。";
        List<DeslopFinding> findings = MechanicalDeslopDetector.scan(flat, rs, 3);
        assertEquals(1, findings.size());
        assertTrue(findings.get(0).getMessage().contains("连续 3 句"), findings.get(0).getMessage());
    }

    @Test
    void sentenceLengthRunIgnoresVariedLengths() {
        DeslopRule rule = DeslopRule.builder()
                .id("sentence-length-run")
                .severity(DeslopSeverity.ADVISORY)
                .review(DeslopReview.MECHANICAL)
                .detector("sentence_length_run")
                .message("句长复读")
                .suggestion("改法")
                .build();
        DeslopRuleset rs = ruleset(rule);

        String varied = "他走了很久没有回头。他停下来数了数台阶又继续往前走了很久很久很久很久。他走了。";
        assertTrue(MechanicalDeslopDetector.scan(varied, rs, 3).isEmpty());
    }

    @Test
    void findingIdsUseMechanicalPrefix() {
        DeslopRule rule = patternRule("cliche-face", "眼中闪过", DeslopScope.FULL);
        DeslopRuleset rs = ruleset(rule);

        DeslopFinding finding = MechanicalDeslopDetector.scan("他眼中闪过冷意。", rs, 3).get(0);
        assertEquals("mech:deslop:cliche-face", finding.getRuleId());
        assertEquals(DeslopSeverity.MAJOR, finding.getSeverity());
        assertEquals(DeslopReview.MECHANICAL, finding.getReview());
    }

    @Test
    void quoteIsTruncatedToSixtyChars() {
        DeslopRule rule = patternRule("long-quote", "甲{80}", DeslopScope.FULL);
        DeslopRuleset rs = ruleset(rule);

        DeslopFinding finding = MechanicalDeslopDetector.scan("甲".repeat(80), rs, 3).get(0);
        assertEquals(MechanicalDeslopDetector.MAX_QUOTE_CHARS + 1, finding.getQuote().length());
    }

    @Test
    void emptyBodyProducesNoFindings() {
        DeslopRule rule = patternRule("cliche-face", "眼中闪过", DeslopScope.FULL);
        assertTrue(MechanicalDeslopDetector.scan("", ruleset(rule), 3).isEmpty());
        assertTrue(MechanicalDeslopDetector.scan(null, ruleset(rule), 3).isEmpty());
    }
}
