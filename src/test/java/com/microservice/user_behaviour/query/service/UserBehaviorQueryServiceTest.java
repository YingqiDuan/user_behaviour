package com.microservice.user_behaviour.query.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import com.microservice.user_behaviour.model.UserBehaviorEntity;
import com.microservice.user_behaviour.model.UserBehaviorEvent;
import com.microservice.user_behaviour.query.repository.UserBehaviorQueryRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("用户行为查询服务测试")
class UserBehaviorQueryServiceTest {

    @Mock
    private UserBehaviorQueryRepository repository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ListOperations<String, Object> listOperations;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    @InjectMocks
    private UserBehaviorQueryService queryService;

    private UserBehaviorEntity testEntity;
    private UserBehaviorEvent testEvent;
    private List<UserBehaviorEntity> testEntities;

    @BeforeEach
    void setUp() {
        // 设置测试配置
        ReflectionTestUtils.setField(queryService, "cacheTtl", 3600L);
        ReflectionTestUtils.setField(queryService, "cacheEnabled", true);

        // 准备测试数据
        testEntity = UserBehaviorEntity.builder()
                .id(1L)
                .userId("user123")
                .eventType("PAGE_VIEW")
                .source("web")
                .eventTime(LocalDateTime.now())
                .processedTime(LocalDateTime.now())
                .build();

        testEvent = UserBehaviorEvent.builder()
                .userId("user123")
                .eventType("PAGE_VIEW")
                .source("web")
                .eventTime(LocalDateTime.now())
                .build();

        testEntities = Arrays.asList(testEntity);

        // Use lenient mocking for Redis operations to avoid unnecessary stubbing errors
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @Test
    @DisplayName("获取用户事件 - 缓存命中")
    void getUserEvents_CacheHit_ShouldReturnFromCache() {
        // Given
        String cacheKey = "query:user:user123:events:10";
        List<Object> cachedEvents = Arrays.asList(testEvent);
        
        when(listOperations.range(eq(cacheKey), eq(0L), eq(-1L)))
                .thenReturn(cachedEvents);

        // When
        List<UserBehaviorEvent> result = queryService.getUserEvents("user123", 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("user123", result.get(0).getUserId());
        
        // 验证只查询了缓存，没有查询数据库
        verify(listOperations, times(1)).range(eq(cacheKey), eq(0L), eq(-1L));
        verify(repository, never()).findByUserIdOrderByEventTimeDesc(anyString(), any(Pageable.class));
    }

    @Test
    @DisplayName("获取用户事件 - 缓存未命中")
    void getUserEvents_CacheMiss_ShouldQueryDatabaseAndUpdateCache() {
        // Given
        String cacheKey = "query:user:user123:events:10";
        
        // 缓存未命中
        when(listOperations.range(eq(cacheKey), eq(0L), eq(-1L)))
                .thenReturn(null);
        
        // 数据库查询返回结果
        when(repository.findByUserIdOrderByEventTimeDesc(eq("user123"), any(Pageable.class)))
                .thenReturn(testEntities);

        // When
        List<UserBehaviorEvent> result = queryService.getUserEvents("user123", 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("user123", result.get(0).getUserId());
        
        // 验证查询了数据库
        verify(repository, times(1)).findByUserIdOrderByEventTimeDesc(eq("user123"), any(Pageable.class));
        
        // 验证更新了缓存 (delete和expire足以证明缓存被更新)
        verify(redisTemplate, times(1)).delete(eq(cacheKey));
        verify(redisTemplate, times(1)).expire(eq(cacheKey), eq(3600L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("获取用户事件 - 缓存读取失败回退到数据库")
    void getUserEvents_CacheReadFailure_ShouldFallbackToDatabase() {
        // Given
        String cacheKey = "query:user:user123:events:10";
        
        // 缓存读取失败
        when(listOperations.range(eq(cacheKey), eq(0L), eq(-1L)))
                .thenThrow(new RuntimeException("Redis connection error"));
        
        // 数据库查询返回结果
        when(repository.findByUserIdOrderByEventTimeDesc(eq("user123"), any(Pageable.class)))
                .thenReturn(testEntities);

        // When
        List<UserBehaviorEvent> result = queryService.getUserEvents("user123", 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        
        // 验证查询了数据库
        verify(repository, times(1)).findByUserIdOrderByEventTimeDesc(eq("user123"), any(Pageable.class));
    }

    @Test
    @DisplayName("获取用户事件 - 缓存禁用时直接查询数据库")
    void getUserEvents_CacheDisabled_ShouldQueryDatabaseDirectly() {
        // Given
        ReflectionTestUtils.setField(queryService, "cacheEnabled", false);
        
        when(repository.findByUserIdOrderByEventTimeDesc(eq("user123"), any(Pageable.class)))
                .thenReturn(testEntities);

        // When
        List<UserBehaviorEvent> result = queryService.getUserEvents("user123", 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        
        // 验证直接查询了数据库，没有访问缓存
        verify(repository, times(1)).findByUserIdOrderByEventTimeDesc(eq("user123"), any(Pageable.class));
        verify(listOperations, never()).range(anyString(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("获取热门事件类型 - 从Redis缓存读取")
    void getTopEventTypes_FromRedisCache_ShouldReturnCachedResults() {
        // Given
        Set<ZSetOperations.TypedTuple<Object>> cachedResults = Set.of(
                ZSetOperations.TypedTuple.of("PAGE_VIEW", 100.0),
                ZSetOperations.TypedTuple.of("BUTTON_CLICK", 80.0)
        );
        
        when(zSetOperations.reverseRangeWithScores(eq("stats:event_type_count"), eq(0L), eq(9L)))
                .thenReturn(cachedResults);

        // When
        Set<ZSetOperations.TypedTuple<Object>> result = queryService.getTopEventTypes(10);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        
        // 验证从Redis读取
        verify(zSetOperations, times(1)).reverseRangeWithScores(eq("stats:event_type_count"), eq(0L), eq(9L));
    }

    @Test
    @DisplayName("获取最活跃用户 - 从Redis缓存读取")
    void getTopActiveUsers_FromRedisCache_ShouldReturnCachedResults() {
        // Given
        Set<ZSetOperations.TypedTuple<Object>> cachedResults = Set.of(
                ZSetOperations.TypedTuple.of("user123", 50.0),
                ZSetOperations.TypedTuple.of("user456", 30.0)
        );
        
        when(zSetOperations.reverseRangeWithScores(eq("stats:user_event_count"), eq(0L), eq(9L)))
                .thenReturn(cachedResults);

        // When
        Set<ZSetOperations.TypedTuple<Object>> result = queryService.getTopActiveUsers(10);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        
        // 验证从Redis读取
        verify(zSetOperations, times(1)).reverseRangeWithScores(eq("stats:user_event_count"), eq(0L), eq(9L));
    }

    @Test
    @DisplayName("缓存写入失败不影响查询结果")
    void getUserEvents_CacheWriteFailure_ShouldStillReturnResults() {
        // Given
        String cacheKey = "query:user:user123:events:10";
        
        // 缓存未命中
        when(listOperations.range(eq(cacheKey), eq(0L), eq(-1L)))
                .thenReturn(null);
        
        // 数据库查询返回结果
        when(repository.findByUserIdOrderByEventTimeDesc(eq("user123"), any(Pageable.class)))
                .thenReturn(testEntities);
        
        // 缓存写入失败
        when(redisTemplate.delete(eq(cacheKey)))
                .thenThrow(new RuntimeException("Cache write error"));

        // When
        List<UserBehaviorEvent> result = queryService.getUserEvents("user123", 10);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        
        // 验证仍然查询了数据库并返回结果
        verify(repository, times(1)).findByUserIdOrderByEventTimeDesc(eq("user123"), any(Pageable.class));
    }

    @Test
    @DisplayName("空结果不缓存")
    void getUserEvents_EmptyResult_ShouldNotCache() {
        // Given
        String cacheKey = "query:user:user123:events:10";
        
        // 缓存未命中
        when(listOperations.range(eq(cacheKey), eq(0L), eq(-1L)))
                .thenReturn(null);
        
        // 数据库查询返回空结果
        when(repository.findByUserIdOrderByEventTimeDesc(eq("user123"), any(Pageable.class)))
                .thenReturn(Arrays.asList());

        // When
        List<UserBehaviorEvent> result = queryService.getUserEvents("user123", 10);

        // Then
        assertNotNull(result);
        assertTrue(result.isEmpty());
        
        // 验证空结果不会更新缓存 (检查不会调用delete说明没有尝试缓存)
        verify(redisTemplate, never()).delete(eq(cacheKey));
    }
} 