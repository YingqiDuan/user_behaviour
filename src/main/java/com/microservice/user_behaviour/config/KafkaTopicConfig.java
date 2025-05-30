package com.microservice.user_behaviour.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${user.behavior.topic}")
    private String userBehaviorTopic;
    
    @Value("${user.behavior.topic.pageview}")
    private String pageViewTopic;
    
    @Value("${user.behavior.topic.click}")
    private String clickTopic;
    
    @Value("${user.behavior.topic.search}")
    private String searchTopic;
    
    @Value("${user.behavior.topic.purchase}")
    private String purchaseTopic;
    
    @Value("${user.behavior.topic.default}")
    private String defaultTopic;

    @Bean
    public NewTopic userBehaviorTopic() {
        return TopicBuilder.name(userBehaviorTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
    
    @Bean
    public NewTopic pageViewTopic() {
        return TopicBuilder.name(pageViewTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
    
    @Bean
    public NewTopic clickTopic() {
        return TopicBuilder.name(clickTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
    
    @Bean
    public NewTopic searchTopic() {
        return TopicBuilder.name(searchTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
    
    @Bean
    public NewTopic purchaseTopic() {
        return TopicBuilder.name(purchaseTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
    
    @Bean
    public NewTopic defaultTopic() {
        return TopicBuilder.name(defaultTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
} 