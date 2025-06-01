package com.microservice.user_behaviour.query.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.microservice.user_behaviour.model.UserBehaviorEntity;

@Repository
@Profile("query")
public interface UserBehaviorQueryRepository extends JpaRepository<UserBehaviorEntity, Long> {

    /**
     * 根据用户ID查询用户行为事件，按时间倒序
     */
    List<UserBehaviorEntity> findByUserIdOrderByEventTimeDesc(String userId, Pageable pageable);
    
    /**
     * 根据用户ID和事件类型查询
     */
    List<UserBehaviorEntity> findByUserIdAndEventTypeOrderByEventTimeDesc(
            String userId, String eventType, Pageable pageable);
    
    /**
     * 根据时间范围查询用户行为
     */
    List<UserBehaviorEntity> findByUserIdAndEventTimeBetweenOrderByEventTimeDesc(
            String userId, LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * 统计用户各事件类型的数量
     */
    @Query("SELECT e.eventType, COUNT(e) FROM UserBehaviorEntity e WHERE e.userId = :userId GROUP BY e.eventType")
    List<Object[]> countEventTypesByUserId(@Param("userId") String userId);
    
    /**
     * 统计最热门的事件类型
     */
    @Query("SELECT e.eventType, COUNT(e) as cnt FROM UserBehaviorEntity e GROUP BY e.eventType ORDER BY cnt DESC")
    List<Object[]> findTopEventTypes(Pageable pageable);
    
    /**
     * 统计最活跃的用户
     */
    @Query("SELECT e.userId, COUNT(e) as cnt FROM UserBehaviorEntity e GROUP BY e.userId ORDER BY cnt DESC")
    List<Object[]> findTopActiveUsers(Pageable pageable);
    
    /**
     * 根据时间范围统计事件类型
     */
    @Query("SELECT e.eventType, COUNT(e) as cnt FROM UserBehaviorEntity e " +
           "WHERE e.eventTime BETWEEN :startTime AND :endTime " +
           "GROUP BY e.eventType ORDER BY cnt DESC")
    List<Object[]> findEventTypeStatsByTimeRange(
            @Param("startTime") LocalDateTime startTime, 
            @Param("endTime") LocalDateTime endTime, 
            Pageable pageable);
    
    /**
     * 获取用户在指定时间范围内的事件统计
     */
    @Query("SELECT COUNT(e) FROM UserBehaviorEntity e WHERE e.userId = :userId " +
           "AND e.eventTime BETWEEN :startTime AND :endTime")
    Long countUserEventsByTimeRange(
            @Param("userId") String userId,
            @Param("startTime") LocalDateTime startTime, 
            @Param("endTime") LocalDateTime endTime);
    
    /**
     * 分页查询所有用户行为事件
     */
    Page<UserBehaviorEntity> findAllByOrderByEventTimeDesc(Pageable pageable);
    
    /**
     * 根据事件类型查询
     */
    Page<UserBehaviorEntity> findByEventTypeOrderByEventTimeDesc(String eventType, Pageable pageable);
} 