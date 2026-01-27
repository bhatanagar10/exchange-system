package com.trading.bot.service;

import com.trading.bot.config.TradingBotConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Service to create users in the exchange via API.
 */
@Slf4j
@Service
public class UserService {

    private final RestTemplate restTemplate;
    private final TradingBotConfig config;

    public UserService(RestTemplate restTemplate, TradingBotConfig config) {
        this.restTemplate = restTemplate;
        this.config = config;
    }

    /**
     * Register a new user in the exchange and return the generated ID.
     */
    public Long registerUser(String name, BigDecimal initialBalance) {
        try {
            String baseUrl = config.getExchange().getApiUrl();
            String registerUrl = baseUrl + "/users";
            
            Map<String, Object> request = new HashMap<>();
            request.put("initialBalance", initialBalance.doubleValue());
            
            Map<String, Object> response = restTemplate.postForObject(registerUrl, request, Map.class);
            
            if (response != null && response.containsKey("userId")) {
                Object userIdObj = response.get("userId");
                Long userId;
                if (userIdObj instanceof Number) {
                    userId = ((Number) userIdObj).longValue();
                } else {
                    userId = Long.parseLong(userIdObj.toString());
                }
                log.info("Registered user '{}' in exchange with ID: {}", name, userId);
                return userId;
            }
            
            throw new RuntimeException("Exchange did not return userId");
            
        } catch (Exception e) {
            log.error("Failed to register user '{}' in exchange: {}", name, e.getMessage(), e);
            throw new RuntimeException("Failed to register user in exchange", e);
        }
    }

    /**
     * Check if user exists in exchange.
     */
    public boolean userExists(Long userId) {
        try {
            String baseUrl = config.getExchange().getApiUrl();
            String checkUrl = baseUrl + "/users/" + userId + "/exists";
            
            Map<String, Object> response = restTemplate.getForObject(checkUrl, Map.class);
            if (response != null && response.containsKey("exists")) {
                return Boolean.TRUE.equals(response.get("exists"));
            }
            return false;
            
        } catch (Exception e) {
            log.warn("Failed to check if user {} exists: {}", userId, e.getMessage());
            return false;
        }
    }
}

