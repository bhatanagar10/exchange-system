package com.mine.engine.service;

import com.mine.engine.model.Market;
import com.mine.engine.model.Order;
import com.mine.engine.model.OrderBook;
import com.mine.engine.model.OrderExecutionType;
import com.mine.engine.model.OrderType;
import com.mine.engine.model.Transaction;
import com.mine.engine.model.User;
import com.mine.engine.service.order.LimitOrderExecutionStrategy;
import com.mine.engine.service.order.MarketOrderExecutionStrategy;
import com.mine.engine.service.order.OrderExecutionStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static com.mine.engine.model.Market.BTC;


@Slf4j
@Service
public class StockService {

    private final StockDataService stockDataService;
    private final Map<UUID, User> userData; // Injected singleton map
    private final List<Transaction> transactions; // Injected singleton list

    // Order and transaction counters
    private AtomicLong orderIdCounter;
    private AtomicLong transactionIdCounter;
    
    // Strategy map for order execution
    private final Map<OrderExecutionType, OrderExecutionStrategy> executionStrategies;
    
    /**
     * Constructor with dependency injection of singleton data structures
     * All classes that inject stockDataService, userData, and transactions will get the same instances
     */
    public StockService(StockDataService stockDataService, Map<UUID, User> userData, List<Transaction> transactions) {
        // Inject the stock data service
        this.stockDataService = stockDataService;
        
        // Inject the singleton userData map
        this.userData = userData;
        
        // Inject the singleton transactions list
        this.transactions = transactions;
        
        // Initialize order and transaction counters
        this.orderIdCounter = new AtomicLong(1);
        this.transactionIdCounter = new AtomicLong(1);
        
        // Initialize execution strategies
        this.executionStrategies = new EnumMap<>(OrderExecutionType.class);
        this.executionStrategies.put(OrderExecutionType.MARKET, 
                new MarketOrderExecutionStrategy(stockDataService, userData, transactions, transactionIdCounter));
        this.executionStrategies.put(OrderExecutionType.LIMIT, 
                new LimitOrderExecutionStrategy(stockDataService, userData, transactions, transactionIdCounter));
        
        log.info("StockService initialized with {} markets. StockDataService, UserData, and Transactions injected (singletons)", 
                Market.values().length);
    }

    // Place a buy order
    public String placeBuyOrder(UUID userId, double price , long quantity, OrderExecutionType orderExecutionType)  {

        Order buyOrder = new Order(orderIdCounter.getAndIncrement(), userId,
                OrderType.BUY, orderExecutionType, price, quantity, BTC);

        List<Transaction> executedTransactions = new ArrayList<>();
        long remainingQuantity = quantity;

        // Get the appropriate strategy based on execution type
        OrderExecutionStrategy strategy = executionStrategies.get(orderExecutionType);
        if (strategy != null) {
            remainingQuantity = strategy.executeBuyOrder(buyOrder, remainingQuantity, executedTransactions);
        } else {
            log.info("Unknown order execution type: {}", orderExecutionType);
        }

        long executedQuantity = quantity - remainingQuantity;
        String message = String.format("Buy order placed. Executed: %d, Remaining: %d",
                executedQuantity, remainingQuantity);

        return message;
    }

    // Place a sell order
    public String placeSellOrder(UUID userId, double price ,long quantity, OrderExecutionType orderExecutionType) {

        User user = userData.get(userId);

        // Reserve/deduct stocks for the order
        user.getMarkets().put(BTC, user.getMarkets().get(BTC) - quantity);

        Order sellOrder = new Order(orderIdCounter.getAndIncrement(), userId,
                OrderType.SELL, orderExecutionType, price, quantity, BTC);

        List<Transaction> executedTransactions = new ArrayList<>();
        long remainingQuantity = quantity;

        // Get the appropriate strategy based on execution type
        OrderExecutionStrategy strategy = executionStrategies.get(orderExecutionType);
        if (strategy != null) {
            remainingQuantity = strategy.executeSellOrder(sellOrder, remainingQuantity, executedTransactions);
        } else {
            log.info("Unknown order execution type: {}", orderExecutionType);
        }

        long executedQuantity = quantity - remainingQuantity;

        String message = String.format("Sell order placed. Executed: %d, Remaining: %d",
                executedQuantity, remainingQuantity);

        return message;
    }

    /**
     * Cancel an order for a user.
     * Removes order from orderbook and returns reserved funds/assets.
     */
    public String cancelOrder(UUID userId, OrderType orderType) {
        Order cancelledOrder = stockDataService.removeOrderByUser(BTC, userId, orderType);

        if (cancelledOrder == null) {
            return "No active " + orderType + " order found for user";
        }

        // Return reserved funds/assets
        User user = userData.get(userId);
        if (orderType == OrderType.BUY) {
            // Buy orders don't reserve cash upfront - cash is only deducted on execution
            // So nothing to return for buy orders
            log.info("Cancelled BUY order for user {}: Order ID {}, Quantity {}, Price ${}", 
                    userId, cancelledOrder.getId(), cancelledOrder.getQuantity(), cancelledOrder.getPrice());
        } else {
            // Return BTC that was reserved for sell order
            long reservedBtc = cancelledOrder.getQuantity();
            user.getMarkets().put(BTC, user.getMarkets().get(BTC) + reservedBtc);
            log.info("Cancelled SELL order for user {}: returned {} BTC", userId, reservedBtc);
        }

        return String.format("Order cancelled successfully. Order ID: %d, Type: %s, Quantity: %d, Price: %.2f",
                cancelledOrder.getId(), cancelledOrder.getType(), cancelledOrder.getQuantity(), cancelledOrder.getPrice());
    }

}
