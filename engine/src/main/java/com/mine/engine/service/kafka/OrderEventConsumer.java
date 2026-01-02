package com.mine.engine.service.kafka;

import com.mine.engine.config.KafkaConfig;
import com.mine.engine.model.Message;
import com.mine.engine.service.StockService;
import com.mine.engine.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Kafka consumer for processing order events.
 * Processes buy/sell orders synchronously with manual acknowledgment.
 * @deprecated Use {@link com.mine.engine.service.messaging.impl.KafkaMessageConsumer} instead
 */
@Service
@ConditionalOnProperty(name = "messaging.provider", havingValue = "kafka", matchIfMissing = true)
@Deprecated
public class OrderEventConsumer {

    private final StockService stockService;
    private final Utils utils;
    private static final Logger logger = LoggerFactory.getLogger(OrderEventConsumer.class);

    public OrderEventConsumer(StockService stockService, Utils utils) {
        this.stockService = stockService;
        this.utils = utils;
    }

    /**
     * Manual acknowledgment message listener
     * Next message will NOT be delivered until manual ACK is sent from code
     * 
     * @param message The message payload
     * @param acknowledgment The Kafka acknowledgment for manual commit
     * @param partition The partition from which the message was received
     * @param offset The offset of the message
     */
    @KafkaListener(topics = KafkaConfig.ORDER_EVENTS_TOPIC, groupId = "${spring.kafka.consumer.group-id:engine-order-consumer-group}")
    public void receiveMessage(
            @Payload Message message,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        logger.info("Received message from partition {} at offset {} (waiting for manual ACK): {}", 
                partition, offset, message);
        logger.info("Message type: {}", message.getClass().getSimpleName());
        logger.info("Next message will NOT be delivered until ACK is sent");
        
        try {
            // Process message - next message will NOT be delivered until ACK is sent
            processMessage(message);
            
            // Manual acknowledgment - NEXT MESSAGE WILL BE DELIVERED AFTER THIS ACK
            acknowledgeMessage(acknowledgment);
            
        } catch (Exception e) {
            logger.error("Error processing message from partition {} at offset {}: {}", 
                    partition, offset, e.getMessage(), e);
            // In Kafka, we can choose to commit or not commit on error
            // Not committing will cause the message to be redelivered
            // For now, we'll acknowledge even on error to prevent infinite retries
            // In production, you might want to send to a dead letter topic instead
            acknowledgeMessage(acknowledgment);
        }
    }
    
    /**
     * Process message - this method must complete before acknowledging
     * Next message will not be delivered until acknowledgeMessage() is called
     */
    private void processMessage(Message message) {
        logger.info("Processing message synchronously: {}", message);

        switch(message.getOrderType()) {
            case BUY:
                // Simulate buy order processing
                logger.info("Processing BUY order for user : {}", message.getUserId());
                stockService.placeBuyOrder(message.getUserId(), message.getPrice(), message.getQuantity(),
                        message.getOrderExecutionType());
                break;
            case SELL:
                // Simulate sell order processing
                logger.info("Processing SELL order for user: {}", message.getUserId());
                stockService.placeSellOrder(message.getUserId(), message.getPrice(), message.getQuantity(),
                        message.getOrderExecutionType());
                break;
            default:
                logger.warn("Unknown order type: {}", message.getOrderType());
        }
        utils.printStockData();
        // Your business logic here
        // Message stays unacknowledged until acknowledgeMessage() is called
    }
    
    /**
     * Manually acknowledge the message
     * Once this is called, Kafka will deliver the next message (if available)
     * 
     * @param acknowledgment The Kafka acknowledgment
     */
    public void acknowledgeMessage(Acknowledgment acknowledgment) {
        logger.info("Sending manual ACK");
        acknowledgment.acknowledge();
        logger.info("Message acknowledged successfully - Kafka will now deliver next message");
    }
}

