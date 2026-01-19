package com.mine.engine.service;

import com.mine.engine.config.KafkaConfig;
import com.mine.engine.model.Order;
import com.mine.engine.model.Transaction;
import com.mine.engine.model.dto.OrderEvent;
import com.mine.engine.model.dto.TransactionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for publishing order and transaction events to Kafka for database synchronization
 * These events are consumed by the order-sync service
 * Uses separate topics from order placement events
 */
@Slf4j
@Service
public class DatabaseSyncEventPublisher {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    public DatabaseSyncEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    
    /**
     * Publish order event to Kafka
     */
    public void publishOrderEvent(Order order, OrderEvent.EventType eventType, String status) {
        try {
            OrderEvent event = OrderEvent.builder()
                    .eventType(eventType)
                    .orderId(order.getId())
                    .userId(order.getUserId())
                    .orderType(convertOrderType(order.getType()))
                    .executionType(convertExecutionType(order.getExecutionType()))
                    .price(order.getPrice())
                    .quantity(order.getQuantity())
                    .originalQuantity(order.getOriginalQuantity())
                    .status(status)
                    .market(convertMarket(order.getMarket()))
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            kafkaTemplate.send(KafkaConfig.ORDER_DB_SYNC_TOPIC, event);
            log.debug("Published order DB sync event to Kafka: {} - {}", eventType, order.getId());
            
        } catch (Exception e) {
            // Never throw exceptions that could block the trading engine
            log.error("Error publishing order event to Kafka: {}", order.getId(), e);
        }
    }
    
    /**
     * Publish transaction event to Kafka
     */
    public void publishTransactionEvent(Transaction transaction) {
        try {
            TransactionEvent event = TransactionEvent.builder()
                    .transactionId(transaction.getId())
                    .buyOrderId(transaction.getBuyOrderId())
                    .sellOrderId(transaction.getSellOrderId())
                    .buyerId(transaction.getBuyerId())
                    .sellerId(transaction.getSellerId())
                    .executionPrice(transaction.getExecutionPrice())
                    .executionQuantity(transaction.getExecutionQuantity())
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            kafkaTemplate.send(KafkaConfig.TRANSACTION_DB_SYNC_TOPIC, event);
            log.debug("Published transaction DB sync event to Kafka: {}", transaction.getId());
            
        } catch (Exception e) {
            // Never throw exceptions that could block the trading engine
            log.error("Error publishing transaction event to Kafka: {}", transaction.getId(), e);
        }
    }
    
    private OrderEvent.OrderType convertOrderType(com.mine.engine.model.OrderType orderType) {
        return orderType == com.mine.engine.model.OrderType.BUY 
                ? OrderEvent.OrderType.BUY 
                : OrderEvent.OrderType.SELL;
    }
    
    private OrderEvent.ExecutionType convertExecutionType(com.mine.engine.model.OrderExecutionType executionType) {
        return executionType == com.mine.engine.model.OrderExecutionType.MARKET 
                ? OrderEvent.ExecutionType.MARKET 
                : OrderEvent.ExecutionType.LIMIT;
    }
    
    private OrderEvent.Market convertMarket(com.mine.engine.model.Market market) {
        return market == com.mine.engine.model.Market.BTC 
                ? OrderEvent.Market.BTC 
                : OrderEvent.Market.ETH;
    }
}
