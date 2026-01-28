package com.trading.bot.service;

import com.trading.bot.config.TradingBotConfig;
import com.trading.bot.model.Bot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for risk management and position limits
 */
@Slf4j
@Service
public class RiskManagementService {

    private final TradingBotConfig config;
    
    // Track P&L for each bot: botId -> {initialBalance, currentBalance, totalPnL}
    private final Map<Long, BotPnL> botPnLMap = new ConcurrentHashMap<>();

    private static class BotPnL {
        BigDecimal initialBalance;
        BigDecimal currentBalance;
        BigDecimal totalPnL;
        int tradeCount;
        int winCount;
        
        BotPnL(BigDecimal initialBalance) {
            this.initialBalance = initialBalance;
            this.currentBalance = initialBalance;
            this.totalPnL = BigDecimal.ZERO;
            this.tradeCount = 0;
            this.winCount = 0;
        }
    }

    public RiskManagementService(TradingBotConfig config) {
        this.config = config;
    }

    /**
     * Initialize P&L tracking for a bot
     */
    public void initializeBot(Bot bot) {
        if (!botPnLMap.containsKey(bot.getId())) {
            botPnLMap.put(bot.getId(), new BotPnL(bot.getBalance()));
        }
    }

    /**
     * Check if order can be placed based on risk limits
     */
    public boolean canPlaceOrder(Bot bot, boolean isBuy, BigDecimal amount, BigDecimal price) {
        initializeBot(bot);

        // Check position limits
        if (exceedsPositionLimit(bot, isBuy, amount)) {
            log.warn("Order rejected: exceeds position limit for bot {}", bot.getName());
            return false;
        }

        // Check loss limits
        if (exceedsMaxLoss(bot)) {
            log.warn("Order rejected: exceeds max loss for bot {}", bot.getName());
            return false;
        }

        // Check order size limits
        BigDecimal orderValue = amount.multiply(price);
        BigDecimal maxOrderValue = config.getSimulation().getMaxOrderSize()
                .multiply(price);
        
        if (orderValue.compareTo(maxOrderValue) > 0) {
            log.warn("Order rejected: order value {} exceeds max {} for bot {}", 
                    orderValue, maxOrderValue, bot.getName());
            return false;
        }

        // Check minimum order size
        BigDecimal minOrderValue = config.getSimulation().getMinOrderSize()
                .multiply(price);
        if (orderValue.compareTo(minOrderValue) < 0) {
            log.warn("Order rejected: order value {} below minimum {} for bot {}", 
                    orderValue, minOrderValue, bot.getName());
            return false;
        }

        // Check balance sufficiency
        if (isBuy) {
            if (bot.getBalance() == null || bot.getBalance().compareTo(orderValue) < 0) {
                log.warn("Order rejected: insufficient balance for bot {}", bot.getName());
                return false;
            }
        } else {
            if (bot.getAssetBalance() == null || bot.getAssetBalance().compareTo(amount) < 0) {
                log.warn("Order rejected: insufficient asset balance for bot {}", bot.getName());
                return false;
            }
        }

        return true;
    }

    /**
     * Check if position exceeds limit
     */
    private boolean exceedsPositionLimit(Bot bot, boolean isBuy, BigDecimal amount) {
        // Simple check: don't allow orders larger than max order size
        BigDecimal maxSize = config.getSimulation().getMaxOrderSize();
        return amount.compareTo(maxSize) > 0;
    }

    /**
     * Check if bot has exceeded maximum loss
     */
    private boolean exceedsMaxLoss(Bot bot) {
        BotPnL pnl = botPnLMap.get(bot.getId());
        if (pnl == null) return false;

        BigDecimal lossPercent = calculateLossPercent(pnl);
        double maxLossPercent = config.getSafety().getMaxLossPercent();
        
        return lossPercent.compareTo(BigDecimal.valueOf(maxLossPercent)) > 0;
    }

    /**
     * Calculate loss percentage
     */
    private BigDecimal calculateLossPercent(BotPnL pnl) {
        if (pnl.initialBalance.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal loss = pnl.initialBalance.subtract(pnl.currentBalance);
        return loss.divide(pnl.initialBalance, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /**
     * Track a trade (update P&L)
     */
    public void trackTrade(Bot bot, BigDecimal entryPrice, BigDecimal exitPrice, 
                           BigDecimal quantity, boolean isBuy) {
        initializeBot(bot);
        BotPnL pnl = botPnLMap.get(bot.getId());
        
        BigDecimal tradePnL;
        if (isBuy) {
            // Bought at entryPrice, sold at exitPrice
            tradePnL = exitPrice.subtract(entryPrice).multiply(quantity);
        } else {
            // Sold at entryPrice, bought at exitPrice
            tradePnL = entryPrice.subtract(exitPrice).multiply(quantity);
        }
        
        pnl.totalPnL = pnl.totalPnL.add(tradePnL);
        pnl.currentBalance = pnl.currentBalance.add(tradePnL);
        pnl.tradeCount++;
        
        if (tradePnL.compareTo(BigDecimal.ZERO) > 0) {
            pnl.winCount++;
        }
        
        log.debug("Tracked trade for bot {}: P&L = {}, Total P&L = {}", 
                bot.getName(), tradePnL, pnl.totalPnL);
    }

    /**
     * Get current P&L for bot
     */
    public BigDecimal getCurrentPnL(Bot bot) {
        BotPnL pnl = botPnLMap.get(bot.getId());
        return pnl != null ? pnl.totalPnL : BigDecimal.ZERO;
    }

    /**
     * Get win rate for bot
     */
    public double getWinRate(Bot bot) {
        BotPnL pnl = botPnLMap.get(bot.getId());
        if (pnl == null || pnl.tradeCount == 0) return 0.0;
        return (double) pnl.winCount / pnl.tradeCount;
    }
}
