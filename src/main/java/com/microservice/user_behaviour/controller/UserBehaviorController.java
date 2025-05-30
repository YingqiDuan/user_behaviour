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

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Slf4j
public class UserBehaviorController {

    private final UserBehaviorService userBehaviorService;
    
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
        
        log.info("Received user behavior event: {}", event);
        
        try {
            CompletableFuture<Void> future = userBehaviorService.sendUserBehaviorEvent(event)
                .thenAccept(result -> log.debug("Event published successfully"));
            
            // Non-blocking response
            return ResponseEntity.accepted().body("Event accepted for processing");
        } catch (Exception e) {
            log.error("Error processing user behavior event", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error processing event: " + e.getMessage());
        }
    }
    
    @PostMapping("/batch")
    public ResponseEntity<String> collectEvents(@Valid @RequestBody UserBehaviorEvent[] events, 
                                         HttpServletRequest request) {
        log.info("Received batch of {} user behavior events", events.length);
        
        String clientIp = getClientIp(request);
        LocalDateTime now = LocalDateTime.now();
        
        try {
            for (UserBehaviorEvent event : events) {
                // Set missing fields
                if (event.getEventTime() == null) {
                    event.setEventTime(now);
                }
                
                if (event.getIpAddress() == null) {
                    event.setIpAddress(clientIp);
                }
                
                userBehaviorService.sendUserBehaviorEvent(event);
            }
            
            return ResponseEntity.accepted().body("Batch of " + events.length + " events accepted for processing");
        } catch (Exception e) {
            log.error("Error processing batch of user behavior events", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error processing events: " + e.getMessage());
        }
    }
    
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
} 