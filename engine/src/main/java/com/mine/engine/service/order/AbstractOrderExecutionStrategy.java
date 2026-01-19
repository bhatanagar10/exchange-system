package com.mine.engine.service.order;

import com.mine.engine.model.Market;
import com.mine.engine.model.Order;
import com.mine.engine.model.Transaction;
import com.mine.engine.model.User;
import com.mine.engine.service.DatabaseSyncEventPublisher;
import com.mine.engine.service.StockDataService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Abstract base class for order execution strategies
 * Provides common trade execution logic
 */
public abstract class AbstractOrderExecutionStrategy implements OrderExecutionStrategy {
    
    protected final StockDataService stockDataService;
    protected final Map<Long, User> userData;
    protected final List<Transaction> transactions;
    protected final AtomicLong transactionIdCounter;
    protected final DatabaseSyncEventPublisher eventPublisher;
    
    protected AbstractOrderExecutionStrategy(
            StockDataService stockDataService,
            Map<Long, User> userData,
            List<Transaction> transactions,
            AtomicLong transactionIdCounter,
            DatabaseSyncEventPublisher eventPublisher) {
        this.stockDataService = stockDataService;
        this.userData = userData;
        this.transactions = transactions;
        this.transactionIdCounter = transactionIdCounter;
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * Execute a trade between buy and sell orders
     * This is common logic used by both MARKET and LIMIT strategies
     */
    protected void executeTrade(Order buyOrder, Order sellOrder, double executionPrice,
                              long executionQuantity, List<Transaction> executedTransactions) {
        // Update order quantities
        buyOrder.setQuantity(buyOrder.getQuantity() - executionQuantity);
        sellOrder.setQuantity(sellOrder.getQuantity() - executionQuantity);

        // Update user balances
        User buyer = userData.get(buyOrder.getUserId());
        User seller = userData.get(sellOrder.getUserId());

        // Transfer stocks and cash
        // Add stocks to buyer
        Market market = buyOrder.getMarket();
        buyer.getMarkets().put(market, buyer.getMarkets().getOrDefault(market, 0L) + executionQuantity);
        
        // Add cash to seller
        seller.addCash(executionPrice * executionQuantity);
        buyer.deductCash(executionPrice * executionQuantity);
        
        // Deduct stocks from seller (already reserved)
        seller.getMarkets().put(market, seller.getMarkets().getOrDefault(market, 0L) - executionQuantity);

        // Create and record transaction
        Transaction transaction = new Transaction(
                transactionIdCounter.getAndIncrement(),
                buyOrder.getId(),
                sellOrder.getId(),
                buyOrder.getUserId(),
                sellOrder.getUserId(),
                executionPrice,
                executionQuantity
        );

        transactions.add(transaction);
        executedTransactions.add(transaction);
        
        // Publish transaction event to Kafka for database sync
        eventPublisher.publishTransactionEvent(transaction);
        
        // Publish order update events to Kafka for database sync
        // Determine status for buy order
        String buyOrderStatus = buyOrder.getQuantity() == 0 ? "FILLED" : "PARTIALLY_FILLED";
        eventPublisher.publishOrderEvent(buyOrder, 
                com.mine.engine.model.dto.OrderEvent.EventType.ORDER_UPDATED, buyOrderStatus);
        
        // Determine status for sell order
        String sellOrderStatus = sellOrder.getQuantity() == 0 ? "FILLED" : "PARTIALLY_FILLED";
        eventPublisher.publishOrderEvent(sellOrder, 
                com.mine.engine.model.dto.OrderEvent.EventType.ORDER_UPDATED, sellOrderStatus);
    }
}

