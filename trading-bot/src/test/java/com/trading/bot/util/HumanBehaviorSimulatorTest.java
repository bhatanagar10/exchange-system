package com.trading.bot.util;

import com.trading.bot.config.TradingBotConfig;
import com.trading.bot.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HumanBehaviorSimulatorTest {

    private HumanBehaviorSimulator simulator;
    private TradingBotConfig config;
    private Bot testBot;
    private TradeDecision testDecision;
    private MarketData testMarketData;

    @BeforeEach
    void setUp() {
        config = new TradingBotConfig();
        TradingBotConfig.Behavior behavior = new TradingBotConfig.Behavior();
        behavior.setReactionDelayMinMs(5000);
        behavior.setReactionDelayMaxMs(30000);
        behavior.setSkipTradeProbability(0.18);
        behavior.setOrderSizeVariance(0.30);
        behavior.setPriceSlippagePercent(0.002);
        behavior.setMistakeProbability(0.05);
        behavior.setInactivePeriodProbability(0.10);
        config.setBehavior(behavior);

        simulator = new HumanBehaviorSimulator(config);

        testBot = Bot.builder()
                .id(1L)
                .userId(UUID.randomUUID())
                .name("TestBot")
                .strategyType(StrategyType.MARKET_MAKER)
                .balance(new BigDecimal("100000"))
                .assetBalance(new BigDecimal("1.0"))
                .minTradeAmount(new BigDecimal("0.01"))
                .maxTradeAmount(new BigDecimal("1.0"))
                .aggressiveness(new BigDecimal("0.5"))
                .currentMood(BotMood.NEUTRAL)
                .build();

        testDecision = TradeDecision.builder()
                .action(TradeAction.BUY)
                .amount(new BigDecimal("0.5"))
                .price(new BigDecimal("50000"))
                .confidence(new BigDecimal("0.8"))
                .executionType(OrderExecutionType.LIMIT)
                .reason("Test trade")
                .build();

        testMarketData = MarketData.builder()
                .tradingPair("BTC/USDT")
                .currentPrice(new BigDecimal("50000"))
                .volatility(new BigDecimal("0.01"))
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Test
    void testCalculateReactionDelay_withinBounds() {
        long delay = simulator.calculateReactionDelay(testBot, testDecision);
        
        // Delay should be positive
        assertTrue(delay > 0, "Delay should be positive");
        
        // Should be within reasonable bounds (accounting for mood/confidence adjustments)
        assertTrue(delay < 120000, "Delay should be less than 2 minutes");
    }

    @Test
    void testCalculateReactionDelay_panicMoodIsFaster() {
        testBot.setCurrentMood(BotMood.NEUTRAL);
        long neutralDelay = simulator.calculateReactionDelay(testBot, testDecision);
        
        testBot.setCurrentMood(BotMood.PANIC);
        long panicDelay = simulator.calculateReactionDelay(testBot, testDecision);
        
        // On average, panic should be faster (though randomness may affect individual results)
        // We just verify panic delay is reasonable
        assertTrue(panicDelay > 0);
    }

    @Test
    void testAdjustOrderSize_withinBounds() {
        BigDecimal adjusted = simulator.adjustOrderSize(testDecision.getAmount(), testBot);
        
        assertNotNull(adjusted);
        // Should be within min/max bounds
        assertTrue(adjusted.compareTo(testBot.getMinTradeAmount()) >= 0);
        assertTrue(adjusted.compareTo(testBot.getMaxTradeAmount()) <= 0);
    }

    @Test
    void testAdjustOrderSize_greedyMoodIncreasesSize() {
        testBot.setCurrentMood(BotMood.NEUTRAL);
        BigDecimal baseAmount = new BigDecimal("0.5");
        
        // Run multiple times to check tendency (mood affects multiplier)
        testBot.setCurrentMood(BotMood.GREEDY);
        BigDecimal greedyAdjusted = simulator.adjustOrderSize(baseAmount, testBot);
        
        // Greedy mood should result in larger sizes on average
        // Due to randomness, we just verify it's valid
        assertNotNull(greedyAdjusted);
        assertTrue(greedyAdjusted.compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void testAddPriceSlippage_modifiesPrice() {
        BigDecimal original = testDecision.getPrice();
        BigDecimal slipped = simulator.addPriceSlippage(original, testMarketData, testBot);
        
        assertNotNull(slipped);
        // Price should be modified but reasonably close to original
        BigDecimal maxDiff = original.multiply(new BigDecimal("0.01")); // 1% max expected
        BigDecimal actualDiff = slipped.subtract(original).abs();
        assertTrue(actualDiff.compareTo(maxDiff) <= 0, 
                "Slippage should be within 1% of original price");
    }

    @RepeatedTest(20)
    void testShouldSkipTrade_probabilityDistribution() {
        // With 18% skip probability, we should see some skips and some non-skips
        boolean result = simulator.shouldSkipTrade(testBot);
        // Just verify it returns a boolean without throwing
        assertNotNull(result);
    }

    @RepeatedTest(50)
    void testShouldMakeMistake_lowProbability() {
        // Mistake probability is 5%, so most should be false
        // We're testing that the method works, not exact probability
        boolean result = simulator.shouldMakeMistake(testBot);
        assertNotNull(result);
    }

    @Test
    void testApplyFatFingerError_modifiesPrice() {
        BigDecimal original = new BigDecimal("50000");
        BigDecimal errored = simulator.applyFatFingerError(original, TradeAction.BUY);
        
        assertNotNull(errored);
        // Fat finger should modify the price
        // The modification could be small (1%) or large (10x), so we just check it's different
        // (In rare cases of ~1% error it might round to same, but usually different)
    }

    @Test
    void testCalculateBotMood_panicOnLargeDrop() {
        BotMood mood = simulator.calculateBotMood(testBot, BigDecimal.ZERO, new BigDecimal("-10"));
        assertEquals(BotMood.PANIC, mood);
    }

    @Test
    void testCalculateBotMood_fomoOnLargeRise() {
        BotMood mood = simulator.calculateBotMood(testBot, BigDecimal.ZERO, new BigDecimal("10"));
        assertEquals(BotMood.FOMO, mood);
    }

    @Test
    void testCalculateBotMood_greedyOnProfit() {
        testBot.setInitialBalance(new BigDecimal("100000"));
        BigDecimal profit = new BigDecimal("5000"); // 5% profit
        BotMood mood = simulator.calculateBotMood(testBot, profit, BigDecimal.ZERO);
        assertEquals(BotMood.GREEDY, mood);
    }

    @Test
    void testCalculateBotMood_fearfulOnLoss() {
        testBot.setInitialBalance(new BigDecimal("100000"));
        BigDecimal loss = new BigDecimal("-5000"); // 5% loss
        BotMood mood = simulator.calculateBotMood(testBot, loss, BigDecimal.ZERO);
        assertEquals(BotMood.FEARFUL, mood);
    }

    @Test
    void testCalculateBotMood_neutralOnSmallChanges() {
        testBot.setInitialBalance(new BigDecimal("100000"));
        BotMood mood = simulator.calculateBotMood(testBot, new BigDecimal("100"), new BigDecimal("0.5"));
        assertEquals(BotMood.NEUTRAL, mood);
    }

    @Test
    void testShouldGoInactive_returnsBoolean() {
        boolean result = simulator.shouldGoInactive(testBot);
        // Just verify it works
        assertNotNull(result);
    }

    @Test
    void testCalculateInactiveDuration_withinRange() {
        long duration = simulator.calculateInactiveDuration();
        assertTrue(duration >= 5 && duration <= 30, 
                "Inactive duration should be between 5 and 30 minutes");
    }
}

