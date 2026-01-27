package com.mine.main.service;

import com.mine.main.config.KafkaConfig;
import com.mine.main.model.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class KafkaOrderProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaOrderProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        log.info("KafkaOrderProducer initialized");
    }

    /**
     * Send order message to Kafka topic for engine to process
     */
    public void sendOrder(Message message) {
        try {
            kafkaTemplate.send(KafkaConfig.ORDER_EVENTS_TOPIC, message);
            log.info("Order sent to Kafka topic {} - UserId: {}, Type: {}, Price: {}, Quantity: {}", 
                    KafkaConfig.ORDER_EVENTS_TOPIC, 
                    message.getUserId(), 
                    message.getOrderType(), 
                    message.getPrice(), 
                    message.getQuantity());
        } catch (Exception e) {
            log.error("Failed to send order to Kafka: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send order to Kafka", e);
        }
    }
}
