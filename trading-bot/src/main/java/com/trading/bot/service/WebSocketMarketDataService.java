package com.trading.bot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.bot.config.TradingBotConfig;
import com.trading.bot.dto.MarketDataDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket client service to subscribe to market data from websocket server
 * Caches market data and provides it to trading bot services
 */
@Slf4j
@Service
public class WebSocketMarketDataService {

    private final TradingBotConfig config;
    private final ObjectMapper objectMapper;
    
    private WebSocketStompClient stompClient;
    private StompSession stompSession;
    private volatile MarketDataDTO cachedMarketData = null;
    private final Object lock = new Object();
    
    // WebSocket server URL (from config or default)
    private final String websocketUrl;

    public WebSocketMarketDataService(TradingBotConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        // Get WebSocket URL from config
        this.websocketUrl = config.getExchange().getWebsocketUrl();
    }

    @PostConstruct
    public void connect() {
        try {
            log.info("Connecting to WebSocket server at {}", websocketUrl);
            
            // Use SockJS client for better compatibility
            List<Transport> transports = java.util.Arrays.asList(
                    new WebSocketTransport(new StandardWebSocketClient())
            );
            SockJsClient sockJsClient = new SockJsClient(transports);
            stompClient = new WebSocketStompClient(sockJsClient);
            
            // Configure message converter
            stompClient.setMessageConverter(new MappingJackson2MessageConverter());
            
            // Configure task scheduler for heartbeats
            ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
            taskScheduler.initialize();
            stompClient.setTaskScheduler(taskScheduler);
            
            // Connect to WebSocket server (using SockJS endpoint)
            StompSessionHandler sessionHandler = new MarketDataStompSessionHandler();
            stompSession = stompClient.connect(websocketUrl, sessionHandler).get(5, TimeUnit.SECONDS);
            
            log.info("Successfully connected to WebSocket server");
            
        } catch (Exception e) {
            log.error("Failed to connect to WebSocket server: {}", e.getMessage(), e);
            log.warn("Trading bot will fall back to HTTP API calls");
        }
    }

    @PreDestroy
    public void disconnect() {
        if (stompSession != null && stompSession.isConnected()) {
            try {
                stompSession.disconnect();
                log.info("Disconnected from WebSocket server");
            } catch (Exception e) {
                log.error("Error disconnecting from WebSocket: {}", e.getMessage());
            }
        }
    }

    /**
     * Get cached current price
     */
    public BigDecimal getCurrentPrice(String tradingPair) {
        synchronized (lock) {
            if (cachedMarketData != null && cachedMarketData.getCurrentPrice() != null) {
                return BigDecimal.valueOf(cachedMarketData.getCurrentPrice());
            }
        }
        return null; // Will fall back to HTTP API
    }

    /**
     * Get cached order book data
     */
    public OrderBookService.OrderBookData getOrderBook(String tradingPair) {
        synchronized (lock) {
            if (cachedMarketData != null) {
                return convertToOrderBookData(cachedMarketData);
            }
        }
        return null; // Will fall back to HTTP API
    }

    /**
     * Get cached market data
     */
    public MarketDataDTO getMarketData() {
        synchronized (lock) {
            return cachedMarketData;
        }
    }

    /**
     * Check if WebSocket is connected
     */
    public boolean isConnected() {
        return stompSession != null && stompSession.isConnected();
    }

    /**
     * Convert MarketDataDTO to OrderBookService.OrderBookData
     */
    private OrderBookService.OrderBookData convertToOrderBookData(MarketDataDTO marketData) {
        List<OrderBookService.OrderLevel> buyOrders = new java.util.ArrayList<>();
        if (marketData.getBuyOrders() != null) {
            for (MarketDataDTO.OrderDTO order : marketData.getBuyOrders()) {
                buyOrders.add(new OrderBookService.OrderLevel(
                        BigDecimal.valueOf(order.getPrice()),
                        order.getQuantity()
                ));
            }
        }
        
        List<OrderBookService.OrderLevel> sellOrders = new java.util.ArrayList<>();
        if (marketData.getSellOrders() != null) {
            for (MarketDataDTO.OrderDTO order : marketData.getSellOrders()) {
                sellOrders.add(new OrderBookService.OrderLevel(
                        BigDecimal.valueOf(order.getPrice()),
                        order.getQuantity()
                ));
            }
        }
        
        return new OrderBookService.OrderBookData(buyOrders, sellOrders);
    }

    /**
     * STOMP session handler for market data
     */
    private class MarketDataStompSessionHandler extends StompSessionHandlerAdapter {
        
        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            log.info("WebSocket STOMP session connected");
            
            // Subscribe to market data topic
            session.subscribe("/topic/public", new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return MarketDataDTO.class;
                }

                @Override
                public void handleFrame(StompHeaders headers, Object payload) {
                    try {
                        // Payload should be MarketDataDTO after JSON deserialization
                        if (payload instanceof MarketDataDTO) {
                            MarketDataDTO marketData = (MarketDataDTO) payload;
                            
                            synchronized (lock) {
                                cachedMarketData = marketData;
                            }
                            
                            log.debug("Received market data update: pair={}, price={}, buyOrders={}, sellOrders={}", 
                                    marketData.getPair(), 
                                    marketData.getCurrentPrice(),
                                    marketData.getBuyOrderCount(),
                                    marketData.getSellOrderCount());
                        } else {
                            // Try to deserialize from JSON string if needed
                            log.warn("Received unexpected payload type: {}", payload.getClass().getName());
                        }
                        
                    } catch (Exception e) {
                        log.error("Error processing WebSocket message: {}", e.getMessage(), e);
                    }
                }
            });
            
            log.info("Subscribed to /topic/public for market data");
        }

        @Override
        public void handleException(StompSession session, StompCommand command, 
                                   StompHeaders headers, byte[] payload, Throwable exception) {
            log.error("WebSocket STOMP exception: {}", exception.getMessage(), exception);
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            log.error("WebSocket transport error: {}", exception.getMessage(), exception);
            // Attempt to reconnect
            try {
                Thread.sleep(5000);
                connect();
            } catch (Exception e) {
                log.error("Failed to reconnect to WebSocket: {}", e.getMessage());
            }
        }
    }
}
