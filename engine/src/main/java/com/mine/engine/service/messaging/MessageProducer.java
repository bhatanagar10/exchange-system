package com.mine.engine.service.messaging;

/**
 * Interface for message producers.
 * Implementations can use RabbitMQ, Kafka, or any other messaging system.
 */
public interface MessageProducer {
    /**
     * Send a message to the messaging system.
     * 
     * @param message The message object to send
     */
    void sendMessage(Object message);
}

