package com.mine.engine.runner;

import com.mine.engine.model.OrderExecutionType;
import com.mine.engine.service.StockService;
import com.mine.engine.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds the order book with initial market maker orders on startup.
 * Creates 3-4 market maker bots and places tiered buy/sell orders around reference price.
 */
@Slf4j
@Component
@Order(1) // Run early to seed order book before other operations
public class OrderBookSeeder implements CommandLineRunner {

    private final StockService stockService;
    private final UserService userService;
    
    // Reference price: $500 for 1 quantity
    private static final double REFERENCE_PRICE = 500.0;
    private static final long QUANTITY_PER_ORDER = 1L;
    private static final int MARKET_MAKER_COUNT = 4;
    
    // Tier spacing: orders at ±0.5%, ±1%, ±1.5%, ±2% from reference
    private static final double[] TIER_PERCENTAGES = {0.005, 0.01, 0.015, 0.02};

    public OrderBookSeeder(StockService stockService, UserService userService) {
        this.stockService = stockService;
        this.userService = userService;
    }

    @Override
    public void run(String... args) {
        log.info("=== Starting Order Book Seeding ===");
        log.info("Reference price: ${} for {} quantity", REFERENCE_PRICE, QUANTITY_PER_ORDER);
        log.info("Will create {} market makers with tiered orders:", MARKET_MAKER_COUNT);
        log.info("  BUY orders at: ${}, ${}, ${}, ${}", 
                REFERENCE_PRICE * 0.995, REFERENCE_PRICE * 0.99, 
                REFERENCE_PRICE * 0.985, REFERENCE_PRICE * 0.98);
        log.info("  SELL orders at: ${}, ${}, ${}, ${}", 
                REFERENCE_PRICE * 1.005, REFERENCE_PRICE * 1.01, 
                REFERENCE_PRICE * 1.015, REFERENCE_PRICE * 1.02);
        
        try {
            // Create market maker users
            Long[] marketMakerIds = createMarketMakerUsers();
            
            // Seed buy orders (below reference price)
            seedBuyOrders(marketMakerIds);
            
            // Seed sell orders (above reference price)
            seedSellOrders(marketMakerIds);
            
            log.info("=== Order Book Seeding Complete ===");
            log.info("Created {} market makers with {} buy orders and {} sell orders around ${}", 
                    MARKET_MAKER_COUNT, TIER_PERCENTAGES.length, TIER_PERCENTAGES.length, REFERENCE_PRICE);
            
        } catch (Exception e) {
            log.error("Failed to seed order book: {}", e.getMessage(), e);
        }
    }

    /**
     * Create market maker users with sufficient balance.
     */
    private Long[] createMarketMakerUsers() {
        Long[] userIds = new Long[MARKET_MAKER_COUNT];
        
        // Each market maker needs enough balance for their orders
        // 4 buy orders * $500 * 1 quantity = $2000 per maker
        // Plus extra for sell orders (they need BTC holdings)
        double initialBalance = 10000.0; // $10,000 per market maker
        
        for (int i = 0; i < MARKET_MAKER_COUNT; i++) {
            Long userId = userService.addUser(initialBalance);
            userIds[i] = userId;
            log.info("Created market maker {}: {} with balance ${}", i + 1, userId, initialBalance);
        }
        
        return userIds;
    }

    /**
     * Seed buy orders below reference price.
     * Places orders at -0.5%, -1%, -1.5%, -2% from reference.
     */
    private void seedBuyOrders(Long[] marketMakerIds) {
        log.info("Seeding BUY orders below reference price...");
        
        int makerIndex = 0;
        int tierIndex = 0;
        for (double tierPercent : TIER_PERCENTAGES) {
            double buyPrice = REFERENCE_PRICE * (1 - tierPercent);
            buyPrice = Math.round(buyPrice * 100.0) / 100.0; // Round to 2 decimals
            
            Long makerId = marketMakerIds[makerIndex % MARKET_MAKER_COUNT];
            
            // Generate dummy idempotency key for seeding orders
            String idempotencyKey = String.format("SEED-BUY-%d-%d-%d", makerId, tierIndex, System.currentTimeMillis());
            
            try {
                String result = stockService.placeBuyOrder(
                        makerId,
                        buyPrice,
                        QUANTITY_PER_ORDER,
                        OrderExecutionType.LIMIT,
                        System.currentTimeMillis(), // Use current timestamp for seeding
                        idempotencyKey // Dummy idempotency key for seeding orders
                );
                
                log.info("  BUY order: {} @ ${} ({}% below reference) - {}", 
                        QUANTITY_PER_ORDER, buyPrice, tierPercent * 100, result);
                
            } catch (Exception e) {
                log.warn("Failed to place buy order at ${}: {}", buyPrice, e.getMessage());
            }
            
            makerIndex++;
            tierIndex++;
        }
    }

    /**
     * Seed sell orders above reference price.
     * Places orders at +0.5%, +1%, +1.5%, +2% from reference.
     * First, give market makers some BTC holdings to sell.
     */
    private void seedSellOrders(Long[] marketMakerIds) {
        log.info("Seeding SELL orders above reference price...");
        
        // First, give each market maker BTC holdings directly
        // They need at least 4 BTC (one per sell order)
        long btcPerMaker = TIER_PERCENTAGES.length; // One BTC per tier
        
        for (Long makerId : marketMakerIds) {
            try {
                // Get user and add BTC holdings directly
                com.mine.engine.model.User user = userService.getUser(makerId);
                if (user != null) {
                    // Add BTC holdings directly to user
                    user.getMarkets().put(
                            com.mine.engine.model.Market.BTC,
                            user.getMarkets().getOrDefault(com.mine.engine.model.Market.BTC, 0L) + btcPerMaker
                    );
                    log.debug("Gave market maker {} {} BTC holdings for sell orders", makerId, btcPerMaker);
                }
            } catch (Exception e) {
                log.warn("Failed to give BTC holdings to market maker {}: {}", makerId, e.getMessage());
            }
        }
        
        // Now place sell orders above reference price
        int makerIndex = 0;
        int tierIndex = 0;
        for (double tierPercent : TIER_PERCENTAGES) {
            double sellPrice = REFERENCE_PRICE * (1 + tierPercent);
            sellPrice = Math.round(sellPrice * 100.0) / 100.0; // Round to 2 decimals
            
            Long makerId = marketMakerIds[makerIndex % MARKET_MAKER_COUNT];
            
            // Generate dummy idempotency key for seeding orders
            String idempotencyKey = String.format("SEED-SELL-%d-%d-%d", makerId, tierIndex, System.currentTimeMillis());
            
            try {
                String result = stockService.placeSellOrder(
                        makerId,
                        sellPrice,
                        QUANTITY_PER_ORDER,
                        OrderExecutionType.LIMIT,
                        System.currentTimeMillis(), // Use current timestamp for seeding
                        idempotencyKey // Dummy idempotency key for seeding orders
                );
                
                log.info("  SELL order: {} @ ${} ({}% above reference) - {}", 
                        QUANTITY_PER_ORDER, sellPrice, tierPercent * 100, result);
                
            } catch (Exception e) {
                log.warn("Failed to place sell order at ${}: {}", sellPrice, e.getMessage());
            }
            
            makerIndex++;
            tierIndex++;
        }
    }
}

