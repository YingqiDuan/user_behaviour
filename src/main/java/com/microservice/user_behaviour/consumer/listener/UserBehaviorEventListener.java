package com.microservice.user_behaviour.consumer.listener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.TopicPartition;
import org.springframework.kafka.listener.BatchAcknowledgingMessageListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.microservice.user_behaviour.consumer.service.UserBehaviorProcessingService;
import com.microservice.user_behaviour.model.UserBehaviorEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class UserBehaviorEventListener implements BatchAcknowledgingMessageListener<String, UserBehaviorEvent> {

    private final UserBehaviorProcessingService processingService;
    
    /**
     * Listen for events on all user behavior topics
     */
    @KafkaListener(
        id = "userBehaviorListener",
        topicPartitions = {
            @TopicPartition(topic = "${user.behavior.topic}", partitions = {"0", "1", "2"}),
            @TopicPartition(topic = "${user.behavior.topic.pageview}", partitions = {"0", "1", "2"}),
            @TopicPartition(topic = "${user.behavior.topic.click}", partitions = {"0", "1", "2"}),
            @TopicPartition(topic = "${user.behavior.topic.search}", partitions = {"0", "1", "2"}),
            @TopicPartition(topic = "${user.behavior.topic.purchase}", partitions = {"0", "1", "2"}),
            @TopicPartition(topic = "${user.behavior.topic.default}", partitions = {"0", "1", "2"})
        },
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Override
    public void onMessage(List<ConsumerRecord<String, UserBehaviorEvent>> records, Acknowledgment acknowledgment) {
        try {
            log.info("Received batch of {} records", records.size());
            
            // Map to hold batch record metadata (uses the last record's metadata)
            Map<String, Object> recordMetadata = new HashMap<>();
            List<UserBehaviorEvent> events = records.stream()
                    .map(record -> {
                        // Set metadata from the last record (simplified approach)
                        recordMetadata.put("topic", record.topic());
                        recordMetadata.put("partition", record.partition());
                        recordMetadata.put("offset", record.offset());
                        
                        // Log details about the record
                        log.debug("Processing record: topic={}, partition={}, offset={}, key={}",
                                record.topic(), record.partition(), record.offset(), record.key());
                        
                        return record.value();
                    })
                    .filter(event -> event != null) // Filter out any null events
                    .toList();
            
            if (!events.isEmpty()) {
                // Process the batch of events
                processingService.processBatch(events, recordMetadata);
            }
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            log.debug("Batch processing completed and acknowledged");
        } catch (Exception e) {
            log.error("Error processing Kafka records", e);
            // Don't acknowledge to trigger redelivery
            // Could implement more sophisticated error handling, like dead letter queue
        }
    }
    
    /**
     * Alternative method to process single records if batch processing is disabled
     */
    public void processSingleRecord(ConsumerRecord<String, UserBehaviorEvent> record) {
        try {
            UserBehaviorEvent event = record.value();
            if (event != null) {
                processingService.processEvent(event, record.topic(), record.partition(), record.offset());
            }
        } catch (Exception e) {
            log.error("Error processing record: {}", record, e);
        }
    }
} 