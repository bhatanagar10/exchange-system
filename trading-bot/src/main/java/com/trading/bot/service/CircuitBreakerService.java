package com.trading.bot.service;

import com.trading.bot.config.TradingBotConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Circuit breaker service to prevent cascading failures
 */
@Slf4j
@Service
public class CircuitBreakerService {

    private final TradingBotConfig config;
    private final Map<String, CircuitBreakerState> breakers = new ConcurrentHashMap<>();

    @Data
    private static class CircuitBreakerState {
        private int errorCount = 0;
        private int successCount = 0;
        private long windowStartTime = System.currentTimeMillis();
        private boolean open = false;
        private long openUntil = 0;
    }

    public CircuitBreakerService(TradingBotConfig config) {
        this.config = config;
    }

    /**
     * Check if circuit breaker is open for a bot
     */
    public boolean isCircuitOpen(String botId) {
        CircuitBreakerState state = breakers.computeIfAbsent(botId, k -> new CircuitBreakerState());
        
        // Reset window if expired
        long windowMs = config.getSafety().getCircuitBreakerWindowMs();
        if (System.currentTimeMillis() - state.getWindowStartTime() > windowMs) {
            resetWindow(botId);
            state = breakers.get(botId);
        }

        // Check if circuit is currently open
        if (state.isOpen()) {
            if (System.currentTimeMillis() < state.getOpenUntil()) {
                return true; // Still in cooldown period
            } else {
                // Cooldown expired, try half-open
                state.setOpen(false);
                state.setErrorCount(0);
                state.setSuccessCount(0);
                log.info("Circuit breaker half-open for bot {}", botId);
            }
        }

        // Calculate error rate
        int totalRequests = state.getErrorCount() + state.getSuccessCount();
        if (totalRequests == 0) {
            return false;
        }

        double errorRate = (double) state.getErrorCount() / totalRequests;
        double threshold = config.getSafety().getCircuitBreakerThreshold();

        if (errorRate > threshold) {
            state.setOpen(true);
            state.setOpenUntil(System.currentTimeMillis() + windowMs);
            log.warn("Circuit breaker opened for bot {}: error rate {} > threshold {}", 
                    botId, errorRate, threshold);
            return true;
        }

        return false;
    }

    /**
     * Record an error
     */
    public void recordError(String botId) {
        CircuitBreakerState state = breakers.computeIfAbsent(botId, k -> new CircuitBreakerState());
        state.setErrorCount(state.getErrorCount() + 1);
        log.debug("Recorded error for bot {}, error count: {}", botId, state.getErrorCount());
    }

    /**
     * Record a success
     */
    public void recordSuccess(String botId) {
        CircuitBreakerState state = breakers.computeIfAbsent(botId, k -> new CircuitBreakerState());
        state.setSuccessCount(state.getSuccessCount() + 1);
        
        // If circuit was open and we have successes, consider closing
        if (state.isOpen() && state.getSuccessCount() >= 5) {
            state.setOpen(false);
            state.setErrorCount(0);
            state.setSuccessCount(0);
            log.info("Circuit breaker closed for bot {} after successful requests", botId);
        }
    }

    /**
     * Reset error window
     */
    private void resetWindow(String botId) {
        CircuitBreakerState state = breakers.get(botId);
        if (state != null) {
            state.setErrorCount(0);
            state.setSuccessCount(0);
            state.setWindowStartTime(System.currentTimeMillis());
        }
    }
}
