#!/bin/bash
echo "Starting User Behavior Consumer Service..."
java -Dspring.profiles.active=consumer -cp target/user_behaviour-0.0.1-SNAPSHOT.jar com.microservice.user_behaviour.consumer.UserBehaviorConsumerApplication 