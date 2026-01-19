# Order Sync Service

A standalone Spring Boot application that consumes order and transaction events from Kafka and synchronizes them to PostgreSQL database.

## Features

- ✅ **Kafka Consumer**: Listens to `order-db-sync` and `transaction-db-sync` topics
- ✅ **Synchronous Processing**: Processes events one by one (one event at a time)
- ✅ **Database Sync**: Persists orders and transactions to PostgreSQL
- ✅ **Halt on Failure**: Automatically halts processing when an event fails
- ✅ **Manual Resume**: REST API endpoint to manually resume processing
- ✅ **Failed Event Logging**: Logs all failed events to file for investigation

## Architecture

```
Engine Application → Kafka Topics → Order Sync Service → PostgreSQL Database
                       (order-db-sync)        (Consumes)        (Syncs)
                       (transaction-db-sync)  (One-by-one)
                       
Note: Separate from order-events topic (used for user order placement)
```

## Prerequisites

- Java 21
- Maven
- PostgreSQL (running on localhost:5432)
- Kafka (running on localhost:9072)

## Configuration

Edit `src/main/resources/application.properties`:

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/postgres
spring.datasource.username=postgres
spring.datasource.password=root

# Kafka
spring.kafka.bootstrap-servers=localhost:9072
```

## Database Schema

The application automatically creates the following tables:

### `orders` table
- `id` (PK, auto-generated)
- `order_id` (unique, from engine: ORD-{userId}-{timestamp})
- `user_id`
- `order_type` (BUY, SELL)
- `execution_type` (MARKET, LIMIT)
- `price`
- `quantity`
- `original_quantity`
- `status` (PENDING, PARTIALLY_FILLED, FILLED, CANCELLED)
- `market` (BTC, ETH)
- `created_at`
- `updated_at`

### `transactions` table
- `id` (PK, auto-generated)
- `transaction_id` (unique, from engine)
- `buy_order_id`
- `sell_order_id`
- `buyer_id`
- `seller_id`
- `execution_price`
- `execution_quantity`
- `created_at`

## Event Models

### OrderEvent
Published to `order-db-sync` topic:
```json
{
  "eventType": "ORDER_CREATED",
  "orderId": "ORD-1-1234567890",
  "userId": 1,
  "orderType": "BUY",
  "executionType": "LIMIT",
  "price": 50000.0,
  "quantity": 100,
  "originalQuantity": 100,
  "status": "PENDING",
  "market": "BTC",
  "timestamp": 1234567890
}
```

### TransactionEvent
Published to `transaction-db-sync` topic:
```json
{
  "transactionId": 1,
  "buyOrderId": "ORD-1-1234567890",
  "sellOrderId": "ORD-2-1234567891",
  "buyerId": 1,
  "sellerId": 2,
  "executionPrice": 50000.0,
  "executionQuantity": 50,
  "timestamp": 1234567892
}
```

## Running the Application

```bash
cd order-sync
mvn spring-boot:run
```

Or build and run:

```bash
mvn clean package
java -jar target/order-sync-0.0.1-SNAPSHOT.jar
```

## API Endpoints

### Get Processing Status
```
GET /api/admin/status
```

Response:
```json
{
  "enabled": true,
  "haltReason": null,
  "status": "RUNNING"
}
```

### Resume Processing (Manual Trigger)
```
POST /api/admin/resume
```

Response:
```json
{
  "message": "Processing resumed successfully",
  "status": {
    "enabled": true,
    "haltReason": null,
    "status": "RUNNING"
  }
}
```

### Get Failed Events Log Path
```
GET /api/admin/failed-events-log
```

Response:
```json
{
  "logFilePath": "C:\\path\\to\\failed-events\\failed-events.log",
  "message": "Failed events are logged to: ..."
}
```

## Behavior

### Normal Operation
1. Application starts and begins consuming events from Kafka
2. Events are processed **one by one** (synchronously)
3. Each event is processed within a database transaction
4. Event is acknowledged only after successful database write
5. Processing continues to next event

### On Failure
1. When an event fails to process:
   - Error is logged
   - Failed event is logged to `failed-events/failed-events.log`
   - Processing is **automatically halted**
   - Failed event is **not acknowledged** (will be redelivered)
   - Status changes to `HALTED`

2. While halted:
   - No new events are processed
   - Events are rejected (not acknowledged)
   - Events remain in Kafka and will be redelivered when processing resumes

### Manual Recovery
1. Investigate the failed event by checking:
   - Application logs
   - `failed-events/failed-events.log` file
   - Database for issues

2. Fix the underlying issue (database connection, schema, etc.)

3. Resume processing via REST API:
   ```bash
   curl -X POST http://localhost:8083/api/admin/resume
   ```

4. Processing resumes and failed event is retried

## Failed Events Log

Failed events are logged to: `failed-events/failed-events.log`

Format:
```
[2026-01-11 16:30:45.123] FAILED ORDER EVENT | Type: ORDER_CREATED | OrderId: ORD-1-1234567890 | UserId: 1 | Error: Connection refused | Event: OrderEvent(...)
```

## Integration with Engine

The engine application should publish events to Kafka topics:

1. **Order DB Sync Events** (`order-db-sync` topic):
   - When order is created: `ORDER_CREATED`
   - When order quantity changes: `ORDER_UPDATED`
   - When order is cancelled: `ORDER_CANCELLED`
   - When order is filled: `ORDER_FILLED`

2. **Transaction DB Sync Events** (`transaction-db-sync` topic):
   - When a trade executes: `TransactionEvent`

**Note**: These topics are separate from `order-events` topic which is used for user order placement.

## Kafka Topics

Topics are auto-created by the engine application. If manual creation is needed:

```bash
# Order DB sync topic
kafka-topics --create --topic order-db-sync --bootstrap-server localhost:9072 --partitions 1 --replication-factor 1

# Transaction DB sync topic
kafka-topics --create --topic transaction-db-sync --bootstrap-server localhost:9072 --partitions 1 --replication-factor 1
```

## Monitoring

- Check application logs for processing status
- Monitor `/api/admin/status` endpoint
- Review `failed-events/failed-events.log` for errors
- Monitor Kafka consumer lag
- Check database for data consistency

## Notes

- **Synchronous Processing**: Events are processed one at a time to ensure order and prevent database contention
- **Manual Acknowledgment**: Events are only acknowledged after successful database write
- **Idempotency**: Duplicate events are handled (checks for existing records)
- **Halt on Failure**: Processing stops on first failure to prevent cascading errors
- **Manual Recovery**: Requires manual intervention to resume after failure


