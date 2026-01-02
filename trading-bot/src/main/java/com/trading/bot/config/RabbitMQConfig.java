package com.trading.bot.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for sending orders to the exchange engine.
 */
@Configuration
public class RabbitMQConfig {

    private final TradingBotConfig tradingBotConfig;

    public RabbitMQConfig(TradingBotConfig tradingBotConfig) {
        this.tradingBotConfig = tradingBotConfig;
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        return rabbitTemplate;
    }

//    /**
//     * Get the exchange name for order submission.
//     */
//    public String getOrderExchange() {
//        return tradingBotConfig.getRabbitmq().getExchange();
//    }
//
//    /**
//     * Get the routing key for order submission.
//     */
//    public String getOrderRoutingKey() {
//        return tradingBotConfig.getRabbitmq().getRoutingKey();
//    }
}

