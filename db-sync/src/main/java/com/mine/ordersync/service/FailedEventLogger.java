package com.mine.ordersync.service;

import com.mine.ordersync.model.OrderEvent;
import com.mine.ordersync.model.TransactionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Service for logging failed events to file
 */
@Slf4j
@Service
public class FailedEventLogger {
    
    private static final String FAILED_EVENTS_DIR = "failed-events";
    private static final String FAILED_EVENTS_FILE = "failed-events.log";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(
            "yyyy-MM-dd HH:mm:ss.SSS");
    
    private final Path failedEventsPath;
    
    public FailedEventLogger() {
        this.failedEventsPath = Paths.get(FAILED_EVENTS_DIR, FAILED_EVENTS_FILE);
        initializeLogFile();
    }
    
    private void initializeLogFile() {
        try {
            Files.createDirectories(failedEventsPath.getParent());
            if (!Files.exists(failedEventsPath)) {
                Files.createFile(failedEventsPath);
            }
        } catch (IOException e) {
            log.error("Error initializing failed events log file", e);
        }
    }
    
    /**
     * Log failed order event
     */
    public void logFailedOrderEvent(OrderEvent event, Exception error) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String logEntry = String.format(
            "[%s] FAILED ORDER EVENT | Type: %s | OrderId: %s | UserId: %d | Error: %s | Event: %s%n",
            timestamp,
            event.getEventType(),
            event.getOrderId(),
            event.getUserId(),
            error.getMessage(),
            event.toString()
        );
        
        writeToFile(logEntry);
        log.error("Failed order event logged: {} - {}", event.getOrderId(), error.getMessage());
    }
    
    /**
     * Log failed transaction event
     */
    public void logFailedTransactionEvent(TransactionEvent event, Exception error) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String logEntry = String.format(
            "[%s] FAILED TRANSACTION EVENT | TransactionId: %d | BuyOrderId: %s | SellOrderId: %s | Error: %s | Event: %s%n",
            timestamp,
            event.getTransactionId(),
            event.getBuyOrderId(),
            event.getSellOrderId(),
            error.getMessage(),
            event.toString()
        );
        
        writeToFile(logEntry);
        log.error("Failed transaction event logged: {} - {}", event.getTransactionId(), error.getMessage());
    }
    
    private void writeToFile(String logEntry) {
        try {
            Files.write(
                failedEventsPath,
                logEntry.getBytes(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            log.error("Error writing to failed events log file", e);
        }
    }
    
    /**
     * Get the path to the failed events log file
     */
    public String getFailedEventsLogPath() {
        return failedEventsPath.toAbsolutePath().toString();
    }
}


