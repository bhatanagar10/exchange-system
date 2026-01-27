package com.mine.engine.controller;

import com.mine.engine.model.Market;
import com.mine.engine.model.User;
import com.mine.engine.service.UserRedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for Account/User APIs.
 * NOTE: User creation and balance updates are now handled by main service.
 * This controller only provides read-only access for compatibility.
 */
@Slf4j
@RestController
@RequestMapping("/api/account")
@CrossOrigin(origins = "*")
public class AccountController {

    private final UserRedisService userRedisService;

    public AccountController(UserRedisService userRedisService) {
        this.userRedisService = userRedisService;
    }

    /**
     * Get user balance and holdings (read-only, for compatibility).
     * GET /api/account/{userId}/balance
     * NOTE: User data is managed by main service in Redis
     */
    @GetMapping("/{userId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable Long userId) {
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
    }

    /**
     * Check if user exists.
     * GET /api/account/{userId}/exists
     */
    @GetMapping("/{userId}/exists")
    public ResponseEntity<Map<String, Boolean>> checkUserExists(@PathVariable Long userId) {
        Map<String, Boolean> response = new HashMap<>();
        response.put("exists", userRedisService.userExists(userId));
        return ResponseEntity.ok(response);
    }

    // ================== Request/Response DTOs ==================

    @lombok.Data
    public static class BalanceResponse {
        private Long userId;
        private double cashBalance;
        private Map<Market, Long> holdings;
    }
}

