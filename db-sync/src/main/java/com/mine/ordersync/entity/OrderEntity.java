package com.mine.ordersync.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Order entity for database persistence
 */
@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_order_id", columnList = "order_id"),
    @Index(name = "idx_user_id", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    
    @Column(name = "order_id", nullable = false, unique = true, length = 100)
    private String orderId;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(name = "order_type", nullable = false, length = 10)
    private String orderType; // BUY, SELL
    
    @Column(name = "execution_type", nullable = false, length = 20)
    private String executionType; // MARKET, LIMIT
    
    @Column(name = "price", nullable = false)
    private Double price;
    
    @Column(name = "quantity", nullable = false)
    private Long quantity;
    
    @Column(name = "original_quantity", nullable = false)
    private Long originalQuantity;
    
    @Column(name = "status", nullable = false, length = 20)
    private String status; // PENDING, PARTIALLY_FILLED, FILLED, CANCELLED
    
    @Column(name = "market", nullable = false, length = 50)
    private String market; // BTC, ETH
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}


