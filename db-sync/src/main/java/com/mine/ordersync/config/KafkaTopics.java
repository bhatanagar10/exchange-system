package com.mine.ordersync.config;

/**
 * Kafka topic names for database synchronization events
 * Separate from order placement topics
 */
public class KafkaTopics {
    
    public static final String ORDER_DB_SYNC_TOPIC = "order-db-sync";
    public static final String TRANSACTION_DB_SYNC_TOPIC = "transaction-db-sync";
}


