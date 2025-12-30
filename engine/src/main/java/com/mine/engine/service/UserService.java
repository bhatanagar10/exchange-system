package com.mine.engine.service;

import com.mine.engine.entity.UserEntity;
import com.mine.engine.entity.UserMarketEntity;
import com.mine.engine.model.Market;
import com.mine.engine.model.User;
import com.mine.engine.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * UserService - Manages user creation and retrieval
 * Handles user data operations separate from trading operations
 * Persists users to database and maintains in-memory cache
 * Converts between UserEntity (database) and User (cache model)
 */
@Slf4j
@Service
public class UserService {

    private final Map<UUID, User> userData; // Injected singleton map for in-memory cache (User model)
    private final UserRepository userRepository; // Repository for database operations (UserEntity)

    /**
     * Constructor with dependency injection of singleton userData map and repository
     * All classes that inject userData will get the same instance
     */
    public UserService(Map<UUID, User> userData, UserRepository userRepository) {
        this.userData = userData;
        this.userRepository = userRepository;
        log.info("UserService initialized with singleton userData map and UserRepository");
    }
    
    /**
     * Convert UserEntity (database model) to User (cache model)
     * Converts List<UserMarketEntity> to Map<Market, Long>
     */
    private User toUserModel(UserEntity entity) {
        if (entity == null) {
            return null;
        }
        User user = new User();
        user.setId(entity.getId());
        user.setCash(entity.getCash());
        
        // Convert List<UserMarketEntity> to Map<Market, Long>
        Map<Market, Long> marketsMap = new HashMap<>();
        if (entity.getUserMarkets() != null) {
            for (UserMarketEntity userMarket : entity.getUserMarkets()) {
                marketsMap.put(userMarket.getMarket(), userMarket.getStocks());
            }
        }
        
        // Ensure all markets are initialized with 0 if not present
        for (Market market : Market.values()) {
            marketsMap.putIfAbsent(market, 0L);
        }
        
        user.setMarkets(marketsMap);
        return user;
    }

    /**
     * Add a new user with initial cash and optional market quantities
     * Saves user to database first, then adds to in-memory cache
     * 
     * @param initialCash Initial cash balance for the user
     * @return The created user model with database-generated ID
     * @throws Exception if user already exists
     */
    @Transactional
    public UUID addUser(double initialCash) {
        
        // Create user entity for database
        UserEntity userEntity = new UserEntity();
        userEntity.setCash(initialCash);
        
        // Initialize user markets with 0 quantity for all markets
        List<UserMarketEntity> userMarkets = new ArrayList<>();
        for (Market market : Market.values()) {
            UserMarketEntity userMarket = new UserMarketEntity();
            userMarket.setUser(userEntity);
            userMarket.setMarket(market);
            userMarket.setStocks(0L);

            userMarkets.add(userMarket);
        }
        userEntity.setUserMarkets(userMarkets);

        // Save user entity to database (this will also save user markets due to cascade)
        userEntity = userRepository.save(userEntity);
        
        // Get the database-generated or set ID
        UUID dbUserId = userEntity.getId();
        log.info("User saved to database - ID: {}, Initial Cash: {}", dbUserId, initialCash);
        
        // Convert entity to cache model and add to in-memory cache
        User userModel = toUserModel(userEntity);
        userData.put(dbUserId, userModel);
        
        log.info("User added to cache - ID: {}, Initial Cash: {}, Markets: {}", 
                dbUserId, initialCash, userModel.getMarkets());
        
        return dbUserId;
    }
    
    /**
     * Get user by ID
     * 
     * @param userId The UUID of the user
     * @return The user object, or null if not found
     */
    public User getUser(UUID userId) {
        return userData.get(userId);
    }
    
}
