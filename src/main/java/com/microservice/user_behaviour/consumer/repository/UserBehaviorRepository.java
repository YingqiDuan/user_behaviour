package com.microservice.user_behaviour.consumer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.microservice.user_behaviour.consumer.entity.UserBehaviorEntity;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserBehaviorRepository extends JpaRepository<UserBehaviorEntity, Long> {
    
    List<UserBehaviorEntity> findByUserIdAndEventTypeAndEventTimeBetween(
            String userId, String eventType, LocalDateTime start, LocalDateTime end);
    
    List<UserBehaviorEntity> findByEventTypeOrderByEventTimeDesc(String eventType, org.springframework.data.domain.Pageable pageable);
    
    long countByEventTypeAndEventTimeBetween(String eventType, LocalDateTime start, LocalDateTime end);
} 