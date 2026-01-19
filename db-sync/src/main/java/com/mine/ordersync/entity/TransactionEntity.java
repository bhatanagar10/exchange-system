package com.mine.ordersync.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Transaction entity for database persistence
 */
@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_buy_order_id", columnList = "buy_order_id"),
    @Index(name = "idx_sell_order_id", columnList = "sell_order_id"),
    @Index(name = "idx_buyer_id", columnList = "buyer_id"),
    @Index(name = "idx_seller_id", columnList = "seller_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    
    @Column(name = "transaction_id", nullable = false, unique = true)
    private Long transactionId;
    
    @Column(name = "buy_order_id", nullable = false, length = 100)
    private String buyOrderId;
    
    @Column(name = "sell_order_id", nullable = false, length = 100)
    private String sellOrderId;
    
    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;
    
    @Column(name = "seller_id", nullable = false)
    private Long sellerId;
    
    @Column(name = "execution_price", nullable = false)
    private Double executionPrice;
    
    @Column(name = "execution_quantity", nullable = false)
    private Long executionQuantity;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}


