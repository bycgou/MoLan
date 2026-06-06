package com.novel.system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig {

    /**
     * IO密集型异步任务线程池（文风分析、LLM调用等）
     */
    @Bean("ioTaskExecutor")
    public Executor ioTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("io-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 包装 ioTaskExecutor，自动传播 SecurityContext 到异步线程
     * 用于 CompletableFuture.runAsync() 等手动提交异步任务的场景
     */
    @Bean("securityContextIoExecutor")
    public Executor securityContextIoExecutor() {
        return new DelegatingSecurityContextExecutor(ioTaskExecutor());
    }

    /**
     * 章节后处理专用线程池（人物提取、伏笔提取、时间线提取等 LLM 调用）
     * 低并发避免批量生成时压垮 LLM API
     */
    @Bean("postProcessExecutor")
    public Executor postProcessExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("post-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * 默认异步执行器 — 自动传播 SecurityContext 到子线程
     * 解决 @Async 方法中 getCurrentUserId() 返回 null 的问题
     */
    @Bean(name = "taskExecutor")
    public Executor securityContextTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(new SecurityContextTaskDecorator());
        executor.initialize();
        return executor;
    }

    private static class SecurityContextTaskDecorator implements TaskDecorator {
        @Override
        public Runnable decorate(Runnable runnable) {
            SecurityContext context = SecurityContextHolder.getContext();
            return () -> {
                try {
                    SecurityContextHolder.setContext(context);
                    runnable.run();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
        }
    }
}
