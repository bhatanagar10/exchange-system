package com.mine.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * DTO for order data sent to WebSocket clients
 * Contains price, quantity, and userId
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private double price;
    private long quantity;
    private UUID userId;
}

