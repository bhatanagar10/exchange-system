package com.trading.bot.model.dto;

import com.trading.bot.model.OrderExecutionType;
import lombok.*;

/**
 * Message format for sending orders to the exchange via Kafka.
 * Matches the engine's Message model structure exactly.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderMessage {

    private Long userId;
    private double price;
    private long quantity;
    private OrderExecutionType orderExecutionType;
    private OrderType orderType;
    private Long timestamp; // Timestamp when order was placed (datetime at which order was placed)
    private String idempotencyKey; // Idempotency key to prevent duplicate order processing
    
    public enum OrderType {
        BUY, SELL
    }
}

