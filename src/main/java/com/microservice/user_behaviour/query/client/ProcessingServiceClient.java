package com.microservice.user_behaviour.query.client;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "user-behavior-consumer", fallback = ProcessingServiceClientFallback.class)
public interface ProcessingServiceClient {

    /**
     * 获取实时处理统计信息
     */
    @GetMapping("/api/stats")
    Map<String, Object> getProcessingStats();

    /**
     * 获取缓存健康状态
     */
    @GetMapping("/api/cache/health/redis")
    Map<String, Object> getCacheHealth();

    /**
     * 获取热点数据报告
     */
    @GetMapping("/api/cache/hot-data/report")
    Map<String, Object> getHotDataReport();

    /**
     * 获取用户实时统计（如果处理服务有提供的话）
     */
    @GetMapping("/api/stats/user/{userId}/realtime")
    Map<String, Object> getUserRealtimeStats(@PathVariable String userId);
} 