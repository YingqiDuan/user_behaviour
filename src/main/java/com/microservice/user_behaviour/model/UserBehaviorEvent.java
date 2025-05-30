package com.microservice.user_behaviour.model;

import java.time.LocalDateTime;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBehaviorEvent {

    @NotBlank(message = "User ID cannot be empty")
    private String userId;
    
    @NotBlank(message = "Event type cannot be empty")
    private String eventType;
    
    @NotBlank(message = "Source cannot be empty")
    private String source;
    
    @NotNull(message = "Event time cannot be null")
    private LocalDateTime eventTime;
    
    private Map<String, Object> eventData;
    
    private String sessionId;
    
    private String deviceInfo;
    
    private String ipAddress;
} 