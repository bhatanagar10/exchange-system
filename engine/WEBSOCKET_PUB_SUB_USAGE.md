# WebSocket Pub-Sub Integration Usage

## Architecture

```
Engine Application                    RabbitMQ                    Websocket Server                    WebSocket Clients
     |                                    |                                |                                  |
     | 1. Publish message                 |                                |                                  |
     |---------------------------------->|                                |                                  |
     |                                    | 2. Route to queue             |                                  |
     |                                    |------------------------------>|                                  |
     |                                    |                                | 3. Consume & Broadcast           |
     |                                    |                                |--------------------------------->|
     |                                    |                                |                                  |
```

## How It Works

1. **Engine** publishes messages to RabbitMQ topic exchange `engine.websocket.exchange`
2. **RabbitMQ** routes messages to the queue `websocket.server.queue` (bound with routing key `engine.websocket.routing.key`)
3. **Websocket Server** consumes messages from the queue and broadcasts via STOMP to `/topic/public`
4. **WebSocket Clients** receive the messages in real-time

## Usage in Engine Application

### Inject the Service

```java
@Service
public class YourService {
    
    private final WebSocketPublisherService webSocketPublisherService;
    
    public YourService(WebSocketPublisherService webSocketPublisherService) {
        this.webSocketPublisherService = webSocketPublisherService;
    }
    
    public void someMethod() {
        // Send any object - it will be converted to JSON
        Map<String, Object> message = new HashMap<>();
        message.put("sender", "Engine");
        message.put("content", "Stock price updated: $150.25");
        message.put("type", "CHAT");
        
        webSocketPublisherService.publishToWebSocket(message);
    }
}
```

### Example: Send ChatMessage DTO

If you want to send a structured message, you can create a DTO that matches the ChatMessage format:

```java
Map<String, Object> message = new HashMap<>();
message.put("sender", "Engine");
message.put("content", "Order executed: BUY 100 shares at $150.25");
message.put("type", "CHAT");

webSocketPublisherService.publishToWebSocket(message);
```

### Example: Send Custom Object

```java
// Any object will be converted to JSON
MyCustomObject data = new MyCustomObject("value1", "value2");
webSocketPublisherService.publishToWebSocket(data);
```

The websocket server will convert it to ChatMessage format automatically.

## Configuration

### Engine Side
- **Exchange**: `engine.websocket.exchange` (Topic Exchange)
- **Routing Key**: `engine.websocket.routing.key`

### Websocket Side
- **Queue**: `websocket.server.queue` (durable)
- **Exchange**: `engine.websocket.exchange` (Topic Exchange)
- **Routing Key**: `engine.websocket.routing.key`
- **STOMP Broadcast**: `/topic/public`

## Testing

1. Start RabbitMQ
2. Start Websocket Server
3. Connect WebSocket clients (browser at http://localhost:8081)
4. From Engine application, call:
   ```java
   webSocketPublisherService.publishToWebSocket("Test message");
   ```
5. All connected WebSocket clients should receive the message

