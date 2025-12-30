package com.mine.websocket.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Exchange name - must match engine configuration
    public static final String WEBSOCKET_EXCHANGE = "engine.websocket.exchange";
    
    // Queue name for websocket server to consume from
    public static final String WEBSOCKET_QUEUE = "websocket.server.queue";
    
    // Routing key - must match engine configuration
    public static final String WEBSOCKET_ROUTING_KEY = "engine.websocket.routing.key";

    @Bean
    public Queue websocketQueue() {
        return QueueBuilder.durable(WEBSOCKET_QUEUE).build();
    }

    @Bean
    public TopicExchange websocketExchange() {
        return new TopicExchange(WEBSOCKET_EXCHANGE);
    }

    @Bean
    public Binding websocketBinding() {
        return BindingBuilder
                .bind(websocketQueue())
                .to(websocketExchange())
                .with(WEBSOCKET_ROUTING_KEY);
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
}

