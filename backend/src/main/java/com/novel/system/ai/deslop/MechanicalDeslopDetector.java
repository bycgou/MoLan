package com.novel.system.ai.deslop;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 反 AI 味的**机械层**扫描（零 LLM）。
 *
 * <p>设计约束：
 * <ul>
 *   <li>纯函数：给正文与规则表，返回 findings —— 不读文件、不发调用，
 *       于是可以在对照集上算误报率（误报率是规则表的验收标准）。</li>
 *   <li>ruleId 用 {@code mech:deslop:<id>} 前缀：统计「机械判据」时只认代码产出。</li>
 *   <li>对话豁免按**等长遮罩**处理：剔除对话后字符位置不变，
 *       「章末 N 段」的范围与密度的字数分母不会因剔除而漂移。</li>
 * </ul>
 *
 * <p>不依赖 Spring：这是唯一能在离线对照集上复算的部分。
 */
public final class MechanicalDeslopDetector {

    /** 每条规则每章最多报几处 —— 命中是「模式」不是「逐处修单」。 */
    public static final int MAX_HITS_PER_RULE = 5;

    /** 引文截断长度。 */
    public static final int MAX_QUOTE_CHARS = 60;

    /** 规则 id 前缀，区分代码产出与模型自评。 */
    public static final String RULE_ID_PREFIX = "mech:deslop:";

    /** 句长复读检测最多报几处。 */
    private static final int MAX_LENGTH_RUN_FINDINGS = 2;

    /** 句长复读判定阈值：与该连句首句的长度差 < 15%。 */
    private static final double LENGTH_RUN_DRIFT = 0.15;

    /** 中文常用的成对引号，等长遮罩，不改变文本长度。 */
    private static final Pattern[] QUOTED_PATTERNS = new Pattern[]{
            Pattern.compile("「[^」]*」"),
            Pattern.compile("“[^”]*”"),
            Pattern.compile("‘[^’]*’"),
            Pattern.compile("『[^』]*』"),
    };

    /** 句子切分：按句末标点断句，保留标点。 */
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[。！？…])");

    private MechanicalDeslopDetector() {
    }

    /**
     * 把对话**等长遮罩**成空格：对话内容不参与机械判据（白名单 dialogue）。
     *
     * <p>与「剔除对话后按剩余文本统计」的做法刻意不同：这里必须等长 ——
     * 「章末 N 段」的范围与密度的字数分母才不会因遮罩而漂移。
     */
    public static String maskDialogue(String text) {
        String out = text;
        for (Pattern pattern : QUOTED_PATTERNS) {
            out = pattern.matcher(out).replaceAll(m -> " ".repeat(m.group().length()));
        }
        return out;
    }

    /**
     * 扫描一章正文，返回的全部 finding 都是代码产出（{@code mech:deslop:} 前缀）。
     *
     * @param tailParagraphs 「章末预告体」看最后几段
     */
    public static List<DeslopFinding> scan(String body, DeslopRuleset ruleset, int tailParagraphs) {
        List<DeslopFinding> findings = new ArrayList<>();
        if (body == null || body.isEmpty() || ruleset == null || ruleset.getRules() == null) {
            return findings;
        }
        int tail = tailParagraphs > 0 ? tailParagraphs : 3;
        boolean exemptDialogue = ruleset.whitelistsDialogue();
        String scanned = exemptDialogue ? maskDialogue(body) : body;

        String[] paragraphs = scanned.split("\n+");
        int from = Math.max(0, paragraphs.length - tail);
        String tailText = String.join("\n", List.of(paragraphs).subList(from, paragraphs.length));

        for (DeslopRule rule : ruleset.getRules()) {
            if (rule.getDetector() != null) {
                if ("sentence_length_run".equals(rule.getDetector())) {
                    findings.addAll(scanSentenceLengthRun(scanned, rule));
                }
                continue;
            }

            DeslopDensity density = rule.getDensity();
            if (density != null) {
                scanDensity(scanned, rule, density, findings);
                continue;
            }

            if (rule.getPattern() != null) {
                scanPattern(rule, rule.getScope() == DeslopScope.CHAPTER_TAIL ? tailText : scanned, findings);
            }
        }
        return findings;
    }

    private static void scanPattern(DeslopRule rule, String target, List<DeslopFinding> findings) {
        Matcher matcher = Pattern.compile(rule.getPattern()).matcher(target);
        int hits = 0;
        while (matcher.find() && hits < MAX_HITS_PER_RULE) {
            String quote = matcher.group();
            if (quote == null || quote.trim().isEmpty()) {
                continue;
            }
            findings.add(findingOf(rule, quote, rule.getMessage()));
            hits++;
        }
    }

    private static void scanDensity(String scanned, DeslopRule rule, DeslopDensity density,
                                    List<DeslopFinding> findings) {
        Matcher matcher = Pattern.compile(density.getPattern()).matcher(scanned);
        int matches = 0;
        String first = "";
        while (matcher.find()) {
            if (matches == 0) {
                first = matcher.group();
            }
            matches++;
        }
        if (matches == 0) {
            return;
        }
        double allowance = density.allowanceFor(scanned.length());
        if (matches > allowance) {
            String message = rule.getMessage() + "：全文 " + matches + " 处，基线允许约 "
                    + String.format("%.1f", allowance) + " 处（每 " + density.getPer() + " 字 ≤ "
                    + density.getLimit() + "）";
            findings.add(findingOf(rule, first, message));
        }
    }

    /**
     * 句长复读检测：连续 ≥3 句、每句与该连句首句的长度差 &lt; 15%。
     * 节奏平是 AI 叙述的结构特征，且完全机械可判。
     */
    private static List<DeslopFinding> scanSentenceLengthRun(String scanned, DeslopRule rule) {
        List<DeslopFinding> findings = new ArrayList<>();
        List<String> sentences = new ArrayList<>();
        for (String part : SENTENCE_SPLIT.split(scanned, -1)) {
            String trimmed = part.trim();
            if (trimmed.length() >= 6) {
                sentences.add(trimmed);
            }
        }
        if (sentences.size() < 3) {
            return findings;
        }

        int anchorLen = sentences.get(0).length();
        int runStart = 0;
        for (int i = 1; i <= sentences.size(); i++) {
            if (i < sentences.size()) {
                int len = sentences.get(i).length();
                double drift = Math.abs(len - anchorLen) / (double) Math.max(1, anchorLen);
                if (drift < LENGTH_RUN_DRIFT) {
                    continue;
                }
            }
            int runLen = i - runStart;
            if (runLen >= 3 && findings.size() < MAX_LENGTH_RUN_FINDINGS) {
                String run = String.join("", sentences.subList(runStart, i));
                String message = rule.getMessage() + "（连续 " + runLen + " 句，每句约 " + anchorLen + " 字）";
                findings.add(findingOf(rule, run, message));
            }
            if (i < sentences.size()) {
                runStart = i;
                anchorLen = sentences.get(i).length();
            }
        }
        return findings;
    }

    private static DeslopFinding findingOf(DeslopRule rule, String quote, String message) {
        return DeslopFinding.builder()
                .ruleId(RULE_ID_PREFIX + rule.getId())
                .severity(rule.getSeverity())
                .review(rule.getReview())
                .quote(clipQuote(quote))
                .message(message)
                .suggestion(rule.getSuggestion())
                .build();
    }

    private static String clipQuote(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.length() <= MAX_QUOTE_CHARS) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_QUOTE_CHARS) + "…";
    }
}
