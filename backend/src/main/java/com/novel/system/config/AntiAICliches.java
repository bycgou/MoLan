package com.novel.system.config;

/**
 * AI套话黑名单 - 统一数据源
 *
 * 用于：
 * 1. WritingAgent @SystemMessage - 生成时约束
 * 2. StyleCheckerService - 生成后检测
 * 3. PromptTemplateServiceImpl - 模板注入
 */
public final class AntiAICliches {

    private AntiAICliches() {}

    /**
     * 高频AI套话黑名单（18个）
     * 出现在正文中即视为AI味过重
     */
    public static final String[] CLICHES = {
        "不禁", "似乎", "仿佛", "眼神中闪过", "嘴角微微上扬",
        "心中涌起一股", "一种...的感觉", "空气中弥漫着", "不约而同",
        "若有所思", "意味深长", "难以名状", "若隐若现", "一股暖流",
        "缓缓说道", "淡淡地说", "微微点头", "轻声说道"
    };

    /**
     * 过渡句万能胶黑名单
     * 这些过渡句过于模板化，应直接切场景
     */
    public static final String[] TRANSITION_CLICHES = {
        "时间飞逝", "转眼间", "转眼X天过去了", "时光荏苒",
        "与此同时", "另一边", "且不说", "单说",
        "就在XXX即将XXX的时候", "就在这个时候",
        "话音刚落", "沉默片刻", "微微一怔",
        "且听下回分解", "欲知后事如何"
    };

    /**
     * 升级/收获描写流水账黑名单
     * 这类描写过于模板化
     */
    public static final String[] UPGRADE_CLICHES = {
        "盘膝而坐", "运转功法", "灵力奔涌", "气息爆发",
        "睁开双眼", "嘴角笑意", "实力大增", "突破成功",
        "丹田中", "经脉中", "气海中"
    };

    /**
     * 格式化为Prompt注入文本 - 套话
     */
    public static String toPromptText() {
        StringBuilder sb = new StringBuilder();
        sb.append("禁止使用以下高频AI套话（全文出现即失败）：\n");
        for (int i = 0; i < CLICHES.length; i++) {
            sb.append("\"").append(CLICHES[i]).append("\"");
            if (i < CLICHES.length - 1) {
                sb.append(" ");
            }
            if ((i + 1) % 5 == 0 && i < CLICHES.length - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 格式化为Prompt注入文本 - 过渡句
     */
    public static String toTransitionPromptText() {
        StringBuilder sb = new StringBuilder();
        sb.append("禁止使用以下万能过渡句（直接切场景，不需要过渡）：\n");
        for (int i = 0; i < TRANSITION_CLICHES.length; i++) {
            sb.append("\"").append(TRANSITION_CLICHES[i]).append("\"");
            if (i < TRANSITION_CLICHES.length - 1) {
                sb.append(" ");
            }
            if ((i + 1) % 4 == 0 && i < TRANSITION_CLICHES.length - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 格式化为Prompt注入文本 - 升级流水账
     */
    public static String toUpgradePromptText() {
        StringBuilder sb = new StringBuilder();
        sb.append("禁止使用以下升级流水账描写（用细节和情感替代）：\n");
        for (int i = 0; i < UPGRADE_CLICHES.length; i++) {
            sb.append("\"").append(UPGRADE_CLICHES[i]).append("\"");
            if (i < UPGRADE_CLICHES.length - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    /**
     * 检查文本是否包含AI套话
     * @param text 待检查文本
     * @return 包含的套话列表，为空表示无套话
     */
    public static java.util.List<String> findCliches(String text) {
        java.util.List<String> found = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) {
            return found;
        }
        for (String cliche : CLICHES) {
            if (text.contains(cliche)) {
                found.add(cliche);
            }
        }
        return found;
    }

    /**
     * 检查文本是否包含过渡句
     * @param text 待检查文本
     * @return 包含的过渡句列表
     */
    public static java.util.List<String> findTransitionCliches(String text) {
        java.util.List<String> found = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) {
            return found;
        }
        for (String cliche : TRANSITION_CLICHES) {
            if (text.contains(cliche)) {
                found.add(cliche);
            }
        }
        return found;
    }

    /**
     * 检查文本是否包含升级流水账
     * @param text 待检查文本
     * @return 包含的流水账词汇列表
     */
    public static java.util.List<String> findUpgradeCliches(String text) {
        java.util.List<String> found = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) {
            return found;
        }
        for (String cliche : UPGRADE_CLICHES) {
            if (text.contains(cliche)) {
                found.add(cliche);
            }
        }
        return found;
    }
}
