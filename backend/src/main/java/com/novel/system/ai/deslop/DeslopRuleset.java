package com.novel.system.ai.deslop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 反 AI 味规则表。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeslopRuleset {

    private int version;

    private List<DeslopRule> rules;

    /**
     * 白名单作用域。当前只支持 {@code dialogue}：对白内容不参与机械判据。
     */
    private List<String> whitelistScopes;

    public boolean whitelistsDialogue() {
        return whitelistScopes != null && whitelistScopes.contains("dialogue");
    }
}
