package com.microservice.user_behaviour.consumer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.microservice.user_behaviour")
@EnableScheduling
public class UserBehaviorConsumerApplication {

    public static void main(String[] args) {
        // Set active profile to consumer
        System.setProperty("spring.profiles.active", "consumer");
        SpringApplication.run(UserBehaviorConsumerApplication.class, args);
    }
} 