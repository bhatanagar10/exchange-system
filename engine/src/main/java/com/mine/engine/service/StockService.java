package com.mine.engine.service;

import com.mine.engine.model.Market;
import com.mine.engine.model.Message;
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
import java.util.concurrent.atomic.AtomicLong;

import static com.mine.engine.model.Market.BTC;


@Slf4j
@Service
public class StockService {

    private static final String ORDER_ID_PREFIX = "ORD-";

    private final StockDataService stockDataService;
    private final UserRedisService userRedisService; // Redis service for user data
    private final List<Transaction> transactions; // Injected singleton list
    private final DatabaseSyncEventPublisher eventPublisher;
    private final IdempotencyService idempotencyService;

    // Transaction counter (order ID is now generated from userId + timestamp)
    private AtomicLong transactionIdCounter;
    
    // Strategy map for order execution
    private final Map<OrderExecutionType, OrderExecutionStrategy> executionStrategies;
    
    /**
     * Constructor with dependency injection of singleton data structures
     * All classes that inject stockDataService, userRedisService, and transactions will get the same instances
     */
    public StockService(StockDataService stockDataService, UserRedisService userRedisService, 
                       List<Transaction> transactions, DatabaseSyncEventPublisher eventPublisher,
                       IdempotencyService idempotencyService) {
        // Inject the stock data service
        this.stockDataService = stockDataService;
        
        // Inject the user Redis service (reads/writes from Redis)
        this.userRedisService = userRedisService;
        
        // Inject the singleton transactions list
        this.transactions = transactions;
        
        // Inject event publisher for database sync
        this.eventPublisher = eventPublisher;
        
        // Inject idempotency service
        this.idempotencyService = idempotencyService;
        
        // Initialize transaction counter (order ID is now generated from userId + timestamp)
        this.transactionIdCounter = new AtomicLong(1);
        
        // Initialize execution strategies
        this.executionStrategies = new EnumMap<>(OrderExecutionType.class);
        this.executionStrategies.put(OrderExecutionType.MARKET, 
                new MarketOrderExecutionStrategy(stockDataService, userRedisService, transactions, transactionIdCounter, eventPublisher));
        this.executionStrategies.put(OrderExecutionType.LIMIT, 
                new LimitOrderExecutionStrategy(stockDataService, userRedisService, transactions, transactionIdCounter, eventPublisher));
        
        log.info("StockService initialized with {} markets. StockDataService, UserRedisService, and Transactions injected", 
                Market.values().length);
    }

    /**
     * Generate order ID in format: ORD-{userId}-{timestamp}
     */
    private String generateOrderId(Long userId, Long timestamp) {
        return ORDER_ID_PREFIX + userId + "-" + timestamp;
    }

    // Place a buy order
    public String placeBuyOrder(Long userId, double price, long quantity, OrderExecutionType orderExecutionType, Long timestamp, String idempotencyKey)  {
        
        // Check idempotency key before processing
        if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
            // Check if idempotency key is already processed or in progress
            if (idempotencyService.isKeyProcessed(idempotencyKey)) {
                log.warn("Order with idempotency key {} is already processed or in progress. Skipping duplicate order.", idempotencyKey);
                return String.format("Order with idempotency key %s already processed or in progress", idempotencyKey);
            }
            
            // Mark idempotency key as IN_PROGRESS before processing
            if (!idempotencyService.markAsInProgress(idempotencyKey)) {
                log.warn("Failed to mark idempotency key {} as IN_PROGRESS. Order may already be processing.", idempotencyKey);
                return String.format("Order with idempotency key %s is already being processed", idempotencyKey);
            }
        }
        
        try {
            if (timestamp == null) {
                throw new IllegalArgumentException("Timestamp is required for order creation");
            }
            
            String orderId = generateOrderId(userId, timestamp);
            Order buyOrder = new Order(orderId, userId,
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
            
            // Publish order event to Kafka for database sync
            String status = remainingQuantity == 0 ? "FILLED" : (executedQuantity > 0 ? "PARTIALLY_FILLED" : "PENDING");
            eventPublisher.publishOrderEvent(buyOrder, 
                    com.mine.engine.model.dto.OrderEvent.EventType.ORDER_CREATED, status);
            
            // Mark idempotency key as done after successful processing
            if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
                idempotencyService.markAsDone(idempotencyKey);
            }
            
            String message = String.format("Buy order placed. Executed: %d, Remaining: %d",
                    executedQuantity, remainingQuantity);

            return message;
        } catch (Exception e) {
            // On error, leave idempotency key as IN_PROGRESS to prevent duplicate processing
            // This ensures that if the same request comes again, it will be rejected
            log.error("Error processing buy order with idempotency key {}: {}", idempotencyKey, e.getMessage(), e);
            throw e;
        }
    }

    // Place a sell order
    public String placeSellOrder(Long userId, double price, long quantity, OrderExecutionType orderExecutionType, Long timestamp, String idempotencyKey) {

        // Check idempotency key before processing
        if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
            // Check if idempotency key is already processed or in progress
            if (idempotencyService.isKeyProcessed(idempotencyKey)) {
                log.warn("Order with idempotency key {} is already processed or in progress. Skipping duplicate order.", idempotencyKey);
                return String.format("Order with idempotency key %s already processed or in progress", idempotencyKey);
            }
            
            // Mark idempotency key as IN_PROGRESS before processing
            if (!idempotencyService.markAsInProgress(idempotencyKey)) {
                log.warn("Failed to mark idempotency key {} as IN_PROGRESS. Order may already be processing.", idempotencyKey);
                return String.format("Order with idempotency key %s is already being processed", idempotencyKey);
            }
        }

        try {
            if (timestamp == null) {
                throw new IllegalArgumentException("Timestamp is required for order creation");
            }
            
            User user = userRedisService.getUser(userId);
            if (user == null) {
                throw new IllegalArgumentException("User not found: " + userId);
            }

            // Reserve/deduct stocks for the order
            user.getMarkets().put(BTC, user.getMarkets().get(BTC) - quantity);
            userRedisService.updateUser(user); // Update in Redis

            String orderId = generateOrderId(userId, timestamp);
            Order sellOrder = new Order(orderId, userId,
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
            
            // Publish order event to Kafka for database sync
            String status = remainingQuantity == 0 ? "FILLED" : (executedQuantity > 0 ? "PARTIALLY_FILLED" : "PENDING");
            eventPublisher.publishOrderEvent(sellOrder, 
                    com.mine.engine.model.dto.OrderEvent.EventType.ORDER_CREATED, status);
            
            // Mark idempotency key as done after successful processing
            if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
                idempotencyService.markAsDone(idempotencyKey);
            }
            
            String message = String.format("Sell order placed. Executed: %d, Remaining: %d",
                    executedQuantity, remainingQuantity);

            return message;
        } catch (Exception e) {
            // On error, leave idempotency key as IN_PROGRESS to prevent duplicate processing
            // This ensures that if the same request comes again, it will be rejected
            log.error("Error processing sell order with idempotency key {}: {}", idempotencyKey, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Process an order (buy or sell) based on the message.
     * Handles idempotency checks internally.
     * 
     * @param message The order message containing all order details
     * @return Result message indicating order status
     */
    public String processOrder(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        
        switch(message.getOrderType()) {
            case BUY:
                return placeBuyOrder(message.getUserId(), message.getPrice(), message.getQuantity(),
                        message.getOrderExecutionType(), message.getTimestamp(), message.getIdempotencyKey());
            case SELL:
                return placeSellOrder(message.getUserId(), message.getPrice(), message.getQuantity(),
                        message.getOrderExecutionType(), message.getTimestamp(), message.getIdempotencyKey());
            default:
                throw new IllegalArgumentException("Unknown order type: " + message.getOrderType());
        }
    }

    /**
     * Cancel an order for a user.
     * Removes order from orderbook and returns reserved funds/assets.
     */
    public String cancelOrder(Long userId, OrderType orderType) {
        Order cancelledOrder = stockDataService.removeOrderByUser(BTC, userId, orderType);

        if (cancelledOrder == null) {
            return "No active " + orderType + " order found for user";
        }

        // Return reserved funds/assets
        User user = userRedisService.getUser(userId);
        if (user == null) {
            return "User not found";
        }
        if (orderType == OrderType.BUY) {
            // Buy orders don't reserve cash upfront - cash is only deducted on execution
            // So nothing to return for buy orders
            log.info("Cancelled BUY order for user {}: Order ID {}, Quantity {}, Price ${}", 
                    userId, cancelledOrder.getId(), cancelledOrder.getQuantity(), cancelledOrder.getPrice());
        } else {
            // Return BTC that was reserved for sell order
            long reservedBtc = cancelledOrder.getQuantity();
            user.getMarkets().put(BTC, user.getMarkets().get(BTC) + reservedBtc);
            userRedisService.updateUser(user); // Update in Redis
            log.info("Cancelled SELL order for user {}: returned {} BTC", userId, reservedBtc);
        }
        
        // Publish order cancelled event to Kafka for database sync
        eventPublisher.publishOrderEvent(cancelledOrder, 
                com.mine.engine.model.dto.OrderEvent.EventType.ORDER_CANCELLED, "CANCELLED");

        return String.format("Order cancelled successfully. Order ID: %s, Type: %s, Quantity: %d, Price: %.2f",
                cancelledOrder.getId(), cancelledOrder.getType(), cancelledOrder.getQuantity(), cancelledOrder.getPrice());
    }

}
