package com.mine.engine.service.order;

import com.mine.engine.model.Order;
import com.mine.engine.model.Transaction;
import com.mine.engine.model.User;
import com.mine.engine.service.StockDataService;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static com.mine.engine.model.Market.BTC;

/**
 * Strategy for executing LIMIT orders
 * Limit orders execute only at the specified price or better
 */
@Slf4j
public class LimitOrderExecutionStrategy extends AbstractOrderExecutionStrategy {
    
    public LimitOrderExecutionStrategy(
            StockDataService stockDataService,
            Map<Long, User> userData,
            List<Transaction> transactions,
            AtomicLong transactionIdCounter,
            com.mine.engine.service.DatabaseSyncEventPublisher eventPublisher) {
        super(stockDataService, userData, transactions, transactionIdCounter, eventPublisher);
    }
    
    @Override
    public long executeBuyOrder(Order buyOrder, long remainingQuantity, List<Transaction> executedTransactions) {
        List<Order> unmatchedSells = new ArrayList<>();

        while (remainingQuantity > 0 && !stockDataService.isSellOrdersEmpty(BTC)) {
            Order bestSell = stockDataService.pollSellOrder(BTC);
            if (bestSell == null) {
                break;
            }

            if (buyOrder.getPrice() >= bestSell.getPrice()) {
                double executionPrice = bestSell.getPrice();
                long executionQuantity = Math.min(remainingQuantity, bestSell.getQuantity());

                executeTrade(buyOrder, bestSell, executionPrice, executionQuantity, executedTransactions);
                remainingQuantity -= executionQuantity;

                // If sell order is not fully executed, put it back
                if (bestSell.getQuantity() > 0) {
                    stockDataService.addSellOrder(BTC, bestSell);
                }
            } else {
                unmatchedSells.add(bestSell);
                break;
            }
        }

        // Put back unmatched sell orders
        for (Order order : unmatchedSells) {
            stockDataService.addSellOrder(BTC, order);
        }

        if (remainingQuantity > 0) {
            buyOrder.setQuantity(remainingQuantity);
            stockDataService.addBuyOrder(BTC, buyOrder);
        }

        return remainingQuantity;
    }
    
    @Override
    public long executeSellOrder(Order sellOrder, long remainingQuantity, List<Transaction> executedTransactions) {
        List<Order> unmatchedBuys = new ArrayList<>();

        while (remainingQuantity > 0 && !stockDataService.isBuyOrdersEmpty(BTC)) {
            Order bestBuy = stockDataService.pollBuyOrder(BTC);
            if (bestBuy == null) {
                break;
            }

            if (sellOrder.getPrice() <= bestBuy.getPrice()) {
                double executionPrice = bestBuy.getPrice();
                long executionQuantity = Math.min(remainingQuantity, bestBuy.getQuantity());

                executeTrade(bestBuy, sellOrder, executionPrice, executionQuantity, executedTransactions);
                remainingQuantity -= executionQuantity;

                // If buy order is not fully executed, put it back
                if (bestBuy.getQuantity() > 0) {
                    stockDataService.addBuyOrder(BTC, bestBuy);
                }
            } else {
                unmatchedBuys.add(bestBuy);
                break;
            }
        }

        // Put back unmatched buy orders
        for (Order order : unmatchedBuys) {
            stockDataService.addBuyOrder(BTC, order);
        }

        if (remainingQuantity > 0) {
            sellOrder.setQuantity(remainingQuantity);
            stockDataService.addSellOrder(BTC, sellOrder);
        }

        return remainingQuantity;
    }
}

