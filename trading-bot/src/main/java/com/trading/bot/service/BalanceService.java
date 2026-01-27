package com.trading.bot.service;

import com.trading.bot.config.TradingBotConfig;
import com.trading.bot.model.Bot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Simplified Balance Service - Auto-replenishes balances via exchange API.
 */
@Slf4j
@Service
public class BalanceService {

    private final RestTemplate restTemplate;
    private final TradingBotConfig config;
    private final InMemoryBotStorage botStorage;

    private static final BigDecimal MIN_BALANCE = new BigDecimal("1000");
    private static final BigDecimal REPLENISH_AMOUNT = new BigDecimal("50000");
    private static final BigDecimal MIN_BTC = new BigDecimal("10");
    private static final BigDecimal REPLENISH_BTC = new BigDecimal("100");

    public BalanceService(RestTemplate restTemplate, 
                         TradingBotConfig config,
                         InMemoryBotStorage botStorage) {
        this.restTemplate = restTemplate;
        this.config = config;
        this.botStorage = botStorage;
    }

    /**
     * Check and replenish balance if needed - no regrets, just add money!
     */
    public void checkAndReplenish(Bot bot) {
        // Replenish cash if low
        if (bot.getBalance() == null || bot.getBalance().compareTo(MIN_BALANCE) < 0) {
            replenishCash(bot);
        }

        // Replenish BTC if low
        if (bot.getAssetBalance() == null || bot.getAssetBalance().compareTo(MIN_BTC) < 0) {
            replenishBtc(bot);
        }
    }

    private void replenishCash(Bot bot) {
        try {
            String baseUrl = config.getExchange().getApiUrl();
            String depositUrl = baseUrl + "/users/" + bot.getUserId() + "/balance";
            
            Map<String, Object> request = new HashMap<>();
            request.put("amount", REPLENISH_AMOUNT.doubleValue());
            
            restTemplate.postForObject(depositUrl, request, Map.class);
            
            bot.setBalance(bot.getBalance() != null ? 
                    bot.getBalance().add(REPLENISH_AMOUNT) : REPLENISH_AMOUNT);
            botStorage.save(bot);
            
            log.info("Replenished cash for bot {}: +${}", bot.getName(), REPLENISH_AMOUNT);
            
        } catch (Exception e) {
            log.warn("Failed to replenish cash for bot {}: {}. Adding directly.", 
                    bot.getName(), e.getMessage());
            bot.setBalance(bot.getBalance() != null ? 
                    bot.getBalance().add(REPLENISH_AMOUNT) : REPLENISH_AMOUNT);
            botStorage.save(bot);
        }
    }

    private void replenishBtc(Bot bot) {
        try {
            // Note: BTC replenishment endpoint not yet implemented in main service
            // For now, we'll skip this or add it later
            String baseUrl = config.getExchange().getApiUrl();
            // TODO: Add BTC replenishment endpoint to main service if needed
            log.warn("BTC replenishment not yet implemented in main service for bot {}", bot.getName());
            
            bot.setAssetBalance(bot.getAssetBalance() != null ? 
                    bot.getAssetBalance().add(REPLENISH_BTC) : REPLENISH_BTC);
            botStorage.save(bot);
            
            log.info("Replenished BTC for bot {}: +{} BTC", bot.getName(), REPLENISH_BTC);
            
        } catch (Exception e) {
            log.warn("Failed to replenish BTC for bot {}: {}. Adding directly.", 
                    bot.getName(), e.getMessage());
            bot.setAssetBalance(bot.getAssetBalance() != null ? 
                    bot.getAssetBalance().add(REPLENISH_BTC) : REPLENISH_BTC);
            botStorage.save(bot);
        }
    }
}

