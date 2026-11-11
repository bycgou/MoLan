package com.novel.system.ai.deslop;

/**
 * 规则的生效范围。
 *
 * <p>{@link #CHAPTER_TAIL} 用于「章末总结体 / 预告体」这类只该在章末被抓的形态 ——
 * 正文中间出现同样的句式是合法的。
 */
public enum DeslopScope {
    /** 全文。 */
    FULL,
    /** 仅正文最后若干段。 */
    CHAPTER_TAIL
}
