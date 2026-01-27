package com.mine.main.controller;

import com.mine.main.model.Market;
import com.mine.main.model.User;
import com.mine.main.service.UserRedisService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {

    private final UserRedisService userRedisService;

    public UserController(UserRedisService userRedisService) {
        this.userRedisService = userRedisService;
    }

    /**
     * Create a new user
     * POST /api/users
     */
    @PostMapping
    public ResponseEntity<CreateUserResponse> createUser(@RequestBody CreateUserRequest request) {
        try {
            Long userId = userRedisService.createUser(request.getInitialBalance());
            
            CreateUserResponse response = new CreateUserResponse();
            response.setUserId(userId);
            response.setCashBalance(request.getInitialBalance());
            response.setMessage("User created successfully");
            
            log.info("User created - ID: {}, Initial Balance: {}", userId, request.getInitialBalance());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to create user: {}", e.getMessage(), e);
            CreateUserResponse response = new CreateUserResponse();
            response.setMessage("Failed to create user: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Get user balance and holdings
     * GET /api/users/{userId}/balance
     */
    @GetMapping("/{userId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable Long userId) {
        try {
            User user = userRedisService.getUser(userId);
            
            if (user == null) {
                log.warn("User not found: {}", userId);
                return ResponseEntity.notFound().build();
            }

            BalanceResponse response = new BalanceResponse();
            response.setUserId(userId);
            response.setCashBalance(user.getCash());
            response.setHoldings(new HashMap<>(user.getMarkets()));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to get balance for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Add balance to user account
     * POST /api/users/{userId}/balance
     */
    @PostMapping("/{userId}/balance")
    public ResponseEntity<BalanceResponse> addBalance(
            @PathVariable Long userId,
            @RequestBody AddBalanceRequest request) {
        try {
            if (!userRedisService.userExists(userId)) {
                log.warn("User not found: {}", userId);
                return ResponseEntity.notFound().build();
            }

            userRedisService.addBalance(userId, request.getAmount());
            
            User user = userRedisService.getUser(userId);
            BalanceResponse response = new BalanceResponse();
            response.setUserId(userId);
            response.setCashBalance(user.getCash());
            response.setHoldings(new HashMap<>(user.getMarkets()));
            
            log.info("Balance added to user {} - Amount: {}, New Balance: {}", 
                    userId, request.getAmount(), user.getCash());
            return ResponseEntity.ok(response);
            
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Failed to add balance for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Check if user exists
     * GET /api/users/{userId}/exists
     */
    @GetMapping("/{userId}/exists")
    public ResponseEntity<Map<String, Boolean>> checkUserExists(@PathVariable Long userId) {
        Map<String, Boolean> response = new HashMap<>();
        response.put("exists", userRedisService.userExists(userId));
        return ResponseEntity.ok(response);
    }

    // DTOs
    @Data
    public static class CreateUserRequest {
        private Double initialBalance;
    }

    @Data
    public static class CreateUserResponse {
        private Long userId;
        private double cashBalance;
        private String message;
    }

    @Data
    public static class BalanceResponse {
        private Long userId;
        private double cashBalance;
        private Map<Market, Long> holdings;
    }

    @Data
    public static class AddBalanceRequest {
        private double amount;
    }
}
