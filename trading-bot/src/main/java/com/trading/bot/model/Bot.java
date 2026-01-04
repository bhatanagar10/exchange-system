package com.trading.bot.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simplified Bot Model - In-Memory Storage Only
 * Represents a trading bot that trades aggressively to keep market active.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bot {

    private static final AtomicLong idCounter = new AtomicLong(1);
    
    private Long id;
    private Long userId;  // Exchange user ID (created via API)
    private String name;
    private String tradingPair;
    private BigDecimal balance;
    private BigDecimal assetBalance;
    private Boolean isActive;
    private LocalDateTime lastTradeAt;
    
    // Track active order - null means no active order
    private Boolean hasActiveBuyOrder;   // true if bot has a buy order in order book
    private Boolean hasActiveSellOrder;  // true if bot has a sell order in order book
    
    // Trading strategy type
    private String strategyType;  // "MATCHER", "AGGRESSIVE", "CONSERVATIVE", "TIMEOUT"
    
    /**
     * Generate ID for new bot
     */
    public void generateId() {
        if (this.id == null) {
            this.id = idCounter.getAndIncrement();
        }
    }
}
