package com.novel.system.service.chapter;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

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
        TokenBudget budget = new TokenBudget();
        // 预留 20% 作为安全余量
        int availableTokens = (int) (modelMaxToken * 0.8);

        for (Map.Entry<String, Double> entry : BUDGET_RATIOS.entrySet()) {
            int tokens = (int) (availableTokens * entry.getValue());
            budget.getAllocation().put(entry.getKey(), tokens);
        }

        log.debug("Token预算分配: modelMax={}, available={}, allocation={}",
                modelMaxToken, availableTokens, budget.getAllocation());
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
        if (text == null || text.isEmpty()) {
            return "";
        }
        // 中文约 1.5 字符/token，安全起见用 1.2 字符/token
        int maxChars = (int) (tokenBudget * 1.2);
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...[TRUNCATED]";
    }

    /**
     * 获取默认预算（用于未配置模型时的降级）
     */
    public TokenBudget getDefaultBudget() {
        return calculateBudget(8192);
    }
}
