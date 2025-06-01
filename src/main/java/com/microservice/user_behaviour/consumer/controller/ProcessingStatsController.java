package com.microservice.user_behaviour.consumer.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.microservice.user_behaviour.consumer.repository.UserBehaviorRepository;
import com.microservice.user_behaviour.consumer.service.UserBehaviorProcessingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
@Slf4j
@Profile("consumer")
public class ProcessingStatsController {

    private final UserBehaviorRepository repository;
    private final UserBehaviorProcessingService processingService;
    
    @GetMapping
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        
        long totalCount = repository.count();
        stats.put("totalEvents", totalCount);
        stats.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.ok(stats);
    }
    
    @GetMapping("/event-types/{eventType}")
    public ResponseEntity<Map<String, Object>> getEventTypeStats(
            @PathVariable String eventType,
            @RequestParam(required = false) String period) {
        
        Map<String, Object> stats = new HashMap<>();
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start;
        
        // Default to last 24 hours
        if (period == null || period.isEmpty()) {
            period = "day";
        }
        
        switch (period.toLowerCase()) {
            case "hour":
                start = now.minusHours(1);
                break;
            case "day":
                start = now.minusDays(1);
                break;
            case "week":
                start = now.minusWeeks(1);
                break;
            case "month":
                start = now.minusMonths(1);
                break;
            default:
                start = now.minusDays(1);
        }
        
        long count = repository.countByEventTypeAndEventTimeBetween(eventType, start, now);
        
        stats.put("eventType", eventType);
        stats.put("period", period);
        stats.put("count", count);
        stats.put("startTime", start);
        stats.put("endTime", now);
        
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/db-test")
    public ResponseEntity<Map<String, Object>> testDatabaseConnection() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Test basic repository connection
            long count = repository.count();
            result.put("status", "SUCCESS");
            result.put("connection", "OK");
            result.put("totalEvents", count);
            result.put("timestamp", LocalDateTime.now());
            
            // Test if we can query for PAGE_VIEW events in the last hour
            try {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime oneHourAgo = now.minusHours(1);
                long recentCount = repository.countByEventTypeAndEventTimeBetween("PAGE_VIEW", oneHourAgo, now);
                result.put("pageViewEventsLastHour", recentCount);
            } catch (Exception e) {
                result.put("queryError", e.getMessage());
            }
            
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("connection", "FAILED");
            result.put("error", e.getMessage());
            result.put("timestamp", LocalDateTime.now());
        }
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/flush")
    public ResponseEntity<Map<String, Object>> manualFlush() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Trigger manual flush
            processingService.flushQueue();
            
            // Get updated count
            long count = repository.count();
            
            result.put("status", "SUCCESS");
            result.put("message", "Manual flush completed");
            result.put("totalEventsAfterFlush", count);
            result.put("timestamp", LocalDateTime.now());
            
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
            result.put("timestamp", LocalDateTime.now());
        }
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/debug")
    public ResponseEntity<Map<String, Object>> getDebugInfo() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Get internal processing stats
            result.put("queueSize", processingService.getQueueSize());
            result.put("receivedCount", processingService.getReceivedCount());
            result.put("processedCount", processingService.getProcessedCount());
            result.put("databaseCount", repository.count());
            result.put("batchSize", processingService.getBatchSize());
            result.put("status", "SUCCESS");
            result.put("timestamp", LocalDateTime.now());
            
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
            result.put("timestamp", LocalDateTime.now());
        }
        
        return ResponseEntity.ok(result);
    }
} 