package com.trading.bot.service;

import com.trading.bot.config.TradingBotConfig;
import com.trading.bot.model.Bot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Order Service - Places orders via main service REST API.
 */
@Slf4j
@Service
public class OrderService {

    private final RestTemplate restTemplate;
    private final TradingBotConfig config;

    public OrderService(RestTemplate restTemplate, TradingBotConfig config) {
        this.restTemplate = restTemplate;
        this.config = config;
    }

    /**
     * Place an order for a bot via main service.
     */
    public boolean placeOrder(Bot bot, boolean isBuy, BigDecimal amount, BigDecimal price) {
        try {
            String baseUrl = config.getExchange().getApiUrl();
            String endpoint = isBuy ? "/orders/buy" : "/orders/sell";
            String url = baseUrl + endpoint;
            
            Map<String, Object> request = new HashMap<>();
            request.put("userId", bot.getUserId());
            request.put("price", price.doubleValue());
            request.put("quantity", amount.longValue());
            request.put("orderExecutionType", "LIMIT");
            
            Map response = restTemplate.postForObject(url, request, Map.class);
            
            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                log.debug("Order placed successfully for bot {}: {}", bot.getName(), response.get("message"));
                return true;
            } else {
                log.warn("Order placement failed for bot {}: {}", bot.getName(), 
                        response != null ? response.get("message") : "Unknown error");
                return false;
            }
            
        } catch (Exception e) {
            log.error("Failed to place order for bot {}: {}", bot.getName(), e.getMessage());
            return false;
        }
    }
}

