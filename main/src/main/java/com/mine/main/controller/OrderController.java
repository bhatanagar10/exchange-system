package com.mine.main.controller;

import com.mine.main.model.Message;
import com.mine.main.model.OrderExecutionType;
import com.mine.main.model.OrderType;
import com.mine.main.service.EngineClientService;
import com.mine.main.service.KafkaOrderProducer;
import com.mine.main.service.UserRedisService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    private final KafkaOrderProducer kafkaOrderProducer;
    private final EngineClientService engineClientService;
    private final UserRedisService userRedisService;

    public OrderController(
            KafkaOrderProducer kafkaOrderProducer,
            EngineClientService engineClientService,
            UserRedisService userRedisService) {
        this.kafkaOrderProducer = kafkaOrderProducer;
        this.engineClientService = engineClientService;
        this.userRedisService = userRedisService;
    }

    /**
     * Place a buy order
     * POST /api/orders/buy
     */
    @PostMapping("/buy")
    public ResponseEntity<OrderResponse> placeBuyOrder(@RequestBody BuyOrderRequest request) {
        try {
            // Validate user exists
            if (!userRedisService.userExists(request.getUserId())) {
                log.warn("User not found: {}", request.getUserId());
                OrderResponse response = new OrderResponse();
                response.setSuccess(false);
                response.setMessage("User not found");
                return ResponseEntity.badRequest().body(response);
            }

            // Generate idempotency key
            long timestamp = System.currentTimeMillis();
            String idempotencyKey = String.format("MAIN-%d-BUY-%d-%s", 
                    request.getUserId(), timestamp, UUID.randomUUID().toString().substring(0, 8));

            // Create message
            Message message = Message.builder()
                    .userId(request.getUserId())
                    .price(request.getPrice())
                    .quantity(request.getQuantity())
                    .orderExecutionType(OrderExecutionType.valueOf(request.getOrderExecutionType().toUpperCase()))
                    .orderType(OrderType.BUY)
                    .timestamp(timestamp)
                    .idempotencyKey(idempotencyKey)
                    .build();

            // Send to Kafka
            kafkaOrderProducer.sendOrder(message);

            OrderResponse response = new OrderResponse();
            response.setSuccess(true);
            response.setMessage("Buy order placed successfully");
            response.setOrderId(idempotencyKey);
            
            log.info("Buy order placed - UserId: {}, Price: {}, Quantity: {}", 
                    request.getUserId(), request.getPrice(), request.getQuantity());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to place buy order: {}", e.getMessage(), e);
            OrderResponse response = new OrderResponse();
            response.setSuccess(false);
            response.setMessage("Failed to place order: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Place a sell order
     * POST /api/orders/sell
     */
    @PostMapping("/sell")
    public ResponseEntity<OrderResponse> placeSellOrder(@RequestBody SellOrderRequest request) {
        try {
            // Validate user exists
            if (!userRedisService.userExists(request.getUserId())) {
                log.warn("User not found: {}", request.getUserId());
                OrderResponse response = new OrderResponse();
                response.setSuccess(false);
                response.setMessage("User not found");
                return ResponseEntity.badRequest().body(response);
            }

            // Generate idempotency key
            long timestamp = System.currentTimeMillis();
            String idempotencyKey = String.format("MAIN-%d-SELL-%d-%s", 
                    request.getUserId(), timestamp, UUID.randomUUID().toString().substring(0, 8));

            // Create message
            Message message = Message.builder()
                    .userId(request.getUserId())
                    .price(request.getPrice())
                    .quantity(request.getQuantity())
                    .orderExecutionType(OrderExecutionType.valueOf(request.getOrderExecutionType().toUpperCase()))
                    .orderType(OrderType.SELL)
                    .timestamp(timestamp)
                    .idempotencyKey(idempotencyKey)
                    .build();

            // Send to Kafka
            kafkaOrderProducer.sendOrder(message);

            OrderResponse response = new OrderResponse();
            response.setSuccess(true);
            response.setMessage("Sell order placed successfully");
            response.setOrderId(idempotencyKey);
            
            log.info("Sell order placed - UserId: {}, Price: {}, Quantity: {}", 
                    request.getUserId(), request.getPrice(), request.getQuantity());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to place sell order: {}", e.getMessage(), e);
            OrderResponse response = new OrderResponse();
            response.setSuccess(false);
            response.setMessage("Failed to place order: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Cancel an order
     * POST /api/orders/cancel
     */
    @PostMapping("/cancel")
    public ResponseEntity<CancelOrderResponse> cancelOrder(@RequestBody CancelOrderRequest request) {
        try {
            // Forward cancel request to engine service
            Map<String, Object> engineResponse = engineClientService.cancelOrder(
                    request.getUserId(), 
                    request.getOrderType()
            );

            CancelOrderResponse response = new CancelOrderResponse();
            response.setSuccess(true);
            response.setMessage("Order cancellation request sent");
            response.setUserId(request.getUserId());
            response.setOrderType(request.getOrderType());
            
            log.info("Order cancellation requested - UserId: {}, OrderType: {}", 
                    request.getUserId(), request.getOrderType());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to cancel order: {}", e.getMessage(), e);
            CancelOrderResponse response = new CancelOrderResponse();
            response.setSuccess(false);
            response.setMessage("Failed to cancel order: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    // DTOs
    @Data
    public static class BuyOrderRequest {
        private Long userId;
        private double price;
        private long quantity;
        private String orderExecutionType; // "LIMIT" or "MARKET"
    }

    @Data
    public static class SellOrderRequest {
        private Long userId;
        private double price;
        private long quantity;
        private String orderExecutionType; // "LIMIT" or "MARKET"
    }

    @Data
    public static class OrderResponse {
        private boolean success;
        private String message;
        private String orderId;
    }

    @Data
    public static class CancelOrderRequest {
        private Long userId;
        private String orderType; // "BUY" or "SELL"
    }

    @Data
    public static class CancelOrderResponse {
        private boolean success;
        private String message;
        private Long userId;
        private String orderType;
    }
}
