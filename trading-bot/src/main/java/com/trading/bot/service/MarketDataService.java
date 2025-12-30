package com.trading.bot.service;

import com.trading.bot.config.TradingBotConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Simplified Market Data Service - Fetches price from exchange API.
 */
@Slf4j
@Service
public class MarketDataService {

    private final RestTemplate restTemplate;
    private final TradingBotConfig config;
    private final Map<String, BigDecimal> fallbackPrices = new HashMap<>();

    public MarketDataService(RestTemplate restTemplate, TradingBotConfig config) {
        this.restTemplate = restTemplate;
        this.config = config;
        fallbackPrices.put("BTC/USDT", new BigDecimal("500.00"));
    }

    /**
     * Get current price for trading pair.
     */
    public BigDecimal getCurrentPrice(String tradingPair) {
        try {
            String baseUrl = config.getExchange().getApiUrl();
            String priceUrl = baseUrl + "/market/price/" + tradingPair;
            
            Map<String, Object> response = restTemplate.getForObject(priceUrl, Map.class);
            
            if (response != null && response.containsKey("price")) {
                Object priceObj = response.get("price");
                if (priceObj instanceof Number) {
                    return BigDecimal.valueOf(((Number) priceObj).doubleValue());
                }
            }
            
        } catch (Exception e) {
            log.warn("Failed to fetch price for {}: {}. Using fallback.", tradingPair, e.getMessage());
        }
        
        // Fallback to default price
        return fallbackPrices.getOrDefault(tradingPair, new BigDecimal("500.00"));
    }
}
