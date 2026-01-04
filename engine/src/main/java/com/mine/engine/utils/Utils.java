package com.mine.engine.utils;

import com.mine.engine.model.Market;
import com.mine.engine.model.Order;
import com.mine.engine.model.OrderBook;
import com.mine.engine.service.StockDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class Utils {
    private final StockDataService stockDataService;

    public Utils(StockDataService stockDataService) {
        this.stockDataService = stockDataService;
    }

    public void printStockData() {
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
                // Create a sorted list from PriorityQueue to print all orders
                List<Order> buyOrdersList = new ArrayList<>(buyOrders);
                buyOrdersList.sort((o1, o2) -> {
                    int priceCompare = Double.compare(o2.getPrice(), o1.getPrice());
                    if (priceCompare != 0) return priceCompare;
                    return o1.getId().compareTo(o2.getId()); // String comparison
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
                sellOrdersList.sort(Comparator.comparingDouble(Order::getPrice).thenComparing(Order::getId));

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
