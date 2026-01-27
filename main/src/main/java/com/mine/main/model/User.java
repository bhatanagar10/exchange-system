package com.mine.main.model;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class User {
    private Long id;
    private double cash;
    private Map<Market, Long> markets;
    
    public User() {
        this.cash = 0.0;
        this.markets = new HashMap<>();
        // Initialize all markets with 0 quantity
        for (Market market : Market.values()) {
            this.markets.put(market, 0L);
        }
    }
    
    public User(Long id, double cash, Map<Market, Long> markets) {
        this.id = id;
        this.cash = cash;
        this.markets = markets != null ? new HashMap<>(markets) : new HashMap<>();
        // Ensure all markets are initialized
        for (Market market : Market.values()) {
            this.markets.putIfAbsent(market, 0L);
        }
    }
    
    public void addCash(double amount) { 
        this.cash += amount; 
    }
    
    public void deductCash(double amount) { 
        this.cash -= amount; 
    }
}
