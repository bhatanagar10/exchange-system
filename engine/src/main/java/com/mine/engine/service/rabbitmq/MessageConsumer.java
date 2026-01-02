package com.mine.engine.service.rabbitmq;

import com.mine.engine.config.RabbitMQConfig;
import com.mine.engine.model.Message;
import com.mine.engine.service.StockService;
import com.mine.engine.utils.Utils;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@ConditionalOnProperty(name = "messaging.provider", havingValue = "rabbitmq", matchIfMissing = false)
@Deprecated
public class MessageConsumer {

    private final StockService stockService;
    private final Utils utils;
    private static final Logger logger = LoggerFactory.getLogger(MessageConsumer.class);

    public MessageConsumer(StockService stockService, Utils utils) {
        this.stockService = stockService;
        this.utils = utils;
    }

    /**
     * Manual acknowledgment message listener
     * Next message will NOT be delivered until manual ACK is sent from code
     * 
     * @param message The message payload
     * @param channel The RabbitMQ channel for manual acknowledgment
     * @param deliveryTag The delivery tag for acknowledging this specific message
     */
    @RabbitListener(queues = RabbitMQConfig.TEST_QUEUE)
    public void receiveMessage(
            @Payload Message message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        
        logger.info("Received message (waiting for manual ACK): {}", message);
        logger.info("Message type: {}", message.getClass().getSimpleName());
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
     * Once this is called, RabbitMQ will deliver the next message (if available)
     * 
     * @param channel The RabbitMQ channel
     * @param deliveryTag The delivery tag for this message
     */
    public void acknowledgeMessage(Channel channel, long deliveryTag) throws IOException {
        logger.info("Sending manual ACK for delivery tag: {}", deliveryTag);
        channel.basicAck(deliveryTag, false);
        logger.info("Message acknowledged successfully - RabbitMQ will now deliver next message");
    }
    
    /**
     * Reject the message and optionally requeue it
     * 
     * @param channel The RabbitMQ channel
     * @param deliveryTag The delivery tag
     * @param requeue If true, message will be requeued; if false, message will be discarded
     */
    public void rejectMessage(Channel channel, long deliveryTag, boolean requeue) throws IOException {
        logger.warn("Rejecting message with delivery tag: {}, requeue: {}", deliveryTag, requeue);
        channel.basicNack(deliveryTag, false, requeue);
        if (requeue) {
            logger.info("Message rejected and requeued - will be redelivered");
        } else {
            logger.info("Message rejected and discarded - will NOT be redelivered");
        }
    }

}

