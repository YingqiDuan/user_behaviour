package com.microservice.user_behaviour.query.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import com.microservice.user_behaviour.model.UserBehaviorEntity;
import com.microservice.user_behaviour.model.UserBehaviorEvent;
import com.microservice.user_behaviour.query.repository.UserBehaviorQueryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
@Profile("query")
public class UserBehaviorQueryService {

    private final UserBehaviorQueryRepository repository;
    @Qualifier("queryRedisTemplate")
    private final RedisTemplate<String, Object> redisTemplate;
    
    @Value("${query.cache.ttl:3600}") // 1小时缓存
    private long cacheTtl;
    
    @Value("${query.cache.enable:true}")
    private boolean cacheEnabled;
    
    // 缓存键前缀
    private static final String USER_EVENTS_CACHE_KEY = "query:user:%s:events:%d";
    private static final String USER_SUMMARY_CACHE_KEY = "query:user:%s:summary";
    private static final String TOP_EVENT_TYPES_CACHE_KEY = "query:stats:top_event_types";
    private static final String TOP_ACTIVE_USERS_CACHE_KEY = "query:stats:top_active_users";
    private static final String EVENT_STATS_CACHE_KEY = "query:stats:events:%s";
    
    /**
     * 查询用户最近的行为事件（实现缓存Aside模式）
     */
    @SuppressWarnings("unchecked")
    public List<UserBehaviorEvent> getUserEvents(String userId, int limit) {
        String cacheKey = String.format(USER_EVENTS_CACHE_KEY, userId, limit);
        
        // 1. 先查缓存
        if (cacheEnabled) {
            try {
                List<Object> cachedEvents = redisTemplate.opsForList().range(cacheKey, 0, -1);
                if (cachedEvents != null && !cachedEvents.isEmpty()) {
                    log.info("Cache HIT for user events: userId={}, limit={}", userId, limit);
                    return cachedEvents.stream()
                            .map(obj -> (UserBehaviorEvent) obj)
                            .toList();
                }
            } catch (Exception e) {
                log.warn("Cache read failed for user events: userId={}", userId, e);
            }
        }
        
        // 2. 缓存未命中，查询数据库
        log.info("Cache MISS for user events: userId={}, limit={}", userId, limit);
        Pageable pageable = PageRequest.of(0, limit);
        List<UserBehaviorEntity> entities = repository.findByUserIdOrderByEventTimeDesc(userId, pageable);
        
        // 3. 转换为Event对象
        List<UserBehaviorEvent> events = entities.stream()
                .map(this::convertToEvent)
                .toList();
        
        // 4. 回填缓存
        if (cacheEnabled && !events.isEmpty()) {
            try {
                redisTemplate.delete(cacheKey);
                redisTemplate.opsForList().leftPushAll(cacheKey, events.toArray());
                redisTemplate.expire(cacheKey, cacheTtl, TimeUnit.SECONDS);
                log.debug("Cache updated for user events: userId={}, count={}", userId, events.size());
            } catch (Exception e) {
                log.warn("Cache write failed for user events: userId={}", userId, e);
            }
        }
        
        return events;
    }
    
    /**
     * 查询用户行为概要统计（缓存Aside模式）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getUserSummary(String userId) {
        String cacheKey = String.format(USER_SUMMARY_CACHE_KEY, userId);
        
        // 1. 先查缓存
        if (cacheEnabled) {
            try {
                Map<String, Object> cachedSummary = 
                        (Map<String, Object>) redisTemplate.opsForValue().get(cacheKey);
                if (cachedSummary != null) {
                    log.info("Cache HIT for user summary: userId={}", userId);
                    return cachedSummary;
                }
            } catch (Exception e) {
                log.warn("Cache read failed for user summary: userId={}", userId, e);
            }
        }
        
        // 2. 缓存未命中，查询数据库
        log.info("Cache MISS for user summary: userId={}", userId);
        Map<String, Object> summary = buildUserSummary(userId);
        
        // 3. 回填缓存
        if (cacheEnabled) {
            try {
                redisTemplate.opsForValue().set(cacheKey, summary, cacheTtl, TimeUnit.SECONDS);
                log.debug("Cache updated for user summary: userId={}", userId);
            } catch (Exception e) {
                log.warn("Cache write failed for user summary: userId={}", userId, e);
            }
        }
        
        return summary;
    }
    
    /**
     * 查询热门事件类型排行榜（优先从Redis缓存读取）
     */
    public Set<ZSetOperations.TypedTuple<Object>> getTopEventTypes(int topN) {
        try {
            // 直接从Redis有序集合读取
            Set<ZSetOperations.TypedTuple<Object>> cachedResults = 
                    redisTemplate.opsForZSet().reverseRangeWithScores("stats:event_type_count", 0, topN - 1);
            
            if (cachedResults != null && !cachedResults.isEmpty()) {
                log.info("Retrieved {} top event types from Redis cache", cachedResults.size());
                return cachedResults;
            }
        } catch (Exception e) {
            log.warn("Failed to read top event types from Redis cache", e);
        }
        
        // 如果Redis没有数据，从数据库查询
        log.info("Fallback to database for top event types");
        return getTopEventTypesFromDatabase(topN);
    }
    
    /**
     * 查询最活跃用户排行榜
     */
    public Set<ZSetOperations.TypedTuple<Object>> getTopActiveUsers(int topN) {
        try {
            // 直接从Redis有序集合读取
            Set<ZSetOperations.TypedTuple<Object>> cachedResults = 
                    redisTemplate.opsForZSet().reverseRangeWithScores("stats:user_event_count", 0, topN - 1);
            
            if (cachedResults != null && !cachedResults.isEmpty()) {
                log.info("Retrieved {} top active users from Redis cache", cachedResults.size());
                return cachedResults;
            }
        } catch (Exception e) {
            log.warn("Failed to read top active users from Redis cache", e);
        }
        
        // 如果Redis没有数据，从数据库查询
        log.info("Fallback to database for top active users");
        return getTopActiveUsersFromDatabase(topN);
    }
    
    /**
     * 查询指定时间范围的事件统计
     */
    public Map<String, Object> getEventStatsByTimeRange(LocalDateTime startTime, LocalDateTime endTime, int topN) {
        String period = startTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));
        String cacheKey = String.format(EVENT_STATS_CACHE_KEY, period);
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 尝试从Redis获取
            Set<ZSetOperations.TypedTuple<Object>> cachedStats = 
                    redisTemplate.opsForZSet().reverseRangeWithScores(
                            "stats:hourly:" + period, 0, topN - 1);
            
            if (cachedStats != null && !cachedStats.isEmpty()) {
                result.put("eventStats", cachedStats);
                result.put("source", "cache");
                result.put("period", period);
                log.info("Retrieved event stats from cache for period: {}", period);
                return result;
            }
        } catch (Exception e) {
            log.warn("Failed to read event stats from cache for period: {}", period, e);
        }
        
        // 从数据库查询
        Pageable pageable = PageRequest.of(0, topN);
        List<Object[]> dbResults = repository.findEventTypeStatsByTimeRange(startTime, endTime, pageable);
        
        Map<String, Long> eventTypeStats = dbResults.stream()
                .collect(Collectors.toMap(
                        arr -> (String) arr[0],
                        arr -> (Long) arr[1]
                ));
        
        result.put("eventStats", eventTypeStats);
        result.put("source", "database");
        result.put("timeRange", Map.of("start", startTime, "end", endTime));
        
        return result;
    }
    
    /**
     * 分页查询所有事件
     */
    public Page<UserBehaviorEvent> getAllEvents(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<UserBehaviorEntity> entityPage = repository.findAllByOrderByEventTimeDesc(pageable);
        
        return entityPage.map(this::convertToEvent);
    }
    
    /**
     * 根据事件类型查询
     */
    public Page<UserBehaviorEvent> getEventsByType(String eventType, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<UserBehaviorEntity> entityPage = repository.findByEventTypeOrderByEventTimeDesc(eventType, pageable);
        
        return entityPage.map(this::convertToEvent);
    }
    
    /**
     * 构建用户行为概要
     */
    private Map<String, Object> buildUserSummary(String userId) {
        Map<String, Object> summary = new HashMap<>();
        
        // 查询用户各事件类型统计
        List<Object[]> eventTypeStats = repository.countEventTypesByUserId(userId);
        Map<String, Long> eventTypeCounts = eventTypeStats.stream()
                .collect(Collectors.toMap(
                        arr -> (String) arr[0],
                        arr -> (Long) arr[1]
                ));
        
        // 计算总事件数
        long totalEvents = eventTypeCounts.values().stream().mapToLong(Long::longValue).sum();
        
        // 查询最近30天的事件数
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        LocalDateTime now = LocalDateTime.now();
        Long recentEventCount = repository.countUserEventsByTimeRange(userId, thirtyDaysAgo, now);
        
        summary.put("userId", userId);
        summary.put("totalEvents", totalEvents);
        summary.put("recentEvents30Days", recentEventCount);
        summary.put("eventTypeCounts", eventTypeCounts);
        summary.put("lastUpdated", LocalDateTime.now());
        
        return summary;
    }
    
    /**
     * 从数据库查询热门事件类型
     */
    private Set<ZSetOperations.TypedTuple<Object>> getTopEventTypesFromDatabase(int topN) {
        Pageable pageable = PageRequest.of(0, topN);
        List<Object[]> dbResults = repository.findTopEventTypes(pageable);
        
        return dbResults.stream()
                .map(arr -> ZSetOperations.TypedTuple.of(arr[0], ((Long) arr[1]).doubleValue()))
                .collect(Collectors.toSet());
    }
    
    /**
     * 从数据库查询最活跃用户
     */
    private Set<ZSetOperations.TypedTuple<Object>> getTopActiveUsersFromDatabase(int topN) {
        Pageable pageable = PageRequest.of(0, topN);
        List<Object[]> dbResults = repository.findTopActiveUsers(pageable);
        
        return dbResults.stream()
                .map(arr -> ZSetOperations.TypedTuple.of(arr[0], ((Long) arr[1]).doubleValue()))
                .collect(Collectors.toSet());
    }
    
    /**
     * 将Entity转换为Event
     */
    private UserBehaviorEvent convertToEvent(UserBehaviorEntity entity) {
        return UserBehaviorEvent.builder()
                .userId(entity.getUserId())
                .eventType(entity.getEventType())
                .source(entity.getSource())
                .eventTime(entity.getEventTime())
                .sessionId(entity.getSessionId())
                .deviceInfo(entity.getDeviceInfo())
                .ipAddress(entity.getIpAddress())
                .build();
    }
    
    /**
     * 获取查询服务统计信息
     */
    public Map<String, Object> getQueryServiceStats() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // 检查Redis连接
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            stats.put("redisConnected", "PONG".equals(pong));
            
            // 缓存配置信息
            stats.put("cacheEnabled", cacheEnabled);
            stats.put("cacheTtl", cacheTtl);
            
            // 数据库连接状态（简单检查）
            long totalEvents = repository.count();
            stats.put("totalEventsInDB", totalEvents);
            stats.put("dbConnected", true);
            
        } catch (Exception e) {
            log.error("Error getting query service stats", e);
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }
} 