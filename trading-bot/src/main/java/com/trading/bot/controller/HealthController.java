package com.trading.bot.controller;

import com.trading.bot.service.InMemoryBotStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple health check endpoint.
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
@CrossOrigin(origins = "*")
public class HealthController {

    private final InMemoryBotStorage botStorage;

    public HealthController(InMemoryBotStorage botStorage) {
        this.botStorage = botStorage;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "trading-bot");
        status.put("totalBots", botStorage.count());
        status.put("activeBots", botStorage.findAll().stream()
                .filter(bot -> Boolean.TRUE.equals(bot.getIsActive()))
                .count());
        return ResponseEntity.ok(status);
    }
}
