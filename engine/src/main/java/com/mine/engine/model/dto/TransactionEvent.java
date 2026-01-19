package com.mine.engine.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Event model for transaction creation
 * Published to Kafka when trades execute for database synchronization
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEvent {
    
    private Long transactionId;
    private String buyOrderId;
    private String sellOrderId;
    private Long buyerId;
    private Long sellerId;
    private Double executionPrice;
    private Long executionQuantity;
    private Long timestamp;
}
