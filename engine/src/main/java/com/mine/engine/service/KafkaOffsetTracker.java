package com.mine.engine.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to track Kafka consumer offsets per partition
 * Used for snapshot/restore functionality to track which messages have been processed
 * Only created when messaging.provider=kafka
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "messaging.provider", havingValue = "kafka", matchIfMissing = true)
public class KafkaOffsetTracker {
    
    // Map of partition -> last acknowledged offset
    private final Map<Integer, Long> partitionOffsets;
    
    public KafkaOffsetTracker() {
        this.partitionOffsets = new ConcurrentHashMap<>();
        log.info("KafkaOffsetTracker initialized");
    }
    
    /**
     * Update the offset for a partition after a message is acknowledged
     * 
     * @param partition The partition number
     * @param offset The offset that was acknowledged (next offset to process will be offset + 1)
     */
    public void updateOffset(int partition, long offset) {
        partitionOffsets.put(partition, offset);
        log.debug("Updated offset for partition {} to {}", partition, offset);
    }
    
    /**
     * Get the last acknowledged offset for a partition
     * 
     * @param partition The partition number
     * @return The last acknowledged offset, or null if no offset has been tracked for this partition
     */
    public Long getOffset(int partition) {
        return partitionOffsets.get(partition);
    }
    
    /**
     * Get all tracked offsets (snapshot of current state)
     * 
     * @return Map of partition -> offset
     */
    public Map<Integer, Long> getAllOffsets() {
        return new ConcurrentHashMap<>(partitionOffsets);
    }
    
    /**
     * Clear all tracked offsets
     */
    public void clear() {
        partitionOffsets.clear();
        log.info("Cleared all tracked offsets");
    }
    
    /**
     * Restore offsets from a map (used when loading snapshot)
     * 
     * @param offsets Map of partition -> offset
     */
    public void restoreOffsets(Map<Integer, Long> offsets) {
        partitionOffsets.clear();
        if (offsets != null) {
            partitionOffsets.putAll(offsets);
            log.info("Restored {} partition offsets", offsets.size());
        }
    }
}

