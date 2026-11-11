package com.novel.system.ai.graph;

import com.novel.system.ai.deslop.DeslopFinding;
import com.novel.system.ai.deslop.DeslopMetricThresholds;
import com.novel.system.ai.deslop.DeslopRuleset;
import com.novel.system.ai.deslop.DeslopRulesetProvider;
import com.novel.system.ai.deslop.DeslopSeverity;
import com.novel.system.ai.deslop.MechanicalDeslopDetector;
import com.novel.system.dto.style.StyleMetrics;
import com.novel.system.service.style.StyleTextAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 反 AI 味门禁节点 — 在正文与自审之间做一次机械判据扫描。
 *
 * <p>两条硬约束：
 * <ul>
 *   <li><b>只有 mechanical 且 blocking 的规则能挡稿</b>；统计指标一律 advisory，
 *       只计数与出建议，不参与路由。</li>
 *   <li><b>重写预算独立</b>（{@code deslop.max-rewrites}），不占 writing 的重试配额，
 *       避免把自审的重写机会吃光。</li>
 * </ul>
 *
 * <p>本节点不做任何自动改稿：只产出 findings 与修订建议，落地由写作节点消费。
 */
@Slf4j
@Component
public class DeslopCheckNode implements NovelGraphNode {

    private final DeslopRulesetProvider rulesetProvider;
    private final StyleTextAnalyzer styleTextAnalyzer;
    private final boolean enforce;
    private final int maxRewrites;
    private final int tailParagraphs;

    public DeslopCheckNode(DeslopRulesetProvider rulesetProvider,
                           StyleTextAnalyzer styleTextAnalyzer,
                           @Value("${deslop.enforce:false}") boolean enforce,
                           @Value("${deslop.max-rewrites:1}") int maxRewrites,
                           @Value("${deslop.tail-paragraphs:3}") int tailParagraphs) {
        this.rulesetProvider = rulesetProvider;
        this.styleTextAnalyzer = styleTextAnalyzer;
        this.enforce = enforce;
        this.maxRewrites = maxRewrites;
        this.tailParagraphs = tailParagraphs;
    }

    @Override
    public String nodeName() { return "deslop_check"; }

    @Override
    public NovelAgentState apply(NovelAgentState state) throws Exception {
        NovelAgentState.DraftContent draft = state.getDraftContent();
        if (draft == null || draft.getContent() == null || draft.getContent().isBlank()) {
            log.info("[{}] 无正文，跳过扫描", nodeName());
            return state.toBuilder()
                    .deslopDecision(Decision.PASS.name())
                    .build();
        }

        String body = draft.getContent();
        DeslopRuleset ruleset = rulesetProvider.get();
        List<DeslopFinding> findings = new ArrayList<>(
                MechanicalDeslopDetector.scan(body, ruleset, tailParagraphs));
        findings.addAll(collectMetricFindings(body));

        long blockingCount = findings.stream()
                .filter(f -> f.getSeverity() == DeslopSeverity.BLOCKING)
                .count();
        log.info("[{}] 扫描完成, findings={}, blocking={}",
                nodeName(), findings.size(), blockingCount);

        if (blockingCount == 0) {
            return state.toBuilder()
                    .deslopFindings(findings)
                    .deslopDecision(Decision.PASS.name())
                    .build();
        }

        if (!enforce) {
            log.info("[{}] 命中 blocking={} 但 deslop.enforce=false，仅记录不挡稿", nodeName(), blockingCount);
            return state.toBuilder()
                    .deslopFindings(findings)
                    .deslopDecision(Decision.PASS.name())
                    .build();
        }

        int rewriteCount = state.getDeslopRewriteCount();
        if (rewriteCount >= maxRewrites) {
            log.warn("[{}] 重写预算已耗尽({}/{}), 放行", nodeName(), rewriteCount, maxRewrites);
            return state.toBuilder()
                    .deslopFindings(findings)
                    .deslopDecision(Decision.REWRITE_EXHAUSTED.name())
                    .build();
        }

        List<String> notes = formatNotes(findings);
        List<String> pending = new ArrayList<>();
        if (state.getPendingRevisionNotes() != null) {
            pending.addAll(state.getPendingRevisionNotes());
        }
        pending.addAll(notes);

        log.info("[{}] 命中 blocking={}, 请求重写({}/{})", nodeName(), blockingCount, rewriteCount + 1, maxRewrites);
        return state.toBuilder()
                .deslopFindings(findings)
                .deslopRewriteCount(rewriteCount + 1)
                .deslopDecision(Decision.REWRITE.name())
                .pendingRevisionNotes(pending)
                .build();
    }

    private List<DeslopFinding> collectMetricFindings(String body) {
        try {
            StyleMetrics metrics = styleTextAnalyzer.analyze(body);
            return DeslopMetricThresholds.evaluate(metrics);
        } catch (Exception e) {
            log.warn("[{}] 统计指标计算失败，忽略: {}", nodeName(), e.getMessage());
            return List.of();
        }
    }

    /**
     * 把 findings 格式化成给模型看的修订建议 —— 带逐字命中示例，比抽象描述可执行。
     */
    static List<String> formatNotes(List<DeslopFinding> findings) {
        List<String> notes = new ArrayList<>();
        for (DeslopFinding finding : findings) {
            if (finding.getSeverity() != DeslopSeverity.BLOCKING) {
                continue;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("- 「").append(shortRuleId(finding.getRuleId())).append("」");
            sb.append(finding.getMessage());
            if (finding.getQuote() != null && !finding.getQuote().isBlank()) {
                sb.append("｜命中：").append(finding.getQuote());
            }
            if (finding.getSuggestion() != null && !finding.getSuggestion().isBlank()) {
                sb.append("｜改法：").append(finding.getSuggestion());
            }
            notes.add(sb.toString());
        }
        return notes;
    }

    private static String shortRuleId(String ruleId) {
        if (ruleId == null) {
            return "";
        }
        int idx = ruleId.lastIndexOf(':');
        return idx >= 0 && idx < ruleId.length() - 1 ? ruleId.substring(idx + 1) : ruleId;
    }

    /** 门禁决策。 */
    public enum Decision {
        /** 无 blocking 命中（或门禁未开启）。 */
        PASS,
        /** 命中 blocking，请求重写。 */
        REWRITE,
        /** 命中 blocking 但重写预算耗尽，放行。 */
        REWRITE_EXHAUSTED
    }
}
