package com.trading.bot.service;

import com.trading.bot.config.TradingBotConfig;
import com.trading.bot.model.Bot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to track order status and manage order state
 */
@Slf4j
@Service
public class OrderTrackingService {

    private final RestTemplate restTemplate;
    private final TradingBotConfig config;
    private final InMemoryBotStorage botStorage;
    
    // Track active orders: botId -> {orderId, timestamp, isBuy}
    private final Map<Long, OrderInfo> activeOrders = new ConcurrentHashMap<>();
    
    private static class OrderInfo {
        String orderId;
        LocalDateTime timestamp;
        boolean isBuy;
        
        OrderInfo(String orderId, LocalDateTime timestamp, boolean isBuy) {
            this.orderId = orderId;
            this.timestamp = timestamp;
            this.isBuy = isBuy;
        }
    }

    public OrderTrackingService(RestTemplate restTemplate, 
                                TradingBotConfig config,
                                InMemoryBotStorage botStorage) {
        this.restTemplate = restTemplate;
        this.config = config;
        this.botStorage = botStorage;
    }

    /**
     * Track a new order
     */
    public void trackOrder(Long botId, String orderId, boolean isBuy) {
        activeOrders.put(botId, new OrderInfo(orderId, LocalDateTime.now(), isBuy));
        log.debug("Tracking order {} for bot {}", orderId, botId);
    }

    /**
     * Clear order tracking for a bot
     */
    public void clearOrder(Long botId) {
        activeOrders.remove(botId);
        log.debug("Cleared order tracking for bot {}", botId);
    }

    /**
     * Check if bot has active order
     */
    public boolean hasActiveOrder(Long botId) {
        return activeOrders.containsKey(botId);
    }

    /**
     * Get order age in seconds
     */
    public long getOrderAge(Long botId) {
        OrderInfo info = activeOrders.get(botId);
        if (info == null) return 0;
        return java.time.Duration.between(info.timestamp, LocalDateTime.now()).getSeconds();
    }

    /**
     * Periodically check order status and update bot state
     */
    @Scheduled(fixedRate = 5000)
    public void checkOrderStatus() {
        for (Map.Entry<Long, OrderInfo> entry : activeOrders.entrySet()) {
            Long botId = entry.getKey();
            OrderInfo orderInfo = entry.getValue();
            
            botStorage.findById(botId).ifPresent(bot -> {
                long age = getOrderAge(botId);
                
                // If order is older than timeout, assume it's filled or cancelled
                int timeoutSeconds = Math.toIntExact(config.getExecution().getBotExecutionIntervalMs() / 1000 * 2);
                if (age > timeoutSeconds) {
                    log.debug("Order for bot {} is older than {} seconds, clearing state", botId, timeoutSeconds);
                    bot.setHasActiveBuyOrder(false);
                    bot.setHasActiveSellOrder(false);
                    botStorage.save(bot);
                    clearOrder(botId);
                }
            });
        }
    }
}
