package com.forgeshift.wso2discovery.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Thread pool used by @Async discovery workers.
 */
@Configuration
@RequiredArgsConstructor
public class AsyncConfig {

    private final DiscoveryProperties props;

    @Bean(name = "discoveryExecutor")
    public TaskExecutor discoveryExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(Math.max(2, props.getParallelThreadPoolSize()));
        exec.setMaxPoolSize(Math.max(4, props.getParallelThreadPoolSize() * 2));
        exec.setQueueCapacity(50);
        exec.setThreadNamePrefix("discovery-");
        exec.initialize();
        return exec;
    }
}
