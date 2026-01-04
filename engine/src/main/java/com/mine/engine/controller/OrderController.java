package com.mine.engine.controller;

import com.mine.engine.model.OrderType;
import com.mine.engine.service.StockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for Order Management APIs.
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@CrossOrigin(origins = "*")
public class OrderController {

    private final StockService stockService;

    public OrderController(StockService stockService) {
        this.stockService = stockService;
    }

    /**
     * Cancel an order for a user.
     * POST /api/orders/{userId}/cancel
     * Body: {"orderType": "BUY" or "SELL"}
     */
    @PostMapping("/{userId}/cancel")
    public ResponseEntity<CancelOrderResponse> cancelOrder(
            @PathVariable Long userId,
            @RequestBody CancelOrderRequest request) {
        
        try {
            OrderType orderType = OrderType.valueOf(request.getOrderType().toUpperCase());
            String result = stockService.cancelOrder(userId, orderType);
            
            CancelOrderResponse response = new CancelOrderResponse();
            response.setUserId(userId);
            response.setOrderType(orderType.toString());
            response.setMessage(result);
            response.setSuccess(true);
            
            log.info("Order cancelled for user {}: {}", userId, result);
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid order type: {}", request.getOrderType());
            CancelOrderResponse response = new CancelOrderResponse();
            response.setSuccess(false);
            response.setMessage("Invalid order type. Must be BUY or SELL");
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            log.error("Error cancelling order for user {}: {}", userId, e.getMessage(), e);
            CancelOrderResponse response = new CancelOrderResponse();
            response.setSuccess(false);
            response.setMessage("Failed to cancel order: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    // Request/Response DTOs
    @lombok.Data
    public static class CancelOrderRequest {
        private String orderType; // "BUY" or "SELL"
    }

    @lombok.Data
    public static class CancelOrderResponse {
        private Long userId;
        private String orderType;
        private String message;
        private boolean success;
    }
}

