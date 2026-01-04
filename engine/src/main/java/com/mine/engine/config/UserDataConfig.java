package com.mine.engine.config;

import com.mine.engine.model.Transaction;
import com.mine.engine.model.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Configuration for singleton data structures
 * This ensures all classes inject and manipulate the same instances
 */
@Configuration
public class UserDataConfig {

    /**
     * Singleton bean for userData map
     * Thread-safe ConcurrentHashMap for concurrent access
     * All classes that inject this will get the same instance
     */
    @Bean
    public Map<Long, User> userData() {
        return new ConcurrentHashMap<>();
    }
    
    // Note: stockData is now managed by StockDataService (@Service)
    // Classes should inject StockDataService instead of Map<Market, OrderBook>
    
    /**
     * Singleton bean for transactions list
     * Thread-safe CopyOnWriteArrayList for concurrent access
     * All classes that inject this will get the same instance
     */
    @Bean
    public List<Transaction> transactions() {
        return new CopyOnWriteArrayList<>();
    }
}

