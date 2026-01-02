package com.mine.engine.runner;

import com.mine.engine.model.Market;
import com.mine.engine.model.Message;
import com.mine.engine.model.Order;
import com.mine.engine.model.OrderBook;
import com.mine.engine.model.OrderExecutionType;
import com.mine.engine.model.OrderType;
import com.mine.engine.service.StockDataService;
import com.mine.engine.service.UserService;
import com.mine.engine.service.messaging.MessageProducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Market Simulator - Simulates trading activity for testing
 * Adds users and randomly places buy/sell orders
 */
@Slf4j
@Component
public class MarketSimulatorRunner {

    private final UserService userService;
    private final StockDataService stockDataService;
    private final MessageProducer producer;

    public MarketSimulatorRunner(UserService userService, StockDataService stockDataService, MessageProducer producer) {
        this.userService = userService;
        this.stockDataService = stockDataService;
        this.producer = producer;
    }

    public void run(String... args) throws InterruptedException {
        log.info("=== Starting Market Simulator ===");
        
        // Add 5 users with initial cash
        List<UUID> userIds = addUsers();
        
        // Print initial state
        log.info("\n=== Initial Stock Data ===");
        printStockData();
        
        // Simulate random trades
        simulateTrades(userIds);
        
        log.info("\n=== Final Stock Data ===");
        printStockData();
        
        log.info("\n=== Market Simulator Completed ===");
    }
    
    /**
     * Add 5 users with initial cash
     */
    private List<UUID> addUsers()  {

        List<UUID> userIds = new ArrayList<>();
        // Add 5 users with different initial cash amounts
        double[] initialCashAmounts = {100000.0, 150000.0, 200000.0, 120000.0, 180000.0};
        
        for (int i = 0; i < 5; i++) {
            double initialCash = initialCashAmounts[i];
            
            UUID userId = userService.addUser(initialCash);
            userIds.add(userId);
            
            log.info("Added User {}: UUID={}, Initial Cash=${}", 
                    i + 1, userId, String.format("%.2f", initialCash));
        }
        
        return userIds;
    }
    
    /**
     * Simulate random buy and sell orders
     */
    private void simulateTrades(List<UUID> userIds) throws InterruptedException {

            // Randomly select a user
            UUID userId1 = userIds.get(0);
            UUID userId2 = userIds.get(1);
            UUID userId3 = userIds.get(2);
            UUID userId4 = userIds.get(3);
            UUID userId5 = userIds.get(4);

            log.info("User {} placing buy order: Price=$10000.00, Quantity=10", userId1);
//            stockService.placeBuyOrder(userId1,10000,10, OrderExecutionType.LIMIT);
            producer.sendMessage(Message.builder().userId(userId1).price(10000).quantity(10)
                    .orderExecutionType(OrderExecutionType.LIMIT).orderType(OrderType.BUY).build());
//            printStockData();
            Thread.sleep(2000);
            log.info("User {} placing buy order: Price=$20000.00, Quantity=20", userId2);
//            stockService.placeBuyOrder(userId2,20000,20, OrderExecutionType.LIMIT);
            producer.sendMessage(Message.builder().userId(userId2).price(20000).quantity(20)
                    .orderExecutionType(OrderExecutionType.LIMIT).orderType(OrderType.BUY).build());
//            printStockData();
        Thread.sleep(2000);
            log.info("User {} placing sell order: Price=$30000.00, Quantity=10", userId3);
//            stockService.placeSellOrder(userId3,30000,10, OrderExecutionType.LIMIT);
            producer.sendMessage(Message.builder().userId(userId3).price(30000).quantity(10)
                    .orderExecutionType(OrderExecutionType.LIMIT).orderType(OrderType.SELL).build());
//            printStockData();
        Thread.sleep(2000);
            log.info("User {} placing sell order: Price=$40000.00, Quantity=20", userId4);
//            stockService.placeSellOrder(userId4,40000,20, OrderExecutionType.LIMIT);
            producer.sendMessage(Message.builder().userId(userId4).price(40000).quantity(20)
                    .orderExecutionType(OrderExecutionType.LIMIT).orderType(OrderType.SELL).build());
//            printStockData();
        Thread.sleep(2000);
            log.info("User {} placing buy order: Price=$40000.00, Quantity=40", userId5);
//            stockService.placeBuyOrder(userId5,40000,40, OrderExecutionType.LIMIT);
            producer.sendMessage(Message.builder().userId(userId5).price(40000).quantity(40)
                    .orderExecutionType(OrderExecutionType.LIMIT).orderType(OrderType.BUY).build());
//            printStockData();
    }
    
    /**
     * Print current stock data for all markets
     */
    private void printStockData() {
        log.info("\n--- Stock Data Status ---");
        
        for (Market market : Market.values()) {
            var orderBook = stockDataService.getOrderBook(market);
            
            if (orderBook == null) {
                log.info("Market {}: No order book", market);
                continue;
            }
            
            var buyOrders = stockDataService.getAllBuyOrders(market);
            var sellOrders = stockDataService.getAllSellOrders(market);
            
            int buyOrderCount = buyOrders != null ? buyOrders.size() : 0;
            int sellOrderCount = sellOrders != null ? sellOrders.size() : 0;
            
            log.info("Market {} ({}):", market, market.getDisplayName());
            log.info("  - Buy Orders: {}", buyOrderCount);
            log.info("  - Sell Orders: {}", sellOrderCount);
            
            // Print all buy orders
            if (buyOrderCount > 0) {
                log.info("  --- All Buy Orders (sorted by price DESC) ---");
                // Create a sorted list to print all orders
                List<Order> buyOrdersList = new ArrayList<>(buyOrders);
                buyOrdersList.sort((o1, o2) -> {
                    int priceCompare = Double.compare(o2.getPrice(), o1.getPrice());
                    if (priceCompare != 0) return priceCompare;
                    return Long.compare(o1.getId(), o2.getId());
                });
                
                int index = 1;
                for (var order : buyOrdersList) {
                    log.info("    {}. Order ID: {}, User: {}, Price: ${}, Quantity: {}, Type: {}, Execution: {}", 
                            index++,
                            order.getId(),
                            order.getUserId(),
                            String.format("%.2f", order.getPrice()),
                            order.getQuantity(),
                            order.getType(),
                            order.getExecutionType());
                }
            } else {
                log.info("  --- No Buy Orders ---");
            }
            
            // Print all sell orders
            if (sellOrderCount > 0) {
                log.info("  --- All Sell Orders (sorted by price ASC) ---");
                // Create a sorted list to print all orders
                List<Order> sellOrdersList = new ArrayList<>(sellOrders);
                sellOrdersList.sort(Comparator.comparingDouble(Order::getPrice).thenComparingLong(Order::getId));
                
                int index = 1;
                for (var order : sellOrdersList) {
                    log.info("    {}. Order ID: {}, User: {}, Price: ${}, Quantity: {}, Type: {}, Execution: {}", 
                            index++,
                            order.getId(),
                            order.getUserId(),
                            String.format("%.2f", order.getPrice()),
                            order.getQuantity(),
                            order.getType(),
                            order.getExecutionType());
                }
            } else {
                log.info("  --- No Sell Orders ---");
            }
        }
    }
}

