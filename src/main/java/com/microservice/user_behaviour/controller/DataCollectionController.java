package com.microservice.user_behaviour.controller;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.microservice.user_behaviour.model.UserBehaviorEvent;
import com.microservice.user_behaviour.service.UserBehaviorService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller for collecting user behavior data through a simpler, more concise API.
 * This provides the /collect endpoint as an alternative to the more detailed API in UserBehaviorController.
 */
@RestController
@RequestMapping("/collect")
@RequiredArgsConstructor
@Slf4j
public class DataCollectionController {

    private final UserBehaviorService userBehaviorService;
    
    // Consider events with these types as critical (requiring synchronous delivery)
    private static final String[] CRITICAL_EVENT_TYPES = {"PURCHASE", "CHECKOUT", "LOGIN_FAILURE"};
    
    /**
     * Collect a single event
     */
    @PostMapping
    public ResponseEntity<String> collectEvent(
            @Valid @RequestBody UserBehaviorEvent event,
            HttpServletRequest request) {
        
        // Set missing fields if they're not provided
        if (event.getEventTime() == null) {
            event.setEventTime(LocalDateTime.now());
        }
        
        if (event.getIpAddress() == null) {
            event.setIpAddress(getClientIp(request));
        }
        
        log.info("Received user behavior event via /collect: {}", event);
        
        try {
            // Check if this is a critical event that requires synchronous processing
            if (isCriticalEvent(event.getEventType())) {
                boolean success = userBehaviorService.sendSynchronously(event, 5000);
                if (success) {
                    return ResponseEntity.accepted()
                        .body("Critical event accepted and confirmed delivery");
                } else {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to deliver critical event");
                }
            } else {
                // For non-critical events, use asynchronous processing
                userBehaviorService.sendUserBehaviorEvent(event);
                return ResponseEntity.accepted().body("Event accepted");
            }
        } catch (Exception e) {
            log.error("Error processing user behavior event", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error processing event");
        }
    }
    
    /**
     * Collect multiple events in a batch
     */
    @PostMapping("/batch")
    public ResponseEntity<String> collectEvents(
            @Valid @RequestBody UserBehaviorEvent[] events,
            HttpServletRequest request) {
        
        log.info("Received batch of {} events via /collect/batch", events.length);
        
        String clientIp = getClientIp(request);
        LocalDateTime now = LocalDateTime.now();
        
        try {
            int successCount = 0;
            int criticalCount = 0;
            
            for (UserBehaviorEvent event : events) {
                if (event.getEventTime() == null) {
                    event.setEventTime(now);
                }
                
                if (event.getIpAddress() == null) {
                    event.setIpAddress(clientIp);
                }
                
                if (isCriticalEvent(event.getEventType())) {
                    criticalCount++;
                    if (userBehaviorService.sendSynchronously(event, 5000)) {
                        successCount++;
                    }
                } else {
                    userBehaviorService.sendUserBehaviorEvent(event);
                    successCount++;
                }
            }
            
            if (successCount == events.length) {
                return ResponseEntity.accepted()
                    .body(String.format("Batch accepted (%d events, %d critical)", events.length, criticalCount));
            } else {
                return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .body(String.format("Partial success: %d of %d events processed (%d critical)", 
                        successCount, events.length, criticalCount));
            }
        } catch (Exception e) {
            log.error("Error processing batch of events", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error processing batch");
        }
    }
    
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
    
    private boolean isCriticalEvent(String eventType) {
        if (eventType == null) return false;
        
        String upperEventType = eventType.toUpperCase();
        for (String criticalType : CRITICAL_EVENT_TYPES) {
            if (criticalType.equals(upperEventType)) {
                return true;
            }
        }
        return false;
    }
} 