package com.novel.system.ai.graph;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import org.springframework.stereotype.Component;

@Getter
@Component
public class AgentMetrics {
    private final MeterRegistry registry;
    private final Timer nodeExecutionTime;
    private final Counter nodeRetries;
    private final Counter nodeErrors;
    private final Timer llmCallDuration;
    private final Counter llmTokensTotal;
    private final Counter llmCallsTotal;
    private final Timer chapterGenerationTotal;
    private final Counter auditDecisions;

    public AgentMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.nodeExecutionTime = Timer.builder("moyun_agent_node_duration_seconds")
            .description("Node execution duration")
            .register(registry);

        this.nodeRetries = Counter.builder("moyun_agent_node_retries_total")
            .description("Total node retries")
            .register(registry);

        this.nodeErrors = Counter.builder("moyun_agent_node_errors_total")
            .description("Total node errors")
            .register(registry);

        this.llmCallDuration = Timer.builder("moyun_agent_llm_call_duration_seconds")
            .description("LLM call duration")
            .register(registry);

        this.llmTokensTotal = Counter.builder("moyun_agent_llm_tokens_total")
            .description("Total LLM tokens consumed")
            .register(registry);

        this.llmCallsTotal = Counter.builder("moyun_agent_llm_calls_total")
            .description("Total LLM calls")
            .register(registry);

        this.chapterGenerationTotal = Timer.builder("moyun_agent_chapter_generation_seconds")
            .description("Chapter generation total duration")
            .register(registry);

        this.auditDecisions = Counter.builder("moyun_agent_audit_decisions_total")
            .description("Audit router decisions")
            .register(registry);
    }

    public Timer.Sample startNodeTimer() {
        return Timer.start();
    }

    public void recordNodeDuration(Timer.Sample sample, String nodeName, Mode mode) {
        sample.stop(Timer.builder("moyun_agent_node_duration_seconds")
            .tag("node", nodeName)
            .tag("mode", mode.name())
            .register(registry));
    }
}
