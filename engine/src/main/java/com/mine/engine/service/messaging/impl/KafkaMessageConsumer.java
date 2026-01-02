package com.mine.engine.service.messaging.impl;

import com.mine.engine.config.KafkaConfig;
import com.mine.engine.model.Message;
import com.mine.engine.service.StockService;
import com.mine.engine.service.messaging.MessageConsumer;
import com.mine.engine.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Kafka implementation of MessageConsumer.
 */
@Service
@ConditionalOnProperty(name = "messaging.provider", havingValue = "kafka", matchIfMissing = true)
public class KafkaMessageConsumer implements MessageConsumer {

    private final StockService stockService;
    private final Utils utils;
    private static final Logger logger = LoggerFactory.getLogger(KafkaMessageConsumer.class);

    public KafkaMessageConsumer(StockService stockService, Utils utils) {
        this.stockService = stockService;
        this.utils = utils;
    }

    /**
     * Manual acknowledgment message listener
     * Next message will NOT be delivered until manual ACK is sent from code
     */
    @KafkaListener(topics = KafkaConfig.ORDER_EVENTS_TOPIC, groupId = "${spring.kafka.consumer.group-id:engine-order-consumer-group}")
    public void receiveMessage(
            @Payload Message message,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        logger.info("Received message via Kafka from partition {} at offset {} (waiting for manual ACK): {}", 
                partition, offset, message);
        logger.info("Next message will NOT be delivered until ACK is sent");
        
        try {
            // Process message - next message will NOT be delivered until ACK is sent
            processMessage(message);
            
            // Manual acknowledgment - NEXT MESSAGE WILL BE DELIVERED AFTER THIS ACK
            acknowledgeMessage(acknowledgment);
            
        } catch (Exception e) {
            logger.error("Error processing message from partition {} at offset {}: {}", 
                    partition, offset, e.getMessage(), e);
            // Acknowledge even on error to prevent infinite retries
            // In production, you might want to send to a dead letter topic instead
            acknowledgeMessage(acknowledgment);
        }
    }

    @Override
    public void processMessage(Message message) {
        logger.info("Processing message synchronously via Kafka: {}", message);

        switch(message.getOrderType()) {
            case BUY:
                logger.info("Processing BUY order for user : {}", message.getUserId());
                stockService.placeBuyOrder(message.getUserId(), message.getPrice(), message.getQuantity(),
                        message.getOrderExecutionType());
                break;
            case SELL:
                logger.info("Processing SELL order for user: {}", message.getUserId());
                stockService.placeSellOrder(message.getUserId(), message.getPrice(), message.getQuantity(),
                        message.getOrderExecutionType());
                break;
            default:
                logger.warn("Unknown order type: {}", message.getOrderType());
        }
        utils.printStockData();
    }
    
    private void acknowledgeMessage(Acknowledgment acknowledgment) {
        logger.info("Sending manual ACK via Kafka");
        acknowledgment.acknowledge();
        logger.info("Message acknowledged successfully - Kafka will now deliver next message");
    }
}

