package com.trading.bot.model.dto;

import com.trading.bot.model.OrderExecutionType;
import lombok.*;

import java.util.UUID;

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

    private UUID userId;
    private double price;
    private long quantity;
    private OrderExecutionType orderExecutionType;
    private OrderType orderType;
    
    public enum OrderType {
        BUY, SELL
    }
}

