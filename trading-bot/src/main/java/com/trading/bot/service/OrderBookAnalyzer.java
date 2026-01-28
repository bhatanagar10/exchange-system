package com.trading.bot.service;

import com.trading.bot.service.OrderBookService.OrderBookData;
import com.trading.bot.service.OrderBookService.OrderLevel;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Service to analyze order book depth and metrics
 */
@Slf4j
@Service
public class OrderBookAnalyzer {

    @Data
    @Builder
    public static class OrderBookMetrics {
        private BigDecimal bidDepth;
        private BigDecimal askDepth;
        private double imbalance; // -1 to 1, positive = bullish, negative = bearish
        private BigDecimal spread;
        private BigDecimal spreadPercent;
        private BigDecimal bestBid;
        private BigDecimal bestAsk;
        private int bidOrderCount;
        private int askOrderCount;
    }

    /**
     * Analyze order book and return metrics
     */
    public OrderBookMetrics analyze(OrderBookData orderBook) {
        if (orderBook == null || orderBook.getBuyOrders().isEmpty() && orderBook.getSellOrders().isEmpty()) {
            return OrderBookMetrics.builder()
                    .bidDepth(BigDecimal.ZERO)
                    .askDepth(BigDecimal.ZERO)
                    .imbalance(0.0)
                    .spread(BigDecimal.ZERO)
                    .spreadPercent(BigDecimal.ZERO)
                    .bestBid(BigDecimal.ZERO)
                    .bestAsk(BigDecimal.ZERO)
                    .bidOrderCount(0)
                    .askOrderCount(0)
                    .build();
        }

        OrderLevel bestBid = orderBook.getBestBuyOrder();
        OrderLevel bestAsk = orderBook.getBestSellOrder();

        BigDecimal bestBidPrice = bestBid != null ? bestBid.getPrice() : BigDecimal.ZERO;
        BigDecimal bestAskPrice = bestAsk != null ? bestAsk.getPrice() : BigDecimal.ZERO;

        // Calculate depth (total quantity)
        BigDecimal bidDepth = calculateDepth(orderBook.getBuyOrders());
        BigDecimal askDepth = calculateDepth(orderBook.getSellOrders());

        // Calculate imbalance
        BigDecimal totalDepth = bidDepth.add(askDepth);
        double imbalance = 0.0;
        if (totalDepth.compareTo(BigDecimal.ZERO) > 0) {
            // (bidDepth - askDepth) / totalDepth
            // Range: -1 (all asks) to +1 (all bids)
            imbalance = bidDepth.subtract(askDepth)
                    .divide(totalDepth, 4, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        // Calculate spread
        BigDecimal spread = BigDecimal.ZERO;
        BigDecimal spreadPercent = BigDecimal.ZERO;
        if (bestBidPrice.compareTo(BigDecimal.ZERO) > 0 && bestAskPrice.compareTo(BigDecimal.ZERO) > 0) {
            spread = bestAskPrice.subtract(bestBidPrice);
            BigDecimal midPrice = bestBidPrice.add(bestAskPrice).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            if (midPrice.compareTo(BigDecimal.ZERO) > 0) {
                spreadPercent = spread.divide(midPrice, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }
        }

        return OrderBookMetrics.builder()
                .bidDepth(bidDepth)
                .askDepth(askDepth)
                .imbalance(imbalance)
                .spread(spread)
                .spreadPercent(spreadPercent)
                .bestBid(bestBidPrice)
                .bestAsk(bestAskPrice)
                .bidOrderCount(orderBook.getBuyOrders().size())
                .askOrderCount(orderBook.getSellOrders().size())
                .build();
    }

    /**
     * Calculate total depth (quantity) for orders
     */
    private BigDecimal calculateDepth(List<OrderLevel> orders) {
        return orders.stream()
                .map(order -> BigDecimal.valueOf(order.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculate depth within a price range (percentage from best price)
     */
    public BigDecimal calculateDepthInRange(List<OrderLevel> orders, BigDecimal bestPrice, double priceRangePercent) {
        if (orders.isEmpty() || bestPrice.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal range = bestPrice.multiply(BigDecimal.valueOf(priceRangePercent / 100.0));
        BigDecimal upperBound = bestPrice.add(range);
        BigDecimal lowerBound = bestPrice.subtract(range);

        return orders.stream()
                .filter(order -> {
                    BigDecimal price = order.getPrice();
                    return price.compareTo(lowerBound) >= 0 && price.compareTo(upperBound) <= 0;
                })
                .map(order -> BigDecimal.valueOf(order.getQuantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
