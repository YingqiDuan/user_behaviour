package com.microservice.user_behaviour.util;

import com.microservice.user_behaviour.model.UserBehaviorEvent;
import com.microservice.user_behaviour.service.EventPublishingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 测试事件生成器 - 用于生成测试事件并验证数据存储
 * 
 * 使用方法：
 * 1. 确保 Kafka 和 MySQL 服务正在运行
 * 2. 运行命令：./mvnw spring-boot:run -Dspring-boot.run.profiles=generator
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.microservice.user_behaviour")
@Profile("generator")
public class TestEventGenerator {

    @Autowired
    private EventPublishingService publishingService;

    public static void main(String[] args) {
        new SpringApplicationBuilder(TestEventGenerator.class)
            .web(WebApplicationType.NONE)
            .profiles("generator")
            .run(args);
    }

    @Bean
    public CommandLineRunner generateEvents() {
        return args -> {
            System.out.println("===== 测试事件生成器 =====");
            System.out.println("正在生成测试事件并发送到 Kafka...");

            int count = args.length > 0 ? Integer.parseInt(args[0]) : 10;
            int delay = args.length > 1 ? Integer.parseInt(args[1]) : 500;

            String[] eventTypes = {"PAGE_VIEW", "CLICK", "SEARCH", "PURCHASE", "LOGIN", "LOGOUT"};
            String[] sources = {"WEB", "MOBILE_APP", "DESKTOP_APP", "API"};
            Random random = new Random();

            for (int i = 0; i < count; i++) {
                String userId = "user_" + random.nextInt(100);
                String eventType = eventTypes[random.nextInt(eventTypes.length)];
                String source = sources[random.nextInt(sources.length)];
                
                UserBehaviorEvent event = createTestEvent(userId, eventType, source);
                publishingService.publishEvent(event);
                
                System.out.println("已发送事件 " + (i + 1) + "/" + count + ": " + eventType + " 来自 " + userId);
                
                if (delay > 0 && i < count - 1) {
                    TimeUnit.MILLISECONDS.sleep(delay);
                }
            }
            
            System.out.println("测试事件生成完成！请检查消费者日志和数据库以验证数据存储。");
            System.exit(0);
        };
    }
    
    private UserBehaviorEvent createTestEvent(String userId, String eventType, String source) {
        Map<String, Object> eventData = new HashMap<>();
        String testId = UUID.randomUUID().toString();
        
        switch (eventType) {
            case "PAGE_VIEW":
                eventData.put("page", "/products/" + new Random().nextInt(1000));
                eventData.put("referrer", "/home");
                break;
            case "CLICK":
                eventData.put("element", "button_" + new Random().nextInt(10));
                eventData.put("position", new Random().nextInt(100) + "," + new Random().nextInt(100));
                break;
            case "SEARCH":
                eventData.put("query", "test_query_" + new Random().nextInt(50));
                eventData.put("results", new Random().nextInt(100));
                break;
            case "PURCHASE":
                eventData.put("product_id", "prod_" + new Random().nextInt(500));
                eventData.put("amount", 10 + new Random().nextInt(990));
                break;
            default:
                eventData.put("action", eventType.toLowerCase());
        }
        
        eventData.put("testId", testId);
        eventData.put("timestamp", System.currentTimeMillis());
        
        return UserBehaviorEvent.builder()
                .userId(userId)
                .eventType(eventType)
                .source(source)
                .eventTime(LocalDateTime.now())
                .eventData(eventData)
                .sessionId("session_" + new Random().nextInt(50))
                .deviceInfo("device_" + (new Random().nextBoolean() ? "mobile" : "desktop"))
                .ipAddress("192.168.1." + new Random().nextInt(255))
                .build();
    }
} 