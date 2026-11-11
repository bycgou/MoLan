package com.novel.system.ai.graph;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "novel.validation")
public class ValidationConfig {

    private boolean useGradedAudit = true;
    private double passThreshold = 60.0;

    private Map<String, DimensionConfig> dimensions = Map.of(
            "ooc", new DimensionConfig(0.20, Severity.FATAL),
            "logic", new DimensionConfig(0.20, Severity.FATAL),
            "setting", new DimensionConfig(0.15, Severity.FATAL),
            "clue", new DimensionConfig(0.15, Severity.WARNING),
            "rhythm", new DimensionConfig(0.10, Severity.WARNING),
            "quality", new DimensionConfig(0.10, Severity.WARNING),
            "clueLanding", new DimensionConfig(0.10, Severity.WARNING)
    );

    @Data
    @AllArgsConstructor
    public static class DimensionConfig {
        private double weight;
        private Severity severity;
    }

    public enum Severity { FATAL, WARNING, SKIP }
}
