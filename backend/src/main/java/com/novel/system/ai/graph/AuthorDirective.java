package com.novel.system.ai.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorDirective {

    public enum Action {
        APPROVE,
        REVISE,
        REJECT,
        REDIRECT
    }

    private Action action;

    private String rationale;

    private List<String> creativeNotes;

    private String targetNode;

    private String styleAdjustment;

    private String narrativeDirection;

    public boolean shouldIntervene() {
        return action != null && action != Action.APPROVE;
    }
}
