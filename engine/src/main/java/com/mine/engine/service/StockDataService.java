package com.mine.engine.service;

import com.mine.engine.model.Market;
import com.mine.engine.model.Order;
import com.mine.engine.model.OrderBook;
import com.mine.engine.model.OrderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to encapsulate stock data operations
 * Provides controlled access to order books instead of direct map access
 */
@Slf4j
@Service
public class StockDataService {
    
    private final Map<Market, OrderBook> stockData;
    
    /**
     * Constructor - initializes order books for all markets
     */
    public StockDataService() {
        this.stockData = new ConcurrentHashMap<>();
        
        // Initialize OrderBook for all markets (BTC, ETH, etc.)
        for (Market market : Market.values()) {
            OrderBook orderBook = new OrderBook();
            stockData.put(market, orderBook);
        }
        
        log.info("StockDataService initialized with {} markets", Market.values().length);
    }
    
    /**
     * Get the OrderBook for a specific market
     * 
     * @param market The market to get the order book for
     * @return OrderBook for the market, or null if market doesn't exist
     */
    public OrderBook getOrderBook(Market market) {
        return stockData.get(market);
    }
    
    /**
     * Add a buy order to the order book
     * 
     * @param market The market to add the order to
     * @param order The buy order to add
     */
    public void addBuyOrder(Market market, Order order) {
        OrderBook orderBook = stockData.get(market);
        if (orderBook != null) {
            orderBook.getBuyOrders().offer(order);
        }
    }
    
    /**
     * Add a sell order to the order book
     * 
     * @param market The market to add the order to
     * @param order The sell order to add
     */
    public void addSellOrder(Market market, Order order) {
        OrderBook orderBook = stockData.get(market);
        if (orderBook != null) {
            orderBook.getSellOrders().offer(order);
        }
    }
    
    /**
     * Poll (remove and return) the highest priority buy order
     * 
     * @param market The market to poll from
     * @return The highest priority buy order, or null if queue is empty or market doesn't exist
     */
    public Order pollBuyOrder(Market market) {
        OrderBook orderBook = stockData.get(market);
        return orderBook != null ? orderBook.getBuyOrders().poll() : null;
    }
    
    /**
     * Poll (remove and return) the highest priority sell order
     * 
     * @param market The market to poll from
     * @return The highest priority sell order, or null if queue is empty or market doesn't exist
     */
    public Order pollSellOrder(Market market) {
        OrderBook orderBook = stockData.get(market);
        return orderBook != null ? orderBook.getSellOrders().poll() : null;
    }
    
    /**
     * Peek at (view without removing) the highest priority buy order
     * 
     * @param market The market to peek at
     * @return The highest priority buy order, or null if queue is empty or market doesn't exist
     */
    public Order peekBuyOrder(Market market) {
        OrderBook orderBook = stockData.get(market);
        return orderBook != null ? orderBook.getBuyOrders().peek() : null;
    }
    
    /**
     * Peek at (view without removing) the highest priority sell order
     * 
     * @param market The market to peek at
     * @return The highest priority sell order, or null if queue is empty or market doesn't exist
     */
    public Order peekSellOrder(Market market) {
        OrderBook orderBook = stockData.get(market);
        return orderBook != null ? orderBook.getSellOrders().peek() : null;
    }
    
    /**
     * Check if buy orders queue is empty
     * 
     * @param market The market to check
     * @return true if buy orders queue is empty or market doesn't exist, false otherwise
     */
    public boolean isBuyOrdersEmpty(Market market) {
        OrderBook orderBook = stockData.get(market);
        return orderBook == null || orderBook.getBuyOrders().isEmpty();
    }
    
    /**
     * Check if sell orders queue is empty
     * 
     * @param market The market to check
     * @return true if sell orders queue is empty or market doesn't exist, false otherwise
     */
    public boolean isSellOrdersEmpty(Market market) {
        OrderBook orderBook = stockData.get(market);
        return orderBook == null || orderBook.getSellOrders().isEmpty();
    }
    
    /**
     * Get a copy of all buy orders as a list (for iteration purposes)
     * This creates a snapshot copy, so modifications won't affect the original queue
     * 
     * @param market The market to get buy orders from
     * @return List of buy orders, or empty list if market doesn't exist
     */
    public List<Order> getAllBuyOrders(Market market) {
        OrderBook orderBook = stockData.get(market);
        return orderBook != null ? new ArrayList<>(orderBook.getBuyOrders()) : new ArrayList<>();
    }
    
    /**
     * Get a copy of all sell orders as a list (for iteration purposes)
     * This creates a snapshot copy, so modifications won't affect the original queue
     * 
     * @param market The market to get sell orders from
     * @return List of sell orders, or empty list if market doesn't exist
     */
    public List<Order> getAllSellOrders(Market market) {
        OrderBook orderBook = stockData.get(market);
        return orderBook != null ? new ArrayList<>(orderBook.getSellOrders()) : new ArrayList<>();
    }
    
    /**
     * Check if a market exists in the stock data
     * 
     * @param market The market to check
     * @return true if market exists, false otherwise
     */
    public boolean containsMarket(Market market) {
        return stockData.containsKey(market);
    }
    
    /**
     * Get all available markets
     * 
     * @return Set of all markets
     */
    public Set<Market> getAllMarkets() {
        return stockData.keySet();
    }
    
    /**
     * Get all order book entries for iteration
     * Used by services that need to iterate over all markets (e.g., SnapshotService)
     * 
     * @return Set of map entries containing all market-orderbook pairs
     */
    public Set<Map.Entry<Market, OrderBook>> getAllEntries() {
        return stockData.entrySet();
    }
    
    /**
     * Clear all order books
     * Used when restoring from snapshot
     */
    public void clear() {
        stockData.clear();
    }
    
    /**
     * Restore order books from a map
     * Used when restoring from snapshot
     * 
     * @param orderBooks Map of market to order book to restore
     */
    public void restore(Map<Market, OrderBook> orderBooks) {
        stockData.putAll(orderBooks);
    }
    
    /**
     * Remove and return an order for a specific user and order type
     * This method polls orders until it finds a match, then puts back the rest
     * 
     * @param market The market to search in
     * @param userId The user ID to match
     * @param orderType The order type (BUY or SELL) to match
     * @return The removed order, or null if not found
     */
    public Order removeOrderByUser(Market market, UUID userId, OrderType orderType) {
        OrderBook orderBook = stockData.get(market);
        if (orderBook == null) {
            return null;
        }

        List<Order> tempOrders = new ArrayList<>();
        Order cancelledOrder = null;

        // Determine which queue to search
        boolean isBuyOrder = orderType == OrderType.BUY;
        
        // Poll orders until we find a match or queue is empty
        while (true) {
            Order order = isBuyOrder ? pollBuyOrder(market) : pollSellOrder(market);
            if (order == null) {
                break; // Queue is empty
            }
            
            if (order.getUserId().equals(userId) && order.getType() == orderType) {
                cancelledOrder = order;
                break; // Found the order to cancel
            }
            
            tempOrders.add(order); // Keep for later
        }

        // Put back orders that weren't cancelled
        for (Order order : tempOrders) {
            if (isBuyOrder) {
                addBuyOrder(market, order);
            } else {
                addSellOrder(market, order);
            }
        }

        return cancelledOrder;
    }

    /**
     * Get the internal map size (number of markets)
     * 
     * @return Number of markets
     */
    public int size() {
        return stockData.size();
    }
}

