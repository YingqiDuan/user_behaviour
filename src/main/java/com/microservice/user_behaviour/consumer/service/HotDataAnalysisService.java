package com.microservice.user_behaviour.consumer.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
@Profile("consumer")
public class HotDataAnalysisService {

    private final CacheService cacheService;
    
    @Value("${hotdata.analysis.top.events:10}")
    private int topEventsCount;
    
    @Value("${hotdata.analysis.top.users:20}")
    private int topUsersCount;
    
    @Value("${hotdata.analysis.threshold.hot:100}")
    private double hotThreshold; // 热点阈值
    
    @Value("${hotdata.analysis.threshold.trending:50}")
    private double trendingThreshold; // 趋势阈值
    
    /**
     * 定时分析热点数据 - 每5分钟执行一次
     */
    @Scheduled(fixedRate = 300000) // 5分钟
    public void analyzeHotData() {
        log.info("Starting hot data analysis...");
        
        try {
            // 分析热门事件类型
            analyzeHotEventTypes();
            
            // 分析活跃用户
            analyzeActiveUsers();
            
            // 分析趋势数据
            analyzeTrendingData();
            
            log.info("Hot data analysis completed successfully");
            
        } catch (Exception e) {
            log.error("Error during hot data analysis", e);
        }
    }
    
    /**
     * 分析热门事件类型
     */
    private void analyzeHotEventTypes() {
        Set<ZSetOperations.TypedTuple<Object>> topEvents = 
                cacheService.getTopEventTypes(topEventsCount);
        
        if (!topEvents.isEmpty()) {
            log.info("Top {} event types:", topEventsCount);
            topEvents.forEach(event -> {
                String eventType = (String) event.getValue();
                Double score = event.getScore();
                String hotLevel = getHotLevel(score);
                
                log.info("Event Type: {}, Count: {}, Hot Level: {}", 
                        eventType, score.intValue(), hotLevel);
            });
            
            // 缓存热门事件数据
            cacheService.cacheTopEvents(topEvents);
        }
    }
    
    /**
     * 分析活跃用户
     */
    private void analyzeActiveUsers() {
        Set<ZSetOperations.TypedTuple<Object>> topUsers = 
                cacheService.getTopActiveUsers(topUsersCount);
        
        if (!topUsers.isEmpty()) {
            log.info("Top {} active users:", topUsersCount);
            topUsers.forEach(user -> {
                String userId = (String) user.getValue();
                Double score = user.getScore();
                String activityLevel = getActivityLevel(score);
                
                log.info("User ID: {}, Event Count: {}, Activity Level: {}", 
                        userId, score.intValue(), activityLevel);
            });
        }
    }
    
    /**
     * 分析趋势数据
     */
    private void analyzeTrendingData() {
        String currentHour = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));
        String previousHour = LocalDateTime.now().minusHours(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));
        
        Set<ZSetOperations.TypedTuple<Object>> currentHourStats = 
                cacheService.getEventStatsForPeriod(currentHour, topEventsCount);
        Set<ZSetOperations.TypedTuple<Object>> previousHourStats = 
                cacheService.getEventStatsForPeriod(previousHour, topEventsCount);
        
        Map<String, Double> currentMap = currentHourStats.stream()
                .collect(Collectors.toMap(
                        tuple -> (String) tuple.getValue(),
                        ZSetOperations.TypedTuple::getScore
                ));
        
        Map<String, Double> previousMap = previousHourStats.stream()
                .collect(Collectors.toMap(
                        tuple -> (String) tuple.getValue(),
                        ZSetOperations.TypedTuple::getScore
                ));
        
        log.info("Trending analysis for current hour vs previous hour:");
        currentMap.forEach((eventType, currentCount) -> {
            Double previousCount = previousMap.getOrDefault(eventType, 0.0);
            double growthRate = previousCount > 0 ? 
                    ((currentCount - previousCount) / previousCount) * 100 : 100.0;
            
            if (Math.abs(growthRate) > 20) { // 增长率超过20%才记录
                log.info("Event Type: {}, Current: {}, Previous: {}, Growth: {:.2f}%", 
                        eventType, currentCount.intValue(), previousCount.intValue(), growthRate);
            }
        });
    }
    
    /**
     * 获取热度等级
     */
    private String getHotLevel(Double score) {
        if (score >= hotThreshold) {
            return "HOT";
        } else if (score >= trendingThreshold) {
            return "TRENDING";
        } else {
            return "NORMAL";
        }
    }
    
    /**
     * 获取活跃度等级
     */
    private String getActivityLevel(Double score) {
        if (score >= 1000) {
            return "VERY_HIGH";
        } else if (score >= 500) {
            return "HIGH";
        } else if (score >= 100) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
    
    /**
     * 获取实时热点数据报告
     */
    public Map<String, Object> getHotDataReport() {
        Map<String, Object> report = new HashMap<>();
        
        try {
            // 获取热门事件类型
            Set<ZSetOperations.TypedTuple<Object>> topEvents = 
                    cacheService.getTopEventTypes(topEventsCount);
            report.put("topEventTypes", topEvents);
            
            // 获取活跃用户
            Set<ZSetOperations.TypedTuple<Object>> topUsers = 
                    cacheService.getTopActiveUsers(topUsersCount);
            report.put("topActiveUsers", topUsers);
            
            // 获取当前小时统计
            String currentHour = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH"));
            Set<ZSetOperations.TypedTuple<Object>> hourlyStats = 
                    cacheService.getEventStatsForPeriod(currentHour, topEventsCount);
            report.put("currentHourStats", hourlyStats);
            
            // 获取今日统计
            String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            Set<ZSetOperations.TypedTuple<Object>> dailyStats = 
                    cacheService.getEventStatsForPeriod(today, topEventsCount);
            report.put("todayStats", dailyStats);
            
            // 添加缓存统计信息
            report.put("cacheStats", cacheService.getCacheStats());
            report.put("redisConnected", cacheService.isRedisConnected());
            report.put("reportTime", LocalDateTime.now());
            
        } catch (Exception e) {
            log.error("Error generating hot data report", e);
            report.put("error", "Failed to generate report: " + e.getMessage());
        }
        
        return report;
    }
    
    /**
     * 获取用户行为热度分析
     */
    public Map<String, Object> getUserBehaviorHeatMap(String userId) {
        Map<String, Object> heatMap = new HashMap<>();
        
        try {
            // 获取用户最近事件
            List<com.microservice.user_behaviour.model.UserBehaviorEvent> recentEvents = 
                    cacheService.getUserRecentEvents(userId, 50);
            
            // 分析事件类型分布
            Map<String, Long> eventTypeDistribution = recentEvents.stream()
                    .collect(Collectors.groupingBy(
                            com.microservice.user_behaviour.model.UserBehaviorEvent::getEventType,
                            Collectors.counting()
                    ));
            
            heatMap.put("userId", userId);
            heatMap.put("recentEventCount", recentEvents.size());
            heatMap.put("eventTypeDistribution", eventTypeDistribution);
            heatMap.put("analysisTime", LocalDateTime.now());
            
            // 计算用户活跃度评分
            double activityScore = calculateUserActivityScore(recentEvents);
            heatMap.put("activityScore", activityScore);
            heatMap.put("activityLevel", getActivityLevel(activityScore));
            
        } catch (Exception e) {
            log.error("Error generating user behavior heat map for user: {}", userId, e);
            heatMap.put("error", "Failed to generate heat map: " + e.getMessage());
        }
        
        return heatMap;
    }
    
    /**
     * 计算用户活跃度评分
     */
    private double calculateUserActivityScore(List<com.microservice.user_behaviour.model.UserBehaviorEvent> events) {
        if (events.isEmpty()) {
            return 0.0;
        }
        
        // 基于事件数量和类型多样性计算评分
        double baseScore = events.size();
        
        // 事件类型多样性加分
        long uniqueEventTypes = events.stream()
                .map(com.microservice.user_behaviour.model.UserBehaviorEvent::getEventType)
                .distinct()
                .count();
        
        double diversityBonus = uniqueEventTypes * 10;
        
        return baseScore + diversityBonus;
    }
} 