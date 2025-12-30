package com.mine.engine.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // Queue names
    public static final String TEST_QUEUE = "engine.test.queue";
    
    // Exchange names
    public static final String TEST_EXCHANGE = "engine.test.exchange";
    public static final String WEBSOCKET_EXCHANGE = "engine.websocket.exchange";
    
    // Routing keys
    public static final String TEST_ROUTING_KEY = "engine.test.routing.key";
    public static final String WEBSOCKET_ROUTING_KEY = "engine.websocket.routing.key";

    @Bean
    public Queue testQueue() {
        return QueueBuilder.durable(TEST_QUEUE).build();
    }

    @Bean
    public TopicExchange testExchange() {
        return new TopicExchange(TEST_EXCHANGE);
    }

    @Bean
    public Binding testBinding() {
        return BindingBuilder
                .bind(testQueue())
                .to(testExchange())
                .with(TEST_ROUTING_KEY);
    }

    @Bean
    public TopicExchange websocketExchange() {
        return new TopicExchange(WEBSOCKET_EXCHANGE);
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

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        
        // Configure for synchronous processing with manual acknowledgment
        // Only 1 consumer thread - processes messages one at a time (synchronously)
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        
        // Prefetch count: 1 message at a time for synchronous processing
        // With manual ack, next message won't be delivered until ACK is sent
        factory.setPrefetchCount(1);
        
        // Manual acknowledgment - set to MANUAL
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        
        // Auto start listeners
        factory.setAutoStartup(true);
        
        return factory;
    }
}

