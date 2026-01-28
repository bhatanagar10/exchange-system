package com.mine.engine.service.rabbitmq;

import com.mine.engine.config.RabbitMQConfig;
import com.mine.engine.dto.MarketDataDTO;
import com.mine.engine.dto.OrderDTO;
import com.mine.engine.model.Market;
import com.mine.engine.model.Order;
import com.mine.engine.model.OrderBook;
import com.mine.engine.model.Transaction;
import com.mine.engine.service.StockDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Service to publish messages to websocket server via RabbitMQ Topic Exchange
 * Uses pub-sub pattern: Engine publishes, Websocket server subscribes and broadcasts
 */
@Service
public class WebSocketPublisherService {

    private final StockDataService stockDataService;
    private final List<Transaction> transactions;

    private static final Logger logger = LoggerFactory.getLogger(WebSocketPublisherService.class);
    
    private final RabbitTemplate rabbitTemplate;

    public WebSocketPublisherService(StockDataService stockDataService, 
                                     List<Transaction> transactions,
                                     RabbitTemplate rabbitTemplate) {
        this.stockDataService = stockDataService;
        this.transactions = transactions;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Publish message to websocket topic exchange
     * The websocket server will consume this and broadcast to subscribed clients
     * 
     * @param message The message object to publish (will be converted to JSON)
     */
    public void publishToWebSocket(Object message) {
        logger.info("Publishing message to websocket exchange: {}", message);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.WEBSOCKET_EXCHANGE,
                RabbitMQConfig.WEBSOCKET_ROUTING_KEY,
                message
        );
        logger.info("Message published to websocket exchange successfully");
    }

    @Scheduled(fixedRate = 3000)
    public void sendMessage(){
        try {
            Market market = Market.BTC; // Currently BTC only
            
            // Get all orders
            List<Order> btcBuyOrders = stockDataService.getAllBuyOrders(market);
            List<Order> btcSellOrders = stockDataService.getAllSellOrders(market);
            
            // Convert buy orders to DTO and sort (highest price first)
            List<OrderDTO> buyOrders = new ArrayList<>();
            for (Order order : btcBuyOrders) {
                buyOrders.add(new OrderDTO(order.getPrice(), order.getQuantity(), order.getUserId()));
            }
            buyOrders.sort(Comparator.comparing(OrderDTO::getPrice).reversed());
            
            // Convert sell orders to DTO and sort (lowest price first for asks)
            List<OrderDTO> sellOrders = new ArrayList<>();
            for (Order order : btcSellOrders) {
                sellOrders.add(new OrderDTO(order.getPrice(), order.getQuantity(), order.getUserId()));
            }
            sellOrders.sort(Comparator.comparing(OrderDTO::getPrice)); // Ascending for asks
            
            // Get best bid and ask
            Order bestBid = stockDataService.peekBuyOrder(market);
            Order bestAsk = stockDataService.peekSellOrder(market);
            
            // Calculate price data
            Double bestBidPrice = bestBid != null ? bestBid.getPrice() : null;
            Double bestAskPrice = bestAsk != null ? bestAsk.getPrice() : null;
            Double currentPrice = null;
            Double spread = null;
            
            if (bestBidPrice != null && bestAskPrice != null) {
                currentPrice = (bestBidPrice + bestAskPrice) / 2.0;
                spread = bestAskPrice - bestBidPrice;
            } else if (bestBidPrice != null) {
                currentPrice = bestBidPrice;
            } else if (bestAskPrice != null) {
                currentPrice = bestAskPrice;
            }
            
            // Get last trade price
            Double lastTradePrice = null;
            if (!transactions.isEmpty()) {
                Transaction lastTrade = transactions.get(transactions.size() - 1);
                lastTradePrice = lastTrade.getExecutionPrice();
            }
            
            // Calculate market summary
            int buyOrderCount = btcBuyOrders.size();
            int sellOrderCount = btcSellOrders.size();
            long bidDepth = btcBuyOrders.stream().mapToLong(Order::getQuantity).sum();
            long askDepth = btcSellOrders.stream().mapToLong(Order::getQuantity).sum();
            double imbalance = 0.0;
            if (bidDepth + askDepth > 0) {
                imbalance = (double)(bidDepth - askDepth) / (bidDepth + askDepth);
            }
            
            // Create comprehensive market data DTO
            MarketDataDTO marketData = MarketDataDTO.builder()
                    .buyOrders(buyOrders)
                    .sellOrders(sellOrders)
                    .pair(market.name())
                    .currentPrice(currentPrice)
                    .bestBid(bestBidPrice)
                    .bestAsk(bestAskPrice)
                    .spread(spread)
                    .lastTradePrice(lastTradePrice)
                    .buyOrderCount(buyOrderCount)
                    .sellOrderCount(sellOrderCount)
                    .bidDepth(bidDepth)
                    .askDepth(askDepth)
                    .imbalance(imbalance)
                    .totalTrades(transactions.size())
                    .timestamp(System.currentTimeMillis())
                    .build();
            
            // Publish comprehensive market data to websocket
            publishToWebSocket(marketData);
            
        } catch (Exception e) {
            logger.error("Error sending market data to websocket: {}", e.getMessage(), e);
        }
    }
}

