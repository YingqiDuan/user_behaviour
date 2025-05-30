package com.microservice.user_behaviour.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.microservice.user_behaviour")
@EntityScan("com.microservice.user_behaviour.consumer.entity")
@EnableJpaRepositories("com.microservice.user_behaviour.consumer.repository")
@EnableScheduling
public class UserBehaviorConsumerApplication {

    public static void main(String[] args) {
        // Set active profile to consumer
        System.setProperty("spring.profiles.active", "consumer");
        SpringApplication.run(UserBehaviorConsumerApplication.class, args);
    }
} 