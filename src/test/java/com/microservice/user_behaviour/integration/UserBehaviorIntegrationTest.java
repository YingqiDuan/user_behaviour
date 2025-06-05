package com.microservice.user_behaviour.integration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservice.user_behaviour.consumer.repository.UserBehaviorRepository;
import com.microservice.user_behaviour.model.UserBehaviorEvent;

import lombok.extern.slf4j.Slf4j;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("integration-test")
@DisplayName("用户行为系统集成测试")
@Disabled("Integration tests require Docker and TestContainers infrastructure. Run manually in appropriate environment.")
@Slf4j
class UserBehaviorIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserBehaviorRepository repository;

    // TestContainers
    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("user_behavior_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Kafka配置
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.producer.bootstrap-servers", kafka::getBootstrapServers);

        // MySQL配置
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");

        // Redis配置
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);

        // JPA配置
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "true");

        // Kafka Topic配置
        registry.add("user.behavior.topic", () -> "user-behavior-events");
        registry.add("user.behavior.topic.pageview", () -> "user-behavior-pageview");
        registry.add("user.behavior.topic.click", () -> "user-behavior-click");
        registry.add("user.behavior.topic.search", () -> "user-behavior-search");
        registry.add("user.behavior.topic.purchase", () -> "user-behavior-purchase");
        registry.add("user.behavior.topic.default", () -> "user-behavior-default");

        // 批处理配置
        registry.add("app.batch.size", () -> "5");
    }

    @BeforeEach
    void setUp() {
        // 清理数据库
        repository.deleteAll();
    }

    @Test
    @DisplayName("端到端测试：发送事件 -> Kafka -> 处理 -> 存储到数据库")
    void endToEndTest_SendEvent_ShouldProcessAndStore() throws Exception {
        // Given
        UserBehaviorEvent event = UserBehaviorEvent.builder()
                .userId("integration-user-123")
                .eventType("PAGE_VIEW")
                .source("web")
                .sessionId("session-123")
                .deviceInfo("Chrome/Windows")
                .eventTime(LocalDateTime.now())
                .build();

        String eventJson = objectMapper.writeValueAsString(event);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(eventJson, headers);

        // When - 发送HTTP请求到数据收集服务
        String url = "http://localhost:" + port + "/collect";
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        // Then - 验证HTTP响应
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals("Event accepted", response.getBody());

        // 等待异步处理完成并验证数据库存储
        await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    List<com.microservice.user_behaviour.consumer.entity.UserBehaviorEntity> entities = 
                            repository.findAll();
                    assertEquals(1, entities.size());
                    assertEquals("integration-user-123", entities.get(0).getUserId());
                    assertEquals("PAGE_VIEW", entities.get(0).getEventType());
                });
    }

    @Test
    @DisplayName("批量事件处理集成测试")
    void endToEndTest_BatchEvents_ShouldProcessAll() throws Exception {
        // Given
        UserBehaviorEvent[] events = {
                UserBehaviorEvent.builder()
                        .userId("batch-user-1")
                        .eventType("PAGE_VIEW")
                        .source("web")
                        .build(),
                UserBehaviorEvent.builder()
                        .userId("batch-user-2")
                        .eventType("BUTTON_CLICK")
                        .source("mobile")
                        .build(),
                UserBehaviorEvent.builder()
                        .userId("batch-user-3")
                        .eventType("SEARCH")
                        .source("web")
                        .build()
        };

        String batchJson = objectMapper.writeValueAsString(events);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(batchJson, headers);

        // When
        String url = "http://localhost:" + port + "/collect/batch";
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        // Then
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertTrue(response.getBody().contains("Batch accepted (3 events, 0 critical)"));

        // 等待批量处理完成
        await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    List<com.microservice.user_behaviour.consumer.entity.UserBehaviorEntity> entities = 
                            repository.findAll();
                    assertEquals(3, entities.size());
                });
    }

    @Test
    @DisplayName("关键事件同步处理集成测试")
    void endToEndTest_CriticalEvent_ShouldProcessSynchronously() throws Exception {
        // Given
        UserBehaviorEvent criticalEvent = UserBehaviorEvent.builder()
                .userId("critical-user-123")
                .eventType("PURCHASE")
                .source("web")
                .build();

        String eventJson = objectMapper.writeValueAsString(criticalEvent);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(eventJson, headers);

        // When
        String url = "http://localhost:" + port + "/collect";
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        // Then
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals("Critical event accepted and confirmed delivery", response.getBody());

        // 关键事件应该很快被处理
        await()
                .atMost(Duration.ofSeconds(10))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    List<com.microservice.user_behaviour.consumer.entity.UserBehaviorEntity> entities = 
                            repository.findAll();
                    assertEquals(1, entities.size());
                    assertEquals("PURCHASE", entities.get(0).getEventType());
                });
    }

    @Test
    @DisplayName("大量事件处理性能测试")
    void performanceTest_HighVolumeEvents_ShouldHandleEfficiently() throws Exception {
        // Given - 准备大量事件
        int eventCount = 100;
        UserBehaviorEvent[] events = new UserBehaviorEvent[eventCount];
        
        for (int i = 0; i < eventCount; i++) {
            events[i] = UserBehaviorEvent.builder()
                    .userId("perf-user-" + i)
                    .eventType(i % 2 == 0 ? "PAGE_VIEW" : "BUTTON_CLICK")
                    .source("web")
                    .build();
        }

        String batchJson = objectMapper.writeValueAsString(events);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(batchJson, headers);

        // When
        long startTime = System.currentTimeMillis();
        String url = "http://localhost:" + port + "/collect/batch";
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        long responseTime = System.currentTimeMillis() - startTime;

        // Then
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertTrue(responseTime < 5000, "Response time should be less than 5 seconds");

        // 等待所有事件处理完成
        await()
                .atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    List<com.microservice.user_behaviour.consumer.entity.UserBehaviorEntity> entities = 
                            repository.findAll();
                    assertEquals(eventCount, entities.size());
                });
    }

    @Test
    @DisplayName("错误处理集成测试：无效事件")
    void errorHandlingTest_InvalidEvent_ShouldReturnBadRequest() throws Exception {
        // Given - 无效的事件数据
        String invalidJson = "{\"invalidField\": \"value\"}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(invalidJson, headers);

        // When
        String url = "http://localhost:" + port + "/collect";
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

        // Then
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        // 确认没有数据被存储
        await()
                .during(Duration.ofSeconds(5))
                .atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    List<com.microservice.user_behaviour.consumer.entity.UserBehaviorEntity> entities = 
                            repository.findAll();
                    assertEquals(0, entities.size());
                });
    }

    @Test
    @DisplayName("系统健康检查集成测试")
    void healthCheckTest_SystemComponents_ShouldBeHealthy() {
        // When
        String healthUrl = "http://localhost:" + port + "/actuator/health";
        ResponseEntity<Map> response = restTemplate.getForEntity(healthUrl, Map.class);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("UP", response.getBody().get("status"));
    }

    @Test
    @DisplayName("事件路由测试：不同事件类型路由到不同Topic")
    void eventRoutingTest_DifferentEventTypes_ShouldRouteToCorrectTopics() throws Exception {
        // Given - 不同类型的事件
        UserBehaviorEvent[] events = {
                UserBehaviorEvent.builder().userId("user1").eventType("PAGE_VIEW").source("web").build(),
                UserBehaviorEvent.builder().userId("user2").eventType("BUTTON_CLICK").source("web").build(),
                UserBehaviorEvent.builder().userId("user3").eventType("SEARCH").source("web").build(),
                UserBehaviorEvent.builder().userId("user4").eventType("PURCHASE").source("web").build()
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // When - 分别发送不同类型的事件
        for (UserBehaviorEvent event : events) {
            String eventJson = objectMapper.writeValueAsString(event);
            HttpEntity<String> request = new HttpEntity<>(eventJson, headers);
            
            String url = "http://localhost:" + port + "/collect";
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            
            assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        }

        // Then - 等待所有事件处理完成
        await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    List<com.microservice.user_behaviour.consumer.entity.UserBehaviorEntity> entities = 
                            repository.findAll();
                    assertEquals(4, entities.size());
                    
                    // 验证不同事件类型都被正确处理
                    long pageViewCount = entities.stream().filter(e -> "PAGE_VIEW".equals(e.getEventType())).count();
                    long clickCount = entities.stream().filter(e -> "BUTTON_CLICK".equals(e.getEventType())).count();
                    long searchCount = entities.stream().filter(e -> "SEARCH".equals(e.getEventType())).count();
                    long purchaseCount = entities.stream().filter(e -> "PURCHASE".equals(e.getEventType())).count();
                    
                    assertEquals(1, pageViewCount);
                    assertEquals(1, clickCount);
                    assertEquals(1, searchCount);
                    assertEquals(1, purchaseCount);
                });
    }
} 