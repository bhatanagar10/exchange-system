package com.trading.bot.service;

import com.trading.bot.model.Bot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory storage for bots - simplified version.
 */
@Slf4j
@Service
public class InMemoryBotStorage {

    private final Map<Long, Bot> botsById = new ConcurrentHashMap<>();
    private final Map<Long, Bot> botsByUserId = new ConcurrentHashMap<>();

    /**
     * Save a bot (create or update).
     */
    public Bot save(Bot bot) {
        if (bot.getId() == null) {
            bot.generateId();
        }
        botsById.put(bot.getId(), bot);
        if (bot.getUserId() != null) {
            botsByUserId.put(bot.getUserId(), bot);
        }
        return bot;
    }

    /**
     * Find bot by ID.
     */
    public Optional<Bot> findById(Long id) {
        return Optional.ofNullable(botsById.get(id));
    }

    /**
     * Find bot by user ID.
     */
    public Optional<Bot> findByUserId(Long userId) {
        return Optional.ofNullable(botsByUserId.get(userId));
    }

    /**
     * Find all bots.
     */
    public List<Bot> findAll() {
        return new ArrayList<>(botsById.values());
    }

    /**
     * Delete all bots.
     */
    public void deleteAll() {
        botsById.clear();
        botsByUserId.clear();
    }

    /**
     * Count all bots.
     */
    public long count() {
        return botsById.size();
    }
}



