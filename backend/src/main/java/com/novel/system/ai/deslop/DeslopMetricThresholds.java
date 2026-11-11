package com.novel.system.ai.deslop;

import com.novel.system.dto.style.StyleMetrics;

import java.util.ArrayList;
import java.util.List;

/**
 * 反 AI 味的**统计指标层**（advisory，永不参与路由）。
 *
 * <p>与机械层不同，这一层判的是「整章的分布」，阈值是固定的常量而不是从
 * {@code style_card.stats_json} 里读 —— 后者会让同一段文本在不同卡片下打出不同分，
 * 不可复现，也就没法当验收标准。
 *
 * <p>阈值来源：{@code StyleTextAnalyzer} 的 34 个指标里只挑了 6 个高置信项，
 * 先只接这 6 个，其余指标宁可不接也不要误伤。
 */
public final class DeslopMetricThresholds {

    /** 统计层 finding 的 ruleId 前缀（区别于机械层的 {@code mech:deslop:}）。 */
    public static final String RULE_ID_PREFIX = "stat:deslop:";

    /** 句长多样性下限：低于此值说明句长高度同质，是 AI 叙述的结构特征。 */
    public static final double SENTENCE_DIVERSITY_MIN = 0.30;

    /** 连续对白段数上限：超过此值说明通篇是对话，缺少叙述节奏。 */
    public static final int MAX_CONSECUTIVE_DIALOGUE_LIMIT = 8;

    /** 对白占比下限：低于此值说明几乎没有对白，全是旁白。 */
    public static final double DIALOGUE_RATIO_MIN = 0.05;

    /** 过渡词套话占比上限。 */
    public static final double TRANSITION_CLICHE_RATIO_MAX = 0.15;

    /** 空场景占比上限：超过此值说明场景描写空洞。 */
    public static final double EMPTY_SCENE_RATIO_MAX = 0.5;

    private DeslopMetricThresholds() {
    }

    /**
     * 对一份正文跑统计指标，产出 advisory findings。
     *
     * <p>这些 findings 只计数与出建议，<b>不参与</b>图路由（不挡稿）。
     */
    public static List<DeslopFinding> evaluate(StyleMetrics metrics) {
        List<DeslopFinding> findings = new ArrayList<>();
        if (metrics == null) {
            return findings;
        }
        if (metrics.getSentenceDiversityIndex() < SENTENCE_DIVERSITY_MIN) {
            findings.add(finding("sentence-diversity",
                    "句长多样性偏低（" + round(metrics.getSentenceDiversityIndex())
                            + " < " + SENTENCE_DIVERSITY_MIN + "）",
                    "打散句长：把长句拆成短句，或在连续同长句之间插一句短句"));
        }
        if (metrics.getMaxConsecutiveDialogue() > MAX_CONSECUTIVE_DIALOGUE_LIMIT) {
            findings.add(finding("max-consecutive-dialogue",
                    "连续对白段数过多（" + metrics.getMaxConsecutiveDialogue()
                            + " > " + MAX_CONSECUTIVE_DIALOGUE_LIMIT + "）",
                    "在连续对白之间插入动作或环境描写，切断对白流水"));
        }
        if (metrics.getDialogueRatio() < DIALOGUE_RATIO_MIN) {
            findings.add(finding("dialogue-ratio",
                    "对白占比过低（" + round(metrics.getDialogueRatio())
                            + " < " + DIALOGUE_RATIO_MIN + "）",
                    "让人物开口说话：关键冲突用对话推进，而不是全靠旁白"));
        }
        if (metrics.getTransitionClicheRatio() > TRANSITION_CLICHE_RATIO_MAX) {
            findings.add(finding("transition-cliche-ratio",
                    "过渡词套话占比过高（" + round(metrics.getTransitionClicheRatio())
                            + " > " + TRANSITION_CLICHE_RATIO_MAX + "）",
                    "删掉「与此同时/然而/不禁」这类过渡，直接写下一个动作"));
        }
        if (metrics.getEmptySceneRatio() > EMPTY_SCENE_RATIO_MAX) {
            findings.add(finding("empty-scene-ratio",
                    "空场景占比过高（" + round(metrics.getEmptySceneRatio())
                            + " > " + EMPTY_SCENE_RATIO_MAX + "）",
                    "给场景补一个可被听见的细节，而不是只写氛围词"));
        }
        if (metrics.getActionEndingRatio() == 0 && metrics.getSuspenseEndingRatio() == 0) {
            findings.add(finding("flat-ending",
                    "章末既不是动作收束也不是悬念收束（action=0, suspense=0）",
                    "用角色当下的一个动作或一个未解决的问题收尾"));
        }
        return findings;
    }

    private static DeslopFinding finding(String id, String message, String suggestion) {
        return DeslopFinding.builder()
                .ruleId(RULE_ID_PREFIX + id)
                .severity(DeslopSeverity.ADVISORY)
                .review(DeslopReview.SEMANTIC)
                .quote("")
                .message(message)
                .suggestion(suggestion)
                .build();
    }

    private static String round(double value) {
        return String.format("%.3f", value);
    }
}
