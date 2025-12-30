package com.trading.bot.config;

import com.trading.bot.model.Bot;
import com.trading.bot.service.InMemoryBotStorage;
import com.trading.bot.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Initialize 5-6 bots that trade aggressively.
 */
@Slf4j
@Configuration
public class BotInitializationConfig {

    @Bean
    @Profile("!test")
    public CommandLineRunner initializeBots(InMemoryBotStorage botStorage,
                                            UserService userService,
                                            TradingBotConfig config) {
        return args -> {
            log.info("Initializing trading bots...");

            // Clear existing bots
            botStorage.deleteAll();

            BigDecimal initialBalance = config.getSimulation().getInitialBotBalance();
            String tradingPair = config.getSimulation().getDefaultTradingPair();

            // Create 10 bots with different strategies distributed across:
            // 3 Matchers, 3 Aggressive, 2 Conservative, 2 Timeout
            String[] botNames = {
                "Bot-Matcher-1", "Bot-Matcher-2", "Bot-Matcher-3",
                "Bot-Aggressive-1", "Bot-Aggressive-2", "Bot-Aggressive-3",
                "Bot-Conservative-1", "Bot-Conservative-2",
                "Bot-Timeout-1", "Bot-Timeout-2"
            };
            String[] strategies = {
                "MATCHER", "MATCHER", "MATCHER",
                "AGGRESSIVE", "AGGRESSIVE", "AGGRESSIVE",
                "CONSERVATIVE", "CONSERVATIVE",
                "TIMEOUT", "TIMEOUT"
            };

            for (int i = 0; i < botNames.length; i++) {
                try {
                    String name = botNames[i];
                    String strategy = strategies[i];
                    
                    // Register user in exchange first
                    UUID userId = userService.registerUser(name, initialBalance);
                    
                    // Create bot
                    Bot bot = Bot.builder()
                            .userId(userId)
                            .name(name)
                            .tradingPair(tradingPair)
                            .balance(initialBalance)
                            .assetBalance(BigDecimal.ZERO)
                            .isActive(true)
                            .hasActiveBuyOrder(false)
                            .hasActiveSellOrder(false)
                            .strategyType(strategy)
                            .build();
                    
                    bot.generateId();
                    botStorage.save(bot);
                    
                    log.info("Created and activated bot: {} (User ID: {})", name, userId);
                    
                } catch (Exception e) {
                    log.error("Failed to create bot {}", e.getMessage(), e);
                }
            }

            log.info("Successfully initialized {} bots - they will trade aggressively!", botStorage.count());
        };
    }
}

