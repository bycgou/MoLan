package com.novel.system.ai.deslop;

/**
 * 反 AI 味规则的严重度。
 *
 * <p>铁律：{@link #BLOCKING} 只允许由机械判据（{@link DeslopReview#MECHANICAL}）给出，
 * 语义判断永远不能挡稿。
 */
public enum DeslopSeverity {
    /** 强模板短语，几乎不可能误伤，可作为挡稿判据。 */
    BLOCKING,
    /** 该改但不致命的形态。 */
    MAJOR,
    /** 只计数出提示，不参与任何路由。 */
    ADVISORY
}
