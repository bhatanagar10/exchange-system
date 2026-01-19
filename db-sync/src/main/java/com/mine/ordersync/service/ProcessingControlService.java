package com.mine.ordersync.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service to control event processing (halt/resume)
 */
@Slf4j
@Service
public class ProcessingControlService {
    
    @Getter
    private final AtomicBoolean processingEnabled = new AtomicBoolean(true);
    
    @Getter
    private String haltReason;
    
    /**
     * Halt processing (called when event fails)
     */
    public void haltProcessing(String reason) {
        if (processingEnabled.compareAndSet(true, false)) {
            this.haltReason = reason;
            log.error("Event processing HALTED. Reason: {}", reason);
        }
    }
    
    /**
     * Resume processing (manually triggered)
     */
    public void resumeProcessing() {
        if (processingEnabled.compareAndSet(false, true)) {
            String previousReason = this.haltReason;
            this.haltReason = null;
            log.info("Event processing RESUMED. Previous halt reason: {}", previousReason);
        } else {
            log.warn("Processing is already enabled");
        }
    }
    
    /**
     * Check if processing is enabled
     */
    public boolean isProcessingEnabled() {
        return processingEnabled.get();
    }
    
    /**
     * Get current status
     */
    public ProcessingStatus getStatus() {
        return new ProcessingStatus(
            processingEnabled.get(),
            haltReason,
            processingEnabled.get() ? "RUNNING" : "HALTED"
        );
    }
    
    public record ProcessingStatus(
        boolean enabled,
        String haltReason,
        String status
    ) {}
}


