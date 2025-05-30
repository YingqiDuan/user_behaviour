package com.microservice.user_behaviour.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * A simple utility class for tracking metrics related to user behavior events.
 * In a production environment, this would be replaced with a proper metrics system
 * like Prometheus, Micrometer, etc.
 */
@Component
@Slf4j
public class MetricsUtil {

    private Map<String, AtomicLong> eventTypeCount;
    private AtomicLong totalEvents;
    private AtomicLong failedEvents;
    private long startTime;
    
    @PostConstruct
    public void init() {
        eventTypeCount = new ConcurrentHashMap<>();
        totalEvents = new AtomicLong(0);
        failedEvents = new AtomicLong(0);
        startTime = System.currentTimeMillis();
        
        // Log metrics every minute
        new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(60000); // 1 minute
                    logMetrics();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "metrics-logger").start();
    }
    
    public void incrementEventCount(String eventType) {
        totalEvents.incrementAndGet();
        eventTypeCount.computeIfAbsent(eventType, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    public void incrementFailedCount() {
        failedEvents.incrementAndGet();
    }
    
    public void logMetrics() {
        long uptime = (System.currentTimeMillis() - startTime) / 1000;
        double eventsPerSecond = totalEvents.get() / (double) uptime;
        
        log.info("=== User Behavior Metrics ===");
        log.info("Total events: {}", totalEvents);
        log.info("Failed events: {}", failedEvents);
        log.info("Events per second: {}", String.format("%.2f", eventsPerSecond));
        log.info("Event type breakdown:");
        
        eventTypeCount.forEach((type, count) -> {
            log.info("  - {}: {}", type, count);
        });
        
        log.info("============================");
    }
} 