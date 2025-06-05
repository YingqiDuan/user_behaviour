package com.microservice.user_behaviour.query.client;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ProcessingServiceClientFallback implements ProcessingServiceClient {

    @Override
    public Map<String, Object> getProcessingStats() {
        log.warn("Processing service is unavailable, returning fallback stats");
        Map<String, Object> fallbackStats = new HashMap<>();
        fallbackStats.put("status", "SERVICE_UNAVAILABLE");
        fallbackStats.put("message", "Processing service is currently unavailable");
        fallbackStats.put("timestamp", LocalDateTime.now());
        fallbackStats.put("source", "fallback");
        return fallbackStats;
    }

    @Override
    public Map<String, Object> getCacheHealth() {
        log.warn("Processing service is unavailable, returning fallback cache health");
        Map<String, Object> fallbackHealth = new HashMap<>();
        fallbackHealth.put("status", "UNKNOWN");
        fallbackHealth.put("message", "Unable to check cache health - processing service unavailable");
        fallbackHealth.put("timestamp", LocalDateTime.now());
        return fallbackHealth;
    }

    @Override
    public Map<String, Object> getHotDataReport() {
        log.warn("Processing service is unavailable, returning fallback hot data report");
        Map<String, Object> fallbackReport = new HashMap<>();
        fallbackReport.put("status", "UNAVAILABLE");
        fallbackReport.put("message", "Hot data report unavailable - processing service down");
        fallbackReport.put("timestamp", LocalDateTime.now());
        return fallbackReport;
    }

    @Override
    public Map<String, Object> getUserRealtimeStats(String userId) {
        log.warn("Processing service is unavailable, returning fallback user stats for userId: {}", userId);
        Map<String, Object> fallbackStats = new HashMap<>();
        fallbackStats.put("userId", userId);
        fallbackStats.put("status", "UNAVAILABLE");
        fallbackStats.put("message", "Realtime stats unavailable - processing service down");
        fallbackStats.put("timestamp", LocalDateTime.now());
        return fallbackStats;
    }
} 