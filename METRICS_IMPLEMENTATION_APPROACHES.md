# Metrics & Analytics Implementation Approaches

This document outlines different approaches for implementing system metrics and analytics for your cryptocurrency exchange trading engine.

## Required Metrics

1. **Number of requests served** - Total HTTP requests to all endpoints
2. **Number of orders placed** - Total orders processed (via Kafka/REST)
3. **Average latency of orders processed** - Time from order receipt to completion

---

## Approach 1: Spring Boot Actuator + Micrometer (Recommended for Production)

### Overview
Use Spring Boot Actuator with Micrometer to expose metrics that can be scraped by Prometheus or viewed via Actuator endpoints.

### Pros
- ✅ Built into Spring Boot ecosystem
- ✅ Standard metrics format (Prometheus, InfluxDB, etc.)
- ✅ Low overhead
- ✅ Rich set of built-in metrics (HTTP, JVM, etc.)
- ✅ Can integrate with Grafana for visualization

### Cons
- ⚠️ Requires additional dependencies
- ⚠️ Need to configure Prometheus/Grafana for full visualization

### Implementation Steps
1. Add dependencies: `spring-boot-starter-actuator`, `micrometer-registry-prometheus`
2. Enable metrics endpoints in `application.properties`
3. Create custom metrics using `MeterRegistry`
4. Add `@Timed` annotations or manual timing for order processing
5. Expose `/actuator/metrics` and `/actuator/prometheus` endpoints

### Metrics Exposed
- `http.server.requests` - Total requests, latency, status codes
- `orders.placed.total` - Counter for orders placed
- `orders.processing.duration` - Timer for order processing latency
- `orders.by.type` - Orders by type (BUY/SELL)
- `orders.by.status` - Orders by status (FILLED/PARTIALLY_FILLED/PENDING)

---

## Approach 2: Custom Metrics Service (Simple & Lightweight)

### Overview
Create a simple in-memory metrics service that tracks counters and latencies. Expose via REST endpoint.

### Pros
- ✅ No external dependencies
- ✅ Simple to implement
- ✅ Fast (in-memory)
- ✅ Easy to understand

### Cons
- ⚠️ Metrics lost on restart
- ⚠️ No historical data
- ⚠️ Not suitable for distributed systems
- ⚠️ Limited visualization options

### Implementation Steps
1. Create `MetricsService` with atomic counters and latency tracking
2. Inject into controllers and services
3. Track metrics at key points (request entry, order processing start/end)
4. Create REST endpoint `/api/metrics` to expose metrics
5. Use `AtomicLong` for counters, `ConcurrentLinkedQueue` for latency samples

### Metrics Structure
```java
{
  "totalRequests": 12345,
  "totalOrdersPlaced": 5678,
  "averageOrderLatencyMs": 12.5,
  "ordersByType": {
    "BUY": 3000,
    "SELL": 2678
  },
  "ordersByStatus": {
    "FILLED": 4000,
    "PARTIALLY_FILLED": 1500,
    "PENDING": 178
  }
}
```

---

## Approach 3: Redis-Based Metrics (Distributed & Persistent)

### Overview
Store metrics in Redis using counters and sorted sets for time-series data. Already using Redis for idempotency.

### Pros
- ✅ Persistent across restarts
- ✅ Works with multiple service instances
- ✅ Fast (Redis is in-memory)
- ✅ Can implement time-windowed metrics (last hour, day, etc.)
- ✅ Already have Redis infrastructure

### Cons
- ⚠️ Requires Redis connection for every metric update
- ⚠️ Need to implement aggregation logic
- ⚠️ Redis memory usage grows over time

### Implementation Steps
1. Create `RedisMetricsService` using existing Redis connection
2. Use Redis `INCR` for counters
3. Use Redis sorted sets (ZADD/ZRANGE) for time-series latency data
4. Implement sliding window aggregation
5. Expose via REST endpoint or integrate with Actuator

### Redis Keys Structure
```
metrics:requests:total
metrics:orders:total
metrics:orders:type:BUY
metrics:orders:type:SELL
metrics:latency:orders:timestamps (sorted set)
```

---

## Approach 4: Database-Based Metrics (Historical Analysis)

### Overview
Store metrics in PostgreSQL database with time-series tables. Query for analytics and reporting.

### Pros
- ✅ Full historical data
- ✅ Complex queries and aggregations
- ✅ Can join with order/transaction data
- ✅ SQL-based analytics
- ✅ Already using PostgreSQL

### Cons
- ⚠️ Higher latency (database writes)
- ⚠️ Database load increases
- ⚠️ Need to manage table growth
- ⚠️ More complex implementation

### Implementation Steps
1. Create metrics tables: `request_metrics`, `order_metrics`, `latency_metrics`
2. Use async writes or batch inserts to reduce DB load
3. Create indexes for time-based queries
4. Implement aggregation queries for reporting
5. Create REST endpoint for metrics queries

### Database Schema
```sql
CREATE TABLE order_metrics (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(255),
    user_id BIGINT,
    order_type VARCHAR(10),
    status VARCHAR(20),
    latency_ms BIGINT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE request_metrics (
    id BIGSERIAL PRIMARY KEY,
    endpoint VARCHAR(255),
    method VARCHAR(10),
    status_code INT,
    latency_ms BIGINT,
    created_at TIMESTAMP DEFAULT NOW()
);
```

---

## Approach 5: Prometheus + Grafana Stack (Full Observability)

### Overview
Complete observability stack with Prometheus for metrics collection and Grafana for visualization.

### Pros
- ✅ Industry-standard solution
- ✅ Rich visualization dashboards
- ✅ Alerting capabilities
- ✅ Time-series database built-in
- ✅ Scalable and production-ready

### Cons
- ⚠️ Requires additional infrastructure (Prometheus + Grafana)
- ⚠️ More complex setup
- ⚠️ Learning curve

### Implementation Steps
1. Add Micrometer Prometheus to Spring Boot
2. Deploy Prometheus server (Docker)
3. Configure Prometheus to scrape Spring Boot metrics
4. Deploy Grafana (Docker)
5. Create dashboards for:
   - Request rate and latency
   - Order processing metrics
   - System health

### Docker Compose Addition
```yaml
prometheus:
  image: prom/prometheus:latest
  ports:
    - "9090:9090"
  volumes:
    - ./prometheus.yml:/etc/prometheus/prometheus.yml

grafana:
  image: grafana/grafana:latest
  ports:
    - "3000:3000"
  environment:
    - GF_SECURITY_ADMIN_PASSWORD=admin
```

---

## Approach 6: Hybrid Approach (Recommended)

### Overview
Combine multiple approaches for different use cases:
- **In-memory metrics** for real-time dashboard
- **Redis** for distributed metrics and short-term persistence
- **Database** for historical analysis and reporting
- **Actuator/Micrometer** for standard metrics

### Implementation Strategy
1. **Real-time metrics**: Custom service with in-memory counters (fast, low latency)
2. **Short-term persistence**: Redis for metrics over last 24 hours
3. **Long-term storage**: PostgreSQL for historical analysis
4. **Standard metrics**: Actuator for HTTP, JVM metrics
5. **Visualization**: Simple REST endpoint + optional Grafana

---

## Recommended Implementation Plan

### Phase 1: Quick Start (Approach 2 - Custom Service)
1. Create `MetricsService` with in-memory counters
2. Add metrics tracking to:
   - `OrderController` (request counting)
   - `StockService.processOrder()` (order counting + latency)
   - `KafkaMessageConsumer` (message processing metrics)
3. Create `/api/metrics` endpoint
4. Test and verify metrics

### Phase 2: Enhancement (Approach 1 - Actuator)
1. Add Spring Boot Actuator
2. Create custom Micrometer metrics
3. Expose Prometheus endpoint
4. Add `@Timed` annotations

### Phase 3: Production (Approach 5 - Full Stack)
1. Deploy Prometheus
2. Deploy Grafana
3. Create dashboards
4. Set up alerting

---

## Metrics Collection Points

### Request Metrics
- **Location**: `@RestController` methods or `@ControllerAdvice`
- **Track**: Total requests, endpoint, method, status code, latency

### Order Metrics
- **Location**: `StockService.processOrder()`, `placeBuyOrder()`, `placeSellOrder()`
- **Track**: Order count, order type, status, processing latency

### Message Processing Metrics
- **Location**: `KafkaMessageConsumer.consumeOrderEvent()`
- **Track**: Messages consumed, processing latency, errors

---

## Example Metrics Endpoint Response

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "requests": {
    "total": 125000,
    "lastMinute": 150,
    "lastHour": 8500,
    "byEndpoint": {
      "/api/market/orderbook/BTC": 50000,
      "/api/orders/{userId}/cancel": 2000,
      "/api/market/price/BTC": 73000
    }
  },
  "orders": {
    "total": 45000,
    "lastMinute": 45,
    "lastHour": 2500,
    "byType": {
      "BUY": 23000,
      "SELL": 22000
    },
    "byStatus": {
      "FILLED": 35000,
      "PARTIALLY_FILLED": 8000,
      "PENDING": 2000
    }
  },
  "latency": {
    "averageOrderProcessingMs": 15.5,
    "p50OrderProcessingMs": 12,
    "p95OrderProcessingMs": 35,
    "p99OrderProcessingMs": 80,
    "averageRequestMs": 8.2,
    "p95RequestMs": 25
  }
}
```

---

## Next Steps

1. **Choose an approach** based on your needs:
   - Quick prototype: Approach 2 (Custom Service)
   - Production ready: Approach 1 or 5 (Actuator/Prometheus)
   - Distributed system: Approach 3 (Redis)

2. **I can implement any of these approaches** - just let me know which one you prefer!

3. **Recommended**: Start with Approach 2 for quick results, then enhance with Approach 1 for production.
