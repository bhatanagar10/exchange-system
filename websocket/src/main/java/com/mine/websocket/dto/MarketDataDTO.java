package com.mine.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Comprehensive market data DTO received from engine
 * Must match engine.dto.MarketDataDTO structure
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketDataDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    
    // Order book data
    private List<OrderDTO> buyOrders;
    private List<OrderDTO> sellOrders;
    
    // Price data
    private String pair;
    private Double currentPrice;
    private Double bestBid;
    private Double bestAsk;
    private Double spread;
    private Double lastTradePrice;
    
    // Market summary
    private Integer buyOrderCount;
    private Integer sellOrderCount;
    private Long bidDepth;
    private Long askDepth;
    private Double imbalance;
    private Integer totalTrades;
    
    // Timestamp
    private Long timestamp;
}
