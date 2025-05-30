package com.microservice.user_behaviour.consumer.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_behavior_events", 
       indexes = {
           @Index(name = "idx_user_id", columnList = "user_id"),
           @Index(name = "idx_event_type", columnList = "event_type"),
           @Index(name = "idx_event_time", columnList = "event_time")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBehaviorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "event_type", nullable = false)
    private String eventType;
    
    @Column(name = "source", nullable = false)
    private String source;
    
    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;
    
    @Lob
    @Column(name = "event_data")
    private String eventData;
    
    @Column(name = "session_id")
    private String sessionId;
    
    @Column(name = "device_info")
    private String deviceInfo;
    
    @Column(name = "ip_address")
    private String ipAddress;
    
    @Column(name = "processed_time", nullable = false)
    private LocalDateTime processedTime;
    
    @Column(name = "topic")
    private String topic;
    
    @Column(name = "partition")
    private Integer partition;
    
    @Column(name = "offset")
    private Long offset;
} 