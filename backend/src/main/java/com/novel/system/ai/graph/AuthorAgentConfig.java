package com.novel.system.ai.graph;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "moyun.agent.author")
public class AuthorAgentConfig {
    private boolean enabled = true;
    private List<String> checkpoints = List.of("outline", "writing", "audit_router");
    private int maxInterventions = 3;
}
