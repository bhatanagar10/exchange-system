package com.mine.ordersync.service;

import com.mine.ordersync.entity.OrderEntity;
import com.mine.ordersync.entity.TransactionEntity;
import com.mine.ordersync.model.OrderEvent;
import com.mine.ordersync.model.TransactionEvent;
import com.mine.ordersync.repository.OrderRepository;
import com.mine.ordersync.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for processing events and syncing to database
 */
@Slf4j
@Service
public class EventProcessingService {
    
    private final OrderRepository orderRepository;
    private final TransactionRepository transactionRepository;
    
    public EventProcessingService(OrderRepository orderRepository, 
                                  TransactionRepository transactionRepository) {
        this.orderRepository = orderRepository;
        this.transactionRepository = transactionRepository;
    }
    
    /**
     * Process order event synchronously
     * One event at a time, within transaction
     */
    @Transactional
    public void processOrderEvent(OrderEvent event) {
        log.info("Processing order event: {} for orderId: {}", event.getEventType(), event.getOrderId());
        
        try {
            switch (event.getEventType()) {
                case ORDER_CREATED:
                case ORDER_UPDATED:
                    saveOrUpdateOrder(event);
                    break;
                case ORDER_CANCELLED:
                case ORDER_FILLED:
                    updateOrderStatus(event);
                    break;
                default:
                    log.warn("Unknown order event type: {}", event.getEventType());
            }
            
            log.info("Successfully processed order event: {} for orderId: {}", 
                    event.getEventType(), event.getOrderId());
            
        } catch (Exception e) {
            log.error("Error processing order event: {} for orderId: {}", 
                    event.getEventType(), event.getOrderId(), e);
            throw e; // Re-throw to trigger halt
        }
    }
    
    /**
     * Process transaction event synchronously
     */
    @Transactional
    public void processTransactionEvent(TransactionEvent event) {
        log.info("Processing transaction event for transactionId: {}", event.getTransactionId());
        
        try {
            // Check if transaction already exists
            if (transactionRepository.existsByTransactionId(event.getTransactionId())) {
                log.debug("Transaction {} already exists, skipping", event.getTransactionId());
                return;
            }
            
            // Create transaction entity
            TransactionEntity entity = new TransactionEntity();
            entity.setTransactionId(event.getTransactionId());
            entity.setBuyOrderId(event.getBuyOrderId());
            entity.setSellOrderId(event.getSellOrderId());
            entity.setBuyerId(event.getBuyerId());
            entity.setSellerId(event.getSellerId());
            entity.setExecutionPrice(event.getExecutionPrice());
            entity.setExecutionQuantity(event.getExecutionQuantity());
            
            transactionRepository.save(entity);
            
            log.info("Successfully saved transaction: {}", event.getTransactionId());
            
        } catch (Exception e) {
            log.error("Error processing transaction event: {}", event.getTransactionId(), e);
            throw e; // Re-throw to trigger halt
        }
    }
    
    private void saveOrUpdateOrder(OrderEvent event) {
        OrderEntity entity = orderRepository.findByOrderId(event.getOrderId())
                .orElse(new OrderEntity());
        
        entity.setOrderId(event.getOrderId());
        entity.setUserId(event.getUserId());
        entity.setOrderType(event.getOrderType().name());
        entity.setExecutionType(event.getExecutionType().name());
        entity.setPrice(event.getPrice());
        entity.setQuantity(event.getQuantity());
        entity.setOriginalQuantity(event.getOriginalQuantity());
        entity.setStatus(event.getStatus());
        entity.setMarket(event.getMarket().name());
        
        if (entity.getId() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        
        orderRepository.save(entity);
    }
    
    private void updateOrderStatus(OrderEvent event) {
        OrderEntity entity = orderRepository.findByOrderId(event.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Order not found: " + event.getOrderId()));
        
        entity.setStatus(event.getStatus());
        entity.setQuantity(event.getQuantity());
        entity.setUpdatedAt(LocalDateTime.now());
        
        orderRepository.save(entity);
    }
}


