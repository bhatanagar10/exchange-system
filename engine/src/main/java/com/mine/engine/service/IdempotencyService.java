package com.mine.engine.service;

import com.mine.engine.model.IdempotencyStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service to manage idempotency keys and their processing status using Redis
 * Prevents duplicate order processing for the same idempotency key
 * Persists across server restarts to prevent duplicate processing
 */
@Slf4j
@Service
public class IdempotencyService {

    private static final String REDIS_KEY_PREFIX = "idempotency:";
    private static final Duration KEY_EXPIRATION = Duration.ofDays(7); // Expire keys after 7 days
    
    private final RedisTemplate<String, String> redisTemplate;

    public IdempotencyService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        log.info("IdempotencyService initialized with Redis");
    }
    
    private String getRedisKey(String idempotencyKey) {
        return REDIS_KEY_PREFIX + idempotencyKey;
    }

    /**
     * Check if an idempotency key is already processed or in progress
     * 
     * @param idempotencyKey The idempotency key to check
     * @return true if the key is already IN_PROGRESS or DONE, false otherwise
     */
    public boolean isKeyProcessed(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            return false; // No idempotency key provided, allow processing
        }
        
        String statusStr = redisTemplate.opsForValue().get(getRedisKey(idempotencyKey));
        if (statusStr == null) {
            return false;
        }
        
        try {
            IdempotencyStatus status = IdempotencyStatus.valueOf(statusStr);
            return status == IdempotencyStatus.IN_PROGRESS || status == IdempotencyStatus.DONE;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid idempotency status in Redis for key {}: {}", idempotencyKey, statusStr);
            return false;
        }
    }

    /**
     * Mark an idempotency key as IN_PROGRESS
     * This should be called before processing an order
     * Uses Redis SETNX for atomic operation
     * 
     * @param idempotencyKey The idempotency key to mark as in progress
     * @return true if successfully marked as IN_PROGRESS (key was not already processed), false otherwise
     */
    public boolean markAsInProgress(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            return true; // No idempotency key, allow processing
        }
        
        String redisKey = getRedisKey(idempotencyKey);
        
        // Use setIfAbsent (SETNX) for atomic check-and-set operation
        Boolean setIfAbsent = redisTemplate.opsForValue().setIfAbsent(
                redisKey, 
                IdempotencyStatus.IN_PROGRESS.name(),
                KEY_EXPIRATION
        );
        
        if (Boolean.TRUE.equals(setIfAbsent)) {
            // Successfully set the key
            log.debug("Marked idempotency key {} as IN_PROGRESS in Redis", idempotencyKey);
            return true;
        } else {
            // Key already exists
            String existingStatus = redisTemplate.opsForValue().get(redisKey);
            log.warn("Idempotency key {} already exists in Redis with status: {}", idempotencyKey, existingStatus);
            return false;
        }
    }

    /**
     * Mark an idempotency key as DONE
     * This should be called after successfully processing an order
     * 
     * @param idempotencyKey The idempotency key to mark as done
     */
    public void markAsDone(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            return; // No idempotency key, nothing to mark
        }
        
        String redisKey = getRedisKey(idempotencyKey);
        redisTemplate.opsForValue().set(redisKey, IdempotencyStatus.DONE.name(), KEY_EXPIRATION);
        log.debug("Marked idempotency key {} as DONE in Redis", idempotencyKey);
    }

    /**
     * Get the status of an idempotency key
     * 
     * @param idempotencyKey The idempotency key to check
     * @return The status of the key, or null if the key doesn't exist
     */
    public IdempotencyStatus getStatus(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            return null;
        }
        
        String statusStr = redisTemplate.opsForValue().get(getRedisKey(idempotencyKey));
        if (statusStr == null) {
            return null;
        }
        
        try {
            return IdempotencyStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid idempotency status in Redis for key {}: {}", idempotencyKey, statusStr);
            return null;
        }
    }

    /**
     * Remove an idempotency key from Redis (for cleanup purposes)
     * 
     * @param idempotencyKey The idempotency key to remove
     */
    public void removeKey(String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
            redisTemplate.delete(getRedisKey(idempotencyKey));
            log.debug("Removed idempotency key {} from Redis", idempotencyKey);
        }
    }
}
