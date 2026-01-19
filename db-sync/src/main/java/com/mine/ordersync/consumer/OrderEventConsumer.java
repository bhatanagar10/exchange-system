package com.mine.ordersync.consumer;

import com.mine.ordersync.config.KafkaTopics;
import com.mine.ordersync.model.OrderEvent;
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
 * Kafka consumer for order events
 * Processes events one by one synchronously
 * Halts on failure until manually resumed
 */
@Slf4j
@Component
public class OrderEventConsumer {
    
    private final EventProcessingService eventProcessingService;
    private final ProcessingControlService processingControlService;
    private final FailedEventLogger failedEventLogger;
    
    public OrderEventConsumer(EventProcessingService eventProcessingService,
                              ProcessingControlService processingControlService,
                              FailedEventLogger failedEventLogger) {
        this.eventProcessingService = eventProcessingService;
        this.processingControlService = processingControlService;
        this.failedEventLogger = failedEventLogger;
    }
    
    /**
     * Consume order events from Kafka
     * Process one event at a time, synchronously
     * Manual acknowledgment after successful processing
     */
    @KafkaListener(
        topics = KafkaTopics.ORDER_DB_SYNC_TOPIC,
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "orderEventKafkaListenerContainerFactory"
    )
    public void consumeOrderEvent(
            @Payload OrderEvent event,
            Acknowledgment acknowledgment,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {
        
        // Check if processing is enabled
        if (!processingControlService.isProcessingEnabled()) {
            log.warn("Processing is HALTED. Rejecting event. OrderId: {}. Partition: {}, Offset: {}", 
                    event.getOrderId(), partition, offset);
            // Don't acknowledge - message will be redelivered when processing resumes
            return;
        }
        
        log.info("Received order event from partition {} at offset {}: {}", 
                partition, offset, event.getOrderId());
        
        try {
            // Process event synchronously (one by one)
            eventProcessingService.processOrderEvent(event);
            
            // Acknowledge only after successful processing
            acknowledgment.acknowledge();
            log.info("Order event processed and acknowledged: {}", event.getOrderId());
            
        } catch (Exception e) {
            // Log failed event
            failedEventLogger.logFailedOrderEvent(event, e);
            
            // Halt processing
            String haltReason = String.format("Order event processing failed: OrderId=%s, Error=%s", 
                    event.getOrderId(), e.getMessage());
            processingControlService.haltProcessing(haltReason);
            
            // Don't acknowledge - message will be redelivered
            log.error("Order event processing failed and processing HALTED. Event will be redelivered after resume.", e);
        }
    }
}


