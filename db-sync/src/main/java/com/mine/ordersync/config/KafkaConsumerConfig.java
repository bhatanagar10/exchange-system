package com.mine.ordersync.config;

import com.mine.ordersync.model.OrderEvent;
import com.mine.ordersync.model.TransactionEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer configuration
 * Configured for manual acknowledgment and synchronous processing (one event at a time)
 * Separate factories for OrderEvent and TransactionEvent to avoid deserialization conflicts
 */
@Configuration
public class KafkaConsumerConfig {
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    /**
     * Common consumer configuration properties
     */
    private Map<String, Object> getCommonConsumerConfig() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, "db-sync-consumer-group");
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1); // Process one at a time
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.mine.ordersync.model");
        configProps.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        return configProps;
    }
    
    /**
     * Consumer factory for OrderEvent
     */
    @Bean
    public ConsumerFactory<String, OrderEvent> orderEventConsumerFactory() {
        Map<String, Object> configProps = getCommonConsumerConfig();
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, OrderEvent.class);
        
        return new DefaultKafkaConsumerFactory<>(configProps, 
                new StringDeserializer(), 
                new JsonDeserializer<>(OrderEvent.class, false));
    }
    
    /**
     * Consumer factory for TransactionEvent
     */
    @Bean
    public ConsumerFactory<String, TransactionEvent> transactionEventConsumerFactory() {
        Map<String, Object> configProps = getCommonConsumerConfig();
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        configProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, TransactionEvent.class);
        
        return new DefaultKafkaConsumerFactory<>(configProps, 
                new StringDeserializer(), 
                new JsonDeserializer<>(TransactionEvent.class, false));
    }
    
    /**
     * Listener container factory for OrderEvent
     */
    @Bean(name = "orderEventKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, OrderEvent> orderEventKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, OrderEvent> factory = 
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(orderEventConsumerFactory());
        
        // Manual acknowledgment
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        // Process one event at a time (synchronous)
        factory.setConcurrency(1);
        
        return factory;
    }
    
    /**
     * Listener container factory for TransactionEvent
     */
    @Bean(name = "transactionEventKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, TransactionEvent> transactionEventKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, TransactionEvent> factory = 
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(transactionEventConsumerFactory());
        
        // Manual acknowledgment
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        // Process one event at a time (synchronous)
        factory.setConcurrency(1);
        
        return factory;
    }
}
