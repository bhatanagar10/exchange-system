package com.mine.websocket.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${stomp.broker.relay-host:localhost}")
    private String relayHost;

    @Value("${stomp.broker.relay-port:61613}")
    private int relayPort;

    @Value("${stomp.broker.client-login:admin}")
    private String clientLogin;

    @Value("${stomp.broker.client-passcode:admin}")
    private String clientPasscode;

    @Value("${stomp.broker.system-login:admin}")
    private String systemLogin;

    @Value("${stomp.broker.system-passcode:admin}")
    private String systemPasscode;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable RabbitMQ as external message broker via STOMP
        config.enableStompBrokerRelay("/topic", "/queue")
                .setRelayHost(relayHost)
                .setRelayPort(relayPort)
                .setClientLogin(clientLogin)
                .setClientPasscode(clientPasscode)
                .setSystemLogin(systemLogin)
                .setSystemPasscode(systemPasscode)
                .setVirtualHost("/");
        
        // Set application destination prefix
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register WebSocket endpoint at /ws
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        
        // Also register without SockJS for native WebSocket clients
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }
}

