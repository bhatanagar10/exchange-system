package com.mine.engine.model;

import lombok.Getter;

/**
 * Market enum representing different cryptocurrency markets
 */
@Getter
public enum Market {
    BTC("Bitcoin", "BTC/USD"),
    ETH("Ethereum", "ETH/USD");

    private final String name;
    private final String displayName;

    Market(String name, String displayName) {
        this.name = name;
        this.displayName = displayName;
    }

    /**
     * Get display name for the market
     * @return Display name (e.g., "BTC/USD")
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get full display string
     * @return Full display string (e.g., "BTC - Bitcoin (BTC/USD)")
     */
    public String getDisplayString() {
        return name() + " - " + name + " (" + displayName + ")";
    }

    @Override
    public String toString() {
        return name() + " - " + name;
    }
}

