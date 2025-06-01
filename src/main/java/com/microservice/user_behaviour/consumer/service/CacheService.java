package com.microservice.user_behaviour.consumer.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import com.microservice.user_behaviour.model.UserBehaviorEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
@Profile("consumer")
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    
    @Value("${cache.user.recent.events.size:100}")
    private int recentEventsSize;
    
    @Value("${cache.user.recent.events.ttl:86400}")
    private long recentEventsTtl; // 24小时
    
    @Value("${cache.stats.ttl:3600}")
    private long statsTtl; // 1小时
    
    private static final String USER_RECENT_EVENTS_KEY = "user:%s:recent_events";
    private static final String EVENT_TYPE_COUNT_KEY = "stats:event_type_count";
    private static final String TOP_EVENTS_KEY = "stats:top_events";
    private static final String USER_EVENT_COUNT_KEY = "stats:user_event_count";
    private static final String HOURLY_STATS_KEY = "stats:hourly:%s";
    private static final String DAILY_STATS_KEY = "stats:daily:%s";
    
    /**
     * 缓存用户最近的行为事件
     */
    public void cacheUserRecentEvent(UserBehaviorEvent event) {
        try {
            String key = String.format(USER_RECENT_EVENTS_KEY, event.getUserId());
            
            // 将事件添加到列表头部
            redisTemplate.opsForList().leftPush(key, event);
            
            // 保持列表长度在指定范围内
            redisTemplate.opsForList().trim(key, 0, recentEventsSize - 1);
            
            // 设置过期时间
            redisTemplate.expire(key, recentEventsTtl, TimeUnit.SECONDS);
            
            log.debug("Cached recent event for user: {}, event type: {}", 
                    event.getUserId(), event.getEventType());
            
        } catch (Exception e) {
            log.error("Error caching user recent event for user: {}", 
                    event.getUserId(), e);
        }
    }
    
    /**
     * 获取用户最近的行为事件
     */
    @SuppressWarnings("unchecked")
    public List<UserBehaviorEvent> getUserRecentEvents(String userId, int limit) {
        try {
            String key = String.format(USER_RECENT_EVENTS_KEY, userId);
            List<Object> events = redisTemplate.opsForList().range(key, 0, limit - 1);
            
            if (events != null) {
                log.debug("Retrieved {} recent events from cache for user: {}", 
                        events.size(), userId);
                return events.stream()
                        .map(obj -> (UserBehaviorEvent) obj)
                        .toList();
            }
            
        } catch (Exception e) {
            log.error("Error retrieving user recent events for user: {}", userId, e);
        }
        
        return List.of();
    }
    
    /**
     * 更新事件类型统计
     */
    public void updateEventTypeStats(String eventType) {
        try {
            // 更新总体事件类型计数
            redisTemplate.opsForZSet().incrementScore(EVENT_TYPE_COUNT_KEY, eventType, 1);
            
            // 更新小时级别统计
            String currentHour = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));
            String hourlyKey = String.format(HOURLY_STATS_KEY, currentHour);
            redisTemplate.opsForZSet().incrementScore(hourlyKey, eventType, 1);
            redisTemplate.expire(hourlyKey, 24 * 60 * 60, TimeUnit.SECONDS); // 24小时过期
            
            // 更新日级别统计
            String currentDay = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String dailyKey = String.format(DAILY_STATS_KEY, currentDay);
            redisTemplate.opsForZSet().incrementScore(dailyKey, eventType, 1);
            redisTemplate.expire(dailyKey, 7 * 24 * 60 * 60, TimeUnit.SECONDS); // 7天过期
            
            log.debug("Updated event type stats for: {}", eventType);
            
        } catch (Exception e) {
            log.error("Error updating event type stats for: {}", eventType, e);
        }
    }
    
    /**
     * 更新用户活跃度统计
     */
    public void updateUserActivityStats(String userId) {
        try {
            redisTemplate.opsForZSet().incrementScore(USER_EVENT_COUNT_KEY, userId, 1);
            log.debug("Updated user activity stats for: {}", userId);
            
        } catch (Exception e) {
            log.error("Error updating user activity stats for: {}", userId, e);
        }
    }
    
    /**
     * 获取热门事件类型排行榜
     */
    public Set<ZSetOperations.TypedTuple<Object>> getTopEventTypes(int topN) {
        try {
            Set<ZSetOperations.TypedTuple<Object>> topEvents = 
                    redisTemplate.opsForZSet().reverseRangeWithScores(EVENT_TYPE_COUNT_KEY, 0, topN - 1);
            
            log.debug("Retrieved top {} event types from cache", topN);
            return topEvents;
            
        } catch (Exception e) {
            log.error("Error retrieving top event types", e);
            return Set.of();
        }
    }
    
    /**
     * 获取最活跃用户排行榜
     */
    public Set<ZSetOperations.TypedTuple<Object>> getTopActiveUsers(int topN) {
        try {
            Set<ZSetOperations.TypedTuple<Object>> topUsers = 
                    redisTemplate.opsForZSet().reverseRangeWithScores(USER_EVENT_COUNT_KEY, 0, topN - 1);
            
            log.debug("Retrieved top {} active users from cache", topN);
            return topUsers;
            
        } catch (Exception e) {
            log.error("Error retrieving top active users", e);
            return Set.of();
        }
    }
    
    /**
     * 获取指定时间段的事件统计
     */
    public Set<ZSetOperations.TypedTuple<Object>> getEventStatsForPeriod(String period, int topN) {
        try {
            String key;
            if (period.length() == 13) { // 小时格式: yyyy-MM-dd-HH
                key = String.format(HOURLY_STATS_KEY, period);
            } else { // 日格式: yyyy-MM-dd
                key = String.format(DAILY_STATS_KEY, period);
            }
            
            Set<ZSetOperations.TypedTuple<Object>> stats = 
                    redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, topN - 1);
            
            log.debug("Retrieved event stats for period {} from cache", period);
            return stats;
            
        } catch (Exception e) {
            log.error("Error retrieving event stats for period: {}", period, e);
            return Set.of();
        }
    }
    
    /**
     * 缓存热门数据到专门的key中
     */
    public void cacheTopEvents(Set<ZSetOperations.TypedTuple<Object>> topEvents) {
        try {
            // 清空旧数据
            redisTemplate.delete(TOP_EVENTS_KEY);
            
            // 添加新数据
            for (ZSetOperations.TypedTuple<Object> event : topEvents) {
                redisTemplate.opsForZSet().add(TOP_EVENTS_KEY, 
                        event.getValue(), event.getScore());
            }
            
            // 设置过期时间
            redisTemplate.expire(TOP_EVENTS_KEY, statsTtl, TimeUnit.SECONDS);
            
            log.info("Cached {} top events", topEvents.size());
            
        } catch (Exception e) {
            log.error("Error caching top events", e);
        }
    }
    
    /**
     * 获取缓存的热门事件
     */
    public Set<ZSetOperations.TypedTuple<Object>> getCachedTopEvents() {
        try {
            Set<ZSetOperations.TypedTuple<Object>> topEvents = 
                    redisTemplate.opsForZSet().reverseRangeWithScores(TOP_EVENTS_KEY, 0, -1);
            
            log.debug("Retrieved {} cached top events", 
                    topEvents != null ? topEvents.size() : 0);
            return topEvents;
            
        } catch (Exception e) {
            log.error("Error retrieving cached top events", e);
            return Set.of();
        }
    }
    
    /**
     * 检查Redis连接状态
     */
    public boolean isRedisConnected() {
        try {
            String result = redisTemplate.getConnectionFactory()
                    .getConnection().ping();
            return "PONG".equals(result);
        } catch (Exception e) {
            log.error("Redis connection check failed", e);
            return false;
        }
    }
    
    /**
     * 获取缓存统计信息
     */
    public String getCacheStats() {
        try {
            StringBuilder stats = new StringBuilder();
            stats.append("Redis Cache Statistics:\n");
            stats.append("Event Type Count Keys: ")
                 .append(redisTemplate.opsForZSet().count(EVENT_TYPE_COUNT_KEY, 0, Double.MAX_VALUE))
                 .append("\n");
            stats.append("User Activity Count Keys: ")
                 .append(redisTemplate.opsForZSet().count(USER_EVENT_COUNT_KEY, 0, Double.MAX_VALUE))
                 .append("\n");
            stats.append("Top Events Cached: ")
                 .append(redisTemplate.opsForZSet().count(TOP_EVENTS_KEY, 0, Double.MAX_VALUE))
                 .append("\n");
            
            return stats.toString();
        } catch (Exception e) {
            log.error("Error getting cache stats", e);
            return "Error retrieving cache statistics";
        }
    }
} 