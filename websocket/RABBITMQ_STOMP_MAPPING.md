# RabbitMQ STOMP Mapping

When using Spring's STOMP Broker Relay with RabbitMQ, the STOMP destinations are automatically mapped to RabbitMQ exchanges, queues, and routing keys.

## For `/topic/public` Destination

### Exchange
- **Exchange Name**: `amq.topic` (RabbitMQ's default topic exchange)
- **Exchange Type**: Topic Exchange
- **Auto-created**: Yes (by RabbitMQ)

### Routing Key
- **Routing Key**: `topic.public` 
- **Pattern**: STOMP destination `/topic/public` is converted to routing key `topic.public`
- The `/` is replaced with `.` and the leading `/` is removed

### Queue
- **Queue Name**: Dynamically generated per subscription
- **Pattern**: `stomp-subscription-<session-id>-<subscription-id>`
- **Example**: `stomp-subscription-abc123-sub-0`
- **Auto-created**: Yes (created when a client subscribes)
- **Auto-deleted**: Yes (deleted when client disconnects)

### Binding
- **Binding Key**: `topic.public`
- **Auto-created**: Yes (when subscription is created)
- **Pattern**: Queue is bound to `amq.topic` exchange with routing key `topic.public`

## How It Works

1. **Client subscribes** to `/topic/public`
   - RabbitMQ STOMP plugin creates a temporary queue
   - Queue is bound to `amq.topic` exchange with routing key `topic.public`

2. **Message sent** to `/topic/public`
   - Spring converts STOMP destination to routing key `topic.public`
   - Message is published to `amq.topic` exchange with routing key `topic.public`
   - All queues bound with matching routing key receive the message

3. **Client disconnects**
   - Temporary queue is automatically deleted

## Viewing in RabbitMQ Management UI

1. Go to `http://localhost:15672` (admin/admin)
2. **Exchanges tab**: Look for `amq.topic` exchange
3. **Queues tab**: Look for queues starting with `stomp-subscription-`
4. **Bindings**: Check bindings for `amq.topic` exchange

## STOMP Destination to Routing Key Mapping

| STOMP Destination | Routing Key | Exchange |
|-------------------|-------------|----------|
| `/topic/public` | `topic.public` | `amq.topic` |
| `/topic/chat` | `topic.chat` | `amq.topic` |
| `/queue/private` | `queue.private` | `amq.direct` |
| `/queue/notifications` | `queue.notifications` | `amq.direct` |

## Notes

- **Topics** (`/topic/*`) use `amq.topic` exchange (topic exchange)
- **Queues** (`/queue/*`) use `amq.direct` exchange (direct exchange)
- Queues are **temporary** and **auto-deleted** when clients disconnect
- Multiple clients subscribing to the same topic get **separate queues** (fan-out pattern)
- All queues bound to the same routing key receive the message (pub-sub pattern)

