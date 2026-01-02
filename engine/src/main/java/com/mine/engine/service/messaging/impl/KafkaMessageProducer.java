package com.mine.engine.service.messaging.impl;

import com.mine.engine.config.KafkaConfig;
import com.mine.engine.service.messaging.MessageProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Kafka implementation of MessageProducer.
 */
@Service
@ConditionalOnProperty(name = "messaging.provider", havingValue = "kafka", matchIfMissing = true)
public class KafkaMessageProducer implements MessageProducer {

    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageProducer.class);
    
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaMessageProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void sendMessage(Object message) {
        logger.info("Sending message via Kafka to topic {}: {}", KafkaConfig.ORDER_EVENTS_TOPIC, message);
        kafkaTemplate.send(KafkaConfig.ORDER_EVENTS_TOPIC, message);
        logger.info("Message sent successfully via Kafka to topic {}", KafkaConfig.ORDER_EVENTS_TOPIC);
    }
}

