package com.trading.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

/**
 * Simplified Trading Bot Application
 * Creates 5-6 bots that trade aggressively to keep the exchange active.
 * - No database, no caching
 * - Users created via exchange API
 * - Auto-balance replenishment
 */
@SpringBootApplication
@EnableScheduling
public class TradingBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradingBotApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

