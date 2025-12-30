package com.trading.bot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

/**
 * Configuration properties for the trading bot service.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "trading-bot")
public class TradingBotConfig {

    private Exchange exchange = new Exchange();
    private RabbitMQ rabbitmq = new RabbitMQ();
    private Execution execution = new Execution();
    private Strategy strategy = new Strategy();
    private Simulation simulation = new Simulation();
    private Safety safety = new Safety();
    private Behavior behavior = new Behavior();

    @Getter
    @Setter
    public static class Exchange {
        private String apiUrl = "http://localhost:8080/api";
        private int timeout = 5000;
        private int maxRetries = 3;
        private int retryDelayMs = 1000;
    }

    @Getter
    @Setter
    public static class RabbitMQ {
        private String exchange = "engine.test.exchange";
        private String routingKey = "engine.test.routing.key";
        private String queue = "engine.test.queue";
    }

    @Getter
    @Setter
    public static class Execution {
        private int threadPoolSize = 20;
        private int rateLimitPerMinute = 100;
        private long botExecutionIntervalMs = 15000;
    }

    @Getter
    @Setter
    public static class Strategy {
        private double marketMakerSpread = 0.005;
        private int trendMaShort = 5;
        private int trendMaLong = 15;
        private int meanReversionPeriod = 30;
        private double momentumThreshold = 0.03;
        private double scalperProfitTarget = 0.002;
        private double rangeBufferPercent = 0.01;
    }

    @Getter
    @Setter
    public static class Simulation {
        private String defaultTradingPair = "BTC";
        private BigDecimal minOrderSize = new BigDecimal("1");
        private BigDecimal maxOrderSize = new BigDecimal("100");
        private BigDecimal initialBotBalance = new BigDecimal("100000.0");
    }

    @Getter
    @Setter
    public static class Safety {
        private double maxLossPercent = 0.20;
        private double circuitBreakerThreshold = 0.30;
        private long circuitBreakerWindowMs = 60000;
        private int maxActiveOrdersPerBot = 10;
    }

    @Getter
    @Setter
    public static class Behavior {
        private long reactionDelayMinMs = 5000;
        private long reactionDelayMaxMs = 30000;
        private double skipTradeProbability = 0.18;
        private double orderSizeVariance = 0.30;
        private double priceSlippagePercent = 0.002;
        private double mistakeProbability = 0.05;
        private double inactivePeriodProbability = 0.10;
    }
}

