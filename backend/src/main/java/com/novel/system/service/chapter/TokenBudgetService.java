package com.novel.system.service.chapter;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Token 预算管理服务
 *
 * 根据模型的 maxToken 配置动态分配各部分的 token 上限。
 * 解决硬编码截断导致的问题：小模型 context 溢出，大模型浪费可用空间。
 */
@Slf4j
@Service
public class TokenBudgetService {

    /**
     * 优先级分层：决定 token 预算分配的保护等级
     */
    public enum PriorityTier {
        T0_CRITICAL(0.20, false),    // 绝对保护，永不压缩
        T1_COMPRESSIBLE(0.30, true), // 可按比例压缩
        T2_DYNAMIC(0.35, true),      // 按水位线截断
        T3_SACRIFICIAL(0.15, true);  // 最先被牺牲，最低保障5%

        final double budgetRatio;
        final boolean compressible;

        PriorityTier(double budgetRatio, boolean compressible) {
            this.budgetRatio = budgetRatio;
            this.compressible = compressible;
        }
    }

    /**
     * 预算分配比例（按优先级从高到低）
     */
    private static final Map<String, Double> BUDGET_RATIOS = new LinkedHashMap<>();

    static {
        BUDGET_RATIOS.put("antiHallucination", 0.08);  // 防幻觉规则
        BUDGET_RATIOS.put("styleCard", 0.05);          // 文风卡片
        BUDGET_RATIOS.put("storyState", 0.06);         // 第一层：故事状态
        BUDGET_RATIOS.put("characterState", 0.06);     // 第三层：角色档案
        BUDGET_RATIOS.put("clueBook", 0.04);           // 第二层：伏笔账本
        BUDGET_RATIOS.put("recentOutline", 0.10);      // 第四层：滑动窗口 — 子层4b近章大纲
        BUDGET_RATIOS.put("arcSummary", 0.06);         // 第四层：滑动窗口 — 子层4a弧段摘要
        BUDGET_RATIOS.put("novelOutline", 0.08);       // 小说总纲
        BUDGET_RATIOS.put("worldSetting", 0.04);       // 世界观
        BUDGET_RATIOS.put("output", 0.39);             // 输出空间（至少30%）
        BUDGET_RATIOS.put("safetyMargin", 0.08);       // 安全余量
    }

    /**
     * 段落 → 优先级分层映射
     */
    private static final Map<String, PriorityTier> SECTION_TIERS = new LinkedHashMap<>();
    static {
        // T0 - Critical (never compressed)
        SECTION_TIERS.put("antiHallucination", PriorityTier.T0_CRITICAL);
        SECTION_TIERS.put("storyState", PriorityTier.T0_CRITICAL);
        SECTION_TIERS.put("clueBook", PriorityTier.T0_CRITICAL);
        SECTION_TIERS.put("characterState", PriorityTier.T0_CRITICAL);
        // T1 - Compressible
        SECTION_TIERS.put("arcSummary", PriorityTier.T1_COMPRESSIBLE);
        SECTION_TIERS.put("novelOutline", PriorityTier.T1_COMPRESSIBLE);
        SECTION_TIERS.put("styleCard", PriorityTier.T1_COMPRESSIBLE);
        SECTION_TIERS.put("worldSetting", PriorityTier.T1_COMPRESSIBLE);
        SECTION_TIERS.put("emotionLedger", PriorityTier.T1_COMPRESSIBLE);
        // T2 - Dynamic
        SECTION_TIERS.put("recentOutline", PriorityTier.T2_DYNAMIC);
        // T3 - Sacrificial
        SECTION_TIERS.put("echoRecall", PriorityTier.T3_SACRIFICIAL);
    }

    /**
     * 预算分配结果
     */
    @Data
    @NoArgsConstructor
    public static class TokenBudget {
        private int totalTokens;
        private Map<String, Integer> allocation = new LinkedHashMap<>();

        public int getAllocated(String key) {
            return allocation.getOrDefault(key, 0);
        }
    }

    /**
     * 根据模型 maxToken 计算预算分配
     *
     * @param modelMaxToken 模型的最大 token 数（如 4096、8192、32768）
     * @return 预算分配结果
     */
    public TokenBudget calculateBudget(int modelMaxToken) {
        int availableTokens = (int) (modelMaxToken * 0.80); // 80% usable

        // Allocate output first
        int outputTokens = (int) (availableTokens * 0.39);
        int contextBudget = availableTokens - outputTokens;

        Map<String, Integer> allocation = new LinkedHashMap<>();

        // Allocate by tier priority: T0 first, then T1, T2, T3
        for (PriorityTier tier : PriorityTier.values()) {
            double tierBudget = contextBudget * tier.budgetRatio;
            List<Map.Entry<String, Double>> tierSections = BUDGET_RATIOS.entrySet().stream()
                    .filter(e -> SECTION_TIERS.getOrDefault(e.getKey(), PriorityTier.T1_COMPRESSIBLE) == tier)
                    .collect(Collectors.toList());

            double tierTotalRatio = tierSections.stream()
                    .mapToDouble(Map.Entry::getValue).sum();

            for (Map.Entry<String, Double> section : tierSections) {
                int tokens = tierTotalRatio > 0
                        ? (int) (tierBudget * section.getValue() / tierTotalRatio)
                        : 0;
                allocation.put(section.getKey(), tokens);
            }
        }

        allocation.put("output", outputTokens);

        TokenBudget budget = new TokenBudget();
        budget.setTotalTokens(modelMaxToken);
        budget.setAllocation(allocation);

        log.debug("Token预算分配(tier-based): modelMax={}, contextBudget={}, allocation={}",
                modelMaxToken, contextBudget, allocation);
        return budget;
    }

    /**
     * 截断文本到指定 token 预算（按中文字符估算，1 token ≈ 1.5 字符）
     *
     * @param text 原始文本
     * @param tokenBudget token 预算
     * @return 截断后的文本
     */
    public String truncateToBudget(String text, int tokenBudget) {
        if (text == null || text.isEmpty()) return "";
        int maxChars = (int) (tokenBudget * 1.5); // 1.5 chars/token for Chinese
        if (text.length() <= maxChars) return text;

        // Find nearest sentence boundary before maxChars
        String truncated = text.substring(0, maxChars);
        int lastSentenceEnd = Math.max(
                Math.max(truncated.lastIndexOf('。'), truncated.lastIndexOf('！')),
                Math.max(truncated.lastIndexOf('？'), truncated.lastIndexOf('\n'))
        );

        if (lastSentenceEnd > maxChars * 0.7) {
            // Found a good sentence boundary (at least 70% of budget used)
            return truncated.substring(0, lastSentenceEnd + 1) + "\n...[TRUNCATED]";
        }

        return truncated + "...[TRUNCATED]";
    }

    /**
     * 回收未使用的预算到输出空间
     *
     * @param budget 当前预算分配
     * @param actualUsage 各段落实际使用的 token 数
     * @return 更新后的预算（多余 token 转移到 output）
     */
    public TokenBudget reclaimUnusedBudget(TokenBudget budget, Map<String, Integer> actualUsage) {
        int totalReclaimed = 0;
        for (Map.Entry<String, Integer> entry : budget.getAllocation().entrySet()) {
            String section = entry.getKey();
            int allocated = entry.getValue();
            int used = actualUsage.getOrDefault(section, allocated);
            int unused = Math.max(0, allocated - used);
            totalReclaimed += unused;
            budget.getAllocation().put(section, allocated - unused);
        }
        // Reclaim to output
        int currentOutput = budget.getAllocation().getOrDefault("output", 0);
        budget.getAllocation().put("output", currentOutput + totalReclaimed);
        return budget;
    }

    /**
     * 获取默认预算（用于未配置模型时的降级）
     */
    public TokenBudget getDefaultBudget() {
        return calculateBudget(8192);
    }
}
