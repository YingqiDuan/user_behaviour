package com.microservice.user_behaviour.service;

import com.microservice.user_behaviour.model.UserBehaviorEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ExecutionException;

/**
 * Service responsible for publishing user behavior events to Kafka
 */
@Service
@Slf4j
public class EventPublishingService {

    @Autowired
    private KafkaTemplate<String, UserBehaviorEvent> kafkaTemplate;
    
    @Value("${user.behavior.topic:user-behavior}")
    private String defaultTopic;
    
    @Value("${user.behavior.topic.pageview:user-behavior-pageview}")
    private String pageViewTopic;
    
    @Value("${user.behavior.topic.click:user-behavior-click}")
    private String clickTopic;
    
    @Value("${user.behavior.topic.search:user-behavior-search}")
    private String searchTopic;
    
    @Value("${user.behavior.topic.purchase:user-behavior-purchase}")
    private String purchaseTopic;
    
    @Value("${user.behavior.topic.default:user-behavior-other}")
    private String otherTopic;

    /**
     * Publish a user behavior event to the appropriate Kafka topic based on event type
     * 
     * @param event the event to publish
     * @return true if the event was successfully published
     */
    public boolean publishEvent(UserBehaviorEvent event) {
        String topic = determineTopicForEvent(event);
        String key = event.getUserId();
        
        try {
            log.info("Publishing event of type {} for user {} to topic {}", 
                    event.getEventType(), event.getUserId(), topic);
            
            // Send synchronously to ensure it's delivered
            kafkaTemplate.send(topic, key, event).get();
            
            log.debug("Successfully published event: {}", event);
            return true;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Failed to publish event to Kafka: {}", event, e);
            return false;
        }
    }
    
    /**
     * Determine the appropriate Kafka topic based on event type
     * 
     * @param event the event
     * @return the topic name
     */
    private String determineTopicForEvent(UserBehaviorEvent event) {
        String eventType = event.getEventType();
        
        if (eventType == null) {
            return defaultTopic;
        }
        
        switch (eventType.toUpperCase()) {
            case "PAGE_VIEW":
                return pageViewTopic;
            case "CLICK":
                return clickTopic;
            case "SEARCH":
                return searchTopic;
            case "PURCHASE":
                return purchaseTopic;
            default:
                return otherTopic;
        }
    }
} 