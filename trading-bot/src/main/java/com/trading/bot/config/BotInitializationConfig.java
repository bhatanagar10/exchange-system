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

            // Create 18 bots with different strategies distributed across:
            // 2 Matchers, 2 Aggressive, 2 Conservative, 2 Timeout, 2 Market Maker, 
            // 1 Trend Following, 1 Mean Reversion, 4 Market Activity, 2 Order Book Balancer
            String[] botNames = {
                "Bot-Matcher-1", "Bot-Matcher-2",
                "Bot-Aggressive-1", "Bot-Aggressive-2",
                "Bot-Conservative-1", "Bot-Conservative-2",
                "Bot-Timeout-1", "Bot-Timeout-2",
                "Bot-MarketMaker-1", "Bot-MarketMaker-2",
                "Bot-TrendFollowing-1",
                "Bot-MeanReversion-1",
                "Bot-MarketActivity-1", "Bot-MarketActivity-2", 
                "Bot-MarketActivity-3", "Bot-MarketActivity-4",
                "Bot-OrderBookBalancer-1", "Bot-OrderBookBalancer-2"
            };
            String[] strategies = {
                "MATCHER", "MATCHER",
                "AGGRESSIVE", "AGGRESSIVE",
                "CONSERVATIVE", "CONSERVATIVE",
                "TIMEOUT", "TIMEOUT",
                "MARKET_MAKER", "MARKET_MAKER",
                "TREND_FOLLOWING",
                "MEAN_REVERSION",
                "MARKET_ACTIVITY", "MARKET_ACTIVITY",
                "MARKET_ACTIVITY", "MARKET_ACTIVITY",
                "ORDER_BOOK_BALANCER", "ORDER_BOOK_BALANCER"
            };

            for (int i = 0; i < botNames.length; i++) {
                try {
                    String name = botNames[i];
                    String strategy = strategies[i];
                    
                    // Register user in exchange first
                    Long userId = userService.registerUser(name, initialBalance);
                    
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

