package com.trading.bot.service;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to track and calculate performance metrics
 */
@Slf4j
@Service
public class PerformanceMetricsService {

    private final Map<Long, BotPerformanceMetrics> metricsMap = new ConcurrentHashMap<>();

    @Data
    @Builder
    public static class BotPerformanceMetrics {
        private int totalTrades;
        private int winCount;
        private int lossCount;
        private double winRate;
        private BigDecimal totalPnL;
        private BigDecimal averagePnL;
        private BigDecimal maxProfit;
        private BigDecimal maxLoss;
        private BigDecimal sharpeRatio; // Simplified
    }

    /**
     * Record a trade
     */
    public void recordTrade(Long botId, BigDecimal pnl) {
        BotPerformanceMetrics metrics = metricsMap.computeIfAbsent(botId, 
                k -> BotPerformanceMetrics.builder()
                        .totalTrades(0)
                        .winCount(0)
                        .lossCount(0)
                        .totalPnL(BigDecimal.ZERO)
                        .maxProfit(BigDecimal.ZERO)
                        .maxLoss(BigDecimal.ZERO)
                        .build());

        metrics.setTotalTrades(metrics.getTotalTrades() + 1);
        metrics.setTotalPnL(metrics.getTotalPnL().add(pnl));

        if (pnl.compareTo(BigDecimal.ZERO) > 0) {
            metrics.setWinCount(metrics.getWinCount() + 1);
            if (pnl.compareTo(metrics.getMaxProfit()) > 0) {
                metrics.setMaxProfit(pnl);
            }
        } else if (pnl.compareTo(BigDecimal.ZERO) < 0) {
            metrics.setLossCount(metrics.getLossCount() + 1);
            if (pnl.compareTo(metrics.getMaxLoss()) < 0) {
                metrics.setMaxLoss(pnl);
            }
        }

        // Calculate win rate
        if (metrics.getTotalTrades() > 0) {
            metrics.setWinRate((double) metrics.getWinCount() / metrics.getTotalTrades());
        }

        // Calculate average P&L
        if (metrics.getTotalTrades() > 0) {
            metrics.setAveragePnL(metrics.getTotalPnL()
                    .divide(BigDecimal.valueOf(metrics.getTotalTrades()), 2, 
                            java.math.RoundingMode.HALF_UP));
        }

        log.debug("Updated metrics for bot {}: trades={}, winRate={}, totalPnL={}", 
                botId, metrics.getTotalTrades(), metrics.getWinRate(), metrics.getTotalPnL());
    }

    /**
     * Get performance metrics for a bot
     */
    public BotPerformanceMetrics getMetrics(Long botId) {
        return metricsMap.getOrDefault(botId, BotPerformanceMetrics.builder()
                .totalTrades(0)
                .winCount(0)
                .lossCount(0)
                .winRate(0.0)
                .totalPnL(BigDecimal.ZERO)
                .averagePnL(BigDecimal.ZERO)
                .maxProfit(BigDecimal.ZERO)
                .maxLoss(BigDecimal.ZERO)
                .build());
    }
}
