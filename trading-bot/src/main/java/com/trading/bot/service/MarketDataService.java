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
            
            if (response != null) {
                // Try to get price (mapped from currentPrice by main service)
                Object priceObj = response.get("price");
                if (priceObj == null) {
                    // Fallback to currentPrice if price is not available
                    priceObj = response.get("currentPrice");
                }
                
                if (priceObj instanceof Number) {
                    BigDecimal price = BigDecimal.valueOf(((Number) priceObj).doubleValue());
                    log.debug("Fetched price for {}: {}", tradingPair, price);
                    return price;
                }
            }
            
        } catch (Exception e) {
            log.warn("Failed to fetch price for {}: {}. Using fallback.", tradingPair, e.getMessage());
        }
        
        // Fallback to default price
        return fallbackPrices.getOrDefault(tradingPair, new BigDecimal("500.00"));
    }
}
