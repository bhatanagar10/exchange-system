package com.mine.engine.service;

import com.mine.engine.model.Market;
import com.mine.engine.model.Order;
import com.mine.engine.model.OrderBook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * SnapshotService - Creates fast binary snapshots of stockData for quick recovery
 * Uses binary serialization for fastest read/write performance
 * Snapshots stored every 10 seconds
 */
@Slf4j
@Service
public class SnapshotService {

    private final StockDataService stockDataService;
    
    private final Path snapshotPath;
    private final boolean snapshotEnabled;
    
    private static final String SNAPSHOT_FILE = "stockData.snapshot";
    private static final String SNAPSHOT_DIRECTORY = "C:\\Users\\INNOVATIONM-ADMIN\\Documents\\mine\\exchange\\engine";

    public SnapshotService(
            StockDataService stockDataService,
            @Value("${snapshot.enabled:true}") boolean snapshotEnabled) {
        this.stockDataService = stockDataService;
        this.snapshotEnabled = snapshotEnabled;
        this.snapshotPath = Paths.get(SNAPSHOT_DIRECTORY);

        // Create snapshot directory if it doesn't exist
        createSnapshotDirectory();
        
        log.info("SnapshotService initialized - Path: {}, Enabled: {}", snapshotPath, snapshotEnabled);
    }

    /**
     * Create snapshot directory if it doesn't exist
     */
    private void createSnapshotDirectory() {
        try {
            if (!Files.exists(snapshotPath)) {
                Files.createDirectories(snapshotPath);
                log.info("Created snapshot directory: {}", snapshotPath);
            }
        } catch (IOException e) {
            log.error("Failed to create snapshot directory: {}", snapshotPath, e);
        }
    }

    /**
     * Scheduled snapshot - runs every 10 seconds
     * Creates binary snapshot for fastest recovery
     */
//    @Scheduled(fixedRate = 10000) // 10 seconds = 10000 milliseconds
    public void createSnapshot() {
        log.info("Starting snapshot creation...");
        if (!snapshotEnabled) {
            return;
        }

        try {
            long startTime = System.currentTimeMillis();
            
            // Create deep copy of stockData for snapshot (thread-safe)
            // Converts PriorityQueues to Lists for reliable serialization
            Map<Market, SerializableOrderBook> snapshotData = createSnapshotCopy();
            
            // Write to binary file (fastest format - no compression)
            Path snapshotFile = snapshotPath.resolve(SNAPSHOT_FILE);
            
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new BufferedOutputStream(
                            Files.newOutputStream(snapshotFile)))) {
                oos.writeObject(snapshotData);
                oos.flush();
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Snapshot created in {}ms - File: {}, Markets: {}", 
                    duration, snapshotFile, snapshotData.size());
            
        } catch (Exception e) {
            log.error("Error creating snapshot", e);
        }
    }

    /**
     * Create a deep copy of stockData for snapshot (thread-safe)
     * Converts PriorityQueues to Lists for reliable serialization
     */
    private Map<Market, SerializableOrderBook> createSnapshotCopy() {
        Map<Market, SerializableOrderBook> copy = new HashMap<>();
        
        // Create deep copy of each OrderBook
        for (Map.Entry<Market, OrderBook> entry : stockDataService.getAllEntries()) {
            Market market = entry.getKey();
            OrderBook originalOrderBook = entry.getValue();
            
            // Convert PriorityQueues to Lists for serialization
            List<Order> buyOrdersList = new ArrayList<>(originalOrderBook.getBuyOrders());
            List<Order> sellOrdersList = new ArrayList<>(originalOrderBook.getSellOrders());
            
            copy.put(market, new SerializableOrderBook(buyOrdersList, sellOrdersList));
        }
        
        return copy;
    }

    /**
     * Load snapshot from disk - fastest binary read
     * 
     * @return Map<Market, OrderBook> if found, null otherwise
     */
    @SuppressWarnings("unchecked")
    public Map<Market, OrderBook> loadSnapshot() {
        try {
            Path snapshotFile = snapshotPath.resolve(SNAPSHOT_FILE);
            
            if (!Files.exists(snapshotFile)) {
                log.info("No snapshot file found at: {}", snapshotFile);
                return null;
            }

            long startTime = System.currentTimeMillis();
            
            // Read binary snapshot (fastest read - no decompression)
            try (ObjectInputStream ois = new ObjectInputStream(
                    new BufferedInputStream(
                            Files.newInputStream(snapshotFile)))) {
                Map<Market, SerializableOrderBook> serializedSnapshot = 
                    (Map<Market, SerializableOrderBook>) ois.readObject();
                
                // Convert back to OrderBook with PriorityQueues
                Map<Market, OrderBook> snapshot = convertToOrderBookMap(serializedSnapshot);
                
                long duration = System.currentTimeMillis() - startTime;
                log.info("Snapshot loaded in {}ms from: {}, Markets: {}", 
                        duration, snapshotFile, snapshot.size());
                
                return snapshot;
            }
            
        } catch (Exception e) {
            log.error("Error loading snapshot", e);
            return null;
        }
    }
    
    /**
     * Convert SerializableOrderBook map to OrderBook map with PriorityQueues
     */
    private Map<Market, OrderBook> convertToOrderBookMap(Map<Market, SerializableOrderBook> serialized) {
        Map<Market, OrderBook> result = new HashMap<>();
        
        for (Map.Entry<Market, SerializableOrderBook> entry : serialized.entrySet()) {
            Market market = entry.getKey();
            SerializableOrderBook serializedOrderBook = entry.getValue();
            
            OrderBook orderBook = new OrderBook();
            
            // Convert Lists back to PriorityQueues
            PriorityQueue<Order> buyOrders = new PriorityQueue<>(
                Comparator.comparing(Order::getPrice).reversed().thenComparing(Order::getId));
            buyOrders.addAll(serializedOrderBook.getBuyOrders());
            orderBook.setBuyOrders(buyOrders);
            
            PriorityQueue<Order> sellOrders = new PriorityQueue<>(
                Comparator.comparing(Order::getPrice).thenComparing(Order::getId));
            sellOrders.addAll(serializedOrderBook.getSellOrders());
            orderBook.setSellOrders(sellOrders);
            
            result.put(market, orderBook);
        }
        
        return result;
    }

    /**
     * Restore stockData from snapshot
     */
//    @Scheduled(fixedRate = 20000)
    public void restoreFromSnapshot() {
        Map<Market, OrderBook> snapshot = loadSnapshot();
        if (snapshot == null) {
            log.info("No snapshot available to restore");
            return;
        }

        try {
            // Restore stockData
            stockDataService.clear();
            stockDataService.restore(snapshot);
            
            log.info("Restored snapshot - Markets: {}", snapshot.size());
            printStockData();
        } catch (Exception e) {
            log.error("Error restoring from snapshot", e);
        }
    }

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

    /**
     * Serializable wrapper for OrderBook data (uses Lists instead of PriorityQueues)
     * This ensures fast and reliable binary serialization
     */
    private static class SerializableOrderBook implements Serializable {
        private static final long serialVersionUID = 1L;
        
        private List<Order> buyOrders;
        private List<Order> sellOrders;
        
        public SerializableOrderBook(List<Order> buyOrders, List<Order> sellOrders) {
            this.buyOrders = new ArrayList<>(buyOrders);
            this.sellOrders = new ArrayList<>(sellOrders);
        }
        
        public List<Order> getBuyOrders() {
            return buyOrders;
        }
        
        public List<Order> getSellOrders() {
            return sellOrders;
        }
    }
}
