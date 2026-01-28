package com.trading.bot.service;

import com.trading.bot.config.TradingBotConfig;
import com.trading.bot.model.Bot;
import com.trading.bot.service.OrderBookAnalyzer.OrderBookMetrics;
import com.trading.bot.service.OrderBookService.OrderBookData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Helper service for trading calculations: order size, fill probability, adaptive spread
 */
@Slf4j
@Service
public class TradingHelperService {

    private final TradingBotConfig config;
    private final OrderBookAnalyzer orderBookAnalyzer;
    private final PriceHistoryService priceHistoryService;

    public TradingHelperService(TradingBotConfig config, 
                                OrderBookAnalyzer orderBookAnalyzer,
                                PriceHistoryService priceHistoryService) {
        this.config = config;
        this.orderBookAnalyzer = orderBookAnalyzer;
        this.priceHistoryService = priceHistoryService;
    }

    /**
     * Calculate optimal order size based on balance and order book depth
     */
    public BigDecimal calculateOptimalOrderSize(Bot bot, boolean isBuy, BigDecimal price, OrderBookData orderBook) {
        BigDecimal maxSize;
        
        if (isBuy) {
            // Based on cash balance
            BigDecimal availableCash = bot.getBalance() != null ? bot.getBalance() : BigDecimal.ZERO;
            maxSize = availableCash.divide(price, 2, RoundingMode.DOWN);
        } else {
            // Based on asset balance
            maxSize = bot.getAssetBalance() != null ? bot.getAssetBalance() : BigDecimal.ZERO;
        }
        
        // Consider order book depth
        OrderBookMetrics metrics = orderBookAnalyzer.analyze(orderBook);
        BigDecimal depthBasedSize;
        if (isBuy) {
            depthBasedSize = metrics.getAskDepth();
        } else {
            depthBasedSize = metrics.getBidDepth();
        }
        
        // Use smaller of the two, with some percentage (10% of depth)
        BigDecimal optimalSize = maxSize.min(depthBasedSize.multiply(BigDecimal.valueOf(0.1)));
        
        // Apply strategy-specific limits
        BigDecimal minSize = config.getSimulation().getMinOrderSize();
        BigDecimal maxSizeLimit = config.getSimulation().getMaxOrderSize();
        
        return optimalSize.max(minSize).min(maxSizeLimit);
    }


    /**
     * Estimate fill probability for an order
     */
    public double estimateFillProbability(BigDecimal orderPrice, boolean isBuy, OrderBookData orderBook) {
        if (orderBook == null) return 0.5; // Default if no order book
        
        List<com.trading.bot.service.OrderBookService.OrderLevel> matchingOrders;
        
        if (isBuy) {
            // For buy orders, check how many sell orders are at or below our price
            matchingOrders = orderBook.getSellOrders().stream()
                    .filter(order -> order.getPrice().compareTo(orderPrice) <= 0)
                    .toList();
        } else {
            // For sell orders, check buy orders at or above our price
            matchingOrders = orderBook.getBuyOrders().stream()
                    .filter(order -> order.getPrice().compareTo(orderPrice) >= 0)
                    .toList();
        }
        
        long matchingQuantity = matchingOrders.stream()
                .mapToLong(com.trading.bot.service.OrderBookService.OrderLevel::getQuantity)
                .sum();
        
        // Normalize to 0-1 probability (assuming 100 units = 100% probability)
        return Math.min(1.0, matchingQuantity / 100.0);
    }

    /**
     * Calculate adaptive spread based on volatility and market conditions
     */
    public BigDecimal calculateAdaptiveSpread(Bot bot, String strategy, OrderBookMetrics metrics) {
        BigDecimal baseSpread;
        
        // Get base spread based on strategy
        switch (strategy) {
            case "AGGRESSIVE":
                baseSpread = BigDecimal.valueOf(0.002); // 0.2%
                break;
            case "CONSERVATIVE":
                baseSpread = BigDecimal.valueOf(0.01); // 1%
                break;
            case "MARKET_MAKER":
                baseSpread = BigDecimal.valueOf(config.getStrategy().getMarketMakerSpread());
                break;
            default:
                baseSpread = BigDecimal.valueOf(0.005); // 0.5%
        }
        
        BigDecimal currentPrice = priceHistoryService.getCurrentPrice(bot.getTradingPair());
        if (currentPrice == null) {
            currentPrice = BigDecimal.valueOf(500);
        }
        
        // Adjust for volatility
        double volatility = priceHistoryService.calculateVolatility(bot.getTradingPair());
        BigDecimal adjustedSpread = baseSpread.multiply(BigDecimal.valueOf(1 + volatility));
        
        // Tighter spread when order book is deep
        BigDecimal totalDepth = metrics.getBidDepth().add(metrics.getAskDepth());
        BigDecimal depthThreshold = BigDecimal.valueOf(1000);
        if (totalDepth.compareTo(depthThreshold) > 0) {
            adjustedSpread = adjustedSpread.multiply(BigDecimal.valueOf(0.9));
        }
        
        // Wider spread when spread is already wide (less competition)
        if (metrics.getSpreadPercent().compareTo(BigDecimal.valueOf(1)) > 0) {
            adjustedSpread = adjustedSpread.multiply(BigDecimal.valueOf(1.1));
        }
        
        return adjustedSpread.setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * Adjust price for better fill probability
     */
    public BigDecimal adjustPriceForBetterFill(BigDecimal originalPrice, boolean isBuy, 
                                               OrderBookData orderBook, double minFillProb) {
        double currentProb = estimateFillProbability(originalPrice, isBuy, orderBook);
        
        if (currentProb >= minFillProb) {
            return originalPrice; // Already good enough
        }
        
        // Adjust price to improve fill probability
        BigDecimal adjustment = originalPrice.multiply(BigDecimal.valueOf(0.001)); // 0.1% adjustment
        
        if (isBuy) {
            // For buy orders, increase price to match asks
            return originalPrice.add(adjustment);
        } else {
            // For sell orders, decrease price to match bids
            return originalPrice.subtract(adjustment);
        }
    }
}
