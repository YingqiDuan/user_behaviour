package com.microservice.user_behaviour.consumer.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.microservice.user_behaviour.consumer.repository.UserBehaviorRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
@Slf4j
public class ProcessingStatsController {

    private final UserBehaviorRepository repository;
    
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
} 