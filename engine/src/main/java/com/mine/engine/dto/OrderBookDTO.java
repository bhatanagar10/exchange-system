package com.mine.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * DTO for order book data sent to WebSocket clients
 * Contains sorted lists of buy and sell orders
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderBookDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private List<OrderDTO> buyOrders;
    private List<OrderDTO> sellOrders;
}

