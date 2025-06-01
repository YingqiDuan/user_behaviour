package com.microservice.user_behaviour.consumer.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.microservice.user_behaviour.consumer.service.CacheService;
import com.microservice.user_behaviour.consumer.service.HotDataAnalysisService;
import com.microservice.user_behaviour.consumer.service.UserBehaviorProcessingService;
import com.microservice.user_behaviour.model.UserBehaviorEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/cache")
@RequiredArgsConstructor
@Slf4j
@Profile("consumer")
public class CacheController {

    private final CacheService cacheService;
    private final HotDataAnalysisService hotDataAnalysisService;
    private final UserBehaviorProcessingService processingService;
    
    /**
     * 获取用户最近的行为事件
     */
    @GetMapping("/users/{userId}/recent-events")
    public ResponseEntity<List<UserBehaviorEvent>> getUserRecentEvents(
            @PathVariable String userId,
            @RequestParam(defaultValue = "20") int limit) {
        
        log.info("Fetching recent events for user: {}, limit: {}", userId, limit);
        
        List<UserBehaviorEvent> events = cacheService.getUserRecentEvents(userId, limit);
        
        if (events.isEmpty()) {
            log.info("No cached events found for user: {}", userId);
        } else {
            log.info("Retrieved {} cached events for user: {}", events.size(), userId);
        }
        
        return ResponseEntity.ok(events);
    }
    
    /**
     * 获取热门事件类型排行榜
     */
    @GetMapping("/stats/top-event-types")
    public ResponseEntity<Set<ZSetOperations.TypedTuple<Object>>> getTopEventTypes(
            @RequestParam(defaultValue = "10") int topN) {
        
        log.info("Fetching top {} event types", topN);
        
        Set<ZSetOperations.TypedTuple<Object>> topEvents = cacheService.getTopEventTypes(topN);
        
        return ResponseEntity.ok(topEvents);
    }
    
    /**
     * 获取最活跃用户排行榜
     */
    @GetMapping("/stats/top-active-users")
    public ResponseEntity<Set<ZSetOperations.TypedTuple<Object>>> getTopActiveUsers(
            @RequestParam(defaultValue = "10") int topN) {
        
        log.info("Fetching top {} active users", topN);
        
        Set<ZSetOperations.TypedTuple<Object>> topUsers = cacheService.getTopActiveUsers(topN);
        
        return ResponseEntity.ok(topUsers);
    }
    
    /**
     * 获取指定时间段的事件统计
     */
    @GetMapping("/stats/period/{period}")
    public ResponseEntity<Set<ZSetOperations.TypedTuple<Object>>> getEventStatsForPeriod(
            @PathVariable String period,
            @RequestParam(defaultValue = "10") int topN) {
        
        log.info("Fetching event stats for period: {}, topN: {}", period, topN);
        
        Set<ZSetOperations.TypedTuple<Object>> stats = cacheService.getEventStatsForPeriod(period, topN);
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * 获取缓存的热门事件
     */
    @GetMapping("/stats/cached-top-events")
    public ResponseEntity<Set<ZSetOperations.TypedTuple<Object>>> getCachedTopEvents() {
        
        log.info("Fetching cached top events");
        
        Set<ZSetOperations.TypedTuple<Object>> topEvents = cacheService.getCachedTopEvents();
        
        return ResponseEntity.ok(topEvents);
    }
    
    /**
     * 获取实时热点数据报告
     */
    @GetMapping("/hot-data/report")
    public ResponseEntity<Map<String, Object>> getHotDataReport() {
        
        log.info("Generating hot data report");
        
        Map<String, Object> report = hotDataAnalysisService.getHotDataReport();
        
        return ResponseEntity.ok(report);
    }
    
    /**
     * 获取用户行为热度分析
     */
    @GetMapping("/hot-data/users/{userId}/heatmap")
    public ResponseEntity<Map<String, Object>> getUserBehaviorHeatMap(@PathVariable String userId) {
        
        log.info("Generating behavior heat map for user: {}", userId);
        
        Map<String, Object> heatMap = hotDataAnalysisService.getUserBehaviorHeatMap(userId);
        
        return ResponseEntity.ok(heatMap);
    }
    
    /**
     * 获取缓存统计信息
     */
    @GetMapping("/stats/cache")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        
        log.info("Fetching cache statistics");
        
        Map<String, Object> stats = Map.of(
                "cacheStats", cacheService.getCacheStats(),
                "redisConnected", cacheService.isRedisConnected(),
                "processingStats", processingService.getProcessingStats()
        );
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * 检查Redis连接状态
     */
    @GetMapping("/health/redis")
    public ResponseEntity<Map<String, Object>> checkRedisHealth() {
        
        boolean connected = cacheService.isRedisConnected();
        
        Map<String, Object> health = Map.of(
                "redisConnected", connected,
                "status", connected ? "UP" : "DOWN",
                "timestamp", System.currentTimeMillis()
        );
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * 获取处理统计信息
     */
    @GetMapping("/stats/processing")
    public ResponseEntity<Map<String, Object>> getProcessingStats() {
        
        log.info("Fetching processing statistics");
        
        Map<String, Object> stats = processingService.getProcessingStats();
        
        return ResponseEntity.ok(stats);
    }
} 