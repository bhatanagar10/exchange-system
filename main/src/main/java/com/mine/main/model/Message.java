package com.mine.main.model;

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
    Long timestamp;
    String idempotencyKey;
}
