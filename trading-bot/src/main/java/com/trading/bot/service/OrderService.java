package com.trading.bot.service;

import com.trading.bot.config.RabbitMQConfig;
import com.trading.bot.model.Bot;
import com.trading.bot.model.dto.OrderMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Simplified Order Service - Places orders via RabbitMQ.
 */
@Slf4j
@Service
public class OrderService {

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQConfig rabbitMQConfig;

    public OrderService(RabbitTemplate rabbitTemplate, RabbitMQConfig rabbitMQConfig) {
        this.rabbitTemplate = rabbitTemplate;
        this.rabbitMQConfig = rabbitMQConfig;
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
                    .build();

            rabbitTemplate.convertAndSend(
                    rabbitMQConfig.getOrderExchange(),
                    rabbitMQConfig.getOrderRoutingKey(),
                    orderMessage
            );

            return true;
            
        } catch (Exception e) {
            log.error("Failed to place order for bot {}: {}", bot.getName(), e.getMessage());
            return false;
        }
    }
}

