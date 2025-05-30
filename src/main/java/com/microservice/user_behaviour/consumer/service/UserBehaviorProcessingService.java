package com.microservice.user_behaviour.consumer.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Value;
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
public class UserBehaviorProcessingService {

    private final UserBehaviorRepository repository;
    private final ObjectMapper objectMapper;
    
    // In-memory queue for batching events before database insertion
    private final ConcurrentLinkedQueue<UserBehaviorEntity> processingQueue = new ConcurrentLinkedQueue<>();
    
    // Counter for events received
    private final AtomicInteger receivedCount = new AtomicInteger(0);
    private final AtomicInteger processedCount = new AtomicInteger(0);
    
    @Value("${app.batch.size:100}")
    private int batchSize;
    
    /**
     * Process a single user behavior event
     */
    public void processEvent(UserBehaviorEvent event, String topic, int partition, long offset) {
        log.debug("Processing event: {}, topic: {}, partition: {}, offset: {}", 
                event, topic, partition, offset);
        
        try {
            // Convert event data to JSON string
            String eventDataJson = null;
            if (event.getEventData() != null) {
                eventDataJson = objectMapper.writeValueAsString(event.getEventData());
            }
            
            // Create entity from event
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
            
            // Add to processing queue
            processingQueue.add(entity);
            receivedCount.incrementAndGet();
            
            // If queue size reaches threshold, flush to database
            if (processingQueue.size() >= batchSize) {
                flushQueue();
            }
        } catch (JsonProcessingException e) {
            log.error("Error processing event data for event: {}", event, e);
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
            log.info("Successfully saved {} events. Total received: {}, processed: {}", 
                    batch.size(), receivedCount.get(), processedCount.get());
        }
    }
} 