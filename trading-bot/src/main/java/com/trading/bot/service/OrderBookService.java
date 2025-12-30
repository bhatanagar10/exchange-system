package com.trading.bot.service;

import com.trading.bot.config.TradingBotConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;

/**
 * Service to fetch order book data from exchange.
 */
@Slf4j
@Service
public class OrderBookService {

    private final RestTemplate restTemplate;
    private final TradingBotConfig config;

    public OrderBookService(RestTemplate restTemplate, TradingBotConfig config) {
        this.restTemplate = restTemplate;
        this.config = config;
    }

    /**
     * Get order book data from exchange.
     * Returns map with "bids" (buy orders) and "asks" (sell orders).
     */
    public OrderBookData getOrderBook(String tradingPair) {
        try {
            String baseUrl = config.getExchange().getApiUrl();
            // Convert BTC/USDT to BTC for API
            String pair = tradingPair.split("/")[0];
            String orderBookUrl = baseUrl + "/market/orderbook/" + pair;
            
            Map<String, Object> response = restTemplate.getForObject(orderBookUrl, Map.class);
            
            if (response == null) {
                return new OrderBookData(Collections.emptyList(), Collections.emptyList());
            }
            
            List<Map<String, Object>> bids = (List<Map<String, Object>>) response.getOrDefault("bids", Collections.emptyList());
            List<Map<String, Object>> asks = (List<Map<String, Object>>) response.getOrDefault("asks", Collections.emptyList());
            
            List<OrderLevel> buyOrders = new ArrayList<>();
            for (Object bidObj : bids) {
                Map<String, Object> bid = (Map<String, Object>) bidObj;
                double price = ((Number) bid.get("price")).doubleValue();
                long quantity = ((Number) bid.get("quantity")).longValue();
                buyOrders.add(new OrderLevel(BigDecimal.valueOf(price), quantity));
            }
            
            // Sort buy orders by price descending (highest first)
            buyOrders.sort((a, b) -> b.getPrice().compareTo(a.getPrice()));
            
            List<OrderLevel> sellOrders = new ArrayList<>();
            for (Object askObj : asks) {
                Map<String, Object> ask = (Map<String, Object>) askObj;
                double price = ((Number) ask.get("price")).doubleValue();
                long quantity = ((Number) ask.get("quantity")).longValue();
                sellOrders.add(new OrderLevel(BigDecimal.valueOf(price), quantity));
            }
            
            // Sort sell orders by price ascending (lowest first)
            sellOrders.sort((a, b) -> a.getPrice().compareTo(b.getPrice()));
            
            return new OrderBookData(buyOrders, sellOrders);
            
        } catch (Exception e) {
            log.warn("Failed to fetch order book for {}: {}", tradingPair, e.getMessage());
            return new OrderBookData(Collections.emptyList(), Collections.emptyList());
        }
    }

    /**
     * Check if a user has active orders in the order book.
     * Note: This is a simplified check - we'll track orders in bot state instead.
     */
    public boolean hasUserOrder(UUID userId, String tradingPair, boolean isBuy) {
        // We'll track this in bot state instead of querying exchange
        // This method is kept for future use if needed
        return false;
    }

    /**
     * Order book data structure.
     */
    public static class OrderBookData {
        private final List<OrderLevel> buyOrders;  // Bids
        private final List<OrderLevel> sellOrders; // Asks

        public OrderBookData(List<OrderLevel> buyOrders, List<OrderLevel> sellOrders) {
            this.buyOrders = buyOrders;
            this.sellOrders = sellOrders;
        }

        public List<OrderLevel> getBuyOrders() {
            return buyOrders;
        }

        public List<OrderLevel> getSellOrders() {
            return sellOrders;
        }

        public OrderLevel getBestBuyOrder() {
            return buyOrders.isEmpty() ? null : buyOrders.get(0);
        }

        public OrderLevel getBestSellOrder() {
            return sellOrders.isEmpty() ? null : sellOrders.get(0);
        }
    }

    /**
     * Order level in order book.
     */
    public static class OrderLevel {
        private final BigDecimal price;
        private final long quantity;

        public OrderLevel(BigDecimal price, long quantity) {
            this.price = price;
            this.quantity = quantity;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public long getQuantity() {
            return quantity;
        }
    }
}

