package com.novel.system.ai.deslop;

/**
 * 判据的复核方式 —— 决定这条规则能不能挡稿。
 *
 * <p>{@link #MECHANICAL} 表示正则/密度这类可由代码完全判定的形态；
 * {@link #SEMANTIC} 表示需要语境理解，只能出提示。
 */
public enum DeslopReview {
    MECHANICAL,
    SEMANTIC
}
