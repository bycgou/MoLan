package com.novel.system.ai.deslop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 一条反 AI 味规则。
 *
 * <p>规则是数据不是代码 —— 完整定义在 {@code resources/deslop/deslop-rules.yaml}，
 * 改规则不改代码，diff 可审查。
 *
 * <p>{@code pattern} / {@code density} / {@code detector} 三种判据必须恰好给一个，
 * 该约束在 {@link DeslopRulesetLoader} 加载期强制。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeslopRule {

    /** 规则 id，kebab-case。 */
    private String id;

    private DeslopSeverity severity;

    private DeslopReview review;

    /** 正则判据，与 density、detector 三选一。 */
    private String pattern;

    /** 生效范围，默认全文。 */
    private DeslopScope scope;

    /** 密度判据，与 pattern、detector 三选一。 */
    private DeslopDensity density;

    /** 内置检测器名，与 pattern、density 三选一。 */
    private String detector;

    /** 自动修复方式，当前仅支持 {@code delete}。 */
    private String autofix;

    private String message;

    private String suggestion;

    private List<String> languages;

    /** 规则依据。证据会过期 —— 模型换代会让某些「AI 特征」直接消失。 */
    private String evidence;
}
