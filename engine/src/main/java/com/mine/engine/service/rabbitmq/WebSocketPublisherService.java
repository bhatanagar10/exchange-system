package com.mine.engine.service.rabbitmq;

import com.mine.engine.config.RabbitMQConfig;
import com.mine.engine.dto.OrderBookDTO;
import com.mine.engine.dto.OrderDTO;
import com.mine.engine.model.Market;
import com.mine.engine.model.Order;
import com.mine.engine.model.OrderBook;
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

    private static final Logger logger = LoggerFactory.getLogger(WebSocketPublisherService.class);
    
    private final RabbitTemplate rabbitTemplate;

    public WebSocketPublisherService(StockDataService stockDataService, RabbitTemplate rabbitTemplate) {
        this.stockDataService = stockDataService;
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
        // Get BTC market data only
        List<Order> btcBuyOrders = stockDataService.getAllBuyOrders(Market.BTC);
        List<Order> btcSellOrders = stockDataService.getAllSellOrders(Market.BTC);
        
        // Convert buy orders to DTO and sort (highest price first)
        List<OrderDTO> buyOrders = new ArrayList<>();
        for (Order order : btcBuyOrders) {
            buyOrders.add(new OrderDTO(order.getPrice(), order.getQuantity(), order.getUserId()));
        }
        buyOrders.sort(Comparator.comparing(OrderDTO::getPrice).reversed());
        
        // Convert sell orders to DTO and sort (highest price first)
        List<OrderDTO> sellOrders = new ArrayList<>();
        for (Order order : btcSellOrders) {
            sellOrders.add(new OrderDTO(order.getPrice(), order.getQuantity(), order.getUserId()));
        }
        sellOrders.sort(Comparator.comparing(OrderDTO::getPrice).reversed());
        
        // Create OrderBookDTO with sorted orders
        OrderBookDTO orderBookDTO = new OrderBookDTO(buyOrders, sellOrders);
        
        // Publish to websocket
        publishToWebSocket(orderBookDTO);
    }
}

