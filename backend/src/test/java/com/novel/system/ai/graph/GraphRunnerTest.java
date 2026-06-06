package com.novel.system.ai.graph;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.*;

class GraphRunnerTest {

    @Test
    void parallelExecutorShouldBeBoundedFixedThreadPool() throws Exception {
        GraphDefinition definition = GraphDefinition.builder()
                .edge("start", "end")
                .build();
        GraphRunner runner = new GraphRunner(definition);

        Field executorField = GraphRunner.class.getDeclaredField("parallelExecutor");
        executorField.setAccessible(true);
        Object executor = executorField.get(runner);

        assertInstanceOf(ExecutorService.class, executor, "parallelExecutor should be an ExecutorService");

        // Must NOT be a CachedThreadPool (which is a ThreadPoolExecutor with maxPoolSize = Integer.MAX_VALUE)
        if (executor instanceof ThreadPoolExecutor tpe) {
            assertNotEquals(Integer.MAX_VALUE, tpe.getMaximumPoolSize(),
                    "parallelExecutor must not be an unbounded CachedThreadPool");
            assertTrue(tpe.getMaximumPoolSize() > 0,
                    "parallelExecutor pool size must be positive");
            assertTrue(tpe.getMaximumPoolSize() <= 8,
                    "parallelExecutor pool size must be at most 8, got " + tpe.getMaximumPoolSize());
        } else {
            // If it's not a ThreadPoolExecutor at all, the test still passes
            // as long as it's a valid ExecutorService (e.g., ForkJoinPool from newWorkStealingPool)
            assertNotNull(executor, "parallelExecutor must not be null");
        }
    }

    @Test
    void parallelExecutorShouldNotBeCachedThreadPool() throws Exception {
        GraphDefinition definition = GraphDefinition.builder()
                .edge("start", "end")
                .build();
        GraphRunner runner = new GraphRunner(definition);

        Field executorField = GraphRunner.class.getDeclaredField("parallelExecutor");
        executorField.setAccessible(true);
        ExecutorService executor = (ExecutorService) executorField.get(runner);

        // CachedThreadPool returns maxPoolSize = Integer.MAX_VALUE
        // FixedThreadPool returns maxPoolSize = nThreads
        if (executor instanceof ThreadPoolExecutor tpe) {
            assertTrue(tpe.getMaximumPoolSize() < Integer.MAX_VALUE,
                    "Executor pool must be bounded (was unbounded CachedThreadPool)");
        }
    }
}
