package com.microservice.user_behaviour.query.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableAsync
@Profile("query")
@Slf4j
public class ConcurrencyConfig implements WebMvcConfigurer {

    @Value("${spring.task.execution.pool.core-size:10}")
    private int corePoolSize;
    
    @Value("${spring.task.execution.pool.max-size:50}")
    private int maxPoolSize;
    
    @Value("${spring.task.execution.pool.queue-capacity:100}")
    private int queueCapacity;
    
    @Bean(name = "queryTaskExecutor")
    public ThreadPoolTaskExecutor queryTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("Query-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        
        log.info("Query task executor configured: core={}, max={}, queue={}", 
                corePoolSize, maxPoolSize, queueCapacity);
        
        return executor;
    }
    
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(queryTaskExecutor());
        configurer.setDefaultTimeout(30000); // 30秒超时
    }
} 