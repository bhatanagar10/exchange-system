package com.trading.bot.service;

import com.trading.bot.config.TradingBotConfig;
import com.trading.bot.model.Bot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service to cancel orders via exchange API.
 */
@Slf4j
@Service
public class CancelOrderService {

    private final RestTemplate restTemplate;
    private final TradingBotConfig config;

    public CancelOrderService(RestTemplate restTemplate, TradingBotConfig config) {
        this.restTemplate = restTemplate;
        this.config = config;
    }

    /**
     * Cancel an order for a bot.
     */
    public boolean cancelOrder(Bot bot, boolean isBuyOrder) {
        try {
            String baseUrl = config.getExchange().getApiUrl();
            String cancelUrl = baseUrl + "/orders/" + bot.getUserId() + "/cancel";
            
            Map<String, String> request = new HashMap<>();
            request.put("orderType", isBuyOrder ? "BUY" : "SELL");
            
            Map<String, Object> response = restTemplate.postForObject(cancelUrl, request, Map.class);
            
            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                // Clear active order flags
                bot.setHasActiveBuyOrder(false);
                bot.setHasActiveSellOrder(false);
                
                log.info("Cancelled {} order for bot {}", isBuyOrder ? "BUY" : "SELL", bot.getName());
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.warn("Failed to cancel order for bot {}: {}", bot.getName(), e.getMessage());
            return false;
        }
    }
}

