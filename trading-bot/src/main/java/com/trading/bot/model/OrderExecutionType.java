package com.trading.bot.model;

/**
 * Order execution type matching the exchange engine.
 */
public enum OrderExecutionType {
    /**
     * Limit order - executes at specified price or better
     */
    LIMIT,
    
    /**
     * Market order - executes immediately at best available price
     */
    MARKET
}

