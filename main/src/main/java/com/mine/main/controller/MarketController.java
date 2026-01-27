package com.mine.main.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Market Controller - Proxies market data requests to engine service
 * Only main service can contact engine for security purposes
 */
@Slf4j
@RestController
@RequestMapping("/api/market")
@CrossOrigin(origins = "*")
public class MarketController {

    private final RestTemplate restTemplate;
    private final String engineApiUrl;

    public MarketController(
            RestTemplate restTemplate,
            @Value("${engine.api.url}") String engineApiUrl) {
        this.restTemplate = restTemplate;
        this.engineApiUrl = engineApiUrl;
        log.info("MarketController initialized with engine URL: {}", engineApiUrl);
    }

    /**
     * Get current price for a trading pair
     * GET /api/market/price/{pair}
     */
    @GetMapping("/price/{pair}")
    public ResponseEntity<Map<String, Object>> getCurrentPrice(@PathVariable String pair) {
        try {
            String url = engineApiUrl + "/market/price/" + pair;
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null) {
                // Extract currentPrice and return as price for compatibility
                if (response.containsKey("currentPrice")) {
                    response.put("price", response.get("currentPrice"));
                }
                log.debug("Fetched price for {}: {}", pair, response.get("currentPrice"));
                return ResponseEntity.ok(response);
            }
            
            return ResponseEntity.notFound().build();
            
        } catch (Exception e) {
            log.error("Failed to fetch price for {}: {}", pair, e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Get order book for a trading pair
     * GET /api/market/orderbook/{pair}
     */
    @GetMapping("/orderbook/{pair}")
    public ResponseEntity<Map<String, Object>> getOrderBook(@PathVariable String pair) {
        try {
            String url = engineApiUrl + "/market/orderbook/" + pair;
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null) {
                log.debug("Fetched orderbook for {}", pair);
                return ResponseEntity.ok(response);
            }
            
            return ResponseEntity.notFound().build();
            
        } catch (Exception e) {
            log.error("Failed to fetch orderbook for {}: {}", pair, e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Get recent trades for a trading pair
     * GET /api/market/trades/{pair}?limit=50
     */
    @GetMapping("/trades/{pair}")
    public ResponseEntity<Map<String, Object>> getRecentTrades(
            @PathVariable String pair,
            @RequestParam(required = false, defaultValue = "50") int limit) {
        try {
            String url = engineApiUrl + "/market/trades/" + pair + "?limit=" + limit;
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null) {
                log.debug("Fetched trades for {}", pair);
                return ResponseEntity.ok(response);
            }
            
            return ResponseEntity.notFound().build();
            
        } catch (Exception e) {
            log.error("Failed to fetch trades for {}: {}", pair, e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Get market summary for a trading pair
     * GET /api/market/summary/{pair}
     */
    @GetMapping("/summary/{pair}")
    public ResponseEntity<Map<String, Object>> getMarketSummary(@PathVariable String pair) {
        try {
            String url = engineApiUrl + "/market/summary/" + pair;
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null) {
                log.debug("Fetched market summary for {}", pair);
                return ResponseEntity.ok(response);
            }
            
            return ResponseEntity.notFound().build();
            
        } catch (Exception e) {
            log.error("Failed to fetch market summary for {}: {}", pair, e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }
}
