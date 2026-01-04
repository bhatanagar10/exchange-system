package com.trading.bot.service;

import com.trading.bot.config.KafkaConfig;
import com.trading.bot.model.Bot;
import com.trading.bot.model.dto.OrderMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

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
            OrderMessage orderMessage = OrderMessage.builder()
                    .userId(bot.getUserId())
                    .price(price.doubleValue())
                    .quantity(amount.longValue())
                    .orderExecutionType(com.trading.bot.model.OrderExecutionType.LIMIT)
                    .orderType(isBuy ? OrderMessage.OrderType.BUY : OrderMessage.OrderType.SELL)
                    .timestamp(System.currentTimeMillis()) // Current timestamp when order is placed
                    .build();

            kafkaTemplate.send(KafkaConfig.ORDER_EVENTS_TOPIC, orderMessage);

            return true;
            
        } catch (Exception e) {
            log.error("Failed to place order for bot {}: {}", bot.getName(), e.getMessage());
            return false;
        }
    }
}

