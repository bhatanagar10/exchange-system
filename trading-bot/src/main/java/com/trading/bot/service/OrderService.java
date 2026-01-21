package com.trading.bot.service;

import com.trading.bot.config.KafkaConfig;
import com.trading.bot.model.Bot;
import com.trading.bot.model.dto.OrderMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Simplified Order Service - Places orders via Kafka.
 */
@Slf4j
@Service
public class OrderService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Place an order for a bot.
     */
    public boolean placeOrder(Bot bot, boolean isBuy, BigDecimal amount, BigDecimal price) {
        try {
            long timestamp = System.currentTimeMillis();
            
            // Generate unique idempotency key: BOT-{botId}-{orderType}-{timestamp}-{uuid}
            String idempotencyKey = String.format("BOT-%d-%s-%d-%s", 
                    bot.getUserId(),
                    isBuy ? "BUY" : "SELL",
                    timestamp,
                    UUID.randomUUID().toString().substring(0, 8));
            
            OrderMessage orderMessage = OrderMessage.builder()
                    .userId(bot.getUserId())
                    .price(price.doubleValue())
                    .quantity(amount.longValue())
                    .orderExecutionType(com.trading.bot.model.OrderExecutionType.LIMIT)
                    .orderType(isBuy ? OrderMessage.OrderType.BUY : OrderMessage.OrderType.SELL)
                    .timestamp(timestamp) // Current timestamp when order is placed
                    .idempotencyKey(idempotencyKey) // Unique idempotency key for this order
                    .build();

            kafkaTemplate.send(KafkaConfig.ORDER_EVENTS_TOPIC, orderMessage);
            
            log.debug("Placed order with idempotency key: {}", idempotencyKey);

            return true;
            
        } catch (Exception e) {
            log.error("Failed to place order for bot {}: {}", bot.getName(), e.getMessage());
            return false;
        }
    }
}

