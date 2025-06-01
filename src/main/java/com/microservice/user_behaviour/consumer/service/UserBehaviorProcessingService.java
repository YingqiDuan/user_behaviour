package com.microservice.user_behaviour.consumer.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservice.user_behaviour.consumer.entity.UserBehaviorEntity;
import com.microservice.user_behaviour.consumer.repository.UserBehaviorRepository;
import com.microservice.user_behaviour.model.UserBehaviorEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
@Profile("consumer")
public class UserBehaviorProcessingService {

    private final UserBehaviorRepository repository;
    private final ObjectMapper objectMapper;
    private final CacheService cacheService;
    
    // In-memory queue for batching events before database insertion
    private final ConcurrentLinkedQueue<UserBehaviorEntity> processingQueue = new ConcurrentLinkedQueue<>();
    
    // Counter for events received
    private final AtomicInteger receivedCount = new AtomicInteger(0);
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger cachedCount = new AtomicInteger(0);
    
    @Value("${app.batch.size:100}")
    private int batchSize;
    
    /**
     * Process a single user behavior event
     */
    public void processEvent(UserBehaviorEvent event, String topic, int partition, long offset) {
        log.debug("Processing event: {}, topic: {}, partition: {}, offset: {}", 
                event, topic, partition, offset);
        
        try {
            // 1. 更新缓存 - 先更新缓存以提供实时数据
            updateCache(event);
            
            // 2. Convert event data to JSON string
            String eventDataJson = null;
            if (event.getEventData() != null) {
                eventDataJson = objectMapper.writeValueAsString(event.getEventData());
            }
            
            // 3. Create entity from event
            UserBehaviorEntity entity = UserBehaviorEntity.builder()
                    .userId(event.getUserId())
                    .eventType(event.getEventType())
                    .source(event.getSource())
                    .eventTime(event.getEventTime())
                    .eventData(eventDataJson)
                    .sessionId(event.getSessionId())
                    .deviceInfo(event.getDeviceInfo())
                    .ipAddress(event.getIpAddress())
                    .processedTime(LocalDateTime.now())
                    .topic(topic)
                    .partition(partition)
                    .offset(offset)
                    .build();
            
            // 4. Add to processing queue
            processingQueue.add(entity);
            receivedCount.incrementAndGet();
            
            // 5. If queue size reaches threshold, flush to database
            if (processingQueue.size() >= batchSize) {
                flushQueue();
            }
        } catch (JsonProcessingException e) {
            log.error("Error processing event data for event: {}", event, e);
        } catch (Exception e) {
            log.error("Error processing event: {}", event, e);
        }
    }
    
    /**
     * Update cache with event data
     */
    private void updateCache(UserBehaviorEvent event) {
        try {
            // 缓存用户最近事件
            cacheService.cacheUserRecentEvent(event);
            
            // 更新事件类型统计
            cacheService.updateEventTypeStats(event.getEventType());
            
            // 更新用户活跃度统计
            cacheService.updateUserActivityStats(event.getUserId());
            
            cachedCount.incrementAndGet();
            
            log.debug("Successfully updated cache for event: userId={}, eventType={}", 
                    event.getUserId(), event.getEventType());
            
        } catch (Exception e) {
            log.error("Error updating cache for event: {}", event, e);
        }
    }
    
    /**
     * Process a batch of user behavior events
     */
    public void processBatch(List<UserBehaviorEvent> events, Map<String, Object> recordMetadata) {
        log.info("Processing batch of {} events", events.size());
        
        // Simple data enrichment example
        for (UserBehaviorEvent event : events) {
            // Add any missing fields or default values
            if (event.getEventTime() == null) {
                event.setEventTime(LocalDateTime.now());
            }
            
            // Process each event
            processEvent(event, 
                    recordMetadata.get("topic").toString(), 
                    (int) recordMetadata.get("partition"), 
                    (long) recordMetadata.get("offset"));
        }
    }
    
    /**
     * Scheduled method to flush the queue even if it doesn't reach the threshold
     */
    @Scheduled(fixedDelay = 10000) // Run every 10 seconds
    public void scheduledFlush() {
        if (!processingQueue.isEmpty()) {
            log.debug("Scheduled flush of {} events", processingQueue.size());
            flushQueue();
        }
    }
    
    /**
     * Flush the queue to the database
     */
    @Transactional
    public synchronized void flushQueue() {
        if (processingQueue.isEmpty()) {
            return;
        }
        
        List<UserBehaviorEntity> batch = new ArrayList<>(processingQueue.size());
        while (!processingQueue.isEmpty()) {
            UserBehaviorEntity entity = processingQueue.poll();
            if (entity != null) {
                batch.add(entity);
            }
        }
        
        if (!batch.isEmpty()) {
            log.info("Flushing batch of {} events to database", batch.size());
            repository.saveAll(batch);
            processedCount.addAndGet(batch.size());
            log.info("Successfully saved {} events. Total received: {}, processed: {}, cached: {}", 
                    batch.size(), receivedCount.get(), processedCount.get(), cachedCount.get());
        }
    }
    
    /**
     * Get processing statistics
     */
    public Map<String, Object> getProcessingStats() {
        return Map.of(
                "queueSize", processingQueue.size(),
                "receivedCount", receivedCount.get(),
                "processedCount", processedCount.get(),
                "cachedCount", cachedCount.get(),
                "batchSize", batchSize,
                "cacheHitRate", calculateCacheHitRate()
        );
    }
    
    /**
     * Calculate cache hit rate (simplified)
     */
    private double calculateCacheHitRate() {
        int total = receivedCount.get();
        int cached = cachedCount.get();
        return total > 0 ? (double) cached / total * 100 : 0.0;
    }
    
    // Debug getter methods
    public int getQueueSize() {
        return processingQueue.size();
    }
    
    public int getReceivedCount() {
        return receivedCount.get();
    }
    
    public int getProcessedCount() {
        return processedCount.get();
    }
    
    public int getCachedCount() {
        return cachedCount.get();
    }
    
    public int getBatchSize() {
        return batchSize;
    }
} 