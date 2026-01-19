package com.mine.ordersync.controller;

import com.mine.ordersync.service.FailedEventLogger;
import com.mine.ordersync.service.ProcessingControlService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin controller for manual control of event processing
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    
    private final ProcessingControlService processingControlService;
    private final FailedEventLogger failedEventLogger;
    
    public AdminController(ProcessingControlService processingControlService,
                          FailedEventLogger failedEventLogger) {
        this.processingControlService = processingControlService;
        this.failedEventLogger = failedEventLogger;
    }
    
    /**
     * Get current processing status
     * GET /api/admin/status
     */
    @GetMapping("/status")
    public ResponseEntity<ProcessingControlService.ProcessingStatus> getStatus() {
        return ResponseEntity.ok(processingControlService.getStatus());
    }
    
    /**
     * Resume processing (manually trigger)
     * POST /api/admin/resume
     */
    @PostMapping("/resume")
    public ResponseEntity<ResumeResponse> resumeProcessing() {
        processingControlService.resumeProcessing();
        
        ResumeResponse response = new ResumeResponse();
        response.setMessage("Processing resumed successfully");
        response.setStatus(processingControlService.getStatus());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get failed events log file path
     * GET /api/admin/failed-events-log
     */
    @GetMapping("/failed-events-log")
    public ResponseEntity<FailedEventsLogResponse> getFailedEventsLogPath() {
        FailedEventsLogResponse response = new FailedEventsLogResponse();
        response.setLogFilePath(failedEventLogger.getFailedEventsLogPath());
        response.setMessage("Failed events are logged to: " + response.getLogFilePath());
        
        return ResponseEntity.ok(response);
    }
    
    @Data
    public static class ResumeResponse {
        private String message;
        private ProcessingControlService.ProcessingStatus status;
    }
    
    @Data
    public static class FailedEventsLogResponse {
        private String logFilePath;
        private String message;
    }
}


