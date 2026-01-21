package com.mine.engine.service;

import com.mine.engine.model.IdempotencyStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to manage idempotency keys and their processing status
 * Prevents duplicate order processing for the same idempotency key
 */
@Slf4j
@Service
public class IdempotencyService {

    // Thread-safe map to store idempotency keys and their statuses
    private final Map<String, IdempotencyStatus> idempotencyMap;

    public IdempotencyService() {
        this.idempotencyMap = new ConcurrentHashMap<>();
        log.info("IdempotencyService initialized");
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
        
        IdempotencyStatus status = idempotencyMap.get(idempotencyKey);
        return status == IdempotencyStatus.IN_PROGRESS || status == IdempotencyStatus.DONE;
    }

    /**
     * Mark an idempotency key as IN_PROGRESS
     * This should be called before processing an order
     * 
     * @param idempotencyKey The idempotency key to mark as in progress
     * @return true if successfully marked as IN_PROGRESS (key was not already processed), false otherwise
     */
    public boolean markAsInProgress(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isEmpty()) {
            return true; // No idempotency key, allow processing
        }
        
        // Use putIfAbsent to atomically check and set
        IdempotencyStatus existingStatus = idempotencyMap.putIfAbsent(idempotencyKey, IdempotencyStatus.IN_PROGRESS);
        
        if (existingStatus != null) {
            // Key already exists
            log.warn("Idempotency key {} already exists with status: {}", idempotencyKey, existingStatus);
            return false;
        }
        
        log.debug("Marked idempotency key {} as IN_PROGRESS", idempotencyKey);
        return true;
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
        
        idempotencyMap.put(idempotencyKey, IdempotencyStatus.DONE);
        log.debug("Marked idempotency key {} as DONE", idempotencyKey);
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
        return idempotencyMap.get(idempotencyKey);
    }

    /**
     * Remove an idempotency key from the map (for cleanup purposes)
     * 
     * @param idempotencyKey The idempotency key to remove
     */
    public void removeKey(String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
            idempotencyMap.remove(idempotencyKey);
            log.debug("Removed idempotency key {}", idempotencyKey);
        }
    }
}
