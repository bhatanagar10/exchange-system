package com.mine.websocket.service;

import com.mine.websocket.config.RabbitMQConfig;
import com.mine.websocket.dto.OrderBookDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Consumes messages from RabbitMQ topic exchange (published by engine)
 * and broadcasts them to WebSocket subscribers via STOMP
 */
@Service
@Slf4j
public class EngineMessageConsumer {

    private final SimpMessagingTemplate messagingTemplate;

    public EngineMessageConsumer(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Consumes messages from RabbitMQ queue (subscribed to engine.websocket.exchange)
     * and broadcasts them to all WebSocket subscribers via STOMP
     */
    @RabbitListener(queues = RabbitMQConfig.WEBSOCKET_QUEUE)
    public void handleEngineMessage(OrderBookDTO orderBookDTO) {
        log.info("Received order book data from engine via RabbitMQ");
        
        try {
            // Broadcast order book data directly to all subscribers of /topic/public
            // The payload will be automatically serialized to JSON by Spring
            messagingTemplate.convertAndSend("/topic/public", orderBookDTO);
            
            log.info("Broadcasted order book data to WebSocket subscribers");
        } catch (Exception e) {
            log.error("Error processing message from engine: {}", e.getMessage(), e);
        }
    }

}

