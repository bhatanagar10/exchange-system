package com.trading.bot.strategy;

import com.trading.bot.model.*;
import com.trading.bot.strategy.impl.MarketMakerStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MarketMakerStrategyTest {

    private MarketMakerStrategy strategy;
    private Bot testBot;
    private MarketData testMarketData;

    @BeforeEach
    void setUp() {
        strategy = new MarketMakerStrategy();
        ReflectionTestUtils.setField(strategy, "baseSpread", 0.005);

        testBot = Bot.builder()
                .id(1L)
                .userId(UUID.randomUUID())
                .name("TestBot")
                .strategyType(StrategyType.MARKET_MAKER)
                .tradingPair("BTC/USDT")
                .balance(new BigDecimal("100000"))
                .initialBalance(new BigDecimal("100000"))
                .assetBalance(new BigDecimal("1.0"))
                .minTradeAmount(new BigDecimal("0.01"))
                .maxTradeAmount(new BigDecimal("1.0"))
                .isActive(true)
                .aggressiveness(new BigDecimal("0.5"))
                .riskTolerance(new BigDecimal("0.5"))
                .currentMood(BotMood.NEUTRAL)
                .build();

        testMarketData = MarketData.builder()
                .tradingPair("BTC/USDT")
                .currentPrice(new BigDecimal("50000"))
                .bestBid(new BigDecimal("49990"))
                .bestAsk(new BigDecimal("50010"))
                .spread(new BigDecimal("20"))
                .spreadPercent(new BigDecimal("0.0004"))
                .orderBookImbalance(new BigDecimal("0.1"))
                .volatility(new BigDecimal("0.01"))
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Test
    void testAnalyze_returnsValidDecision() {
        TradeDecision decision = strategy.analyze(testMarketData, testBot);
        
        assertNotNull(decision);
        assertNotNull(decision.getAction());
        assertTrue(decision.getAction() == TradeAction.BUY || 
                   decision.getAction() == TradeAction.SELL ||
                   decision.getAction() == TradeAction.HOLD);
    }

    @Test
    void testAnalyze_withNullMarketData_returnsHold() {
        TradeDecision decision = strategy.analyze(null, testBot);
        
        assertNotNull(decision);
        assertEquals(TradeAction.HOLD, decision.getAction());
    }

    @Test
    void testAnalyze_withInactiveBot_returnsHold() {
        testBot.setIsActive(false);
        TradeDecision decision = strategy.analyze(testMarketData, testBot);
        
        assertNotNull(decision);
        assertEquals(TradeAction.HOLD, decision.getAction());
    }

    @Test
    void testAnalyze_buyDecision_hasValidPrice() {
        // Make bot buy by having no assets
        testBot.setAssetBalance(BigDecimal.ZERO);
        
        TradeDecision decision = strategy.analyze(testMarketData, testBot);
        
        if (decision.getAction() == TradeAction.BUY) {
            assertNotNull(decision.getPrice());
            // Buy price should be below current price
            assertTrue(decision.getPrice().compareTo(testMarketData.getCurrentPrice()) <= 0);
        }
    }

    @Test
    void testAnalyze_sellDecision_hasValidPrice() {
        // Make bot sell by having assets and using negative imbalance
        testMarketData.setOrderBookImbalance(new BigDecimal("0.5"));
        
        TradeDecision decision = strategy.analyze(testMarketData, testBot);
        
        if (decision.getAction() == TradeAction.SELL) {
            assertNotNull(decision.getPrice());
            // Sell price should be above current price
            assertTrue(decision.getPrice().compareTo(testMarketData.getCurrentPrice()) >= 0);
        }
    }

    @Test
    void testGetStrategyType() {
        assertEquals(StrategyType.MARKET_MAKER, strategy.getStrategyType());
    }

    @Test
    void testGetName() {
        assertEquals("Market Maker", strategy.getName());
    }

    @Test
    void testReset() {
        // Should not throw
        assertDoesNotThrow(() -> strategy.reset());
    }
}

