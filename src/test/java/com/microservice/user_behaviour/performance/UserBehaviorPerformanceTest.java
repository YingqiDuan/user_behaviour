package com.microservice.user_behaviour.performance;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservice.user_behaviour.model.UserBehaviorEvent;

import lombok.extern.slf4j.Slf4j;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {
        com.microservice.user_behaviour.controller.DataCollectionController.class,
        com.microservice.user_behaviour.service.UserBehaviorService.class,
        com.microservice.user_behaviour.util.MetricsUtil.class,
        com.microservice.user_behaviour.config.KafkaTopicConfig.class,
        com.microservice.user_behaviour.config.PerformanceTestConfig.class
    },
    properties = {
        "spring.autoconfigure.exclude=" +
        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
        "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration," +
        "org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration"
    }
)
@ActiveProfiles("performance-test")
@DisplayName("用户行为系统性能测试")
@Disabled("Performance tests require Kafka and full infrastructure setup. Run manually in appropriate environment.")
@Slf4j
class UserBehaviorPerformanceTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
    }

    @Test
    @DisplayName("单个事件API并发性能测试")
    void concurrentSingleEventTest() throws Exception {
        // Given
        int concurrentUsers = 50;
        int requestsPerUser = 20;
        int totalRequests = concurrentUsers * requestsPerUser;
        
        ExecutorService executor = Executors.newFixedThreadPool(concurrentUsers);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // When
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < concurrentUsers; i++) {
            final int userId = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                for (int j = 0; j < requestsPerUser; j++) {
                    try {
                        UserBehaviorEvent event = UserBehaviorEvent.builder()
                                .userId("perf-user-" + userId + "-" + j)
                                .eventType("PAGE_VIEW")
                                .source("web")
                                .eventTime(LocalDateTime.now())
                                .build();

                        String eventJson = objectMapper.writeValueAsString(event);
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        HttpEntity<String> request = new HttpEntity<>(eventJson, headers);

                        long requestStart = System.currentTimeMillis();
                        ResponseEntity<String> response = restTemplate.postForEntity(
                                baseUrl + "/collect", request, String.class);
                        long requestTime = System.currentTimeMillis() - requestStart;
                        
                        totalResponseTime.addAndGet(requestTime);

                        if (response.getStatusCode() == HttpStatus.ACCEPTED) {
                            successCount.incrementAndGet();
                        } else {
                            errorCount.incrementAndGet();
                            log.warn("Request failed with status: {}", response.getStatusCode());
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        log.error("Request failed with exception", e);
                    }
                }
            }, executor);
            
            futures.add(future);
        }

        // 等待所有请求完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
        long totalTime = System.currentTimeMillis() - startTime;

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Then - 性能指标验证
        double successRate = (double) successCount.get() / totalRequests * 100;
        double avgResponseTime = (double) totalResponseTime.get() / totalRequests;
        double throughput = (double) totalRequests / totalTime * 1000; // requests per second

        log.info("=== 单个事件API并发性能测试结果 ===");
        log.info("总请求数: {}", totalRequests);
        log.info("成功请求数: {}", successCount.get());
        log.info("失败请求数: {}", errorCount.get());
        log.info("成功率: {:.2f}%", successRate);
        log.info("平均响应时间: {:.2f}ms", avgResponseTime);
        log.info("吞吐量: {:.2f} requests/second", throughput);
        log.info("总耗时: {}ms", totalTime);

        // 性能断言
        assertTrue(successRate >= 95.0, "成功率应该至少95%");
        assertTrue(avgResponseTime <= 1000, "平均响应时间应该小于1秒");
        assertTrue(throughput >= 10, "吞吐量应该至少10 requests/second");
    }

    @Test
    @DisplayName("批量事件API性能测试")
    void batchEventPerformanceTest() throws Exception {
        // Given
        int batchSize = 100;
        int numberOfBatches = 10;
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);

        // When
        long startTime = System.currentTimeMillis();
        
        for (int batch = 0; batch < numberOfBatches; batch++) {
            UserBehaviorEvent[] events = new UserBehaviorEvent[batchSize];
            
            for (int i = 0; i < batchSize; i++) {
                events[i] = UserBehaviorEvent.builder()
                        .userId("batch-perf-user-" + batch + "-" + i)
                        .eventType(i % 4 == 0 ? "PAGE_VIEW" : 
                                  i % 4 == 1 ? "BUTTON_CLICK" : 
                                  i % 4 == 2 ? "SEARCH" : "PURCHASE")
                        .source("web")
                        .eventTime(LocalDateTime.now())
                        .build();
            }

            try {
                String batchJson = objectMapper.writeValueAsString(events);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> request = new HttpEntity<>(batchJson, headers);

                long requestStart = System.currentTimeMillis();
                ResponseEntity<String> response = restTemplate.postForEntity(
                        baseUrl + "/collect/batch", request, String.class);
                long requestTime = System.currentTimeMillis() - requestStart;
                
                totalResponseTime.addAndGet(requestTime);

                if (response.getStatusCode() == HttpStatus.ACCEPTED) {
                    successCount.incrementAndGet();
                } else {
                    errorCount.incrementAndGet();
                    log.warn("Batch request failed with status: {}", response.getStatusCode());
                }
            } catch (Exception e) {
                errorCount.incrementAndGet();
                log.error("Batch request failed with exception", e);
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;

        // Then
        double successRate = (double) successCount.get() / numberOfBatches * 100;
        double avgResponseTime = (double) totalResponseTime.get() / numberOfBatches;
        double eventsPerSecond = (double) (batchSize * numberOfBatches) / totalTime * 1000;

        log.info("=== 批量事件API性能测试结果 ===");
        log.info("批次数: {}", numberOfBatches);
        log.info("每批事件数: {}", batchSize);
        log.info("总事件数: {}", batchSize * numberOfBatches);
        log.info("成功批次数: {}", successCount.get());
        log.info("失败批次数: {}", errorCount.get());
        log.info("成功率: {:.2f}%", successRate);
        log.info("平均响应时间: {:.2f}ms", avgResponseTime);
        log.info("事件处理速度: {:.2f} events/second", eventsPerSecond);
        log.info("总耗时: {}ms", totalTime);

        // 性能断言
        assertTrue(successRate >= 95.0, "批量处理成功率应该至少95%");
        assertTrue(avgResponseTime <= 5000, "批量处理平均响应时间应该小于5秒");
        assertTrue(eventsPerSecond >= 100, "事件处理速度应该至少100 events/second");
    }

    @Test
    @DisplayName("混合负载性能测试")
    void mixedLoadPerformanceTest() throws Exception {
        // Given
        int concurrentUsers = 20;
        int duration = 30; // seconds
        
        ExecutorService executor = Executors.newFixedThreadPool(concurrentUsers);
        AtomicInteger totalRequests = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalResponseTime = new AtomicLong(0);
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // When
        long startTime = System.currentTimeMillis();
        long endTime = startTime + duration * 1000;
        
        for (int i = 0; i < concurrentUsers; i++) {
            final int userId = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                int requestCount = 0;
                while (System.currentTimeMillis() < endTime) {
                    try {
                        // 随机选择单个事件或批量事件
                        if (requestCount % 5 == 0) {
                            // 发送批量事件
                            sendBatchEvents(userId, requestCount, totalResponseTime, successCount, errorCount);
                        } else {
                            // 发送单个事件
                            sendSingleEvent(userId, requestCount, totalResponseTime, successCount, errorCount);
                        }
                        
                        totalRequests.incrementAndGet();
                        requestCount++;
                        
                        // 短暂休息模拟真实用户行为
                        Thread.sleep(100 + (int)(Math.random() * 200));
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        log.error("Mixed load request failed", e);
                    }
                }
            }, executor);
            
            futures.add(future);
        }

        // 等待测试完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
        long actualDuration = System.currentTimeMillis() - startTime;

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Then
        double successRate = (double) successCount.get() / totalRequests.get() * 100;
        double avgResponseTime = (double) totalResponseTime.get() / totalRequests.get();
        double requestsPerSecond = (double) totalRequests.get() / actualDuration * 1000;

        log.info("=== 混合负载性能测试结果 ===");
        log.info("测试时长: {}ms", actualDuration);
        log.info("并发用户数: {}", concurrentUsers);
        log.info("总请求数: {}", totalRequests.get());
        log.info("成功请求数: {}", successCount.get());
        log.info("失败请求数: {}", errorCount.get());
        log.info("成功率: {:.2f}%", successRate);
        log.info("平均响应时间: {:.2f}ms", avgResponseTime);
        log.info("请求速率: {:.2f} requests/second", requestsPerSecond);

        // 性能断言
        assertTrue(successRate >= 90.0, "混合负载成功率应该至少90%");
        assertTrue(avgResponseTime <= 2000, "混合负载平均响应时间应该小于2秒");
        assertTrue(requestsPerSecond >= 5, "混合负载请求速率应该至少5 requests/second");
    }

    private void sendSingleEvent(int userId, int requestCount, AtomicLong totalResponseTime, 
                                AtomicInteger successCount, AtomicInteger errorCount) throws Exception {
        UserBehaviorEvent event = UserBehaviorEvent.builder()
                .userId("mixed-user-" + userId + "-" + requestCount)
                .eventType("PAGE_VIEW")
                .source("web")
                .eventTime(LocalDateTime.now())
                .build();

        String eventJson = objectMapper.writeValueAsString(event);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(eventJson, headers);

        long requestStart = System.currentTimeMillis();
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/collect", request, String.class);
        long requestTime = System.currentTimeMillis() - requestStart;
        
        totalResponseTime.addAndGet(requestTime);

        if (response.getStatusCode() == HttpStatus.ACCEPTED) {
            successCount.incrementAndGet();
        } else {
            errorCount.incrementAndGet();
        }
    }

    private void sendBatchEvents(int userId, int requestCount, AtomicLong totalResponseTime, 
                                AtomicInteger successCount, AtomicInteger errorCount) throws Exception {
        int batchSize = 10;
        UserBehaviorEvent[] events = new UserBehaviorEvent[batchSize];
        
        for (int i = 0; i < batchSize; i++) {
            events[i] = UserBehaviorEvent.builder()
                    .userId("mixed-batch-user-" + userId + "-" + requestCount + "-" + i)
                    .eventType("BUTTON_CLICK")
                    .source("web")
                    .eventTime(LocalDateTime.now())
                    .build();
        }

        String batchJson = objectMapper.writeValueAsString(events);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(batchJson, headers);

        long requestStart = System.currentTimeMillis();
        ResponseEntity<String> response = restTemplate.postForEntity(
                baseUrl + "/collect/batch", request, String.class);
        long requestTime = System.currentTimeMillis() - requestStart;
        
        totalResponseTime.addAndGet(requestTime);

        if (response.getStatusCode() == HttpStatus.ACCEPTED) {
            successCount.incrementAndGet();
        } else {
            errorCount.incrementAndGet();
        }
    }

    @Test
    @DisplayName("内存使用和垃圾回收压力测试")
    void memoryPressureTest() throws Exception {
        // Given
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        int largeEventCount = 1000;
        AtomicInteger successCount = new AtomicInteger(0);

        // When
        log.info("开始内存压力测试，初始内存使用: {} MB", initialMemory / 1024 / 1024);
        
        for (int i = 0; i < largeEventCount; i++) {
            // 创建包含大量数据的事件
            UserBehaviorEvent event = UserBehaviorEvent.builder()
                    .userId("memory-test-user-" + i)
                    .eventType("PAGE_VIEW")
                    .source("web")
                    .sessionId("session-" + i)
                    .deviceInfo("Chrome/Windows - Large Device Info String with lots of data " + i)
                    .eventTime(LocalDateTime.now())
                    .build();

            try {
                String eventJson = objectMapper.writeValueAsString(event);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                HttpEntity<String> request = new HttpEntity<>(eventJson, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(
                        baseUrl + "/collect", request, String.class);

                if (response.getStatusCode() == HttpStatus.ACCEPTED) {
                    successCount.incrementAndGet();
                }

                // 每100个请求检查一次内存
                if (i % 100 == 0) {
                    long currentMemory = runtime.totalMemory() - runtime.freeMemory();
                    log.debug("处理了 {} 个事件，当前内存使用: {} MB", 
                            i, currentMemory / 1024 / 1024);
                }
                
            } catch (Exception e) {
                log.error("Memory pressure test request failed", e);
            }
        }

        // 强制垃圾回收
        System.gc();
        Thread.sleep(1000);
        
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;

        // Then
        log.info("=== 内存压力测试结果 ===");
        log.info("处理事件数: {}", largeEventCount);
        log.info("成功处理数: {}", successCount.get());
        log.info("初始内存: {} MB", initialMemory / 1024 / 1024);
        log.info("最终内存: {} MB", finalMemory / 1024 / 1024);
        log.info("内存增长: {} MB", memoryIncrease / 1024 / 1024);

        // 内存使用断言
        assertTrue(successCount.get() >= largeEventCount * 0.95, "内存压力下成功率应该至少95%");
        assertTrue(memoryIncrease < 500 * 1024 * 1024, "内存增长应该小于500MB"); // 500MB limit
    }
} 