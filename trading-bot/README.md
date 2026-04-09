# Trading Bot Microservice

A standalone Spring Boot microservice that simulates realistic cryptocurrency trading behavior on an exchange API. This bot system deploys multiple trading bots with different strategies to create organic market activity.

## Features

### 6 Trading Strategies

1. **Market Maker** - Places buy/sell orders around current price with a spread, profiting from the bid-ask spread while providing liquidity.

2. **Trend Follower** - Uses moving average crossover (5-min vs 15-min MA) to identify and follow market trends.

3. **Mean Reversion** - Assumes prices revert to their average. Buys dips below the mean, sells peaks above it.

4. **Momentum/FOMO** - Follows market momentum emotionally. FOMO buys on price spikes, panic sells on drops.

5. **Range Trader** - Identifies support and resistance levels, buying near support and selling near resistance.

6. **Scalper** - Makes rapid small trades exploiting bid-ask spread and micro price movements.

### Human-like Behavior Simulation

- **Reaction Delays**: 5-30 second thinking time before trades
- **Missed Opportunities**: 15-20% chance to skip trades
- **Order Size Variance**: ±30% variation in trade amounts
- **Price Slippage**: ±0.2% variance in order prices
- **Fat-finger Mistakes**: 5% chance of erroneous trades
- **Emotional States**: GREEDY, FEARFUL, FOMO, PANIC, NEUTRAL
- **Inactive Periods**: Simulates human breaks

### Safety Mechanisms

- **Circuit Breaker**: Stops all bots if error rate exceeds 30%
- **Max Loss Protection**: Auto-stops bots that lose >20% of balance
- **Rate Limiting**: Prevents API overload
- **Kill Switch**: Emergency stop endpoint for all bots

## Prerequisites

- Java 17+
- Maven 3.6+
- RabbitMQ (for order submission)
- PostgreSQL or H2 (for bot configuration storage)

## Quick Start

### 1. Clone and Build

```bash
cd trading-bot
mvn clean install
```

### 2. Configure

Edit `src/main/resources/application.yml`:

```yaml
trading-bot:
  exchange:
    api-url: http://localhost:8080/api  # Your exchange API
  rabbitmq:
    exchange: engine.test.exchange
    routing-key: engine.test.routing.key
```

### 3. Run

```bash
mvn spring-boot:run
```

The service starts on port **8081** by default.

### 4. Initialize Bots

On first startup, the system automatically creates 15 pre-configured bots:
- 4 Market Makers (different spread widths)
- 3 Trend Followers (different MA periods)
- 3 Mean Reversion traders (different thresholds)
- 2 Momentum/FOMO traders (high aggressiveness)
- 2 Range Traders
- 1 Scalper (high frequency)

## API Endpoints

### Bot Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/bots` | Create new bot |
| GET | `/api/bots` | List all bots |
| GET | `/api/bots/{id}` | Get bot details |
| PUT | `/api/bots/{id}/start` | Start bot |
| PUT | `/api/bots/{id}/stop` | Stop bot |
| DELETE | `/api/bots/{id}` | Delete bot |
| POST | `/api/bots/emergency-stop` | Kill switch - stop all |
| GET | `/api/bots/{id}/performance` | Get performance metrics |
| POST | `/api/bots/start-all` | Start all bots |
| GET | `/api/bots/strategies` | List available strategies |

### System Status

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/bots/status` | System status |
| GET | `/api/health` | Health check |
| GET | `/api/health/detailed` | Detailed health |
| POST | `/api/bots/reset-circuit-breaker` | Reset circuit breaker |

## Creating a Bot

```bash
curl -X POST http://localhost:8081/api/bots \
  -H "Content-Type: application/json" \
  -d '{
    "name": "MyTrendBot",
    "strategyType": "TREND_FOLLOWER",
    "tradingPair": "BTC/USDT",
    "initialBalance": 100000,
    "maxTradeAmount": 50,
    "minTradeAmount": 1,
    "aggressiveness": 0.6,
    "riskTolerance": 0.5,
    "tradingFrequencySeconds": 30,
    "startImmediately": true
  }'
```

### Strategy Types

- `MARKET_MAKER`
- `TREND_FOLLOWER`
- `MEAN_REVERSION`
- `MOMENTUM`
- `RANGE_TRADER`
- `SCALPER`

## Configuration Reference

```yaml
trading-bot:
  exchange:
    api-url: http://localhost:8080/api
    timeout: 5000
    max-retries: 3
    retry-delay-ms: 1000
  
  execution:
    thread-pool-size: 20
    rate-limit-per-minute: 100
    bot-execution-interval-ms: 15000
  
  strategy:
    market-maker-spread: 0.005      # 0.5% spread
    trend-ma-short: 5               # 5 minutes
    trend-ma-long: 15               # 15 minutes
    mean-reversion-period: 30       # 30 minutes
    momentum-threshold: 0.03        # 3% trigger
    scalper-profit-target: 0.002    # 0.2% profit
    range-buffer-percent: 0.01      # 1% buffer
  
  simulation:
    default-trading-pair: BTC/USDT
    min-order-size: 1
    max-order-size: 100
    initial-bot-balance: 100000.0
  
  safety:
    max-loss-percent: 0.20          # 20% max loss
    circuit-breaker-threshold: 0.30 # 30% error rate
    circuit-breaker-window-ms: 60000
    max-active-orders-per-bot: 10
  
  behavior:
    reaction-delay-min-ms: 5000     # 5 seconds
    reaction-delay-max-ms: 30000    # 30 seconds
    skip-trade-probability: 0.18    # 18% skip
    order-size-variance: 0.30       # ±30%
    price-slippage-percent: 0.002   # ±0.2%
    mistake-probability: 0.05       # 5% errors
    inactive-period-probability: 0.10
```

## Architecture

```
trading-bot/
├── src/main/java/com/trading/bot/
│   ├── config/           # Configuration classes
│   │   ├── TradingBotConfig.java
│   │   ├── RabbitMQConfig.java
│   │   ├── AsyncConfig.java
│   │   ├── CacheConfig.java
│   │   └── DataInitializationConfig.java
│   ├── controller/       # REST endpoints
│   │   ├── BotController.java
│   │   └── HealthController.java
│   ├── service/          # Core business logic
│   │   ├── BotManagerService.java
│   │   ├── BotExecutorService.java
│   │   ├── MarketDataService.java
│   │   └── OrderExecutionService.java
│   ├── strategy/         # Trading strategies
│   │   ├── TradingStrategy.java
│   │   ├── StrategyFactory.java
│   │   └── impl/
│   │       ├── MarketMakerStrategy.java
│   │       ├── TrendFollowerStrategy.java
│   │       ├── MeanReversionStrategy.java
│   │       ├── MomentumStrategy.java
│   │       ├── RangeTraderStrategy.java
│   │       └── ScalperStrategy.java
│   ├── model/            # Entities and DTOs
│   │   ├── Bot.java
│   │   ├── TradeDecision.java
│   │   ├── MarketData.java
│   │   └── dto/
│   ├── repository/       # JPA repositories
│   │   ├── BotRepository.java
│   │   └── BotTradeLogRepository.java
│   └── util/             # Utilities
│       ├── HumanBehaviorSimulator.java
│       └── PriceCalculator.java
└── src/main/resources/
    └── application.yml
```

## How It Works

1. **Scheduler Loop**: Every 15 seconds (configurable), the `BotExecutorService` runs a trading loop.

2. **For Each Active Bot**:
   - Check if bot is in an inactive period (break)
   - Verify max loss hasn't been exceeded
   - Update bot's emotional mood based on recent performance
   - Fetch market data (price, order book, indicators)
   - Run the bot's strategy to get a trade decision
   - Apply human behavior modifications (delays, variance, mistakes)
   - Execute the trade via RabbitMQ to the exchange

3. **Order Flow**:
   ```
   Bot → Strategy.analyze() → TradeDecision → HumanBehaviorSimulator 
   → OrderExecutionService → RabbitMQ → Exchange Engine
   ```

## Exchange Integration

Orders are sent to your exchange via RabbitMQ with this format:

```json
{
  "userId": "uuid",
  "price": 50000.00,
  "quantity": 10,
  "orderExecutionType": "LIMIT",
  "orderType": "BUY"
}
```

This matches your existing engine's `Message` model structure.

## Monitoring

### H2 Console

Access the H2 database console at: `http://localhost:8081/h2-console`
- JDBC URL: `jdbc:h2:file:./data/tradingbot`
- Username: `sa`
- Password: (empty)

### Actuator Endpoints

- `http://localhost:8081/actuator/health`
- `http://localhost:8081/actuator/metrics`

## Testing

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=MarketMakerStrategyTest
```

## Production Considerations

1. **Database**: Switch from H2 to PostgreSQL for production
2. **Monitoring**: Integrate with Prometheus/Grafana
3. **Logging**: Configure log aggregation (ELK stack)
4. **Security**: Add authentication to API endpoints
5. **Scaling**: Consider running multiple instances with different bot groups

## License

MIT License

## Support

For issues and questions, please open a GitHub issue.

