package com.microservice.user_behaviour.query.controller;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.microservice.user_behaviour.model.UserBehaviorEvent;
import com.microservice.user_behaviour.query.service.UserBehaviorQueryService;
import com.microservice.user_behaviour.query.client.ProcessingServiceClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/query")
@RequiredArgsConstructor
@Slf4j
@Profile("query")
public class UserStatsController {

    private final UserBehaviorQueryService queryService;
    private final ProcessingServiceClient processingServiceClient;
    
    /**
     * 查询某用户最近N条行为事件
     * GET /api/query/users/{id}/events?limit=100
     */
    @GetMapping("/users/{id}/events")
    public ResponseEntity<List<UserBehaviorEvent>> getUserEvents(
            @PathVariable String id,
            @RequestParam(defaultValue = "100") int limit) {
        
        log.info("Querying user events: userId={}, limit={}", id, limit);
        
        if (limit > 1000) {
            limit = 1000; // 限制最大查询数量
        }
        
        List<UserBehaviorEvent> events = queryService.getUserEvents(id, limit);
        
        log.info("Retrieved {} events for user: {}", events.size(), id);
        return ResponseEntity.ok(events);
    }
    
    /**
     * 查询当前最热门的事件类型排行榜
     * GET /api/query/stats/top-event-types?topN=10
     */
    @GetMapping("/stats/top-event-types")
    public ResponseEntity<Set<ZSetOperations.TypedTuple<Object>>> getTopEventTypes(
            @RequestParam(defaultValue = "10") int topN) {
        
        log.info("Querying top event types: topN={}", topN);
        
        Set<ZSetOperations.TypedTuple<Object>> topEvents = queryService.getTopEventTypes(topN);
        
        log.info("Retrieved {} top event types", topEvents.size());
        return ResponseEntity.ok(topEvents);
    }
    
    /**
     * 查询某用户的行为概要统计
     * GET /api/query/stats/user/{id}/summary
     */
    @GetMapping("/stats/user/{id}/summary")
    public ResponseEntity<Map<String, Object>> getUserSummary(@PathVariable String id) {
        
        log.info("Querying user summary: userId={}", id);
        
        Map<String, Object> summary = queryService.getUserSummary(id);
        
        log.info("Retrieved summary for user: {}", id);
        return ResponseEntity.ok(summary);
    }
    
    /**
     * 查询最活跃用户排行榜
     * GET /api/query/stats/top-active-users?topN=20
     */
    @GetMapping("/stats/top-active-users")
    public ResponseEntity<Set<ZSetOperations.TypedTuple<Object>>> getTopActiveUsers(
            @RequestParam(defaultValue = "20") int topN) {
        
        log.info("Querying top active users: topN={}", topN);
        
        Set<ZSetOperations.TypedTuple<Object>> topUsers = queryService.getTopActiveUsers(topN);
        
        log.info("Retrieved {} top active users", topUsers.size());
        return ResponseEntity.ok(topUsers);
    }
    
    /**
     * 查询指定时间范围的事件统计
     * GET /api/query/stats/events/time-range?start=2024-01-15T10:00:00&end=2024-01-15T11:00:00&topN=10
     */
    @GetMapping("/stats/events/time-range")
    public ResponseEntity<Map<String, Object>> getEventStatsByTimeRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "10") int topN) {
        
        log.info("Querying event stats by time range: start={}, end={}, topN={}", start, end, topN);
        
        Map<String, Object> stats = queryService.getEventStatsByTimeRange(start, end, topN);
        
        log.info("Retrieved event stats for time range: {} to {}", start, end);
        return ResponseEntity.ok(stats);
    }
    
    /**
     * 分页查询所有事件
     * GET /api/query/events?page=0&size=20
     */
    @GetMapping("/events")
    public ResponseEntity<Page<UserBehaviorEvent>> getAllEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("Querying all events: page={}, size={}", page, size);
        
        if (size > 100) {
            size = 100; // 限制页面大小
        }
        
        Page<UserBehaviorEvent> events = queryService.getAllEvents(page, size);
        
        log.info("Retrieved page {} of events, total elements: {}", page, events.getTotalElements());
        return ResponseEntity.ok(events);
    }
    
    /**
     * 根据事件类型查询
     * GET /api/query/events/type/{eventType}?page=0&size=20
     */
    @GetMapping("/events/type/{eventType}")
    public ResponseEntity<Page<UserBehaviorEvent>> getEventsByType(
            @PathVariable String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        log.info("Querying events by type: eventType={}, page={}, size={}", eventType, page, size);
        
        if (size > 100) {
            size = 100;
        }
        
        Page<UserBehaviorEvent> events = queryService.getEventsByType(eventType, page, size);
        
        log.info("Retrieved page {} of {} events, total elements: {}", 
                page, eventType, events.getTotalElements());
        return ResponseEntity.ok(events);
    }
    
    /**
     * 查询服务健康状态
     * GET /api/query/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        
        log.info("Health check requested");
        
        Map<String, Object> health = queryService.getQueryServiceStats();
        health.put("status", "UP");
        health.put("service", "user-behavior-query");
        health.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(health);
    }
    
    /**
     * 查询今日统计概览
     * GET /api/query/stats/today
     */
    @GetMapping("/stats/today")
    public ResponseEntity<Map<String, Object>> getTodayStats() {
        
        log.info("Querying today's statistics");
        
        LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();
        
        Map<String, Object> todayStats = queryService.getEventStatsByTimeRange(startOfDay, now, 10);
        todayStats.put("date", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        todayStats.put("queryTime", now);
        
        return ResponseEntity.ok(todayStats);
    }
    
    /**
     * 查询本小时统计
     * GET /api/query/stats/current-hour  
     */
    @GetMapping("/stats/current-hour")
    public ResponseEntity<Map<String, Object>> getCurrentHourStats() {
        
        log.info("Querying current hour statistics");
        
        LocalDateTime startOfHour = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        LocalDateTime now = LocalDateTime.now();
        
        Map<String, Object> hourStats = queryService.getEventStatsByTimeRange(startOfHour, now, 10);
        hourStats.put("hour", startOfHour.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00")));
        hourStats.put("queryTime", now);
        
        return ResponseEntity.ok(hourStats);
    }

    /**
     * 获取处理服务的实时统计信息（通过Feign调用）
     * GET /api/query/processing/stats
     */
    @GetMapping("/processing/stats")
    public ResponseEntity<Map<String, Object>> getProcessingServiceStats() {
        
        log.info("Calling processing service for stats via Feign");
        
        try {
            Map<String, Object> stats = processingServiceClient.getProcessingStats();
            stats.put("calledVia", "feign-client");
            stats.put("queryTimestamp", LocalDateTime.now());
            
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Failed to call processing service", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to communicate with processing service");
            errorResponse.put("message", e.getMessage());
            errorResponse.put("timestamp", LocalDateTime.now());
            return ResponseEntity.status(503).body(errorResponse);
        }
    }

    /**
     * 获取完整的系统状态（结合本地和远程服务）
     * GET /api/query/system/status
     */
    @GetMapping("/system/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        
        log.info("Getting comprehensive system status");
        
        Map<String, Object> systemStatus = new HashMap<>();
        
        // 本地查询服务状态
        Map<String, Object> queryServiceStats = queryService.getQueryServiceStats();
        systemStatus.put("queryService", queryServiceStats);
        
        // 远程处理服务状态（通过Feign）
        try {
            Map<String, Object> processingStats = processingServiceClient.getProcessingStats();
            systemStatus.put("processingService", processingStats);
            
            Map<String, Object> cacheHealth = processingServiceClient.getCacheHealth();
            systemStatus.put("cacheHealth", cacheHealth);
            
        } catch (Exception e) {
            log.warn("Failed to get processing service status", e);
            Map<String, Object> errorInfo = new HashMap<>();
            errorInfo.put("status", "ERROR");
            errorInfo.put("message", "Processing service unavailable");
            systemStatus.put("processingService", errorInfo);
        }
        
        systemStatus.put("timestamp", LocalDateTime.now());
        systemStatus.put("systemStatus", "OPERATIONAL");
        
        return ResponseEntity.ok(systemStatus);
    }
} 