package com.mine.engine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Message {
    UUID userId;
    double price;
    long quantity;
    OrderExecutionType orderExecutionType;
    OrderType orderType;
}
