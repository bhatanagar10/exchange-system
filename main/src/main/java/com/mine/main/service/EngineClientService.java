package com.mine.main.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Service to communicate with engine service
 * Only main service can contact engine for security purposes
 */
@Slf4j
@Service
public class EngineClientService {

    private final RestTemplate restTemplate;
    private final String engineApiUrl;

    public EngineClientService(
            RestTemplate restTemplate,
            @Value("${engine.api.url}") String engineApiUrl) {
        this.restTemplate = restTemplate;
        this.engineApiUrl = engineApiUrl;
        log.info("EngineClientService initialized with engine URL: {}", engineApiUrl);
    }

    /**
     * Cancel order via engine API
     */
    public Map<String, Object> cancelOrder(Long userId, String orderType) {
        try {
            String url = engineApiUrl + "/orders/" + userId + "/cancel";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, String> requestBody = Map.of("orderType", orderType);
            HttpEntity<Map<String, String>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    url, 
                    HttpMethod.POST, 
                    request, 
                    Map.class
            );
            
            log.info("Order cancellation request sent to engine - UserId: {}, OrderType: {}", userId, orderType);
            return response.getBody();
            
        } catch (Exception e) {
            log.error("Failed to cancel order via engine: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to cancel order", e);
        }
    }
}
