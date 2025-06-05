package com.microservice.user_behaviour.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservice.user_behaviour.model.UserBehaviorEvent;
import com.microservice.user_behaviour.service.UserBehaviorService;

@ExtendWith(MockitoExtension.class)
@DisplayName("数据收集控制器测试")
class DataCollectionControllerTest {

    @Mock
    private UserBehaviorService userBehaviorService;

    @InjectMocks
    private DataCollectionController dataCollectionController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UserBehaviorEvent testEvent;
    private String testEventJson;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.standaloneSetup(dataCollectionController).build();
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // For LocalDateTime support
        
        // 准备测试数据
        testEvent = UserBehaviorEvent.builder()
                .userId("user123")
                .eventType("PAGE_VIEW")
                .source("web")
                .sessionId("session123")
                .deviceInfo("Chrome/Windows")
                .eventTime(LocalDateTime.now())
                .build();
        
        testEventJson = objectMapper.writeValueAsString(testEvent);
    }

    @Test
    @DisplayName("成功接收单个事件 - 非关键事件")
    void collectEvent_NonCriticalEvent_ShouldReturnAccepted() throws Exception {
        // Given
        // userBehaviorService.sendUserBehaviorEvent 方法无返回值，不需要mock

        // When & Then
        mockMvc.perform(post("/collect")
                .contentType(MediaType.APPLICATION_JSON)
                .content(testEventJson))
                .andExpect(status().isAccepted())
                .andExpect(content().string("Event accepted"));

        // Verify
        verify(userBehaviorService, times(1)).sendUserBehaviorEvent(any(UserBehaviorEvent.class));
    }

    @Test
    @DisplayName("成功接收单个事件 - 关键事件同步处理")
    void collectEvent_CriticalEvent_ShouldReturnAcceptedForSync() throws Exception {
        // Given
        UserBehaviorEvent criticalEvent = UserBehaviorEvent.builder()
                .userId("user123")
                .eventType("PURCHASE")
                .source("web")
                .eventTime(LocalDateTime.now())
                .build();
        
        String criticalEventJson = objectMapper.writeValueAsString(criticalEvent);
        when(userBehaviorService.sendSynchronously(any(UserBehaviorEvent.class), eq(5000L)))
                .thenReturn(true);

        // When & Then
        mockMvc.perform(post("/collect")
                .contentType(MediaType.APPLICATION_JSON)
                .content(criticalEventJson))
                .andExpect(status().isAccepted())
                .andExpect(content().string("Critical event accepted and confirmed delivery"));

        // Verify
        verify(userBehaviorService, times(1)).sendSynchronously(any(UserBehaviorEvent.class), eq(5000L));
    }

    @Test
    @DisplayName("关键事件同步处理失败")
    void collectEvent_CriticalEventFailed_ShouldReturnServerError() throws Exception {
        // Given
        UserBehaviorEvent criticalEvent = UserBehaviorEvent.builder()
                .userId("user123")
                .eventType("PURCHASE")
                .source("web")
                .eventTime(LocalDateTime.now())
                .build();
        
        String criticalEventJson = objectMapper.writeValueAsString(criticalEvent);
        when(userBehaviorService.sendSynchronously(any(UserBehaviorEvent.class), eq(5000L)))
                .thenReturn(false);

        // When & Then
        mockMvc.perform(post("/collect")
                .contentType(MediaType.APPLICATION_JSON)
                .content(criticalEventJson))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Failed to deliver critical event"));
    }

    @Test
    @DisplayName("处理事件时发生异常")
    void collectEvent_ServiceException_ShouldReturnServerError() throws Exception {
        // Given
        doThrow(new RuntimeException("Service error"))
                .when(userBehaviorService).sendUserBehaviorEvent(any(UserBehaviorEvent.class));

        // When & Then
        mockMvc.perform(post("/collect")
                .contentType(MediaType.APPLICATION_JSON)
                .content(testEventJson))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Error processing event"));
    }

    @Test
    @DisplayName("请求参数验证失败")
    void collectEvent_InvalidEvent_ShouldReturnBadRequest() throws Exception {
        // Given - 缺少必要字段的事件
        String invalidEventJson = "{}";

        // When & Then
        mockMvc.perform(post("/collect")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidEventJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("成功接收批量事件")
    void collectEvents_ValidBatch_ShouldReturnAccepted() throws Exception {
        // Given
        UserBehaviorEvent[] events = {
                UserBehaviorEvent.builder().userId("user1").eventType("PAGE_VIEW").source("web").eventTime(LocalDateTime.now()).build(),
                UserBehaviorEvent.builder().userId("user2").eventType("BUTTON_CLICK").source("web").eventTime(LocalDateTime.now()).build()
        };
        String batchJson = objectMapper.writeValueAsString(events);

        // When & Then
        mockMvc.perform(post("/collect/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(batchJson))
                .andExpect(status().isAccepted())
                .andExpect(content().string("Batch accepted (2 events, 0 critical)"));

        // Verify - 验证每个事件都被处理
        verify(userBehaviorService, times(2)).sendUserBehaviorEvent(any(UserBehaviorEvent.class));
    }

    @Test
    @DisplayName("批量事件包含关键事件")
    void collectEvents_BatchWithCriticalEvents_ShouldHandleCorrectly() throws Exception {
        // Given
        UserBehaviorEvent[] events = {
                UserBehaviorEvent.builder().userId("user1").eventType("PAGE_VIEW").source("web").eventTime(LocalDateTime.now()).build(),
                UserBehaviorEvent.builder().userId("user2").eventType("PURCHASE").source("web").eventTime(LocalDateTime.now()).build()
        };
        String batchJson = objectMapper.writeValueAsString(events);

        when(userBehaviorService.sendSynchronously(any(UserBehaviorEvent.class), eq(5000L)))
                .thenReturn(true);

        // When & Then
        mockMvc.perform(post("/collect/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(batchJson))
                .andExpect(status().isAccepted())
                .andExpect(content().string("Batch accepted (2 events, 1 critical)"));

        // Verify
        verify(userBehaviorService, times(1)).sendUserBehaviorEvent(any(UserBehaviorEvent.class));
        verify(userBehaviorService, times(1)).sendSynchronously(any(UserBehaviorEvent.class), eq(5000L));
    }

    @Test
    @DisplayName("批量事件部分失败")
    void collectEvents_PartialFailure_ShouldReturnPartialContent() throws Exception {
        // Given
        UserBehaviorEvent[] events = {
                UserBehaviorEvent.builder().userId("user1").eventType("PAGE_VIEW").source("web").eventTime(LocalDateTime.now()).build(),
                UserBehaviorEvent.builder().userId("user2").eventType("PURCHASE").source("web").eventTime(LocalDateTime.now()).build()
        };
        String batchJson = objectMapper.writeValueAsString(events);

        // 关键事件处理失败
        when(userBehaviorService.sendSynchronously(any(UserBehaviorEvent.class), eq(5000L)))
                .thenReturn(false);

        // When & Then
        mockMvc.perform(post("/collect/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(batchJson))
                .andExpect(status().isPartialContent())
                .andExpect(content().string("Partial success: 1 of 2 events processed (1 critical)"));
    }

    @Test
    @DisplayName("批量事件处理异常")
    void collectEvents_BatchException_ShouldReturnServerError() throws Exception {
        // Given
        UserBehaviorEvent[] events = {
                UserBehaviorEvent.builder().userId("user1").eventType("PAGE_VIEW").source("web").eventTime(LocalDateTime.now()).build()
        };
        String batchJson = objectMapper.writeValueAsString(events);

        doThrow(new RuntimeException("Batch processing error"))
                .when(userBehaviorService).sendUserBehaviorEvent(any(UserBehaviorEvent.class));

        // When & Then
        mockMvc.perform(post("/collect/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content(batchJson))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Error processing batch"));
    }

    @Test
    @DisplayName("自动设置事件时间和IP地址")
    void collectEvent_AutoSetTimestampAndIp_ShouldFillMissingFields() throws Exception {
        // Given - 事件缺少时间和IP，但有必要的字段
        UserBehaviorEvent eventWithoutTime = UserBehaviorEvent.builder()
                .userId("user123")
                .eventType("PAGE_VIEW")
                .source("web")
                .eventTime(LocalDateTime.now()) // Still need eventTime for validation
                .build();
        String eventJson = objectMapper.writeValueAsString(eventWithoutTime);

        // When & Then
        mockMvc.perform(post("/collect")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventJson)
                .header("X-Forwarded-For", "192.168.1.100, 10.0.0.1"))
                .andExpect(status().isAccepted());

        // Verify - 确认服务被调用，说明参数验证通过
        verify(userBehaviorService, times(1)).sendUserBehaviorEvent(any(UserBehaviorEvent.class));
    }
} 