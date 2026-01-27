package com.mine.engine.service;

import com.mine.engine.model.Market;
import com.mine.engine.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service to read and write user data from Redis
 * Engine service reads/writes user data from Redis (managed by main service)
 * Uses Map format to avoid class name conflicts between services
 */
@Slf4j
@Service
public class UserRedisService {

    private static final String USER_KEY_PREFIX = "user:";
    private final RedisTemplate<String, Object> redisTemplate;

    public UserRedisService(@org.springframework.beans.factory.annotation.Qualifier("userRedisTemplate") 
                           RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        log.info("UserRedisService initialized - Engine will read/write user data from Redis");
    }

    private String getUserKey(Long userId) {
        return USER_KEY_PREFIX + userId;
    }

    /**
     * Get user from Redis
     * Reads Map format and converts to User model
     */
    public User getUser(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            Object data = redisTemplate.opsForValue().get(getUserKey(userId));
            if (data == null) {
                log.warn("User not found in Redis: {}", userId);
                return null;
            }
            
            // Convert Map to User
            Map<String, Object> userMap;
            if (data instanceof Map) {
                userMap = (Map<String, Object>) data;
            } else {
                // Try to deserialize as User object (fallback)
                log.warn("Unexpected data type in Redis for user {}: {}", userId, data.getClass());
                return null;
            }
            
            User user = new User();
            user.setId(((Number) userMap.get("id")).longValue());
            user.setCash(((Number) userMap.get("cash")).doubleValue());
            
            // Convert markets map
            Map<Market, Long> markets = new HashMap<>();
            Object marketsObj = userMap.get("markets");
            if (marketsObj instanceof Map) {
                Map<String, Object> marketsMap = (Map<String, Object>) marketsObj;
                for (Map.Entry<String, Object> entry : marketsMap.entrySet()) {
                    try {
                        Market market = Market.valueOf(entry.getKey().toUpperCase());
                        Long quantity = ((Number) entry.getValue()).longValue();
                        markets.put(market, quantity);
                    } catch (IllegalArgumentException e) {
                        log.warn("Unknown market in Redis: {}", entry.getKey());
                    }
                }
            }
            
            // Ensure all markets are initialized
            for (Market market : Market.values()) {
                markets.putIfAbsent(market, 0L);
            }
            user.setMarkets(markets);
            
            return user;
        } catch (Exception e) {
            log.error("Error reading user from Redis: {}", userId, e);
            return null;
        }
    }

    /**
     * Update user in Redis
     * Converts User model to Map format for storage
     * Engine writes user data back to Redis after balance updates
     */
    public void updateUser(User user) {
        if (user == null || user.getId() == null) {
            log.warn("Cannot update user: user or user ID is null");
            return;
        }
        try {
            // Convert User to Map format
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("id", user.getId());
            userMap.put("cash", user.getCash());
            
            // Convert markets map
            Map<String, Long> marketsMap = new HashMap<>();
            if (user.getMarkets() != null) {
                for (Map.Entry<Market, Long> entry : user.getMarkets().entrySet()) {
                    marketsMap.put(entry.getKey().name(), entry.getValue());
                }
            }
            userMap.put("markets", marketsMap);
            
            redisTemplate.opsForValue().set(getUserKey(user.getId()), userMap);
            log.debug("User updated in Redis - ID: {}, Cash: {}", user.getId(), user.getCash());
        } catch (Exception e) {
            log.error("Error updating user in Redis: {}", user.getId(), e);
        }
    }

    /**
     * Check if user exists in Redis
     */
    public boolean userExists(Long userId) {
        if (userId == null) {
            return false;
        }
        try {
            return redisTemplate.hasKey(getUserKey(userId));
        } catch (Exception e) {
            log.error("Error checking user existence in Redis: {}", userId, e);
            return false;
        }
    }
}
