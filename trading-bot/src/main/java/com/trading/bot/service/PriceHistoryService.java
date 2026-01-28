package com.trading.bot.service;

import com.trading.bot.config.TradingBotConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to track price history and calculate trends
 */
@Slf4j
@Service
public class PriceHistoryService {

    private final MarketDataService marketDataService;
    private final WebSocketMarketDataService webSocketMarketDataService;
    private final TradingBotConfig config;
    
    // Price history: tradingPair -> List<PricePoint>
    private final Map<String, List<PricePoint>> priceHistory = new ConcurrentHashMap<>();
    private static final int MAX_HISTORY = 1000;

    @Data
    public static class PricePoint {
        private final BigDecimal price;
        private final LocalDateTime timestamp;
    }

    public enum Trend {
        BULLISH, BEARISH, SIDEWAYS
    }

    public PriceHistoryService(MarketDataService marketDataService, 
                              WebSocketMarketDataService webSocketMarketDataService,
                              TradingBotConfig config) {
        this.marketDataService = marketDataService;
        this.webSocketMarketDataService = webSocketMarketDataService;
        this.config = config;
    }

    /**
     * Update price history periodically
     * Uses WebSocket data when available for real-time updates
     */
    @Scheduled(fixedRate = 1000)
    public void updatePriceHistory() {
        // Update for all known trading pairs
        priceHistory.keySet().forEach(pair -> {
            BigDecimal price = null;
            
            // Try WebSocket cache first
            if (webSocketMarketDataService.isConnected()) {
                price = webSocketMarketDataService.getCurrentPrice(pair);
            }
            
            // Fallback to HTTP API if WebSocket not available
            if (price == null) {
                price = marketDataService.getCurrentPrice(pair);
            }
            
            if (price != null) {
                addPricePoint(pair, price);
            }
        });
    }

    /**
     * Initialize price history for a trading pair
     */
    public void initializePriceHistory(String tradingPair) {
        if (!priceHistory.containsKey(tradingPair)) {
            priceHistory.put(tradingPair, new ArrayList<>());
            // Fetch initial price
            BigDecimal price = marketDataService.getCurrentPrice(tradingPair);
            if (price != null) {
                addPricePoint(tradingPair, price);
            }
        }
    }

    private void addPricePoint(String tradingPair, BigDecimal price) {
        List<PricePoint> history = priceHistory.computeIfAbsent(tradingPair, k -> new ArrayList<>());
        history.add(new PricePoint(price, LocalDateTime.now()));
        
        // Keep only last MAX_HISTORY points
        if (history.size() > MAX_HISTORY) {
            history.remove(0);
        }
    }

    /**
     * Calculate moving average
     */
    public BigDecimal calculateMA(String tradingPair, int period) {
        List<PricePoint> history = priceHistory.get(tradingPair);
        if (history == null || history.size() < period) {
            return marketDataService.getCurrentPrice(tradingPair);
        }

        List<PricePoint> recent = history.subList(Math.max(0, history.size() - period), history.size());
        BigDecimal sum = recent.stream()
                .map(PricePoint::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return sum.divide(BigDecimal.valueOf(recent.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Detect trend by comparing short and long MAs
     */
    public Trend detectTrend(String tradingPair) {
        initializePriceHistory(tradingPair);
        
        int shortPeriod = config.getStrategy().getTrendMaShort();
        int longPeriod = config.getStrategy().getTrendMaLong();
        
        BigDecimal shortMA = calculateMA(tradingPair, shortPeriod);
        BigDecimal longMA = calculateMA(tradingPair, longPeriod);
        
        if (shortMA.compareTo(longMA) > 0) {
            return Trend.BULLISH;
        } else if (shortMA.compareTo(longMA) < 0) {
            return Trend.BEARISH;
        } else {
            return Trend.SIDEWAYS;
        }
    }

    /**
     * Calculate price momentum (rate of change)
     */
    public double calculateMomentum(String tradingPair) {
        List<PricePoint> history = priceHistory.get(tradingPair);
        if (history == null || history.size() < 2) {
            return 0.0;
        }

        BigDecimal currentPrice = history.get(history.size() - 1).getPrice();
        int lookback = Math.min(10, history.size() - 1);
        BigDecimal pastPrice = history.get(history.size() - 1 - lookback).getPrice();
        
        if (pastPrice.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        
        return (currentPrice.doubleValue() - pastPrice.doubleValue()) / pastPrice.doubleValue();
    }

    /**
     * Calculate volatility (standard deviation of returns)
     */
    public double calculateVolatility(String tradingPair) {
        List<PricePoint> history = priceHistory.get(tradingPair);
        if (history == null || history.size() < 2) {
            return 0.0;
        }

        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < history.size(); i++) {
            BigDecimal prevPrice = history.get(i - 1).getPrice();
            BigDecimal currPrice = history.get(i).getPrice();
            if (prevPrice.compareTo(BigDecimal.ZERO) > 0) {
                double ret = (currPrice.doubleValue() - prevPrice.doubleValue()) / prevPrice.doubleValue();
                returns.add(ret);
            }
        }

        if (returns.isEmpty()) return 0.0;

        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = returns.stream()
                .mapToDouble(r -> Math.pow(r - mean, 2))
                .average()
                .orElse(0.0);
        
        return Math.sqrt(variance);
    }

    /**
     * Get current price from history
     */
    public BigDecimal getCurrentPrice(String tradingPair) {
        List<PricePoint> history = priceHistory.get(tradingPair);
        if (history == null || history.isEmpty()) {
            return marketDataService.getCurrentPrice(tradingPair);
        }
        return history.get(history.size() - 1).getPrice();
    }
}
