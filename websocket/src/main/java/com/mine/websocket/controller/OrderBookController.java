package com.mine.websocket.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
public class OrderBookController {

    @Value("${websocket.url}")
    private String websocketUrl;

    @GetMapping("/orderbook")
    public ResponseEntity<String> orderbook() {
        try {
            Resource resource = new ClassPathResource("static/orderbook.html");
            String htmlContent = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            
            // Replace placeholder with actual WebSocket URL
            htmlContent = htmlContent.replace("${WEBSOCKET_URL}", websocketUrl);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_HTML);
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(htmlContent);
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
