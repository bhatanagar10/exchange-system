package com.mine.main.service;

import com.mine.main.model.Market;
import com.mine.main.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class UserRedisService {

    private static final String USER_KEY_PREFIX = "user:";
    private final RedisTemplate<String, Object> redisTemplate;

    public UserRedisService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        log.info("UserRedisService initialized");
    }

    private String getUserKey(Long userId) {
        return USER_KEY_PREFIX + userId;
    }

    /**
     * Create a new user with initial cash balance
     * Stores user data in Redis as Map format to avoid class name conflicts
     */
    public Long createUser(double initialCash) {
        // Generate a new user ID (using timestamp + random for uniqueness)
        Long userId = System.currentTimeMillis() + (long)(Math.random() * 1000);
        
        // Store as Map format to avoid class name conflicts with engine service
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", userId);
        userMap.put("cash", initialCash);
        
        // Initialize all markets with 0
        Map<String, Long> marketsMap = new HashMap<>();
        for (Market market : Market.values()) {
            marketsMap.put(market.name(), 0L);
        }
        userMap.put("markets", marketsMap);
        
        // Save to Redis
        redisTemplate.opsForValue().set(getUserKey(userId), userMap);
        
        log.info("User created in Redis - ID: {}, Initial Cash: {}", userId, initialCash);
        return userId;
    }

    /**
     * Get user from Redis
     * Reads Map format and converts to User model
     */
    public User getUser(Long userId) {
        Object data = redisTemplate.opsForValue().get(getUserKey(userId));
        if (data == null) {
            return null;
        }
        
        // Convert Map to User
        Map<String, Object> userMap;
        if (data instanceof Map) {
            userMap = (Map<String, Object>) data;
        } else {
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
        
        log.debug("User retrieved from Redis - ID: {}, Cash: {}", userId, user.getCash());
        return user;
    }

    /**
     * Update user in Redis
     * Converts User model to Map format for storage
     */
    public void updateUser(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User and user ID must not be null");
        }
        
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
    }

    /**
     * Add cash to user balance
     */
    public void addBalance(Long userId, double amount) {
        User user = getUser(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found: " + userId);
        }
        user.addCash(amount);
        updateUser(user);
        log.info("Added {} to user {} balance, new balance: {}", amount, userId, user.getCash());
    }

    /**
     * Check if user exists
     */
    public boolean userExists(Long userId) {
        return redisTemplate.hasKey(getUserKey(userId));
    }
}
