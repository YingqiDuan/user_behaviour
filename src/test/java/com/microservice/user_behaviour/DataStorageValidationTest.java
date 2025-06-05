package com.microservice.user_behaviour;

import com.microservice.user_behaviour.consumer.entity.UserBehaviorEntity;
import com.microservice.user_behaviour.consumer.repository.UserBehaviorRepository;
import com.microservice.user_behaviour.model.UserBehaviorEvent;
import com.microservice.user_behaviour.service.EventPublishingService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("consumer")
@Disabled("End-to-end test requires full infrastructure (Kafka, MySQL, Redis). Run manually when infrastructure is available.")
public class DataStorageValidationTest {

    @Autowired
    private EventPublishingService publishingService;

    @Autowired
    private UserBehaviorRepository repository;

    @Test
    public void testEndToEndEventStorage() throws Exception {
        // 1. Generate a unique test event
        String testId = UUID.randomUUID().toString();
        UserBehaviorEvent event = createTestEvent(testId);
        
        // 2. Publish the event to Kafka
        publishingService.publishEvent(event);
        
        // 3. Wait for the event to be processed
        TimeUnit.SECONDS.sleep(5);
        
        // 4. Verify the event was stored in the database
        List<UserBehaviorEntity> storedEvents = repository.findByUserIdAndEventTypeAndEventTimeBetween(
                event.getUserId(),
                event.getEventType(), 
                event.getEventTime().minusMinutes(1),
                event.getEventTime().plusMinutes(1));
        
        // 5. Validate the stored data - Fixed logic: assertFalse means we expect the list to NOT be empty
        assertFalse(storedEvents.isEmpty(), "Event should have been stored in the database but was not found");
        
        UserBehaviorEntity storedEvent = storedEvents.stream()
                .filter(e -> e.getUserId().equals(testId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Test event not found"));
        
        assertEquals(event.getEventType(), storedEvent.getEventType());
        assertEquals(event.getSource(), storedEvent.getSource());
        assertNotNull(storedEvent.getEventData(), "Event data should not be null");
        
        System.out.println("✅ Data storage validation successful!");
        System.out.println("Event stored with ID: " + storedEvent.getId());
    }
    
    private UserBehaviorEvent createTestEvent(String testId) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("testId", testId);
        eventData.put("action", "validation_test");
        eventData.put("timestamp", System.currentTimeMillis());
        
        return UserBehaviorEvent.builder()
                .userId(testId)
                .eventType("TEST_EVENT")
                .source("TEST_VALIDATION")
                .eventTime(LocalDateTime.now())
                .eventData(eventData)
                .sessionId("test-session")
                .deviceInfo("test-device")
                .ipAddress("127.0.0.1")
                .build();
    }
} 