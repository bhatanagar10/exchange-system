package com.trading.bot.service;

import com.trading.bot.config.TradingBotConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Market Data Service - Uses WebSocket data when available, falls back to HTTP API
 */
@Slf4j
@Service
public class MarketDataService {

    private final RestTemplate restTemplate;
    private final TradingBotConfig config;
    private final WebSocketMarketDataService webSocketMarketDataService;
    private final Map<String, BigDecimal> fallbackPrices = new HashMap<>();

    public MarketDataService(RestTemplate restTemplate, 
                            TradingBotConfig config,
                            WebSocketMarketDataService webSocketMarketDataService) {
        this.restTemplate = restTemplate;
        this.config = config;
        this.webSocketMarketDataService = webSocketMarketDataService;
        fallbackPrices.put("BTC/USDT", new BigDecimal("500.00"));
    }

    /**
     * Get current price for trading pair.
     * First tries WebSocket cache, then falls back to HTTP API
     */
    public BigDecimal getCurrentPrice(String tradingPair) {
        // Try WebSocket cache first
        if (webSocketMarketDataService.isConnected()) {
            BigDecimal cachedPrice = webSocketMarketDataService.getCurrentPrice(tradingPair);
            if (cachedPrice != null) {
                log.debug("Using WebSocket cached price for {}: {}", tradingPair, cachedPrice);
                return cachedPrice;
            }
        }
        
        // Fallback to HTTP API
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
                    log.debug("Fetched price via HTTP API for {}: {}", tradingPair, price);
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
