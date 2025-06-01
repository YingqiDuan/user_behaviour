package com.microservice.user_behaviour.query;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.microservice.user_behaviour")
@EnableScheduling
@ComponentScan(basePackages = {
    "com.microservice.user_behaviour.query",
    "com.microservice.user_behaviour.model",
    "com.microservice.user_behaviour.util"
})
@Profile("query")
public class UserBehaviorQueryApplication {

    public static void main(String[] args) {
        System.setProperty("spring.profiles.active", "query");
        SpringApplication.run(UserBehaviorQueryApplication.class, args);
    }
} 