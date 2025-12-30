package com.mine.engine.service.order;

import com.mine.engine.model.Order;
import com.mine.engine.model.Transaction;

import java.util.List;

/**
 * Strategy interface for executing orders based on execution type (MARKET or LIMIT)
 */
public interface OrderExecutionStrategy {
    
    /**
     * Execute a buy order using the strategy-specific logic
     * 
     * @param buyOrder The buy order to execute
     * @param remainingQuantity The remaining quantity to execute
     * @param executedTransactions List to add executed transactions to
     * @return The remaining quantity that couldn't be executed
     */
    long executeBuyOrder(Order buyOrder, long remainingQuantity, List<Transaction> executedTransactions);
    
    /**
     * Execute a sell order using the strategy-specific logic
     * 
     * @param sellOrder The sell order to execute
     * @param remainingQuantity The remaining quantity to execute
     * @param executedTransactions List to add executed transactions to
     * @return The remaining quantity that couldn't be executed
     */
    long executeSellOrder(Order sellOrder, long remainingQuantity, List<Transaction> executedTransactions);
}

