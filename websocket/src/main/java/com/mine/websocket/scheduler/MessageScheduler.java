package com.mine.websocket.scheduler;

import com.mine.websocket.dto.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
public class MessageScheduler {

    private final SimpMessagingTemplate messagingTemplate;
    private int messageCount = 0;

    public MessageScheduler(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

//    @Scheduled(fixedRate = 1000) // Send every 1 second (1000 milliseconds)
    public void sendDummyMessage() {
        System.out.println("Inside sendDummyMessage method");
        messageCount++;
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        
        ChatMessage dummyMessage = new ChatMessage(
            "System",
            "Dummy message #" + messageCount + " at " + timestamp,
            ChatMessage.MessageType.CHAT
        );
        
        // Send directly to /topic/public
        messagingTemplate.convertAndSend("/topic/public", dummyMessage);
        
        log.info("Sent dummy message #{} to /topic/public", messageCount);
    }
}

