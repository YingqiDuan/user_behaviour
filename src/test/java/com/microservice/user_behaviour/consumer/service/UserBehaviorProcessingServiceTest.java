package com.microservice.user_behaviour.consumer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservice.user_behaviour.consumer.entity.UserBehaviorEntity;
import com.microservice.user_behaviour.consumer.repository.UserBehaviorRepository;
import com.microservice.user_behaviour.model.UserBehaviorEvent;

@ExtendWith(MockitoExtension.class)
@DisplayName("用户行为处理服务测试")
class UserBehaviorProcessingServiceTest {

    @Mock
    private UserBehaviorRepository repository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CacheService cacheService;

    @InjectMocks
    private UserBehaviorProcessingService processingService;

    private UserBehaviorEvent testEvent;
    private UserBehaviorEntity testEntity;

    @BeforeEach
    void setUp() {
        // Set batchSize to 1 so the queue auto-flushes
        ReflectionTestUtils.setField(processingService, "batchSize", 1);
        
        testEvent = UserBehaviorEvent.builder()
                .userId("user123")
                .eventType("PAGE_VIEW")
                .source("web")
                .sessionId("session123")
                .deviceInfo("Chrome/Windows")
                .eventTime(LocalDateTime.now())
                .build();

        testEntity = UserBehaviorEntity.builder()
                .userId("user123")
                .eventType("PAGE_VIEW")
                .source("web")
                .eventTime(LocalDateTime.now())
                .processedTime(LocalDateTime.now())
                .topic("test-topic")
                .partition(0)
                .offset(100L)
                .build();
    }

    @Test
    @DisplayName("成功处理单个事件")
    void processEvent_ValidEvent_ShouldProcessSuccessfully() throws JsonProcessingException {
        // Given
        String eventDataJson = "{\"key\":\"value\"}";
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn(eventDataJson);

        // When
        processingService.processEvent(testEvent, "test-topic", 0, 100L);

        // Then
        // 验证缓存更新被调用
        verify(cacheService, times(1)).cacheUserRecentEvent(eq(testEvent));
        verify(cacheService, times(1)).updateEventTypeStats(eq("PAGE_VIEW"));
        verify(cacheService, times(1)).updateUserActivityStats(eq("user123"));

        // 验证统计计数器 (queue auto-flushes with batchSize=1)
        assertEquals(1, processingService.getReceivedCount());
        assertEquals(1, processingService.getCachedCount());
        assertEquals(0, processingService.getQueueSize()); // Auto-flushed
        assertEquals(1, processingService.getProcessedCount()); // Auto-flushed
    }

    @Test
    @DisplayName("处理事件时JSON序列化失败")
    void processEvent_JsonProcessingException_ShouldHandleGracefully() throws JsonProcessingException {
        // Given
        lenient().when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("JSON error") {});

        // When
        processingService.processEvent(testEvent, "test-topic", 0, 100L);

        // Then
        // 即使JSON处理失败，缓存更新仍应被调用
        verify(cacheService, times(1)).cacheUserRecentEvent(eq(testEvent));
        assertEquals(1, processingService.getReceivedCount());
        assertEquals(1, processingService.getCachedCount());
    }

    @Test
    @DisplayName("缓存更新失败不影响事件处理")
    void processEvent_CacheFailure_ShouldContinueProcessing() throws JsonProcessingException {
        // Given
        doThrow(new RuntimeException("Cache error"))
                .when(cacheService).cacheUserRecentEvent(any());

        // When
        processingService.processEvent(testEvent, "test-topic", 0, 100L);

        // Then
        // 处理应继续，统计仍应更新
        assertEquals(1, processingService.getReceivedCount());
        assertEquals(0, processingService.getQueueSize()); // Auto-flushed
    }

    @Test
    @DisplayName("批量处理事件")
    void processBatch_ValidEvents_ShouldProcessAll() {
        // Given
        UserBehaviorEvent event1 = UserBehaviorEvent.builder()
                .userId("user1").eventType("PAGE_VIEW").source("web").build();
        UserBehaviorEvent event2 = UserBehaviorEvent.builder()
                .userId("user2").eventType("BUTTON_CLICK").source("mobile").build();
        
        List<UserBehaviorEvent> events = Arrays.asList(event1, event2);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("topic", "test-topic");
        metadata.put("partition", 0);
        metadata.put("offset", 100L);

        // When
        processingService.processBatch(events, metadata);

        // Then
        assertEquals(2, processingService.getReceivedCount());
        assertEquals(0, processingService.getQueueSize()); // Auto-flushed
        assertEquals(2, processingService.getProcessedCount()); // Auto-flushed
    }

    @Test
    @DisplayName("批量处理空事件时间自动设置")
    void processBatch_EventsWithoutTime_ShouldSetCurrentTime() {
        // Given
        UserBehaviorEvent eventWithoutTime = UserBehaviorEvent.builder()
                .userId("user1").eventType("PAGE_VIEW").source("web").build();
        
        List<UserBehaviorEvent> events = Arrays.asList(eventWithoutTime);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("topic", "test-topic");
        metadata.put("partition", 0);
        metadata.put("offset", 100L);

        // When
        processingService.processBatch(events, metadata);

        // Then
        assertNotNull(eventWithoutTime.getEventTime());
        assertEquals(1, processingService.getReceivedCount());
    }

    @Test
    @DisplayName("队列刷新到数据库")
    void flushQueue_WithPendingEvents_ShouldSaveToDatabase() throws JsonProcessingException {
        // Given - Set larger batch size to prevent auto-flush
        ReflectionTestUtils.setField(processingService, "batchSize", 10);
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        
        processingService.processEvent(testEvent, "test-topic", 0, 100L);
        processingService.processEvent(testEvent, "test-topic", 0, 101L);

        // When
        processingService.flushQueue();

        // Then
        ArgumentCaptor<List<UserBehaviorEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository, times(1)).saveAll(captor.capture());
        
        List<UserBehaviorEntity> savedEntities = captor.getValue();
        assertEquals(2, savedEntities.size());
        assertEquals(2, processingService.getProcessedCount());
        assertEquals(0, processingService.getQueueSize()); // 队列应该被清空
    }

    @Test
    @DisplayName("空队列刷新不执行数据库操作")
    void flushQueue_EmptyQueue_ShouldNotCallRepository() {
        // When
        processingService.flushQueue();

        // Then
        verify(repository, never()).saveAll(anyList());
        assertEquals(0, processingService.getProcessedCount());
    }

    @Test
    @DisplayName("获取处理统计信息")
    void getProcessingStats_ShouldReturnCorrectStats() throws JsonProcessingException {
        // Given - Set larger batch size to prevent auto-flush for this test
        ReflectionTestUtils.setField(processingService, "batchSize", 10);
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        
        // 处理一些事件
        processingService.processEvent(testEvent, "test-topic", 0, 100L);
        processingService.processEvent(testEvent, "test-topic", 0, 101L);

        // When
        Map<String, Object> stats = processingService.getProcessingStats();

        // Then
        assertNotNull(stats);
        assertEquals(2, stats.get("queueSize"));
        assertEquals(2, stats.get("receivedCount"));
        assertEquals(0, stats.get("processedCount")); // 未刷新队列
        assertEquals(2, stats.get("cachedCount"));
        assertTrue(((Double) stats.get("cacheHitRate")) > 0);
    }

    @Test
    @DisplayName("处理统计 - 缓存命中率计算")
    void getProcessingStats_CacheHitRateCalculation_ShouldBeCorrect() throws JsonProcessingException {
        // Given - Set larger batch size to prevent auto-flush
        ReflectionTestUtils.setField(processingService, "batchSize", 20);
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        
        // 处理10个事件，其中5个缓存成功
        for (int i = 0; i < 5; i++) {
            processingService.processEvent(testEvent, "test-topic", 0, i);
        }
        
        // 模拟5个事件缓存失败
        doThrow(new RuntimeException("Cache error"))
                .when(cacheService).cacheUserRecentEvent(any());
        
        for (int i = 5; i < 10; i++) {
            processingService.processEvent(testEvent, "test-topic", 0, i);
        }

        // When
        Map<String, Object> stats = processingService.getProcessingStats();

        // Then
        assertEquals(10, stats.get("receivedCount"));
        assertEquals(5, stats.get("cachedCount"));
        assertEquals(50.0, (Double) stats.get("cacheHitRate"), 0.01);
    }

    @Test
    @DisplayName("处理包含复杂事件数据的事件")
    void processEvent_WithComplexEventData_ShouldSerializeCorrectly() throws JsonProcessingException {
        // Given
        Map<String, Object> complexData = new HashMap<>();
        complexData.put("page", "/product/123");
        complexData.put("duration", 5000);
        complexData.put("actions", Arrays.asList("scroll", "click", "hover"));
        
        testEvent.setEventData(complexData);
        
        String expectedJson = "{\"page\":\"/product/123\",\"duration\":5000,\"actions\":[\"scroll\",\"click\",\"hover\"]}";
        lenient().when(objectMapper.writeValueAsString(eq(complexData))).thenReturn(expectedJson);

        // When
        processingService.processEvent(testEvent, "test-topic", 0, 100L);

        // Then
        verify(objectMapper, times(1)).writeValueAsString(eq(complexData));
        assertEquals(1, processingService.getReceivedCount());
        assertEquals(0, processingService.getQueueSize()); // Auto-flushed
    }
} 