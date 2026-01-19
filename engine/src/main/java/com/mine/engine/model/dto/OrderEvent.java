package com.mine.engine.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event model for order creation/update
 * Published to Kafka for database synchronization
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEvent {
    
    public enum EventType {
        ORDER_CREATED,
        ORDER_UPDATED,
        ORDER_CANCELLED,
        ORDER_FILLED
    }
    
    public enum OrderType {
        BUY, SELL
    }
    
    public enum ExecutionType {
        MARKET, LIMIT
    }
    
    public enum Market {
        BTC, ETH
    }
    
    private EventType eventType;
    private String orderId; // Format: ORD-{userId}-{timestamp}
    private Long userId;
    private OrderType orderType;
    private ExecutionType executionType;
    private Double price;
    private Long quantity;
    private Long originalQuantity;
    private String status; // PENDING, PARTIALLY_FILLED, FILLED, CANCELLED
    private Market market;
    private Long timestamp;
}
