package com.mine.engine.service.order;

import com.mine.engine.model.Order;
import com.mine.engine.model.Transaction;
import com.mine.engine.service.StockDataService;
import com.mine.engine.service.UserRedisService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.mine.engine.model.Market.BTC;

/**
 * Strategy for executing MARKET orders
 * Market orders execute immediately at the best available price
 */
@Slf4j
public class MarketOrderExecutionStrategy extends AbstractOrderExecutionStrategy {
    
    public MarketOrderExecutionStrategy(
            StockDataService stockDataService,
            UserRedisService userRedisService,
            List<Transaction> transactions,
            AtomicLong transactionIdCounter,
            com.mine.engine.service.DatabaseSyncEventPublisher eventPublisher) {
        super(stockDataService, userRedisService, transactions, transactionIdCounter, eventPublisher);
    }
    
    @Override
    public long executeBuyOrder(Order buyOrder, long remainingQuantity, List<Transaction> executedTransactions) {
        while (remainingQuantity > 0 && !stockDataService.isSellOrdersEmpty(BTC)) {
            Order bestSell = stockDataService.pollSellOrder(BTC);
            if (bestSell == null) {
                break;
            }
            
            double executionPrice = bestSell.getPrice();
            long executionQuantity = Math.min(remainingQuantity, bestSell.getQuantity());

            executeTrade(buyOrder, bestSell, executionPrice, executionQuantity, executedTransactions);
            remainingQuantity -= executionQuantity;

            // If sell order is not fully executed, put it back
            if (bestSell.getQuantity() > 0) {
                stockDataService.addSellOrder(BTC, bestSell);
            }
        }
        
        return remainingQuantity;
    }
    
    @Override
    public long executeSellOrder(Order sellOrder, long remainingQuantity, List<Transaction> executedTransactions) {
        while (remainingQuantity > 0 && !stockDataService.isBuyOrdersEmpty(BTC)) {
            Order bestBuy = stockDataService.pollBuyOrder(BTC);
            if (bestBuy == null) {
                break;
            }
            
            double executionPrice = bestBuy.getPrice();
            long executionQuantity = Math.min(remainingQuantity, bestBuy.getQuantity());

            executeTrade(bestBuy, sellOrder, executionPrice, executionQuantity, executedTransactions);
            remainingQuantity -= executionQuantity;

            // If buy order is not fully executed, put it back
            if (bestBuy.getQuantity() > 0) {
                stockDataService.addBuyOrder(BTC, bestBuy);
            }
        }
        
        return remainingQuantity;
    }
}

