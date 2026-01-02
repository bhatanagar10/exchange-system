package com.mine.engine.service.messaging.impl;

import com.mine.engine.config.RabbitMQConfig;
import com.mine.engine.service.messaging.MessageProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * RabbitMQ implementation of MessageProducer.
 */
@Service
@ConditionalOnProperty(name = "messaging.provider", havingValue = "rabbitmq", matchIfMissing = false)
public class RabbitMQMessageProducer implements MessageProducer {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQMessageProducer.class);
    
    private final RabbitTemplate rabbitTemplate;

    public RabbitMQMessageProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void sendMessage(Object message) {
        logger.info("Sending message via RabbitMQ: {}", message);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.TEST_EXCHANGE,
                RabbitMQConfig.TEST_ROUTING_KEY,
                message
        );
        logger.info("Message sent successfully via RabbitMQ");
    }
}

