package com.mine.ordersync.consumer;

import com.mine.ordersync.config.KafkaTopics;
import com.mine.ordersync.model.TransactionEvent;
import com.mine.ordersync.service.EventProcessingService;
import com.mine.ordersync.service.FailedEventLogger;
import com.mine.ordersync.service.ProcessingControlService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for transaction events
 * Processes events one by one synchronously
 * Halts on failure until manually resumed
 */
@Slf4j
@Component
public class TransactionEventConsumer {
    
    private final EventProcessingService eventProcessingService;
    private final ProcessingControlService processingControlService;
    private final FailedEventLogger failedEventLogger;
    
    public TransactionEventConsumer(EventProcessingService eventProcessingService,
                                    ProcessingControlService processingControlService,
                                    FailedEventLogger failedEventLogger) {
        this.eventProcessingService = eventProcessingService;
        this.processingControlService = processingControlService;
        this.failedEventLogger = failedEventLogger;
    }
    
    /**
     * Consume transaction events from Kafka
     * Process one event at a time, synchronously
     * Manual acknowledgment after successful processing
     */
    @KafkaListener(
        topics = KafkaTopics.TRANSACTION_DB_SYNC_TOPIC,
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "transactionEventKafkaListenerContainerFactory"
    )
    public void consumeTransactionEvent(
            @Payload TransactionEvent event,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        // Check if processing is enabled
        if (!processingControlService.isProcessingEnabled()) {
            log.warn("Processing is HALTED. Rejecting event. TransactionId: {}. Partition: {}, Offset: {}", 
                    event.getTransactionId(), partition, offset);
            // Don't acknowledge - message will be redelivered when processing resumes
            return;
        }
        
        log.info("Received transaction event from partition {} at offset {}: {}", 
                partition, offset, event.getTransactionId());
        
        try {
            // Process event synchronously (one by one)
            eventProcessingService.processTransactionEvent(event);
            
            // Acknowledge only after successful processing
            acknowledgment.acknowledge();
            log.info("Transaction event processed and acknowledged: {}", event.getTransactionId());
            
        } catch (Exception e) {
            // Log failed event
            failedEventLogger.logFailedTransactionEvent(event, e);
            
            // Halt processing
            String haltReason = String.format("Transaction event processing failed: TransactionId=%d, Error=%s", 
                    event.getTransactionId(), e.getMessage());
            processingControlService.haltProcessing(haltReason);
            
            // Don't acknowledge - message will be redelivered
            log.error("Transaction event processing failed and processing HALTED. Event will be redelivered after resume.", e);
        }
    }
}


