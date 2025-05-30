package com.microservice.user_behaviour.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import com.microservice.user_behaviour.model.UserBehaviorEvent;
import com.microservice.user_behaviour.util.MetricsUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.annotation.PostConstruct;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserBehaviorService {

    private final KafkaTemplate<String, UserBehaviorEvent> kafkaTemplate;
    private final MetricsUtil metricsUtil;
    
    @Value("${user.behavior.topic}")
    private String userBehaviorTopic;
    
    @Value("${user.behavior.topic.pageview}")
    private String pageViewTopic;
    
    @Value("${user.behavior.topic.click}")
    private String clickTopic;
    
    @Value("${user.behavior.topic.search}")
    private String searchTopic;
    
    @Value("${user.behavior.topic.purchase}")
    private String purchaseTopic;
    
    @Value("${user.behavior.topic.default}")
    private String defaultTopic;
    
    // Map to store event type to topic mapping
    private final Map<String, String> eventTypeToTopicMap = new ConcurrentHashMap<>();
    
    /**
     * Initialize the event type to topic mapping
     */
    @PostConstruct
    public void initTopicMapping() {
        eventTypeToTopicMap.put("PAGE_VIEW", pageViewTopic);
        eventTypeToTopicMap.put("BUTTON_CLICK", clickTopic);
        eventTypeToTopicMap.put("LINK_CLICK", clickTopic);
        eventTypeToTopicMap.put("SEARCH", searchTopic);
        eventTypeToTopicMap.put("PURCHASE", purchaseTopic);
        
        log.info("Initialized event type to topic mapping: {}", eventTypeToTopicMap);
    }
    
    /**
     * Determine which topic to use based on event type
     */
    private String getTopicForEvent(UserBehaviorEvent event) {
        // Check if we have a specific topic for this event type
        String eventType = event.getEventType().toUpperCase();
        return eventTypeToTopicMap.getOrDefault(eventType, defaultTopic);
    }
    
    /**
     * Send user behavior event to Kafka asynchronously, with callbacks for success/failure
     */
    public CompletableFuture<SendResult<String, UserBehaviorEvent>> sendUserBehaviorEvent(UserBehaviorEvent event) {
        String key = UUID.randomUUID().toString();
        String topic = getTopicForEvent(event);
        
        log.debug("Routing event of type [{}] to topic [{}]", event.getEventType(), topic);
        
        CompletableFuture<SendResult<String, UserBehaviorEvent>> future = 
            kafkaTemplate.send(topic, key, event);
        
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                // Track successful event
                metricsUtil.incrementEventCount(event.getEventType());
                
                log.info("User behavior event sent successfully: type=[{}], userId=[{}], topic=[{}], partition=[{}], offset=[{}]", 
                        event.getEventType(),
                        event.getUserId(),
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                // Track failed event
                metricsUtil.incrementFailedCount();
                
                log.error("Failed to send user behavior event: type=[{}], userId=[{}]", 
                        event.getEventType(), event.getUserId(), ex);
            }
        });
        
        return future;
    }
    
    /**
     * Send user behavior event to Kafka synchronously, waiting for acknowledgment
     * Useful for critical events where you need to ensure delivery
     */
    public boolean sendSynchronously(UserBehaviorEvent event, long timeout) {
        try {
            String key = UUID.randomUUID().toString();
            String topic = getTopicForEvent(event);
            
            log.debug("Sending event synchronously to topic [{}]", topic);
            
            SendResult<String, UserBehaviorEvent> result = 
                kafkaTemplate.send(topic, key, event).get(timeout, TimeUnit.MILLISECONDS);
            
            metricsUtil.incrementEventCount(event.getEventType());
            
            log.info("User behavior event sent synchronously: type=[{}], userId=[{}], topic=[{}], partition=[{}], offset=[{}]", 
                    event.getEventType(),
                    event.getUserId(),
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread interrupted while sending event synchronously", e);
            metricsUtil.incrementFailedCount();
            return false;
        } catch (ExecutionException | TimeoutException e) {
            log.error("Failed to send event synchronously: {}", event, e);
            metricsUtil.incrementFailedCount();
            return false;
        }
    }
} 