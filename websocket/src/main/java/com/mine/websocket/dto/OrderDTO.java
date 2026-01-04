package com.mine.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * DTO for order data received from engine
 * Must match engine.dto.OrderDTO structure
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private double price;
    private long quantity;
    private Long userId;
}
