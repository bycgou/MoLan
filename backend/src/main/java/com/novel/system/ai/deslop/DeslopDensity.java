package com.novel.system.ai.deslop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 密度判据：某个正则的命中次数超过按字数折算的基线即报。
 *
 * <p>基线 = {@code limit × (字数 / per)}，例如「每 1000 字不超过 2 处」。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeslopDensity {

    /** 命中模式。 */
    private String pattern;

    /** 密度分母：每 N 字。 */
    private int per;

    /** 每 per 字允许的命中数上限。 */
    private double limit;

    /**
     * 当前字数下的基线命中数上限。
     */
    public double allowanceFor(int charCount) {
        return limit * ((double) charCount / per);
    }
}
