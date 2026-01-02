package com.mine.engine.service.kafka;

import com.mine.engine.config.KafkaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Kafka producer for sending order events.
 * @deprecated Use {@link com.mine.engine.service.messaging.impl.KafkaMessageProducer} instead
 */
@Service
@ConditionalOnProperty(name = "messaging.provider", havingValue = "kafka", matchIfMissing = true)
@Deprecated
public class OrderEventProducer {

    private static final Logger logger = LoggerFactory.getLogger(OrderEventProducer.class);
    
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendMessage(Object message) {
        logger.info("Sending message to topic {}: {}", KafkaConfig.ORDER_EVENTS_TOPIC, message);
        kafkaTemplate.send(KafkaConfig.ORDER_EVENTS_TOPIC, message);
        logger.info("Message sent successfully to topic {}", KafkaConfig.ORDER_EVENTS_TOPIC);
    }
}

