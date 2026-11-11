package com.novel.system.ai.deslop;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 一条规则命中。
 *
 * <p>进 {@code NovelAgentState} 并随检查点做 JSON 往返，因此用普通 class 而非 record，
 * 保证 Jackson 有无参构造器可用。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeslopFinding {

    /** 规则 id，带 {@code mech:deslop:} 前缀以区分机械判据与模型自评。 */
    private String ruleId;

    private DeslopSeverity severity;

    private DeslopReview review;

    /** 命中片段，用于定位。 */
    private String quote;

    private String message;

    private String suggestion;

    @JsonIgnore
    public boolean isBlocking() {
        return severity == DeslopSeverity.BLOCKING;
    }
}
