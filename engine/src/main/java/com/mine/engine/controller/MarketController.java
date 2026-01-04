package com.mine.engine.controller;

import com.mine.engine.model.Market;
import com.mine.engine.model.Order;
import com.mine.engine.model.OrderBook;
import com.mine.engine.model.Transaction;
import com.mine.engine.service.StockDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for Market Data APIs.
 * Provides endpoints for trading bots and external clients to fetch market information.
 */
@Slf4j
@RestController
@RequestMapping("/api/market")
@CrossOrigin(origins = "*")
public class MarketController {

    private final StockDataService stockDataService;
    private final List<Transaction> transactions;

    public MarketController(StockDataService stockDataService,
                            List<Transaction> transactions) {
        this.stockDataService = stockDataService;
        this.transactions = transactions;
    }

    /**
     * Get order book for a trading pair.
     * GET /api/market/orderbook/{pair}
     * Example: /api/market/orderbook/BTC
     */
    @GetMapping("/orderbook/{pair}")
    public ResponseEntity<OrderBookResponse> getOrderBook(@PathVariable String pair) {
        try {
            Market market = Market.valueOf(pair.toUpperCase());
            OrderBook orderBook = stockDataService.getOrderBook(market);

            if (orderBook == null) {
                return ResponseEntity.notFound().build();
            }

            // Convert to response format
            List<OrderLevelDTO> bids = new ArrayList<>();
            List<OrderLevelDTO> asks = new ArrayList<>();

            // Get copy of orders to avoid concurrent modification
            List<Order> buyOrders = stockDataService.getAllBuyOrders(market);
            List<Order> sellOrders = stockDataService.getAllSellOrders(market);

            // Sort and convert buy orders (highest price first)
            buyOrders.stream()
                    .sorted(Comparator.comparing(Order::getPrice).reversed())
                    .limit(50)
                    .forEach(order -> bids.add(new OrderLevelDTO(order.getPrice(), order.getQuantity())));

            // Sort and convert sell orders (lowest price first)
            sellOrders.stream()
                    .sorted(Comparator.comparing(Order::getPrice))
                    .limit(50)
                    .forEach(order -> asks.add(new OrderLevelDTO(order.getPrice(), order.getQuantity())));

            OrderBookResponse response = new OrderBookResponse();
            response.setPair(pair);
            response.setBids(bids);
            response.setAsks(asks);
            response.setTimestamp(System.currentTimeMillis());

            // Calculate best bid/ask
            if (!bids.isEmpty()) {
                response.setBestBid(bids.get(0).getPrice());
            }
            if (!asks.isEmpty()) {
                response.setBestAsk(asks.get(0).getPrice());
            }

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown market pair: {}", pair);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get current price for a trading pair.
     * <p>
     * GET /api/market/price/{pair}
     */
    @GetMapping("/price/{pair}")
    public ResponseEntity<PriceResponse> getCurrentPrice(@PathVariable String pair) {
        try {
            Market market = Market.valueOf(pair.toUpperCase());
            OrderBook orderBook = stockDataService.getOrderBook(market);

            if (orderBook == null) {
                return ResponseEntity.notFound().build();
            }

            PriceResponse response = new PriceResponse();
            response.setPair(pair);
            response.setTimestamp(System.currentTimeMillis());

            // Get best bid and ask
            Order bestBid = stockDataService.peekBuyOrder(market);
            Order bestAsk = stockDataService.peekSellOrder(market);

            if (bestBid != null) {
                response.setBestBid(bestBid.getPrice());
            }
            if (bestAsk != null) {
                response.setBestAsk(bestAsk.getPrice());
            }

            // Calculate mid price as current price
            if (bestBid != null && bestAsk != null) {
                response.setCurrentPrice((bestBid.getPrice() + bestAsk.getPrice()) / 2);
                response.setSpread(bestAsk.getPrice() - bestBid.getPrice());
            } else if (bestBid != null) {
                response.setCurrentPrice(bestBid.getPrice());
            } else if (bestAsk != null) {
                response.setCurrentPrice(bestAsk.getPrice());
            }

            // Get last trade price if available
            if (!transactions.isEmpty()) {
                Transaction lastTrade = transactions.get(transactions.size() - 1);
                response.setLastTradePrice(lastTrade.getExecutionPrice());
            }

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get recent trades for a trading pair.
     * <p>
     * GET /api/market/trades/{pair}?limit=50
     */
    @GetMapping("/trades/{pair}")
    public ResponseEntity<TradesResponse> getRecentTrades(
            @PathVariable String pair,
            @RequestParam(defaultValue = "50") int limit) {

        // Get recent transactions (last N)
        int startIndex = Math.max(0, transactions.size() - limit);
        List<Transaction> recentTrades = transactions.subList(startIndex, transactions.size());

        List<TradeDTO> trades = recentTrades.stream()
                .map(t -> new TradeDTO(
                        t.getId(),
                        t.getExecutionPrice(),
                        t.getExecutionQuantity(),
                        t.getBuyerId(),
                        t.getSellerId(),
                        System.currentTimeMillis() // Would be better to have timestamp in Transaction
                ))
                .collect(Collectors.toList());

        // Reverse to get newest first
        Collections.reverse(trades);

        TradesResponse response = new TradesResponse();
        response.setPair(pair);
        response.setTrades(trades);
        response.setCount(trades.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Get market summary with depth.
     * GET /api/market/summary/{pair}
     */
    @GetMapping("/summary/{pair}")
    public ResponseEntity<MarketSummaryResponse> getMarketSummary(@PathVariable String pair) {
        try {
            Market market = Market.valueOf(pair.toUpperCase());
            OrderBook orderBook = stockDataService.getOrderBook(market);

            if (orderBook == null) {
                return ResponseEntity.notFound().build();
            }

            MarketSummaryResponse response = new MarketSummaryResponse();
            response.setPair(pair);
            response.setTimestamp(System.currentTimeMillis());

            // Order counts
            List<Order> buyOrders = stockDataService.getAllBuyOrders(market);
            List<Order> sellOrders = stockDataService.getAllSellOrders(market);
            response.setBuyOrderCount(buyOrders.size());
            response.setSellOrderCount(sellOrders.size());

            // Calculate total depth
            long bidDepth = buyOrders.stream()
                    .mapToLong(Order::getQuantity)
                    .sum();
            long askDepth = sellOrders.stream()
                    .mapToLong(Order::getQuantity)
                    .sum();

            response.setBidDepth(bidDepth);
            response.setAskDepth(askDepth);

            // Order book imbalance
            long totalDepth = bidDepth + askDepth;
            if (totalDepth > 0) {
                response.setImbalance((double) (bidDepth - askDepth) / totalDepth);
            }

            // Best prices
            Order bestBid = stockDataService.peekBuyOrder(market);
            Order bestAsk = stockDataService.peekSellOrder(market);
            if (bestBid != null) response.setBestBid(bestBid.getPrice());
            if (bestAsk != null) response.setBestAsk(bestAsk.getPrice());

            // Trade count
            response.setTotalTrades(transactions.size());

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ================== Response DTOs ==================

    @lombok.Data
    public static class OrderBookResponse {
        private String pair;
        private List<OrderLevelDTO> bids;
        private List<OrderLevelDTO> asks;
        private Double bestBid;
        private Double bestAsk;
        private long timestamp;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class OrderLevelDTO {
        private double price;
        private long quantity;
    }

    @lombok.Data
    public static class PriceResponse {
        private String pair;
        private Double currentPrice;
        private Double bestBid;
        private Double bestAsk;
        private Double spread;
        private Double lastTradePrice;
        private long timestamp;
    }

    @lombok.Data
    public static class TradesResponse {
        private String pair;
        private List<TradeDTO> trades;
        private int count;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class TradeDTO {
        private long id;
        private double price;
        private long quantity;
        private Long buyerId;
        private Long sellerId;
        private long timestamp;
    }

    @lombok.Data
    public static class MarketSummaryResponse {
        private String pair;
        private int buyOrderCount;
        private int sellOrderCount;
        private long bidDepth;
        private long askDepth;
        private Double imbalance;
        private Double bestBid;
        private Double bestAsk;
        private int totalTrades;
        private long timestamp;
    }
}

