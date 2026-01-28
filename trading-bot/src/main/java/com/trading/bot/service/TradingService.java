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
    private final PriceHistoryService priceHistoryService;
    private final OrderBookAnalyzer orderBookAnalyzer;
    private final RiskManagementService riskManagementService;
    private final CircuitBreakerService circuitBreakerService;
    private final TradingHelperService tradingHelperService;
    private final PerformanceMetricsService performanceMetricsService;
    private final OrderTrackingService orderTrackingService;
    private final WebSocketMarketDataService webSocketMarketDataService;
    private final TradingBotConfig config;
    private final Random random = new Random();

    public TradingService(InMemoryBotStorage botStorage,
                         MarketDataService marketDataService,
                         OrderService orderService,
                         BalanceService balanceService,
                         OrderBookService orderBookService,
                         CancelOrderService cancelOrderService,
                         PriceHistoryService priceHistoryService,
                         OrderBookAnalyzer orderBookAnalyzer,
                         RiskManagementService riskManagementService,
                         CircuitBreakerService circuitBreakerService,
                         TradingHelperService tradingHelperService,
                         PerformanceMetricsService performanceMetricsService,
                         OrderTrackingService orderTrackingService,
                         WebSocketMarketDataService webSocketMarketDataService,
                         TradingBotConfig config) {
        this.botStorage = botStorage;
        this.marketDataService = marketDataService;
        this.orderService = orderService;
        this.balanceService = balanceService;
        this.orderBookService = orderBookService;
        this.cancelOrderService = cancelOrderService;
        this.priceHistoryService = priceHistoryService;
        this.orderBookAnalyzer = orderBookAnalyzer;
        this.riskManagementService = riskManagementService;
        this.circuitBreakerService = circuitBreakerService;
        this.tradingHelperService = tradingHelperService;
        this.performanceMetricsService = performanceMetricsService;
        this.orderTrackingService = orderTrackingService;
        this.webSocketMarketDataService = webSocketMarketDataService;
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
        // Try WebSocket cache first, then fall back to HTTP API
        String tradingPair = activeBots.get(0).getTradingPair();
        OrderBookService.OrderBookData orderBook = orderBookService.getOrderBook(tradingPair);
        
        // Also get WebSocket market data for enhanced metrics
        com.trading.bot.dto.MarketDataDTO webSocketMarketData = webSocketMarketDataService.getMarketData();

        for (Bot bot : activeBots) {
            try {
                // Check circuit breaker
                if (circuitBreakerService.isCircuitOpen(bot.getId().toString())) {
                    log.debug("Circuit breaker open for bot {}, skipping", bot.getName());
                    continue;
                }
                
                // Initialize price history for trading pair
                priceHistoryService.initializePriceHistory(bot.getTradingPair());
                
                // Clear active order flags using order tracking service
                if (!orderTrackingService.hasActiveOrder(bot.getId())) {
                    bot.setHasActiveBuyOrder(false);
                    bot.setHasActiveSellOrder(false);
                    botStorage.save(bot);
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
                    case "MARKET_MAKER":
                        tradeMarketMaker(bot, orderBook);
                        break;
                    case "TREND_FOLLOWING":
                        tradeTrendFollowing(bot, orderBook);
                        break;
                    case "MEAN_REVERSION":
                        tradeMeanReversion(bot, orderBook);
                        break;
                    case "MARKET_ACTIVITY":
                        tradeMarketActivity(bot, orderBook);
                        break;
                    case "ORDER_BOOK_BALANCER":
                        tradeOrderBookBalancer(bot, orderBook);
                        break;
                    case "MATCHER":
                    default:
                        tradeMatcher(bot, orderBook);
                        break;
                }
            } catch (Exception e) {
                log.error("Error trading for bot {}: {}", bot.getName(), e.getMessage(), e);
                circuitBreakerService.recordError(bot.getId().toString());
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
            BigDecimal currentPrice = priceHistoryService.getCurrentPrice(bot.getTradingPair());
            if (currentPrice == null) {
                currentPrice = marketDataService.getCurrentPrice(bot.getTradingPair());
                if (currentPrice == null) {
                    currentPrice = new BigDecimal("500.00");
                }
            }
            shouldBuy = random.nextBoolean();
            OrderBookAnalyzer.OrderBookMetrics metrics = orderBookAnalyzer.analyze(orderBook);
            BigDecimal spreadPercent = tradingHelperService.calculateAdaptiveSpread(bot, "MATCHER", metrics);
            BigDecimal spreadAmount = currentPrice.multiply(spreadPercent);
            price = shouldBuy
                    ? currentPrice.subtract(spreadAmount)
                    : currentPrice.add(spreadAmount);
            price = price.setScale(2, RoundingMode.HALF_UP);
            amount = tradingHelperService.calculateOptimalOrderSize(bot, shouldBuy, price, orderBook);
        }

        // Risk check
        if (!riskManagementService.canPlaceOrder(bot, shouldBuy, amount, price)) {
            return;
        }
        
        // Estimate fill probability and adjust price if needed
        double fillProb = tradingHelperService.estimateFillProbability(price, shouldBuy, orderBook);
        if (fillProb < 0.3) {
            price = tradingHelperService.adjustPriceForBetterFill(price, shouldBuy, orderBook, 0.3);
        }
        
        String orderId = orderService.placeOrder(bot, shouldBuy, amount, price);
        if (orderId != null) {
            circuitBreakerService.recordSuccess(bot.getId().toString());
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
        } else {
            circuitBreakerService.recordError(bot.getId().toString());
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

        BigDecimal currentPrice = priceHistoryService.getCurrentPrice(bot.getTradingPair());
        if (currentPrice == null) {
            currentPrice = marketDataService.getCurrentPrice(bot.getTradingPair());
            if (currentPrice == null) {
                currentPrice = new BigDecimal("500.00");
            }
        }

        OrderBookAnalyzer.OrderBookMetrics metrics = orderBookAnalyzer.analyze(orderBook);
        BigDecimal spreadPercent = tradingHelperService.calculateAdaptiveSpread(bot, "AGGRESSIVE", metrics);
        BigDecimal spreadAmount = currentPrice.multiply(spreadPercent);
        
        boolean shouldBuy = random.nextBoolean();
        BigDecimal price = shouldBuy
                ? currentPrice.subtract(spreadAmount)
                : currentPrice.add(spreadAmount);
        price = price.setScale(2, RoundingMode.HALF_UP);
        
        // Calculate optimal order size (large orders for aggressive)
        BigDecimal amount = tradingHelperService.calculateOptimalOrderSize(bot, shouldBuy, price, orderBook);
        // Scale up for aggressive strategy
        amount = amount.multiply(BigDecimal.valueOf(1.5)).min(BigDecimal.valueOf(150));
        amount = amount.setScale(2, RoundingMode.HALF_UP);

        // Risk check
        if (!riskManagementService.canPlaceOrder(bot, shouldBuy, amount, price)) {
            return;
        }
        
        String orderId = orderService.placeOrder(bot, shouldBuy, amount, price);
        if (orderId != null) {
            circuitBreakerService.recordSuccess(bot.getId().toString());
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
        } else {
            circuitBreakerService.recordError(bot.getId().toString());
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

        BigDecimal currentPrice = priceHistoryService.getCurrentPrice(bot.getTradingPair());
        if (currentPrice == null) {
            currentPrice = marketDataService.getCurrentPrice(bot.getTradingPair());
            if (currentPrice == null) {
                currentPrice = new BigDecimal("500.00");
            }
        }

        OrderBookAnalyzer.OrderBookMetrics metrics = orderBookAnalyzer.analyze(orderBook);
        BigDecimal spreadPercent = tradingHelperService.calculateAdaptiveSpread(bot, "CONSERVATIVE", metrics);
        BigDecimal spreadAmount = currentPrice.multiply(spreadPercent);
        
        boolean shouldBuy = random.nextBoolean();
        BigDecimal price = shouldBuy
                ? currentPrice.subtract(spreadAmount)
                : currentPrice.add(spreadAmount);
        price = price.setScale(2, RoundingMode.HALF_UP);
        
        // Calculate optimal order size (smaller for conservative)
        BigDecimal amount = tradingHelperService.calculateOptimalOrderSize(bot, shouldBuy, price, orderBook);
        // Scale down for conservative strategy
        amount = amount.multiply(BigDecimal.valueOf(0.5)).max(BigDecimal.valueOf(5));
        amount = amount.setScale(2, RoundingMode.HALF_UP);

        // Risk check
        if (!riskManagementService.canPlaceOrder(bot, shouldBuy, amount, price)) {
            return;
        }
        
        String orderId = orderService.placeOrder(bot, shouldBuy, amount, price);
        if (orderId != null) {
            circuitBreakerService.recordSuccess(bot.getId().toString());
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
        } else {
            circuitBreakerService.recordError(bot.getId().toString());
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
        BigDecimal currentPrice = priceHistoryService.getCurrentPrice(bot.getTradingPair());
        if (currentPrice == null) {
            currentPrice = marketDataService.getCurrentPrice(bot.getTradingPair());
            if (currentPrice == null) {
                currentPrice = new BigDecimal("500.00");
            }
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
            // Create new order with adaptive spread
            shouldBuy = random.nextBoolean();
            OrderBookAnalyzer.OrderBookMetrics metrics = orderBookAnalyzer.analyze(orderBook);
            BigDecimal spreadPercent = tradingHelperService.calculateAdaptiveSpread(bot, "TIMEOUT", metrics);
            BigDecimal spreadAmount = currentPrice.multiply(spreadPercent);
            price = shouldBuy
                    ? currentPrice.subtract(spreadAmount)
                    : currentPrice.add(spreadAmount);
            price = price.setScale(2, RoundingMode.HALF_UP);
            amount = tradingHelperService.calculateOptimalOrderSize(bot, shouldBuy, price, orderBook);
        }

        // Risk check
        if (!riskManagementService.canPlaceOrder(bot, shouldBuy, amount, price)) {
            return;
        }
        
        String orderId = orderService.placeOrder(bot, shouldBuy, amount, price);
        if (orderId != null) {
            circuitBreakerService.recordSuccess(bot.getId().toString());
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
        } else {
            circuitBreakerService.recordError(bot.getId().toString());
        }
    }

    /**
     * MARKET_MAKER Strategy - Places both buy and sell orders simultaneously to provide liquidity
     */
    private void tradeMarketMaker(Bot bot, OrderBookService.OrderBookData orderBook) {
        balanceService.checkAndReplenish(bot);
        bot = botStorage.findById(bot.getId()).orElse(bot);

        boolean hasBuyOrder = Boolean.TRUE.equals(bot.getHasActiveBuyOrder());
        boolean hasSellOrder = Boolean.TRUE.equals(bot.getHasActiveSellOrder());

        // Market makers can have both orders, but if both exist, skip
        if (hasBuyOrder && hasSellOrder) {
            return;
        }

        BigDecimal currentPrice = priceHistoryService.getCurrentPrice(bot.getTradingPair());
        if (currentPrice == null) {
            currentPrice = marketDataService.getCurrentPrice(bot.getTradingPair());
            if (currentPrice == null) {
                currentPrice = new BigDecimal("500.00");
            }
        }

        OrderBookAnalyzer.OrderBookMetrics metrics = orderBookAnalyzer.analyze(orderBook);
        BigDecimal spreadPercent = tradingHelperService.calculateAdaptiveSpread(bot, "MARKET_MAKER", metrics);
        BigDecimal spreadAmount = currentPrice.multiply(spreadPercent);

        // Place buy order if not exists
        if (!hasBuyOrder) {
            BigDecimal buyPrice = currentPrice.subtract(spreadAmount);
            buyPrice = buyPrice.setScale(2, RoundingMode.HALF_UP);
            BigDecimal buySize = tradingHelperService.calculateOptimalOrderSize(bot, true, buyPrice, orderBook);
            
            if (riskManagementService.canPlaceOrder(bot, true, buySize, buyPrice)) {
                String orderId = orderService.placeOrder(bot, true, buySize, buyPrice);
                if (orderId != null) {
                    circuitBreakerService.recordSuccess(bot.getId().toString());
                    bot.setHasActiveBuyOrder(true);
                    bot.setLastTradeAt(LocalDateTime.now());
                    botStorage.save(bot);
                    log.info("[MARKET_MAKER] Bot {} placed BUY order: {} @ ${}", 
                            bot.getName(), buySize, buyPrice);
                } else {
                    circuitBreakerService.recordError(bot.getId().toString());
                }
            }
        }

        // Place sell order if not exists
        if (!hasSellOrder) {
            BigDecimal sellPrice = currentPrice.add(spreadAmount);
            sellPrice = sellPrice.setScale(2, RoundingMode.HALF_UP);
            BigDecimal sellSize = tradingHelperService.calculateOptimalOrderSize(bot, false, sellPrice, orderBook);
            
            if (riskManagementService.canPlaceOrder(bot, false, sellSize, sellPrice)) {
                String orderId = orderService.placeOrder(bot, false, sellSize, sellPrice);
                if (orderId != null) {
                    circuitBreakerService.recordSuccess(bot.getId().toString());
                    bot.setHasActiveSellOrder(true);
                    bot.setLastTradeAt(LocalDateTime.now());
                    botStorage.save(bot);
                    log.info("[MARKET_MAKER] Bot {} placed SELL order: {} @ ${}", 
                            bot.getName(), sellSize, sellPrice);
                } else {
                    circuitBreakerService.recordError(bot.getId().toString());
                }
            }
        }
    }

    /**
     * TREND_FOLLOWING Strategy - Follows market trends using moving averages
     */
    private void tradeTrendFollowing(Bot bot, OrderBookService.OrderBookData orderBook) {
        balanceService.checkAndReplenish(bot);
        bot = botStorage.findById(bot.getId()).orElse(bot);

        boolean hasBuyOrder = Boolean.TRUE.equals(bot.getHasActiveBuyOrder());
        boolean hasSellOrder = Boolean.TRUE.equals(bot.getHasActiveSellOrder());

        if (hasBuyOrder || hasSellOrder) {
            return;
        }

        PriceHistoryService.Trend trend = priceHistoryService.detectTrend(bot.getTradingPair());
        double momentum = priceHistoryService.calculateMomentum(bot.getTradingPair());
        double momentumThreshold = config.getStrategy().getMomentumThreshold();

        BigDecimal currentPrice = priceHistoryService.getCurrentPrice(bot.getTradingPair());
        if (currentPrice == null) {
            currentPrice = marketDataService.getCurrentPrice(bot.getTradingPair());
            if (currentPrice == null) {
                currentPrice = new BigDecimal("500.00");
            }
        }

        boolean shouldBuy = false;
        BigDecimal price;
        BigDecimal amount;

        if (trend == PriceHistoryService.Trend.BULLISH && momentum > momentumThreshold) {
            // Strong uptrend - buy
            shouldBuy = true;
            OrderBookAnalyzer.OrderBookMetrics metrics = orderBookAnalyzer.analyze(orderBook);
            BigDecimal spreadPercent = tradingHelperService.calculateAdaptiveSpread(bot, "TREND_FOLLOWING", metrics);
            BigDecimal spreadAmount = currentPrice.multiply(spreadPercent).multiply(BigDecimal.valueOf(0.5));
            price = currentPrice.add(spreadAmount);
            amount = tradingHelperService.calculateOptimalOrderSize(bot, true, price, orderBook);
        } else if (trend == PriceHistoryService.Trend.BEARISH && momentum < -momentumThreshold) {
            // Strong downtrend - sell
            shouldBuy = false;
            OrderBookAnalyzer.OrderBookMetrics metrics = orderBookAnalyzer.analyze(orderBook);
            BigDecimal spreadPercent = tradingHelperService.calculateAdaptiveSpread(bot, "TREND_FOLLOWING", metrics);
            BigDecimal spreadAmount = currentPrice.multiply(spreadPercent).multiply(BigDecimal.valueOf(0.5));
            price = currentPrice.subtract(spreadAmount);
            amount = tradingHelperService.calculateOptimalOrderSize(bot, false, price, orderBook);
        } else {
            // Sideways or weak trend - skip
            return;
        }

        price = price.setScale(2, RoundingMode.HALF_UP);

        if (riskManagementService.canPlaceOrder(bot, shouldBuy, amount, price)) {
            String orderId = orderService.placeOrder(bot, shouldBuy, amount, price);
            if (orderId != null) {
                circuitBreakerService.recordSuccess(bot.getId().toString());
                if (shouldBuy) {
                    bot.setHasActiveBuyOrder(true);
                    bot.setHasActiveSellOrder(false);
                } else {
                    bot.setHasActiveBuyOrder(false);
                    bot.setHasActiveSellOrder(true);
                }
                bot.setLastTradeAt(LocalDateTime.now());
                botStorage.save(bot);
                log.info("[TREND_FOLLOWING] Bot {} placed {} order: {} @ ${} (trend: {}, momentum: {})", 
                        bot.getName(), shouldBuy ? "BUY" : "SELL", amount, price, trend, momentum);
            } else {
                circuitBreakerService.recordError(bot.getId().toString());
            }
        }
    }

    /**
     * MEAN_REVERSION Strategy - Trades against price deviations from mean
     */
    private void tradeMeanReversion(Bot bot, OrderBookService.OrderBookData orderBook) {
        balanceService.checkAndReplenish(bot);
        bot = botStorage.findById(bot.getId()).orElse(bot);

        boolean hasBuyOrder = Boolean.TRUE.equals(bot.getHasActiveBuyOrder());
        boolean hasSellOrder = Boolean.TRUE.equals(bot.getHasActiveSellOrder());

        if (hasBuyOrder || hasSellOrder) {
            return;
        }

        BigDecimal currentPrice = priceHistoryService.getCurrentPrice(bot.getTradingPair());
        if (currentPrice == null) {
            currentPrice = marketDataService.getCurrentPrice(bot.getTradingPair());
            if (currentPrice == null) {
                currentPrice = new BigDecimal("500.00");
            }
        }

        // Calculate mean price
        int meanReversionPeriod = config.getStrategy().getMeanReversionPeriod();
        BigDecimal meanPrice = priceHistoryService.calculateMA(bot.getTradingPair(), meanReversionPeriod);

        if (meanPrice.compareTo(BigDecimal.ZERO) == 0) {
            return; // Not enough data
        }

        // Calculate deviation from mean
        double deviation = (currentPrice.doubleValue() - meanPrice.doubleValue()) / meanPrice.doubleValue();
        double bufferPercent = config.getStrategy().getRangeBufferPercent();

        boolean shouldBuy = false;
        BigDecimal price;
        BigDecimal amount;

        if (deviation > bufferPercent) {
            // Price above mean - sell (expect reversion down)
            shouldBuy = false;
            price = currentPrice;
            amount = tradingHelperService.calculateOptimalOrderSize(bot, false, price, orderBook);
        } else if (deviation < -bufferPercent) {
            // Price below mean - buy (expect reversion up)
            shouldBuy = true;
            price = currentPrice;
            amount = tradingHelperService.calculateOptimalOrderSize(bot, true, price, orderBook);
        } else {
            // Within range - skip
            return;
        }

        price = price.setScale(2, RoundingMode.HALF_UP);

        if (riskManagementService.canPlaceOrder(bot, shouldBuy, amount, price)) {
            String orderId = orderService.placeOrder(bot, shouldBuy, amount, price);
            if (orderId != null) {
                circuitBreakerService.recordSuccess(bot.getId().toString());
                if (shouldBuy) {
                    bot.setHasActiveBuyOrder(true);
                    bot.setHasActiveSellOrder(false);
                } else {
                    bot.setHasActiveBuyOrder(false);
                    bot.setHasActiveSellOrder(true);
                }
                bot.setLastTradeAt(LocalDateTime.now());
                botStorage.save(bot);
                log.info("[MEAN_REVERSION] Bot {} placed {} order: {} @ ${} (deviation: {:.2f}%)", 
                        bot.getName(), shouldBuy ? "BUY" : "SELL", amount, price, deviation * 100);
            } else {
                circuitBreakerService.recordError(bot.getId().toString());
            }
        }
    }

    /**
     * MARKET_ACTIVITY Strategy - Keeps exchange active, doesn't care about profit/loss
     * Just places orders frequently to maintain market activity
     */
    private void tradeMarketActivity(Bot bot, OrderBookService.OrderBookData orderBook) {
        balanceService.checkAndReplenish(bot);
        bot = botStorage.findById(bot.getId()).orElse(bot);

        boolean hasBuyOrder = Boolean.TRUE.equals(bot.getHasActiveBuyOrder());
        boolean hasSellOrder = Boolean.TRUE.equals(bot.getHasActiveSellOrder());

        // Market activity bots cancel orders more frequently to keep things moving (40% chance)
        if ((hasBuyOrder || hasSellOrder) && random.nextDouble() < 0.4) {
            boolean cancelled = cancelOrderService.cancelOrder(bot, hasBuyOrder);
            if (cancelled) {
                botStorage.save(bot);
                log.info("[MARKET_ACTIVITY] Bot {} cancelled {} order to maintain activity", 
                        bot.getName(), hasBuyOrder ? "BUY" : "SELL");
            }
            return;
        }

        if (hasBuyOrder || hasSellOrder) {
            return;
        }

        BigDecimal currentPrice = priceHistoryService.getCurrentPrice(bot.getTradingPair());
        if (currentPrice == null) {
            currentPrice = marketDataService.getCurrentPrice(bot.getTradingPair());
            if (currentPrice == null) {
                currentPrice = new BigDecimal("500.00");
            }
        }

        // Place orders frequently with random sizes and prices
        // Don't care about profit - just keep the exchange active
        boolean shouldBuy = random.nextBoolean();
        
        // Use wider price range to ensure orders get placed
        BigDecimal priceSpread = currentPrice.multiply(BigDecimal.valueOf(0.01)); // 1% spread
        BigDecimal price = shouldBuy
                ? currentPrice.subtract(priceSpread.multiply(BigDecimal.valueOf(0.5 + random.nextDouble())))
                : currentPrice.add(priceSpread.multiply(BigDecimal.valueOf(0.5 + random.nextDouble())));
        price = price.setScale(2, RoundingMode.HALF_UP);

        // Random order size between min and max
        BigDecimal minSize = config.getSimulation().getMinOrderSize();
        BigDecimal maxSize = config.getSimulation().getMaxOrderSize();
        BigDecimal amount = minSize.add(maxSize.subtract(minSize).multiply(BigDecimal.valueOf(random.nextDouble())));
        amount = amount.setScale(2, RoundingMode.HALF_UP);

        // Skip risk checks for balance - just check if we have something
        if (shouldBuy && (bot.getBalance() == null || bot.getBalance().compareTo(price.multiply(amount)) < 0)) {
            return; // Skip if no balance, but don't worry about profit
        }
        if (!shouldBuy && (bot.getAssetBalance() == null || bot.getAssetBalance().compareTo(amount) < 0)) {
            return; // Skip if no assets
        }

        String orderId = orderService.placeOrder(bot, shouldBuy, amount, price);
        if (orderId != null) {
            circuitBreakerService.recordSuccess(bot.getId().toString());
            if (shouldBuy) {
                bot.setHasActiveBuyOrder(true);
                bot.setHasActiveSellOrder(false);
            } else {
                bot.setHasActiveBuyOrder(false);
                bot.setHasActiveSellOrder(true);
            }
            bot.setLastTradeAt(LocalDateTime.now());
            botStorage.save(bot);
            log.info("[MARKET_ACTIVITY] Bot {} placed {} order: {} @ ${} (keeping exchange active)", 
                    bot.getName(), shouldBuy ? "BUY" : "SELL", amount, price);
        } else {
            circuitBreakerService.recordError(bot.getId().toString());
        }
    }

    /**
     * ORDER_BOOK_BALANCER Strategy - Balances buy and sell orders in the order book
     * If more buy orders, places sell orders. If more sell orders, places buy orders.
     */
    private void tradeOrderBookBalancer(Bot bot, OrderBookService.OrderBookData orderBook) {
        balanceService.checkAndReplenish(bot);
        bot = botStorage.findById(bot.getId()).orElse(bot);

        boolean hasBuyOrder = Boolean.TRUE.equals(bot.getHasActiveBuyOrder());
        boolean hasSellOrder = Boolean.TRUE.equals(bot.getHasActiveSellOrder());

        if (hasBuyOrder || hasSellOrder) {
            return;
        }

        // Analyze order book imbalance
        OrderBookAnalyzer.OrderBookMetrics metrics = orderBookAnalyzer.analyze(orderBook);
        
        // Check both order count and depth imbalance
        int buyOrderCount = metrics.getBidOrderCount();
        int sellOrderCount = metrics.getAskOrderCount();
        double imbalance = metrics.getImbalance(); // Positive = more buys, Negative = more sells
        
        BigDecimal currentPrice = priceHistoryService.getCurrentPrice(bot.getTradingPair());
        if (currentPrice == null) {
            currentPrice = marketDataService.getCurrentPrice(bot.getTradingPair());
            if (currentPrice == null) {
                currentPrice = new BigDecimal("500.00");
            }
        }

        boolean shouldBuy = false;
        BigDecimal price;
        BigDecimal amount;

        // Determine which side needs more orders
        // If more buy orders (positive imbalance) or more buy order count, place sell orders
        // If more sell orders (negative imbalance) or more sell order count, place buy orders
        
        double imbalanceThreshold = 0.1; // 10% imbalance threshold
        int orderCountDifference = buyOrderCount - sellOrderCount;
        
        if (imbalance > imbalanceThreshold || orderCountDifference > 5) {
            // Too many buy orders - place sell order to balance
            shouldBuy = false;
            
            // Place sell order at or slightly above best bid to ensure it's competitive
            OrderBookService.OrderLevel bestBuyOrder = orderBook.getBestBuyOrder();
            if (bestBuyOrder != null) {
                price = bestBuyOrder.getPrice().add(BigDecimal.valueOf(0.01)); // Slightly above best bid
            } else {
                price = currentPrice.add(currentPrice.multiply(BigDecimal.valueOf(0.005))); // 0.5% above current
            }
            price = price.setScale(2, RoundingMode.HALF_UP);
            
            // Calculate amount to help balance
            BigDecimal sellDepth = metrics.getAskDepth();
            BigDecimal buyDepth = metrics.getBidDepth();
            if (buyDepth.compareTo(BigDecimal.ZERO) > 0) {
                // Place order size proportional to imbalance
                BigDecimal imbalanceAmount = buyDepth.subtract(sellDepth);
                if (imbalanceAmount.compareTo(BigDecimal.ZERO) < 0) {
                    imbalanceAmount = BigDecimal.ZERO;
                }
                amount = imbalanceAmount.max(BigDecimal.valueOf(10))
                        .min(config.getSimulation().getMaxOrderSize());
            } else {
                amount = tradingHelperService.calculateOptimalOrderSize(bot, false, price, orderBook);
            }
            
            log.info("[ORDER_BOOK_BALANCER] Detected imbalance: {} buy orders vs {} sell orders, imbalance: {:.2f}. Placing SELL order to balance", 
                    buyOrderCount, sellOrderCount, imbalance);
            
        } else if (imbalance < -imbalanceThreshold || orderCountDifference < -5) {
            // Too many sell orders - place buy order to balance
            shouldBuy = true;
            
            // Place buy order at or slightly below best ask to ensure it's competitive
            OrderBookService.OrderLevel bestSellOrder = orderBook.getBestSellOrder();
            if (bestSellOrder != null) {
                price = bestSellOrder.getPrice().subtract(BigDecimal.valueOf(0.01)); // Slightly below best ask
            } else {
                price = currentPrice.subtract(currentPrice.multiply(BigDecimal.valueOf(0.005))); // 0.5% below current
            }
            price = price.setScale(2, RoundingMode.HALF_UP);
            
            // Calculate amount to help balance
            BigDecimal sellDepth = metrics.getAskDepth();
            BigDecimal buyDepth = metrics.getBidDepth();
            if (sellDepth.compareTo(BigDecimal.ZERO) > 0) {
                // Place order size proportional to imbalance
                BigDecimal imbalanceAmount = sellDepth.subtract(buyDepth);
                if (imbalanceAmount.compareTo(BigDecimal.ZERO) < 0) {
                    imbalanceAmount = BigDecimal.ZERO;
                }
                amount = imbalanceAmount.max(BigDecimal.valueOf(10))
                        .min(config.getSimulation().getMaxOrderSize());
            } else {
                amount = tradingHelperService.calculateOptimalOrderSize(bot, true, price, orderBook);
            }
            
            log.info("[ORDER_BOOK_BALANCER] Detected imbalance: {} buy orders vs {} sell orders, imbalance: {:.2f}. Placing BUY order to balance", 
                    buyOrderCount, sellOrderCount, imbalance);
            
        } else {
            // Order book is relatively balanced - skip this cycle
            log.debug("[ORDER_BOOK_BALANCER] Order book is balanced ({} buys, {} sells, imbalance: {:.2f}), skipping", 
                    buyOrderCount, sellOrderCount, imbalance);
            return;
        }

        amount = amount.setScale(2, RoundingMode.HALF_UP);

        // Basic balance check only (don't enforce strict risk limits for balancing)
        if (shouldBuy && (bot.getBalance() == null || bot.getBalance().compareTo(price.multiply(amount)) < 0)) {
            log.debug("[ORDER_BOOK_BALANCER] Insufficient balance for buy order");
            return;
        }
        if (!shouldBuy && (bot.getAssetBalance() == null || bot.getAssetBalance().compareTo(amount) < 0)) {
            log.debug("[ORDER_BOOK_BALANCER] Insufficient assets for sell order");
            return;
        }

        String orderId = orderService.placeOrder(bot, shouldBuy, amount, price);
        if (orderId != null) {
            circuitBreakerService.recordSuccess(bot.getId().toString());
            if (shouldBuy) {
                bot.setHasActiveBuyOrder(true);
                bot.setHasActiveSellOrder(false);
            } else {
                bot.setHasActiveBuyOrder(false);
                bot.setHasActiveSellOrder(true);
            }
            bot.setLastTradeAt(LocalDateTime.now());
            botStorage.save(bot);
            log.info("[ORDER_BOOK_BALANCER] Bot {} placed {} order: {} @ ${} to balance order book", 
                    bot.getName(), shouldBuy ? "BUY" : "SELL", amount, price);
        } else {
            circuitBreakerService.recordError(bot.getId().toString());
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
