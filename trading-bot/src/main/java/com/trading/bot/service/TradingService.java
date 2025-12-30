package com.trading.bot.service;

import com.trading.bot.config.TradingBotConfig;
import com.trading.bot.model.Bot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

/**
 * Trading Service - Bots trade intelligently with different strategies.
 * Each bot can only have ONE active order at a time.
 */
@Slf4j
@Service
public class TradingService {

    private final InMemoryBotStorage botStorage;
    private final MarketDataService marketDataService;
    private final OrderService orderService;
    private final BalanceService balanceService;
    private final OrderBookService orderBookService;
    private final CancelOrderService cancelOrderService;
    private final TradingBotConfig config;
    private final Random random = new Random();

    public TradingService(InMemoryBotStorage botStorage,
                         MarketDataService marketDataService,
                         OrderService orderService,
                         BalanceService balanceService,
                         OrderBookService orderBookService,
                         CancelOrderService cancelOrderService,
                         TradingBotConfig config) {
        this.botStorage = botStorage;
        this.marketDataService = marketDataService;
        this.orderService = orderService;
        this.balanceService = balanceService;
        this.orderBookService = orderBookService;
        this.cancelOrderService = cancelOrderService;
        this.config = config;
    }

    /**
     * Main trading loop - runs every 2 seconds.
     */
    @Scheduled(fixedRate = 2000)
    public void executeTradingLoop() {
        List<Bot> activeBots = botStorage.findAll().stream()
                .filter(Bot::getIsActive)
                .toList();

        if (activeBots.isEmpty()) {
            return;
        }

        // Get order book once for all bots
        String tradingPair = activeBots.get(0).getTradingPair();
        OrderBookService.OrderBookData orderBook = orderBookService.getOrderBook(tradingPair);

        for (Bot bot : activeBots) {
            try {
                // Clear active order flags if order was placed more than 10 seconds ago
                // (assuming it was filled or cancelled)
                if (bot.getLastTradeAt() != null) {
                    long secondsSinceLastTrade = java.time.Duration.between(
                            bot.getLastTradeAt(), LocalDateTime.now()).getSeconds();
                    if (secondsSinceLastTrade > 10) {
                        bot.setHasActiveBuyOrder(false);
                        bot.setHasActiveSellOrder(false);
                        botStorage.save(bot);
                    }
                }
                
                // Execute strategy based on bot's strategy type
                String strategy = bot.getStrategyType() != null ? bot.getStrategyType() : "MATCHER";
                switch (strategy) {
                    case "AGGRESSIVE":
                        tradeAggressive(bot, orderBook);
                        break;
                    case "CONSERVATIVE":
                        tradeConservative(bot, orderBook);
                        break;
                    case "TIMEOUT":
                        tradeTimeout(bot, orderBook);
                        break;
                    case "MATCHER":
                    default:
                        tradeMatcher(bot, orderBook);
                        break;
                }
            } catch (Exception e) {
                log.error("Error trading for bot {}: {}", bot.getName(), e.getMessage(), e);
            }
        }
    }

    /**
     * MATCHER Strategy - Matches existing orders (original strategy).
     */
    private void tradeMatcher(Bot bot, OrderBookService.OrderBookData orderBook) {
        // Check and replenish balance if needed
        balanceService.checkAndReplenish(bot);
        bot = botStorage.findById(bot.getId()).orElse(bot);

        // Check if bot already has an active order
        boolean hasBuyOrder = Boolean.TRUE.equals(bot.getHasActiveBuyOrder());
        boolean hasSellOrder = Boolean.TRUE.equals(bot.getHasActiveSellOrder());

        if (hasBuyOrder || hasSellOrder) {
            return;
        }

        // Try to match existing orders
        OrderBookService.OrderLevel bestBuyOrder = orderBook.getBestBuyOrder();
        OrderBookService.OrderLevel bestSellOrder = orderBook.getBestSellOrder();

        boolean shouldBuy;
        BigDecimal price;
        BigDecimal amount;

        if (bestBuyOrder != null && bestSellOrder != null) {
            if (bestBuyOrder.getPrice().compareTo(bestSellOrder.getPrice()) >= 0) {
                shouldBuy = false;
                price = bestBuyOrder.getPrice();
                amount = BigDecimal.valueOf(Math.min(bestBuyOrder.getQuantity(), 50));
            } else {
                shouldBuy = true;
                price = bestSellOrder.getPrice();
                amount = BigDecimal.valueOf(Math.min(bestSellOrder.getQuantity(), 50));
            }
        } else if (bestBuyOrder != null) {
            shouldBuy = false;
            price = bestBuyOrder.getPrice();
            amount = BigDecimal.valueOf(Math.min(bestBuyOrder.getQuantity(), 50));
        } else if (bestSellOrder != null) {
            shouldBuy = true;
            price = bestSellOrder.getPrice();
            amount = BigDecimal.valueOf(Math.min(bestSellOrder.getQuantity(), 50));
        } else {
            BigDecimal currentPrice = marketDataService.getCurrentPrice(bot.getTradingPair());
            if (currentPrice == null) {
                currentPrice = new BigDecimal("500.00");
            }
            shouldBuy = random.nextBoolean();
            amount = BigDecimal.valueOf(10 + random.nextDouble() * 90).setScale(2, RoundingMode.HALF_UP);
            BigDecimal priceSpread = currentPrice.multiply(BigDecimal.valueOf(0.005));
            price = shouldBuy
                    ? currentPrice.subtract(priceSpread)
                    : currentPrice.add(priceSpread);
            price = price.setScale(2, RoundingMode.HALF_UP);
        }

        boolean success = orderService.placeOrder(bot, shouldBuy, amount, price);
        if (success) {
            if (shouldBuy) {
                bot.setHasActiveBuyOrder(true);
                bot.setHasActiveSellOrder(false);
            } else {
                bot.setHasActiveBuyOrder(false);
                bot.setHasActiveSellOrder(true);
            }
            bot.setLastTradeAt(LocalDateTime.now());
            botStorage.save(bot);
            log.info("[MATCHER] Bot {} placed {} order: {} @ ${}", 
                    bot.getName(), shouldBuy ? "BUY" : "SELL", amount, price);
        }
    }

    /**
     * AGGRESSIVE Strategy - Places large orders quickly, cancels and replaces frequently.
     */
    private void tradeAggressive(Bot bot, OrderBookService.OrderBookData orderBook) {
        balanceService.checkAndReplenish(bot);
        bot = botStorage.findById(bot.getId()).orElse(bot);

        boolean hasBuyOrder = Boolean.TRUE.equals(bot.getHasActiveBuyOrder());
        boolean hasSellOrder = Boolean.TRUE.equals(bot.getHasActiveSellOrder());

        // Aggressive bots cancel orders frequently (30% chance every cycle)
        if ((hasBuyOrder || hasSellOrder) && random.nextDouble() < 0.3) {
            boolean cancelled = cancelOrderService.cancelOrder(bot, hasBuyOrder);
            if (cancelled) {
                botStorage.save(bot);
                log.info("[AGGRESSIVE] Bot {} cancelled {} order", 
                        bot.getName(), hasBuyOrder ? "BUY" : "SELL");
            }
            return;
        }

        if (hasBuyOrder || hasSellOrder) {
            return;
        }

        // Place large orders (50-150 units)
        BigDecimal amount = BigDecimal.valueOf(50 + random.nextDouble() * 100)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal currentPrice = marketDataService.getCurrentPrice(bot.getTradingPair());
        if (currentPrice == null) {
            currentPrice = new BigDecimal("500.00");
        }

        // Aggressive pricing - tighter spreads (±0.2%)
        BigDecimal priceSpread = currentPrice.multiply(BigDecimal.valueOf(0.002));
        boolean shouldBuy = random.nextBoolean();
        BigDecimal price = shouldBuy
                ? currentPrice.subtract(priceSpread)
                : currentPrice.add(priceSpread);
        price = price.setScale(2, RoundingMode.HALF_UP);

        boolean success = orderService.placeOrder(bot, shouldBuy, amount, price);
        if (success) {
            if (shouldBuy) {
                bot.setHasActiveBuyOrder(true);
                bot.setHasActiveSellOrder(false);
            } else {
                bot.setHasActiveBuyOrder(false);
                bot.setHasActiveSellOrder(true);
            }
            bot.setLastTradeAt(LocalDateTime.now());
            botStorage.save(bot);
            log.info("[AGGRESSIVE] Bot {} placed large {} order: {} @ ${}", 
                    bot.getName(), shouldBuy ? "BUY" : "SELL", amount, price);
        }
    }

    /**
     * CONSERVATIVE Strategy - Places smaller orders, waits longer, less frequent cancels.
     */
    private void tradeConservative(Bot bot, OrderBookService.OrderBookData orderBook) {
        balanceService.checkAndReplenish(bot);
        bot = botStorage.findById(bot.getId()).orElse(bot);

        boolean hasBuyOrder = Boolean.TRUE.equals(bot.getHasActiveBuyOrder());
        boolean hasSellOrder = Boolean.TRUE.equals(bot.getHasActiveSellOrder());

        // Conservative bots rarely cancel (5% chance)
        if ((hasBuyOrder || hasSellOrder) && random.nextDouble() < 0.05) {
            boolean cancelled = cancelOrderService.cancelOrder(bot, hasBuyOrder);
            if (cancelled) {
                botStorage.save(bot);
                log.info("[CONSERVATIVE] Bot {} cancelled {} order", 
                        bot.getName(), hasBuyOrder ? "BUY" : "SELL");
            }
            return;
        }

        if (hasBuyOrder || hasSellOrder) {
            return;
        }

        // Place smaller orders (5-30 units)
        BigDecimal amount = BigDecimal.valueOf(5 + random.nextDouble() * 25)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal currentPrice = marketDataService.getCurrentPrice(bot.getTradingPair());
        if (currentPrice == null) {
            currentPrice = new BigDecimal("500.00");
        }

        // Conservative pricing - wider spreads (±1%)
        BigDecimal priceSpread = currentPrice.multiply(BigDecimal.valueOf(0.01));
        boolean shouldBuy = random.nextBoolean();
        BigDecimal price = shouldBuy
                ? currentPrice.subtract(priceSpread)
                : currentPrice.add(priceSpread);
        price = price.setScale(2, RoundingMode.HALF_UP);

        boolean success = orderService.placeOrder(bot, shouldBuy, amount, price);
        if (success) {
            if (shouldBuy) {
                bot.setHasActiveBuyOrder(true);
                bot.setHasActiveSellOrder(false);
            } else {
                bot.setHasActiveBuyOrder(false);
                bot.setHasActiveSellOrder(true);
            }
            bot.setLastTradeAt(LocalDateTime.now());
            botStorage.save(bot);
            log.info("[CONSERVATIVE] Bot {} placed small {} order: {} @ ${}", 
                    bot.getName(), shouldBuy ? "BUY" : "SELL", amount, price);
        }
    }

    /**
     * TIMEOUT Strategy - Cancels orders after 10 seconds and places new ones.
     */
    private void tradeTimeout(Bot bot, OrderBookService.OrderBookData orderBook) {
        balanceService.checkAndReplenish(bot);
        bot = botStorage.findById(bot.getId()).orElse(bot);

        boolean hasBuyOrder = Boolean.TRUE.equals(bot.getHasActiveBuyOrder());
        boolean hasSellOrder = Boolean.TRUE.equals(bot.getHasActiveSellOrder());

        // Check if order has been active for more than 10 seconds
        if ((hasBuyOrder || hasSellOrder) && bot.getLastTradeAt() != null) {
            long secondsSinceLastTrade = java.time.Duration.between(
                    bot.getLastTradeAt(), LocalDateTime.now()).getSeconds();
            
            if (secondsSinceLastTrade >= 10) {
                // Cancel the old order
                boolean cancelled = cancelOrderService.cancelOrder(bot, hasBuyOrder);
                if (cancelled) {
                    botStorage.save(bot);
                    log.info("[TIMEOUT] Bot {} cancelled {} order after {} seconds", 
                            bot.getName(), hasBuyOrder ? "BUY" : "SELL", secondsSinceLastTrade);
                    
                    // Immediately place a new order
                    placeNewTimeoutOrder(bot, orderBook);
                    return;
                }
            } else {
                // Order still fresh, keep it
                return;
            }
        }

        // No active order or just cancelled - place new one
        if (!hasBuyOrder && !hasSellOrder) {
            placeNewTimeoutOrder(bot, orderBook);
        }
    }

    /**
     * Place a new order for TIMEOUT strategy.
     */
    private void placeNewTimeoutOrder(Bot bot, OrderBookService.OrderBookData orderBook) {
        BigDecimal currentPrice = marketDataService.getCurrentPrice(bot.getTradingPair());
        if (currentPrice == null) {
            currentPrice = new BigDecimal("500.00");
        }

        // Try to match existing orders first, otherwise create new
        OrderBookService.OrderLevel bestBuyOrder = orderBook.getBestBuyOrder();
        OrderBookService.OrderLevel bestSellOrder = orderBook.getBestSellOrder();

        boolean shouldBuy;
        BigDecimal price;
        BigDecimal amount;

        if (bestBuyOrder != null && bestSellOrder != null) {
            // Match the better opportunity
            if (bestBuyOrder.getPrice().compareTo(bestSellOrder.getPrice()) >= 0) {
                shouldBuy = false;
                price = bestBuyOrder.getPrice();
                amount = BigDecimal.valueOf(Math.min(bestBuyOrder.getQuantity(), 40));
            } else {
                shouldBuy = true;
                price = bestSellOrder.getPrice();
                amount = BigDecimal.valueOf(Math.min(bestSellOrder.getQuantity(), 40));
            }
        } else if (bestBuyOrder != null) {
            shouldBuy = false;
            price = bestBuyOrder.getPrice();
            amount = BigDecimal.valueOf(Math.min(bestBuyOrder.getQuantity(), 40));
        } else if (bestSellOrder != null) {
            shouldBuy = true;
            price = bestSellOrder.getPrice();
            amount = BigDecimal.valueOf(Math.min(bestSellOrder.getQuantity(), 40));
        } else {
            // Create new order with medium spread
            shouldBuy = random.nextBoolean();
            amount = BigDecimal.valueOf(20 + random.nextDouble() * 60).setScale(2, RoundingMode.HALF_UP);
            BigDecimal priceSpread = currentPrice.multiply(BigDecimal.valueOf(0.005));
            price = shouldBuy
                    ? currentPrice.subtract(priceSpread)
                    : currentPrice.add(priceSpread);
            price = price.setScale(2, RoundingMode.HALF_UP);
        }

        boolean success = orderService.placeOrder(bot, shouldBuy, amount, price);
        if (success) {
            if (shouldBuy) {
                bot.setHasActiveBuyOrder(true);
                bot.setHasActiveSellOrder(false);
            } else {
                bot.setHasActiveBuyOrder(false);
                bot.setHasActiveSellOrder(true);
            }
            bot.setLastTradeAt(LocalDateTime.now());
            botStorage.save(bot);
            log.info("[TIMEOUT] Bot {} placed new {} order: {} @ ${}", 
                    bot.getName(), shouldBuy ? "BUY" : "SELL", amount, price);
        }
    }

    /**
     * Clear active order flag for a bot (called when order is filled/cancelled).
     */
    public void clearActiveOrder(Bot bot) {
        bot.setHasActiveBuyOrder(false);
        bot.setHasActiveSellOrder(false);
        botStorage.save(bot);
    }
}
