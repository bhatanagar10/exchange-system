package com.mine.engine.service.messaging.impl;

import com.mine.engine.config.RabbitMQConfig;
import com.mine.engine.model.Message;
import com.mine.engine.service.StockService;
import com.mine.engine.service.messaging.MessageConsumer;
import com.mine.engine.utils.Utils;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * RabbitMQ implementation of MessageConsumer.
 */
@Service
@ConditionalOnProperty(name = "messaging.provider", havingValue = "rabbitmq", matchIfMissing = false)
public class RabbitMQMessageConsumer implements MessageConsumer {

    private final StockService stockService;
    private final Utils utils;
    private static final Logger logger = LoggerFactory.getLogger(RabbitMQMessageConsumer.class);

    public RabbitMQMessageConsumer(StockService stockService, Utils utils) {
        this.stockService = stockService;
        this.utils = utils;
    }

    /**
     * Manual acknowledgment message listener
     * Next message will NOT be delivered until manual ACK is sent from code
     */
    @RabbitListener(queues = RabbitMQConfig.TEST_QUEUE)
    public void receiveMessage(
            @Payload Message message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        
        logger.info("Received message via RabbitMQ (waiting for manual ACK): {}", message);
        logger.info("Delivery tag: {} - Next message will NOT be delivered until ACK is sent", deliveryTag);
        
        try {
            // Process message - next message will NOT be delivered until ACK is sent
            processMessage(message);
            
            // Manual acknowledgment - NEXT MESSAGE WILL BE DELIVERED AFTER THIS ACK
            acknowledgeMessage(channel, deliveryTag);
            
        } catch (Exception e) {
            logger.error("Error processing message with delivery tag {}: {}", deliveryTag, e.getMessage(), e);
            // Reject and requeue the message on error
            rejectMessage(channel, deliveryTag, true);
        }
    }

    @Override
    public void processMessage(Message message) {
        logger.info("Processing message synchronously via RabbitMQ: {}", message);

        switch(message.getOrderType()) {
            case BUY:
                logger.info("Processing BUY order for user : {}", message.getUserId());
                stockService.placeBuyOrder(message.getUserId(), message.getPrice(), message.getQuantity(),
                        message.getOrderExecutionType(), message.getTimestamp());
                break;
            case SELL:
                logger.info("Processing SELL order for user: {}", message.getUserId());
                stockService.placeSellOrder(message.getUserId(), message.getPrice(), message.getQuantity(),
                        message.getOrderExecutionType(), message.getTimestamp());
                break;
            default:
                logger.warn("Unknown order type: {}", message.getOrderType());
        }
        utils.printStockData();
    }
    
    private void acknowledgeMessage(Channel channel, long deliveryTag) throws IOException {
        logger.info("Sending manual ACK for delivery tag: {}", deliveryTag);
        channel.basicAck(deliveryTag, false);
        logger.info("Message acknowledged successfully - RabbitMQ will now deliver next message");
    }
    
    private void rejectMessage(Channel channel, long deliveryTag, boolean requeue) throws IOException {
        logger.warn("Rejecting message with delivery tag: {}, requeue: {}", deliveryTag, requeue);
        channel.basicNack(deliveryTag, false, requeue);
        if (requeue) {
            logger.info("Message rejected and requeued - will be redelivered");
        } else {
            logger.info("Message rejected and discarded - will NOT be redelivered");
        }
    }
}

