package com.mine.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Message {
    Long userId;
    double price;
    long quantity;
    OrderExecutionType orderExecutionType;
    OrderType orderType;
    Long timestamp; // Timestamp when order was placed (datetime at which order was placed)
    String idempotencyKey; // Idempotency key to prevent duplicate order processing
}
