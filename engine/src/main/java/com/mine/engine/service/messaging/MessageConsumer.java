package com.mine.engine.service.messaging;

import com.mine.engine.model.Message;

/**
 * Interface for message consumers.
 * Implementations handle message consumption from RabbitMQ, Kafka, or any other messaging system.
 */
public interface MessageConsumer {
    /**
     * Process a received message.
     * This method is called by the messaging system when a message is received.
     * 
     * @param message The message to process
     */
    void processMessage(Message message);
}

